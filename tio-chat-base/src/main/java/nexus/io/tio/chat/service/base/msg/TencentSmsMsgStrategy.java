package nexus.io.tio.chat.service.base.msg;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20190711.SmsClient;
import com.tencentcloudapi.sms.v20190711.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20190711.models.SendSmsResponse;
import com.tencentcloudapi.sms.v20190711.models.SendStatus;

/**
 * 腾讯云短信：配置项 secretId、secretKey、endpoint、region、sdkAppId
 */
public class TencentSmsMsgStrategy implements MsgStrategy {

  @Override
  public MsgResult exec(MsgParam param) throws Exception {
    if (param.isDebug()) {
      return MsgResult.of(true, "debug模式无需发送");
    }
    HttpProfile http = new HttpProfile();
    http.setEndpoint(param.property("endpoint", "sms.tencentcloudapi.com"));
    ClientProfile profile = new ClientProfile();
    profile.setSignMethod("HmacSHA256");
    profile.setHttpProfile(http);
    Credential credential = new Credential(param.property("secretId", null), param.property("secretKey", null));
    SmsClient client = new SmsClient(credential, param.property("region", "ap-beijing"), profile);

    Kv msg = param.getMsg();
    List<String> phones = new ArrayList<>();
    for (Kv recipient : param.getRecipients()) {
      phones.add(recipient.getStr("recipient"));
    }
    SendSmsRequest request = new SendSmsRequest();
    request.setSmsSdkAppid(param.property("sdkAppId", null));
    request.setSign(param.getTemplate().getStr("sign"));
    request.setTemplateID(param.getTemplate().getStr("template_code"));
    request.setTemplateParamSet(MsgTemplates.params(msg.getStr("param")).values().toArray(new String[0]));
    request.setPhoneNumberSet(phones.toArray(new String[0]));
    request.setSessionContext(msg.getStr("id"));
    SendSmsResponse response = client.SendSms(request);
    SendStatus[] statuses = response.getSendStatusSet();
    boolean success = statuses != null && statuses.length > 0 && "Ok".equals(statuses[0].getCode());
    return MsgResult.of(success, SendSmsResponse.toJsonString(response));
  }
}
