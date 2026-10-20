package nexus.io.tio.chat.service.ai;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

  private String body(Kv target, List<Map<String, Object>> messages, boolean stream) {
    Kv body = Kv.by("model", target.getStr("model")).set("messages", messages).set("stream", stream);
    if (target.get("temperature") != null) {
      body.set("temperature", Double.parseDouble(target.get("temperature").toString()));
    }
    if (target.get("maxTokens") != null) {
      body.set("max_tokens", Values.toInt(target.get("maxTokens")));
    }
    return JsonUtils.toJsonWithLongAsNumber(body);
  }

  @SuppressWarnings("unchecked")
  public String[] chat(Kv target, List<Map<String, Object>> messages) {
    try (Response response = OpenAiClient.chatCompletions(target.getStr("baseUrl"), target.getStr("apiKey"),
        body(target, messages, false))) {
      String text = response.body() == null ? "" : response.body().string();
      ChatErrors.require(response.isSuccessful(), "模型调用失败: " + text);
      Map<String, Object> json = JsonUtils.parseToMap(text, String.class, Object.class);
      List<Object> choices = (List<Object>) json.get("choices");
      Map<String, Object> message = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
      Object reasoning = message.get("reasoning_content");
      return new String[] { String.valueOf(message.get("content")), reasoning == null ? null : reasoning.toString() };
    } catch (IOException e) {
      throw ChatErrors.of("模型调用失败: " + e.getMessage());
    }
  }

  /**
   * 流式调用：onDelta(content, reasoning) 逐段回调，结束时调用 onComplete，失败时调用 onError
   */
  public EventSource stream(Kv target, List<Map<String, Object>> messages, BiConsumer<String, String> onDelta,
      Runnable onComplete, Consumer<String> onError) {
    EventSourceListener listener = new EventSourceListener() {
      private boolean finished;

      @Override
      @SuppressWarnings("unchecked")
      public void onEvent(EventSource eventSource, String id, String type, String data) {
        if ("[DONE]".equals(data)) {
          finish();
          return;
        }
        try {
          Map<String, Object> json = JsonUtils.parseToMap(data, String.class, Object.class);
          List<Object> choices = (List<Object>) json.get("choices");
          if (choices == null || choices.isEmpty()) {
            return;
          }
          Map<String, Object> delta = (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("delta");
          if (delta == null) {
            return;
          }
          Object content = delta.get("content");
          Object reasoning = delta.get("reasoning_content");
          if (content != null || reasoning != null) {
            onDelta.accept(content == null ? "" : content.toString(), reasoning == null ? null : reasoning.toString());
          }
        } catch (Exception e) {
          log.warn("parse stream chunk failed: {}", data);
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
        onError.accept("模型调用失败: " + message);
      }

      private void finish() {
        if (!finished) {
          finished = true;
          onComplete.run();
        }
      }
    };
    return OpenAiClient.chatCompletions(target.getStr("baseUrl"), target.getStr("apiKey"), body(target, messages, true), listener);
  }
}
