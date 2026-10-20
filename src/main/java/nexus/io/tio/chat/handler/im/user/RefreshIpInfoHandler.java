package nexus.io.tio.chat.handler.im.user;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.json.JsonUtils;

/** POST /api/im/user/refreshIpInfo */
public class RefreshIpInfoHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    Long uid = Values.toLong(body.get("uid"));
    ParameterValidationUtils.required(uid, "uid不能为空");
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    return ChatResp.ok(service.refreshIpInfo(uid, JsonUtils.toJson(body.get("ipInfo"))));
  }
}
