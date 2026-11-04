package nexus.io.tio.chat.service.base.msg;

import com.alibaba.fastjson2.JSON;
import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.jfinal.kit.Kv;

/**
 * 阿里云短信：配置项 accessKeyId、accessKeySecret、endpoint、regionId
 */
public class AliSmsMsgStrategy implements MsgStrategy {

  @Override
  public MsgResult exec(MsgParam param) throws Exception {
    if (param.isDebug()) {
      return MsgResult.of(true, "debug模式无需发送");
    }
    Config config = new Config().setAccessKeyId(param.property("accessKeyId", null))
        .setAccessKeySecret(param.property("accessKeySecret", null))
        .setEndpoint(param.property("endpoint", "dysmsapi.aliyuncs.com"))
        .setRegionId(param.property("regionId", "cn-hangzhou"));
    Kv msg = param.getMsg();
    SendSmsRequest request = new SendSmsRequest().setPhoneNumbers(param.phoneNumbers())
        .setSignName(param.getTemplate().getStr("sign")).setTemplateCode(param.getTemplate().getStr("template_code"))
        .setTemplateParam(JSON.toJSONString(MsgTemplates.params(msg.getStr("param")))).setOutId(msg.getStr("id"));
    SendSmsResponse response = new Client(config).sendSms(request);
    boolean success = response.getBody() != null && "OK".equals(response.getBody().getCode());
    return MsgResult.of(success, JSON.toJSONString(response.getBody()));
  }
}
