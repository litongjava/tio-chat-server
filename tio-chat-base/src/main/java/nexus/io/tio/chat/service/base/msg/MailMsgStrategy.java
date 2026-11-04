package nexus.io.tio.chat.service.base.msg;

import java.util.Map;

import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;

import com.jfinal.kit.Kv;

/**
 * 邮件（SMTP）：配置项 hostName、smtpPort、ssl、username、password、fromName、fromEmail、charset
 */
public class MailMsgStrategy implements MsgStrategy {

  @Override
  public MsgResult exec(MsgParam param) throws Exception {
    if (param.isDebug()) {
      return MsgResult.of(true, "debug模式无需发送");
    }
    Map<String, String> values = MsgTemplates.params(param.getMsg().getStr("param"));
    String title = MsgTemplates.render(param.getTemplate().getStr("title"), values);
    String content = MsgTemplates.render(param.getTemplate().getStr("content"), values);
    String messageId = build(param, title, content).send();
    return new MsgResult(true, title, content, messageId);
  }

  private HtmlEmail build(MsgParam param, String title, String content) throws EmailException {
    HtmlEmail email = new HtmlEmail();
    email.setHostName(param.property("hostName", null));
    String port = param.property("smtpPort", null);
    if ("true".equalsIgnoreCase(param.property("ssl", "false"))) {
      email.setSSLOnConnect(true);
      if (port != null) {
        email.setSslSmtpPort(port);
      }
    } else if (port != null) {
      email.setSmtpPort(Integer.parseInt(port));
    }
    String username = param.property("username", null);
    if (username != null) {
      email.setAuthentication(username, param.property("password", ""));
    }
    email.setCharset(param.property("charset", "UTF-8"));
    String fromEmail = param.property("fromEmail", null);
    if (fromEmail != null) {
      email.setFrom(fromEmail, param.property("fromName", fromEmail));
    }
    for (Kv recipient : param.getRecipients()) {
      String name = recipient.getStr("ext");
      if (name == null || name.isEmpty()) {
        email.addTo(recipient.getStr("recipient"));
      } else {
        email.addTo(recipient.getStr("recipient"), name);
      }
    }
    email.setSubject(title);
    email.setHtmlMsg(content);
    return email;
  }
}
