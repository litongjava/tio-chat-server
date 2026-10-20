package nexus.io.tio.chat.service.common;

import java.util.Map;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import nexus.io.db.activerecord.Db;
import nexus.io.tio.utils.email.EMailUtils;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 按 def_msg_template 模板发送邮件
 */
@Slf4j
public class MailService {

  public boolean sendByTemplate(String templateCode, String to, Map<String, String> params) {
    Kv template = Db.findFirstMap("select title, content from def_msg_template where code=? and is_del=0 limit 1",
        templateCode);
    String title = template == null ? "验证码" : render(template.getStr("title"), params);
    String content = template == null ? "您的验证码是：" + params.get("emailCode") : render(template.getStr("content"), params);
    if (EnvUtils.get("mail.host") == null) {
      log.warn("mail.host is not configured, skip sending email to {}, params={}", to, params);
      return false;
    }
    try {
      EMailUtils.send(to, title, content);
      return true;
    } catch (Exception e) {
      log.error("send email failed: {}", to, e);
      return false;
    }
  }

  private String render(String text, Map<String, String> params) {
    if (text == null) {
      return "";
    }
    String result = text;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      String value = entry.getValue() == null ? "" : entry.getValue();
      result = result.replace("${" + entry.getKey() + "}", value);
    }
    return result;
  }
}
