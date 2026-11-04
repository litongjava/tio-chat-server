package nexus.io.tio.chat.handler.im.wx;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.service.im.wx.WxPortalService;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/wx/portal/public：接收微信消息与事件 */
public class WxPortalMessageHandler {
  public HttpResponse handle(HttpRequest request) {
    String xml = Aop.get(WxPortalService.class).receive(request.getBodyString(), request.getParam("signature"),
        request.getParam("timestamp"), request.getParam("nonce"), request.getParam("encrypt_type"),
        request.getParam("msg_signature"));
    return TioRequestContext.getResponse().setString(xml, "utf-8", "application/xml; charset=UTF-8");
  }
}
