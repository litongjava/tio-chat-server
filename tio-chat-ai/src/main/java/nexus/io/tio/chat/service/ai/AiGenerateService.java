package nexus.io.tio.chat.service.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.HttpClients;
import nexus.io.tio.chat.utils.SseUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.thread.TioThreadUtils;

/**
 * AI 写作、思维导图、图片与音视频生成
 */
public class AiGenerateService {
  public static final int STATUS_IN_PROGRESS = 10;
  public static final int STATUS_SUCCESS = 20;
  public static final int STATUS_FAIL = 30;

  private static final String WRITE_ROLE = """
      你是一位出色的写作助手，能够帮助用户生成创意和灵感，并在用户提供场景和提示词时生成对应的回复。
      除此之外不需要除了正文内容外的其他回复，如标题、开头、任何解释性语句或道歉。
      """;
  private static final String MIND_MAP_ROLE = """
      你是一位非常优秀的思维导图助手，你会把用户的所有提问都总结成思维导图，然后以 Markdown 格式输出。
      markdown 只需要输出一级标题，二级标题，三级标题，四级标题，最多输出四级，除此之外不要输出任何其他 markdown 标记。
      """;

  private AiCrudService crud() {
    return Aop.get(AiCrudService.class);
  }

  /**
   * 默认模型：当前用户可见（公开或自己的）的第一个启用模型
   */
  public Long defaultModelId(int type, long uid) {
    Long id = Db.queryLong("""
        select id from ai_model
        where deleted=0 and status=0 and type=? and (public_status=0 or user_id=?)
        order by sort, id limit 1
        """, type, uid);
    ChatErrors.require(id != null, "必须找到默认模型");
    return id;
  }

  /**
   * 写作：type=1 撰写，type=2 回复；流式返回生成内容
   */
  public void writeStream(ChannelContext ctx, long uid, Map<String, Object> req) {
    Integer type = Values.toInt(req.get("type"));
    String prompt = type != null && type == 2
        ? String.format("请针对如下内容：[%s] 做个回复。回复内容参考：[%s], 回复格式：%s，语气：%s，语言：%s，长度：%s。不需要除了正文内容外的其他回复。",
            req.get("originalContent"), req.get("prompt"), req.get("format"), req.get("tone"), req.get("language"), req.get("length"))
        : String.format("请撰写一篇关于 [%s] 的文章。文章的内容格式：%s，语气：%s，语言：%s，长度：%s。请确保涵盖主要内容，不需要除了正文内容外的其他回复。",
            req.get("prompt"), req.get("format"), req.get("tone"), req.get("language"), req.get("length"));
    Kv target = Aop.get(AiLlmService.class).resolve(defaultModelId(1, uid));
    Kv data = Kv.create().set(req).set("userId", uid).set("modelId", target.getLong("modelId")).set("model", target.getStr("model"))
        .set("platform", target.getStr("platform"));
    long id = crud().create("ai_write", data, true);
    stream(ctx, target, WRITE_ROLE, prompt, "ai_write", id);
  }

  public void mindMapStream(ChannelContext ctx, long uid, Map<String, Object> req) {
    Kv target = Aop.get(AiLlmService.class).resolve(defaultModelId(1, uid));
    long id = crud().create("ai_mind_map", Kv.by("prompt", req.get("prompt")).set("userId", uid).set("modelId", target.getLong("modelId"))
        .set("model", target.getStr("model")).set("platform", target.getStr("platform")), true);
    stream(ctx, target, MIND_MAP_ROLE, String.valueOf(req.get("prompt")), "ai_mind_map", id);
  }

  private void stream(ChannelContext ctx, Kv target, String system, String prompt, String table, long recordId) {
    AiLlmService llm = Aop.get(AiLlmService.class);
    List<Map<String, Object>> messages = List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", prompt));
    StringBuilder content = new StringBuilder();
    // 写作与思维导图只需要正文，关闭深度思考
    llm.stream(target, messages, false, new AiLlmService.StreamHandler() {
      @Override
      public boolean onDelta(String delta, String reasoning) {
        content.append(delta);
        return SseUtils.send(ctx, JsonUtils.toJson(ChatResp.toR(RespBodyVo.ok(delta), null)));
      }

      @Override
      public void onUsage(int totalTokens) {
      }

      @Override
      public void onComplete() {
        Db.update("update " + table + " set generated_content=? where id=?", content.toString(), recordId);
        SseUtils.close(ctx);
      }

      @Override
      public void onCancel() {
        Db.update("update " + table + " set generated_content=? where id=?", content.toString(), recordId);
      }

      @Override
      public void onError(String message) {
        Db.update("update " + table + " set error_message=? where id=?", message, recordId);
        AiChatService.error(ctx, message);
      }
    });
  }

  // ---------------------------------------------------------------- 图片

  /**
   * 绘图：调用 OpenAI 兼容的 images/generations 接口，异步更新结果
   */
  public long drawImage(long uid, Map<String, Object> req) {
    Long modelId = Values.toLong(req.get("modelId"));
    if (modelId == null) {
      modelId = defaultModelId(2, uid);
    }
    Kv target = Aop.get(AiLlmService.class).resolve(modelId);
    Kv data = Kv.create().set(req).set("userId", uid).set("modelId", modelId).set("model", target.getStr("model"))
        .set("platform", target.getStr("platform")).set("status", STATUS_IN_PROGRESS).set("publicStatus", false);
    long id = crud().create("ai_image", data, true);
    TioThreadUtils.execute(() -> {
      try {
        Kv body = Kv.by("model", target.getStr("model")).set("prompt", req.get("prompt")).set("n", 1)
            .set("size", req.get("width") + "x" + req.get("height"));
        String resp = HttpClients.post(target.getStr("baseUrl") + "/images/generations", "application/json",
            JsonUtils.toJsonWithLongAsNumber(body), Map.of("Authorization", "Bearer " + target.getStr("apiKey")));
        String url = imageUrl(resp);
        ChatErrors.require(url != null, "生成失败: " + resp);
        Db.update("update ai_image set status=?, pic_url=?, finish_time=now() where id=?", STATUS_SUCCESS, url, id);
      } catch (Exception e) {
        Db.update("update ai_image set status=?, error_message=?, finish_time=now() where id=?", STATUS_FAIL, e.getMessage(), id);
      }
    });
    return id;
  }

  @SuppressWarnings("unchecked")
  private String imageUrl(String resp) {
    Map<String, Object> json = JsonUtils.parseToMap(resp, String.class, Object.class);
    if (json.get("data") instanceof List && !((List<Object>) json.get("data")).isEmpty()) {
      Map<String, Object> first = (Map<String, Object>) ((List<Object>) json.get("data")).get(0);
      Object url = first.get("url");
      if (url != null) {
        return url.toString();
      }
      Object b64 = first.get("b64_json");
      return b64 == null ? null : "data:image/png;base64," + b64;
    }
    return null;
  }

  /**
   * 音频、视频、音乐：记录任务；平台未接入时标记失败并给出原因
   */
  public long createTask(String table, long uid, Map<String, Object> req) {
    Kv data = Kv.create().set(req).set("userId", uid).set("status", STATUS_FAIL)
        .set("errorMessage", "当前未配置该类型的生成平台").set("publicStatus", false);
    return crud().create(table, data, true);
  }

  public boolean notifyTask(String table, String taskId, String status, String url, String urlColumn) {
    int value = "success".equalsIgnoreCase(status) || "SUCCESS".equals(status) ? STATUS_SUCCESS : STATUS_FAIL;
    return Db.update("update " + table + " set status=?, " + urlColumn + "=?, finish_time=now() where task_id=?", value, url, taskId) >= 0;
  }

  // ---------------------------------------------------------------- 模型与平台

  /**
   * 可选模型：公开模型（public_status=0）与当前用户自己的私有模型
   */
  public List<Kv> modelSimpleList(Integer type, String platform) {
    StringBuilder where = new StringBuilder("status=0 and (public_status=0 or user_id=?)");
    List<Object> params = new ArrayList<>();
    params.add(crud().uid());
    if (type != null) {
      where.append(" and type=?");
      params.add(type);
    }
    if (platform != null && !platform.isBlank()) {
      where.append(" and platform=?");
      params.add(platform);
    }
    where.append(" order by sort, id");
    return crud().list("ai_model", where.toString(), params.toArray());
  }

  public int remainingUsage(long uid, Long modelId) {
    Integer remaining = Db.queryInt("select remaining_count from ai_model_usage_record where user_id=? and model_id=?", uid, modelId);
    return remaining == null ? -1 : remaining;
  }

  public List<Kv> platforms() {
    return crud().list("ai_platform", "status=0 order by sort, id");
  }

  /**
   * 把模型名追加到平台的示例模型列表
   */
  public boolean addModelToPlatform(String platform, String model) {
    String examples = Db.queryStr("select examples from ai_platform where platform=? and deleted=0", platform);
    ChatErrors.require(examples != null, "平台不存在");
    for (String item : examples.split(",")) {
      if (item.trim().equals(model)) {
        return true;
      }
    }
    String updated = examples.isBlank() ? model : examples + ", " + model;
    return Db.update("update ai_platform set examples=? where platform=?", updated, platform) > 0;
  }

  public int cleanDuplicateModels() {
    int removed = 0;
    for (Kv row : Db.findMaps("select id, examples from ai_platform where deleted=0")) {
      String examples = row.getStr("examples");
      if (examples == null) {
        continue;
      }
      List<String> unique = new ArrayList<>();
      for (String item : examples.split(",")) {
        String trimmed = item.trim();
        if (!trimmed.isEmpty() && !unique.contains(trimmed)) {
          unique.add(trimmed);
        } else {
          removed++;
        }
      }
      Db.update("update ai_platform set examples=? where id=?", String.join(", ", unique), row.getLong("id"));
    }
    return removed;
  }

  public Kv apiKeyBalance(Long id) {
    Kv key = crud().rawRow("ai_api_key", id);
    ChatErrors.require(key != null, "API 密钥不存在");
    return Kv.by("id", id).set("platform", key.getStr("platform")).set("balance", null).set("currency", null)
        .set("supported", false);
  }
}
