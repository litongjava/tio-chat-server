package nexus.io.tio.chat.handler.oauth.ext;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.model.oauth.LoginResult;
import nexus.io.tio.chat.service.oauth.ThirdPartyAuthService;
import nexus.io.tio.chat.utils.RequestUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.common.HttpResponseStatus;

/** GET /api/oauth/anyTenant/{provider}/callback */
public class ThirdPartyCallbackHandler {
  public HttpResponse handle(HttpRequest request) {
    String provider = request.getPathVar("provider").toLowerCase();
    String state = request.getParam("state");
    String redirect = request.getParam("redirect");
    if ((redirect == null || redirect.isBlank()) && state != null && !state.isBlank()) {
      redirect = state;
    }
    HttpResponse response = TioRequestContext.getResponse();
    LoginResult result;
    try {
      result = Aop.get(ThirdPartyAuthService.class).login(provider, request.getParam("code"), RequestUtils.ip(request));
    } catch (Exception e) {
      response.setStatus(HttpResponseStatus.C400);
      return response.setJson(Kv.by("success", false).set("msg", e.getMessage()));
    }
    if (redirect != null && !redirect.isBlank()) {
      String location = redirect + "?token=" + URLEncoder.encode(result.getToken(), StandardCharsets.UTF_8) + "&refreshToken="
          + URLEncoder.encode(result.getRefreshToken(), StandardCharsets.UTF_8) + "&uid=" + result.getUid()
          + (state != null && !state.isBlank() ? "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8) : "");
      response.sendRedirect(location);
      return response;
    }
    return response.setJson(Kv.by("success", true).set("token", result.getToken()).set("refreshToken", result.getRefreshToken())
        .set("uid", String.valueOf(result.getUid())));
  }
}
