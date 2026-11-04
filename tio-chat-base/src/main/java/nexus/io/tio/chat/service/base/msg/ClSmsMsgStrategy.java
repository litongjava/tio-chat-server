package nexus.io.tio.chat.service.base.msg;

import java.util.Map;
import java.util.regex.Pattern;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jfinal.kit.Kv;

import nexus.io.tio.chat.utils.HttpClients;

/**
 * 创蓝短信：配置项 account、password、endPoint、variableEndPoint、variable
 */
public class ClSmsMsgStrategy implements MsgStrategy {
  private static final String DEF_END_POINT = "http://smssh1.253.com/msg/v1/send/json";
  private static final String DEF_VARIABLE_END_POINT = "http://smssh1.253.com/msg/variable/json";
  private static final Pattern VARIABLE = Pattern.compile("[$][{][a-zA-Z\\d\\\\_$]+[}]");
  private static final String VAR = "{\\$var}";

  @Override
  public MsgResult exec(MsgParam param) throws Exception {
    if (param.isDebug()) {
      return MsgResult.of(true, "Debug模式，无需发送！");
    }
    Kv msg = param.getMsg();
    Kv template = param.getTemplate();
    Map<String, String> values = MsgTemplates.params(msg.getStr("param"));
    boolean variable = "true".equalsIgnoreCase(param.property("variable", "false"));
    String sign = template.getStr("sign");
    String text = VARIABLE.matcher(template.getStr("content") == null ? "" : template.getStr("content")).replaceAll(VAR);
    Kv body = Kv.by("account", param.property("account", "")).set("password", param.property("password", ""))
        .set("msg", "【" + sign + "】" + (variable ? text : MsgTemplates.render(template.getStr("content"), values)))
        .set("report", "false").set("uid", msg.getStr("id")).set("extend", "1");
    if (variable) {
      body.set("params", variableParams(param, values));
    } else {
      body.set("phone", param.phoneNumbers());
    }
    String url = variable ? param.property("variableEndPoint", DEF_VARIABLE_END_POINT)
        : param.property("endPoint", DEF_END_POINT);
    String response = HttpClients.post(url, "application/json", JSON.toJSONString(body), Map.of());
    JSONObject result = JSON.parseObject(response);
    return MsgResult.of(result != null && "0".equals(result.getString("code")), response);
  }

  private String variableParams(MsgParam param, Map<String, String> values) {
    StringBuilder result = new StringBuilder();
    for (Kv recipient : param.getRecipients()) {
      if (result.length() > 0) {
        result.append(';');
      }
      result.append(recipient.getStr("recipient"));
      for (String value : values.values()) {
        result.append(',').append(value);
      }
    }
    return result.toString();
  }
}
