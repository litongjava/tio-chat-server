package nexus.io.tio.chat.service.base.msg;

import com.alibaba.fastjson2.JSON;
import com.baidubce.auth.DefaultBceCredentials;
import com.baidubce.services.sms.SmsClient;
import com.baidubce.services.sms.SmsClientConfiguration;
import com.baidubce.services.sms.model.SendMessageV3Request;
import com.baidubce.services.sms.model.SendMessageV3Response;
import com.jfinal.kit.Kv;

/**
 * 百度云短信：配置项 accessKeyId、secretKey、endPoint
 */
public class BaiduSmsMsgStrategy implements MsgStrategy {

  @Override
  public MsgResult exec(MsgParam param) throws Exception {
    if (param.isDebug()) {
      return MsgResult.of(true, "Debug模式，无需发送！");
    }
    SmsClientConfiguration config = new SmsClientConfiguration();
    config.setCredentials(new DefaultBceCredentials(param.property("accessKeyId", null), param.property("secretKey", null)));
    config.setEndpoint(param.property("endPoint", "http://smsv3.bj.baidubce.com"));
    Kv msg = param.getMsg();
    SendMessageV3Request request = new SendMessageV3Request();
    request.setSignatureId(param.getTemplate().getStr("sign"));
    request.setTemplate(param.getTemplate().getStr("template_code"));
    request.setContentVar(MsgTemplates.params(msg.getStr("param")));
    request.setCustom(msg.getStr("id"));
    request.setMobile(param.phoneNumbers());
    SendMessageV3Response response = new SmsClient(config).sendMessage(request);
    return MsgResult.of("1000".equals(response.getCode()), response.getCode() + " " + response.getMessage());
  }
}
