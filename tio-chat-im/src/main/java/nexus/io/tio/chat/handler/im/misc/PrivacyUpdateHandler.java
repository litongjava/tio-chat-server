package nexus.io.tio.chat.handler.im.misc;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.ImExtraService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/privacy */
public class PrivacyUpdateHandler {
  public HttpResponse handle(HttpRequest request) {
    Long uid = ParameterValidationUtils.optionalLong(request, "uid");
    Kv body = ParameterValidationUtils.bodyKv(request);
    ImExtraService service = Aop.get(ImExtraService.class);
    return ChatResp.ok(service.updatePrivacy(uid == null ? LoginContext.uid() : uid, body));
  }
}
