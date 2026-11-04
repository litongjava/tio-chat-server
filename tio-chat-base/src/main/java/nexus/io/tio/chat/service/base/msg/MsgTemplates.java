package nexus.io.tio.chat.service.base.msg;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * 解析消息参数并用 FreeMarker 渲染模板，语法与原系统一致
 */
final class MsgTemplates {
  private static final Configuration CONFIG = new Configuration(Configuration.VERSION_2_3_31);

  private MsgTemplates() {
  }

  /** [{"key":"code","value":"123"}] -> {code=123} */
  static Map<String, String> params(String json) {
    Map<String, String> result = new LinkedHashMap<>();
    if (json == null || json.isEmpty()) {
      return result;
    }
    List<JSONObject> items = JSON.parseArray(json, JSONObject.class);
    for (JSONObject item : items) {
      result.put(item.getString("key"), item.getString("value"));
    }
    return result;
  }

  static String render(String text, Map<String, String> params) throws IOException, TemplateException {
    if (text == null || text.isEmpty()) {
      return text;
    }
    Template template = new Template("msg", new StringReader(text), CONFIG);
    StringWriter out = new StringWriter();
    template.process(params, out);
    return out.toString();
  }
}
