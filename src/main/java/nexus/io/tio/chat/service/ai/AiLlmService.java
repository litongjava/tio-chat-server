package nexus.io.tio.chat.service.ai;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.db.activerecord.Db;
import nexus.io.openai.client.OpenAiClient;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;

/**
 * 调用 OpenAI 兼容的对话接口，模型与密钥来自 ai_model、ai_api_key
 */
@Slf4j
public class AiLlmService {
  private static final String DEEPSEEK = "DeepSeek";
  private static final String OPENAI = "OpenAI";

  /**
   * 模型调用参数：baseUrl、apiKey、model、temperature、maxTokens
   */
  public Kv resolve(Long modelId) {
    Kv model = Db.findFirstMap("select * from ai_model where id=? and deleted=0", modelId);
    ChatErrors.require(model != null, "模型不存在");
    ChatErrors.require(Values.toInt(model.get("status")) == null || Values.toInt(model.get("status")) == 0, "模型已禁用");
    Kv key = Db.findFirstMap("select * from ai_api_key where id=? and deleted=0", model.getLong("key_id"));
    ChatErrors.require(key != null, "API 密钥不存在");
    return Kv.by("baseUrl", AiPlatforms.baseUrl(model.getStr("platform"), key.getStr("url")))
        .set("apiKey", key.getStr("api_key")).set("model", model.getStr("model")).set("modelId", model.getLong("id"))
        .set("platform", model.getStr("platform")).set("temperature", model.get("temperature"))
        .set("maxTokens", model.get("max_tokens")).set("maxContexts", model.get("max_contexts"));
  }

  /**
   * 请求体：reasoning 为 true 时开启深度思考（DeepSeek 用 thinking 参数切换），流式请求附带用量统计
   */
  private String body(Kv target, List<Map<String, Object>> messages, boolean stream, boolean reasoning) {
    Kv body = Kv.by("model", target.getStr("model")).set("messages", messages).set("stream", stream);
    if (target.get("temperature") != null) {
      body.set("temperature", Double.parseDouble(target.get("temperature").toString()));
    }
    if (target.get("maxTokens") != null) {
      body.set("max_tokens", Values.toInt(target.get("maxTokens")));
    }
    String platform = target.getStr("platform");
    if (DEEPSEEK.equals(platform)) {
      body.set("thinking", Kv.by("type", reasoning ? "enabled" : "disabled"));
    }
    if (stream && (DEEPSEEK.equals(platform) || OPENAI.equals(platform))) {
      body.set("stream_options", Kv.by("include_usage", true));
    }
    return JsonUtils.toJsonWithLongAsNumber(body);
  }

  /**
   * 同步调用，返回 content、reasoning_content、总 token 数
   */
  @SuppressWarnings("unchecked")
  public Kv chat(Kv target, List<Map<String, Object>> messages, boolean reasoning) {
    try (Response response = OpenAiClient.chatCompletions(target.getStr("baseUrl"), target.getStr("apiKey"),
        body(target, messages, false, reasoning))) {
      String text = response.body() == null ? "" : response.body().string();
      ChatErrors.require(response.isSuccessful(), "模型调用失败: " + text);
      Map<String, Object> json = JsonUtils.parseToMap(text, String.class, Object.class);
      List<Object> choices = (List<Object>) json.get("choices");
      Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
      Object content = message.get("content");
      Object reasoningContent = reasoning ? message.get("reasoning_content") : null;
      return Kv.by("content", content == null ? "" : content.toString())
          .set("reasoningContent", reasoningContent == null ? null : reasoningContent.toString())
          .set("totalTokens", totalTokens(json.get("usage")));
    } catch (IOException e) {
      throw ChatErrors.of("模型调用失败: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static Integer totalTokens(Object usage) {
    if (!(usage instanceof Map)) {
      return null;
    }
    return Values.toInt(((Map<String, Object>) usage).get("total_tokens"));
  }

  /**
   * 流式调用的回调
   */
  public interface StreamHandler {
    /**
     * 收到一段增量，返回 false 表示客户端已断开，此时停止上游请求并回调 onCancel
     */
    boolean onDelta(String content, String reasoning);

    void onUsage(int totalTokens);

    void onComplete();

    void onCancel();

    void onError(String message);
  }

  /**
   * 流式调用：reasoning 为 false 时丢弃推理内容
   */
  public EventSource stream(Kv target, List<Map<String, Object>> messages, boolean reasoning, StreamHandler handler) {
    EventSourceListener listener = new EventSourceListener() {
      private boolean finished;

      @Override
      @SuppressWarnings("unchecked")
      public void onEvent(EventSource eventSource, String id, String type, String data) {
        if (finished) {
          return;
        }
        if ("[DONE]".equals(data)) {
          finish();
          return;
        }
        Map<String, Object> json;
        try {
          json = JsonUtils.parseToMap(data, String.class, Object.class);
        } catch (Exception e) {
          log.warn("parse stream chunk failed: {}", data);
          return;
        }
        Integer tokens = totalTokens(json.get("usage"));
        if (tokens != null) {
          handler.onUsage(tokens);
        }
        List<Object> choices = (List<Object>) json.get("choices");
        if (choices == null || choices.isEmpty()) {
          return;
        }
        Map<String, Object> delta = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("delta");
        if (delta == null) {
          return;
        }
        Object content = delta.get("content");
        Object reasoningContent = reasoning ? delta.get("reasoning_content") : null;
        String contentText = content == null ? "" : content.toString();
        String reasoningText = reasoningContent == null ? "" : reasoningContent.toString();
        if (contentText.isEmpty() && reasoningText.isEmpty()) {
          return;
        }
        if (!handler.onDelta(contentText, reasoningText.isEmpty() ? null : reasoningText)) {
          finished = true;
          eventSource.cancel();
          handler.onCancel();
        }
      }

      @Override
      public void onClosed(EventSource eventSource) {
        finish();
      }

      @Override
      public void onFailure(EventSource eventSource, Throwable t, Response response) {
        if (finished) {
          return;
        }
        finished = true;
        String message = t == null ? "" : t.getMessage();
        try {
          if (response != null && response.body() != null) {
            message = response.body().string();
          }
        } catch (Exception ignored) {
        }
        handler.onError("模型调用失败: " + message);
      }

      private void finish() {
        if (!finished) {
          finished = true;
          handler.onComplete();
        }
      }
    };
    // OkHttp 默认请求 gzip，部分平台（如 DeepSeek 的 CDN）会把压缩后的事件流攒批下发，首字延迟数秒；流式请求要求不压缩
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", "Bearer " + target.getStr("apiKey"));
    headers.put("Accept-Encoding", "identity");
    return OpenAiClient.chatCompletions(target.getStr("baseUrl"), headers, body(target, messages, true, reasoning), listener);
  }
}
