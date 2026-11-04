package nexus.io.tio.chat.handler.im.wx;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.service.im.wx.WxPortalService;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.environment.EnvUtils;

/** GET /api/im/wx/portal/public/callBack?code=：网页授权回调，完成后跳转 wx.mp.redirect */
public class WxPortalCallbackHandler {
  public HttpResponse handle(HttpRequest request) {
    String code = request.getParam("code");
    ParameterValidationUtils.notBlank(code, "code 不能为空");
    Aop.get(WxPortalService.class).authorize(code);
    HttpResponse response = TioRequestContext.getResponse();
    response.sendRedirect(EnvUtils.get("wx.mp.redirect", "https://gitee.com/nongyehong"));
    return response;
  }
}
