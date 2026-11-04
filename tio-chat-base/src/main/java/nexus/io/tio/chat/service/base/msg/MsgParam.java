package nexus.io.tio.chat.service.base.msg;

import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次发送需要的全部数据：消息、模板、接收人和接口配置
 */
@Getter
@AllArgsConstructor
public class MsgParam {
  private final Kv msg;
  private final Kv template;
  private final List<Kv> recipients;
  private final Map<String, String> properties;

  /** 配置项是否开启调试：调试模式不调用真实渠道 */
  public boolean isDebug() {
    return "true".equalsIgnoreCase(properties.get("debug"));
  }

  public String property(String key, String defaultValue) {
    String value = properties.get(key);
    return value == null || value.isEmpty() ? defaultValue : value;
  }

  public String phoneNumbers() {
    StringBuilder result = new StringBuilder();
    for (Kv recipient : recipients) {
      if (result.length() > 0) {
        result.append(',');
      }
      result.append(recipient.getStr("recipient"));
    }
    return result.toString();
  }
}
