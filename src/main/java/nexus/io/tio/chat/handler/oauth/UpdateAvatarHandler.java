package nexus.io.tio.chat.handler.oauth;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.oauth.OauthUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** PUT /api/oauth/anyone/avatar */
public class UpdateAvatarHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    ParameterValidationUtils.notBlank(body.getStr("avatar"), "头像不能为空");
    OauthUserService oauthUserService = Aop.get(OauthUserService.class);
    return ChatResp.ok(oauthUserService.updateAvatar(LoginContext.userId(), body.getStr("avatar")));
  }
}
