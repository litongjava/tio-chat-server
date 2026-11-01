package nexus.io.tio.chat.service.ai;

import java.util.Map;

/**
 * 各平台 OpenAI 兼容接口的默认地址，api_key.url 有值时优先使用
 */
public class AiPlatforms {
  private static final Map<String, String> BASE_URLS = Map.ofEntries(
      Map.entry("OpenAI", "https://api.openai.com/v1"),
      Map.entry("AzureOpenAI", "https://api.openai.com/v1"),
      Map.entry("DeepSeek", "https://api.deepseek.com/v1"),
      Map.entry("Moonshot", "https://api.moonshot.cn/v1"),
      Map.entry("TongYi", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
      Map.entry("ZhiPu", "https://open.bigmodel.cn/api/paas/v4"),
      Map.entry("SiliconFlow", "https://api.siliconflow.cn/v1"),
      Map.entry("DouBao", "https://ark.cn-beijing.volces.com/api/v3"),
      Map.entry("HunYuan", "https://api.hunyuan.cloud.tencent.com/v1"),
      Map.entry("OpenRouter", "https://openrouter.ai/api/v1"),
      Map.entry("Ollama", "http://127.0.0.1:11434/v1"),
      Map.entry("GiteeAI", "https://ai.gitee.com/v1"),
      Map.entry("MiniMax", "https://api.minimax.chat/v1"),
      Map.entry("XingHuo", "https://spark-api-open.xf-yun.com/v1"),
      Map.entry("BaiChuan", "https://api.baichuan-ai.com/v1"),
      Map.entry("YiYan", "https://qianfan.baidubce.com/v2"),
      Map.entry("Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"));

  public static String baseUrl(String platform, String customUrl) {
    if (customUrl != null && !customUrl.isBlank()) {
      String url = customUrl.trim();
      return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
    return BASE_URLS.getOrDefault(platform, "https://api.openai.com/v1");
  }
}
