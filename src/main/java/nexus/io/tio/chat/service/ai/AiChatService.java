package nexus.io.tio.chat.service.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.SseUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * AI 对话：会话管理、消息记录、同步与流式回答
 */
public class AiChatService {
  public static final String CONVERSATION = "ai_chat_conversation";
  public static final String MESSAGE = "ai_chat_message";
  private static final String TITLE_DEFAULT = "新对话";
  private static final int MAX_CONTENT = 10240;
  private static final int MSG_TEXT = 1;

  private AiCrudService crud() {
    return Aop.get(AiCrudService.class);
  }

  // ---------------------------------------------------------------- 会话

  public Kv createConversation(long uid, Long roleId, Long knowledgeId) {
    Kv role = roleId == null ? null : Db.findFirstMap("select * from ai_chat_role where id=? and deleted=0", roleId);
    ChatErrors.require(roleId == null || role != null, "聊天角色不存在");
    Long modelId = role != null && role.getLong("model_id") != null ? role.getLong("model_id")
        : Db.queryLong("select id from ai_model where deleted=0 and status=0 and type=1 order by sort, id limit 1");
    ChatErrors.require(modelId != null, "必须找到默认模型");
    Kv model = Db.findFirstMap("select * from ai_model where id=?", modelId);
    Kv data = Kv.by("userId", uid).set("pinned", false).set("modelId", modelId).set("model", model.getStr("model"))
        .set("temperature", model.get("temperature")).set("maxTokens", model.get("max_tokens"))
        .set("maxContexts", model.get("max_contexts")).set("tokenUsage", 0);
    if (role != null) {
      data.set("title", role.getStr("name")).set("roleId", role.getLong("id")).set("systemMessage", role.getStr("system_message"));
    } else {
      data.set("title", TITLE_DEFAULT);
    }
    long id = crud().create(CONVERSATION, data, true);
    return conversation(uid, id);
  }

  public boolean updateConversation(long uid, Map<String, Object> body) {
    Long id = Values.toLong(body.get("id"));
    Kv conversation = crud().get(CONVERSATION, id, uid);
    ChatErrors.require(conversation != null, "对话不存在");
    Map<String, Object> data = new LinkedHashMap<>(body);
    if (body.get("modelId") != null) {
      Kv model = Db.findFirstMap("select * from ai_model where id=? and deleted=0", Values.toLong(body.get("modelId")));
      ChatErrors.require(model != null, "模型不存在");
      data.put("model", model.getStr("model"));
    }
    if (Values.isTrue(body.get("pinned"))) {
      data.put("pinnedTime", LocalDateTime.now());
    }
    return crud().update(CONVERSATION, data, uid);
  }

  public List<Kv> myConversations(long uid) {
    List<Kv> list = crud().list(CONVERSATION, "user_id=? order by pinned desc, update_time desc", uid);
    for (Kv conversation : list) {
      fillConversation(conversation);
    }
    return list;
  }

  public Kv conversation(long uid, Long id) {
    Kv conversation = crud().get(CONVERSATION, id, uid);
    if (conversation != null) {
      fillConversation(conversation);
    }
    return conversation;
  }

  private void fillConversation(Kv conversation) {
    // tokenUsage 是 bigint，按数字返回（客户端判断 typeof number）
    Integer tokenUsage = Values.toInt(conversation.get("tokenUsage"));
    conversation.set("tokenUsage", tokenUsage == null ? 0 : tokenUsage);
    Long roleId = conversation.getLong("roleId");
    if (roleId != null) {
      conversation.set("roleAvatar", Db.queryStr("select avatar from ai_chat_role where id=?", roleId));
    }
    conversation.set("modelName", Db.queryStr("select name from ai_model where id=?", conversation.getLong("modelId")));
    conversation.set("messageCount",
        Db.queryLong("select count(*) from ai_chat_message where conversation_id=? and deleted=0", conversation.getLong("id")).intValue());
  }

  public boolean deleteConversations(long uid, List<Long> ids) {
    for (Long id : ids) {
      crud().delete(CONVERSATION, id, uid);
      Db.update("update ai_chat_message set deleted=1 where conversation_id=?", id);
    }
    return true;
  }

  public boolean deleteUnpinned(long uid) {
    Db.update("update ai_chat_message set deleted=1 where conversation_id in (select id from ai_chat_conversation where user_id=? and pinned=0)",
        uid);
    Db.update("update ai_chat_conversation set deleted=1 where user_id=? and pinned=0", uid);
    return true;
  }

  // ---------------------------------------------------------------- 消息

  public List<Kv> messages(long uid, Long conversationId) {
    Kv conversation = crud().get(CONVERSATION, conversationId, uid);
    if (conversation == null) {
      return new ArrayList<>();
    }
    List<Kv> list = crud().list(MESSAGE, "conversation_id=? order by id", conversationId);
    String avatar = conversation.getLong("roleId") == null ? null
        : Db.queryStr("select avatar from ai_chat_role where id=?", conversation.getLong("roleId"));
    for (Kv message : list) {
      message.set("roleAvatar", avatar).set("segments", List.of());
    }
    return list;
  }

  public boolean deleteMessage(long uid, Long id) {
    return crud().delete(MESSAGE, id, uid);
  }

  public boolean deleteMessagesByConversation(long uid, Long conversationId) {
    return Db.update("update ai_chat_message set deleted=1 where conversation_id=? and user_id=?", conversationId, uid) >= 0;
  }

  private long saveMessage(Kv conversation, Long replyId, long uid, String type, String content, boolean useContext, int msgType) {
    long id = SnowflakeIdUtils.id();
    Row row = Row.by("id", id).set("conversation_id", conversation.getLong("id")).set("reply_id", replyId).set("user_id", uid)
        .set("role_id", conversation.getLong("roleId")).set("type", type).set("model", conversation.getStr("model"))
        .set("model_id", conversation.getLong("modelId")).set("content", clip(content)).set("use_context", useContext)
        .set("msg_type", msgType).set("creator", String.valueOf(uid)).set("updater", String.valueOf(uid))
        .set("create_time", LocalDateTime.now()).set("update_time", LocalDateTime.now()).set("deleted", 0).set("tenant_id", 1L);
    Db.save(MESSAGE, row);
    return id;
  }

  /**
   * content 列为 varchar(10240)，超长的回答截断保存
   */
  private static String clip(String content) {
    return content != null && content.length() > MAX_CONTENT ? content.substring(0, MAX_CONTENT) : content;
  }

  private static String emptyToNull(CharSequence text) {
    return text == null || text.length() == 0 ? null : text.toString();
  }

  /**
   * 保存回答并累计会话 token 用量；totalTokens 为空时按字符数估算
   */
  private void finishAnswer(Long conversationId, long receiveId, String answer, String reasoning, Integer totalTokens,
      int promptChars) {
    Db.update("update ai_chat_message set content=?, reasoning_content=? where id=?", clip(answer), reasoning, receiveId);
    int tokens = totalTokens != null ? totalTokens : estimateTokens(promptChars + answer.length() + (reasoning == null ? 0 : reasoning.length()));
    Db.update("update ai_chat_conversation set token_usage=ifnull(token_usage,0)+?, update_time=now() where id=?", tokens, conversationId);
  }

  private static int estimateTokens(int chars) {
    return (chars + 1) / 2;
  }

  private static int chars(List<Map<String, Object>> messages) {
    int total = 0;
    for (Map<String, Object> message : messages) {
      total += String.valueOf(message.get("content")).length();
    }
    return total;
  }

  private Kv messageVo(long id) {
    Kv row = crud().get(MESSAGE, id, null);
    return Kv.by("id", id).set("type", row.getStr("type")).set("msgType", row.get("msgType")).set("content", row.getStr("content"))
        .set("reasoningContent", row.getStr("reasoningContent")).set("segmentIds", row.get("segmentIds"))
        .set("segments", List.of()).set("createTime", row.get("createTime"));
  }

  /**
   * 组装上下文：系统提示词、知识库片段、最近若干轮历史与本次问题
   */
  private List<Map<String, Object>> prompt(Kv conversation, String content, boolean useContext) {
    List<Map<String, Object>> messages = new ArrayList<>();
    if (conversation.getStr("systemMessage") != null && !conversation.getStr("systemMessage").isBlank()) {
      messages.add(Map.of("role", "system", "content", conversation.getStr("systemMessage")));
    }
    List<String> segments = Aop.get(AiKnowledgeService.class).recallForRole(conversation.getLong("roleId"), content);
    if (!segments.isEmpty()) {
      messages.add(Map.of("role", "system", "content", "参考资料：\n" + String.join("\n---\n", segments)));
    }
    if (useContext) {
      int maxContexts = Values.toInt(conversation.get("maxContexts")) == null ? 10 : Values.toInt(conversation.get("maxContexts"));
      List<Kv> history = Db.findMaps(
          "select type, content from ai_chat_message where conversation_id=? and deleted=0 and use_context=1 order by id desc limit ?",
          conversation.getLong("id"), maxContexts * 2);
      for (int i = history.size() - 1; i >= 0; i--) {
        Kv h = history.get(i);
        if (h.getStr("content") != null && !h.getStr("content").isEmpty()) {
          messages.add(Map.of("role", "assistant".equals(h.getStr("type")) ? "assistant" : "user", "content", h.getStr("content")));
        }
      }
    }
    messages.add(Map.of("role", "user", "content", content));
    return messages;
  }

  public Kv send(long uid, Long conversationId, String content, boolean useContext, boolean reasoning) {
    Kv conversation = crud().get(CONVERSATION, conversationId, uid);
    ChatErrors.require(conversation != null, "对话不存在");
    AiLlmService llm = Aop.get(AiLlmService.class);
    Kv target = llm.resolve(conversation.getLong("modelId"));
    List<Map<String, Object>> messages = prompt(conversation, content, useContext);
    long sendId = saveMessage(conversation, null, uid, "user", content, useContext, MSG_TEXT);
    long receiveId = saveMessage(conversation, sendId, uid, "assistant", "", useContext, MSG_TEXT);
    Kv answer = llm.chat(target, messages, reasoning);
    finishAnswer(conversationId, receiveId, answer.getStr("content"), answer.getStr("reasoningContent"), answer.getInt("totalTokens"),
        chars(messages));
    return Kv.by("send", messageVo(sendId)).set("receive", messageVo(receiveId));
  }

  /**
   * 流式回答：每个 SSE 事件的 data 是一条完整的响应包装 JSON。
   * 客户端中途断开（停止生成）时取消上游请求，已生成的部分写入回答
   */
  public void sendStream(ChannelContext ctx, long uid, Long conversationId, String content, boolean useContext, boolean reasoning) {
    Kv conversation = crud().get(CONVERSATION, conversationId, uid);
    if (conversation == null) {
      error(ctx, "对话不存在");
      return;
    }
    AiLlmService llm = Aop.get(AiLlmService.class);
    Kv target;
    try {
      target = llm.resolve(conversation.getLong("modelId"));
    } catch (Exception e) {
      error(ctx, e.getMessage());
      return;
    }
    List<Map<String, Object>> messages = prompt(conversation, content, useContext);
    long sendId = saveMessage(conversation, null, uid, "user", content, useContext, MSG_TEXT);
    long receiveId = saveMessage(conversation, sendId, uid, "assistant", "", useContext, MSG_TEXT);
    Kv send = messageVo(sendId);
    int promptChars = chars(messages);
    StringBuilder answer = new StringBuilder();
    StringBuilder reasoningText = new StringBuilder();
    Integer[] usage = new Integer[1];
    llm.stream(target, messages, reasoning, new AiLlmService.StreamHandler() {
      @Override
      public boolean onDelta(String delta, String reasoningDelta) {
        Kv receive = Kv.by("id", receiveId).set("type", "assistant").set("msgType", MSG_TEXT).set("content", delta)
            .set("reasoningContent", reasoningDelta).set("segments", List.of());
        if (!SseUtils.send(ctx, JsonUtils.toJson(ChatResp.toR(RespBodyVo.ok(Kv.by("send", send).set("receive", receive)), null)))) {
          return false;
        }
        // 只累计客户端已收到的内容
        answer.append(delta);
        if (reasoningDelta != null) {
          reasoningText.append(reasoningDelta);
        }
        return true;
      }

      @Override
      public void onUsage(int totalTokens) {
        usage[0] = totalTokens;
      }

      @Override
      public void onComplete() {
        finishAnswer(conversationId, receiveId, answer.toString(), emptyToNull(reasoningText), usage[0], promptChars);
        SseUtils.close(ctx);
      }

      @Override
      public void onCancel() {
        // 停止生成：客户端随后可能用 save-generated-content 保存它看到的内容，这里只在回答仍为空时写入
        Db.update("update ai_chat_message set content=?, reasoning_content=? where id=? and content=''", clip(answer.toString()),
            emptyToNull(reasoningText), receiveId);
        Db.update("update ai_chat_conversation set token_usage=ifnull(token_usage,0)+?, update_time=now() where id=?",
            estimateTokens(promptChars + answer.length()), conversationId);
      }

      @Override
      public void onError(String message) {
        Db.update("update ai_chat_message set content=? where id=?", clip(message), receiveId);
        error(ctx, message);
      }
    });
  }

  public static void error(ChannelContext ctx, String message) {
    SseUtils.send(ctx, JsonUtils.toJson(ChatResp.toR(RespBodyVo.fail(500, message), null)));
    SseUtils.close(ctx);
  }

  /**
   * 保存客户端生成的内容（图片、音频、视频，或停止生成时已显示的回答）。
   * 会话最后一条是对同一提问的回答且内容是 generatedContent 的前缀时（停止生成），直接更新这条回答；否则插入提问与回答两条消息
   */
  public Kv saveGenerated(long uid, Long conversationId, String prompt, String generatedContent) {
    Kv conversation = crud().get(CONVERSATION, conversationId, uid);
    ChatErrors.require(conversation != null, "对话不存在");
    String generated = generatedContent == null ? "" : generatedContent;
    Kv last = Db.findFirstMap("""
        select a.id, a.content, a.reply_id, q.content as prompt
        from ai_chat_message a left join ai_chat_message q on q.id = a.reply_id
        where a.conversation_id=? and a.deleted=0
        order by a.id desc limit 1
        """, conversationId);
    if (last != null && last.getLong("reply_id") != null && prompt != null && prompt.equals(last.getStr("prompt"))) {
      String current = last.getStr("content") == null ? "" : last.getStr("content");
      if (generated.startsWith(current)) {
        Db.update("update ai_chat_message set content=? where id=?", clip(generated), last.getLong("id"));
        return Kv.by("send", messageVo(last.getLong("reply_id"))).set("receive", messageVo(last.getLong("id")));
      }
    }
    int msgType = msgTypeOfModel(conversation.getLong("modelId"));
    Long sendId = null;
    if (prompt != null) {
      sendId = saveMessage(conversation, null, uid, "user", prompt, false, msgType);
    }
    long receiveId = saveMessage(conversation, sendId, uid, "assistant", generated, false, msgType);
    Db.update("update ai_chat_conversation set update_time=now() where id=?", conversationId);
    return Kv.by("send", sendId == null ? null : messageVo(sendId)).set("receive", messageVo(receiveId));
  }

  /**
   * 按模型类型确定消息内容类型：模型 1 对话→文本 1，2 图片→2，3 音频→4，7/8 视频→3
   */
  private static int msgTypeOfModel(Long modelId) {
    Integer type = modelId == null ? null : Db.queryInt("select type from ai_model where id=?", modelId);
    if (type == null) {
      return MSG_TEXT;
    }
    switch (type) {
    case 2:
      return 2;
    case 3:
      return 4;
    case 7:
    case 8:
      return 3;
    default:
      return MSG_TEXT;
    }
  }
}
