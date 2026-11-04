package nexus.io.tio.chat.handler.im.wx;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.service.im.wx.WxPortalService;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/wx/portal/public：微信服务器配置校验，原样返回 echostr */
public class WxPortalVerifyHandler {
  public HttpResponse handle(HttpRequest request) {
    String result = Aop.get(WxPortalService.class).verify(request.getParam("signature"), request.getParam("timestamp"),
        request.getParam("nonce"), request.getParam("echostr"));
    return TioRequestContext.getResponse().setString(result, "utf-8", "text/plain;charset=utf-8");
  }
}
