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
    return crud().get(CONVERSATION, id, null);
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

  private long saveMessage(Kv conversation, Long replyId, long uid, String type, String content, boolean useContext) {
    long id = SnowflakeIdUtils.id();
    Row row = Row.by("id", id).set("conversation_id", conversation.getLong("id")).set("reply_id", replyId).set("user_id", uid)
        .set("role_id", conversation.getLong("roleId")).set("type", type).set("model", conversation.getStr("model"))
        .set("model_id", conversation.getLong("modelId")).set("content", content).set("use_context", useContext)
        .set("creator", String.valueOf(uid)).set("updater", String.valueOf(uid)).set("create_time", LocalDateTime.now())
        .set("update_time", LocalDateTime.now()).set("deleted", 0).set("tenant_id", 1L);
    Db.save(MESSAGE, row);
    return id;
  }

  private Kv messageVo(long id) {
    Kv row = crud().get(MESSAGE, id, null);
    return Kv.by("id", id).set("type", row.getStr("type")).set("content", row.getStr("content"))
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

  public Kv send(long uid, Long conversationId, String content, boolean useContext) {
    Kv conversation = crud().get(CONVERSATION, conversationId, uid);
    ChatErrors.require(conversation != null, "对话不存在");
    AiLlmService llm = Aop.get(AiLlmService.class);
    Kv target = llm.resolve(conversation.getLong("modelId"));
    List<Map<String, Object>> messages = prompt(conversation, content, useContext);
    long sendId = saveMessage(conversation, null, uid, "user", content, useContext);
    long receiveId = saveMessage(conversation, sendId, uid, "assistant", "", useContext);
    String[] answer = llm.chat(target, messages);
    Db.update("update ai_chat_message set content=?, reasoning_content=? where id=?", answer[0], answer[1], receiveId);
    Db.update("update ai_chat_conversation set update_time=now() where id=?", conversationId);
    return Kv.by("send", messageVo(sendId)).set("receive", messageVo(receiveId));
  }

  /**
   * 流式回答：每个 SSE 事件的 data 是一条完整的响应包装 JSON
   */
  public void sendStream(ChannelContext ctx, long uid, Long conversationId, String content, boolean useContext) {
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
    long sendId = saveMessage(conversation, null, uid, "user", content, useContext);
    long receiveId = saveMessage(conversation, sendId, uid, "assistant", "", useContext);
    Kv send = messageVo(sendId);
    StringBuilder answer = new StringBuilder();
    StringBuilder reasoning = new StringBuilder();
    llm.stream(target, messages, (delta, reasoningDelta) -> {
      answer.append(delta);
      if (reasoningDelta != null) {
        reasoning.append(reasoningDelta);
      }
      Kv receive = Kv.by("id", receiveId).set("type", "assistant").set("content", delta).set("reasoningContent", reasoningDelta)
          .set("segments", List.of());
      SseUtils.send(ctx, JsonUtils.toJson(ChatResp.toR(RespBodyVo.ok(Kv.by("send", send).set("receive", receive)), null)));
    }, () -> {
      Db.update("update ai_chat_message set content=?, reasoning_content=? where id=?", answer.toString(),
          reasoning.length() == 0 ? null : reasoning.toString(), receiveId);
      Db.update("update ai_chat_conversation set update_time=now() where id=?", conversationId);
      SseUtils.close(ctx);
    }, message -> {
      Db.update("update ai_chat_message set content=? where id=?", message, receiveId);
      error(ctx, message);
    });
  }

  public static void error(ChannelContext ctx, String message) {
    SseUtils.send(ctx, JsonUtils.toJson(ChatResp.toR(RespBodyVo.fail(500, message), null)));
    SseUtils.close(ctx);
  }

  public Kv saveGenerated(long uid, Long conversationId, String content) {
    Kv conversation = crud().get(CONVERSATION, conversationId, uid);
    ChatErrors.require(conversation != null, "对话不存在");
    long receiveId = saveMessage(conversation, null, uid, "assistant", content, true);
    return Kv.by("send", null).set("receive", messageVo(receiveId));
  }
}
