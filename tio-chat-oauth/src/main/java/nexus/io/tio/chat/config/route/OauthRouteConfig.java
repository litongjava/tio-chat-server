package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.oauth.CaptchaHandler;
import nexus.io.tio.chat.handler.oauth.CheckCaptchaHandler;
import nexus.io.tio.chat.handler.oauth.CheckEmailHandler;
import nexus.io.tio.chat.handler.oauth.CheckMobileHandler;
import nexus.io.tio.chat.handler.oauth.CheckTokenHandler;
import nexus.io.tio.chat.handler.oauth.GetUserInfoHandler;
import nexus.io.tio.chat.handler.oauth.LoginHandler;
import nexus.io.tio.chat.handler.oauth.LogoutHandler;
import nexus.io.tio.chat.handler.oauth.QrConfirmHandler;
import nexus.io.tio.chat.handler.oauth.QrGenerateHandler;
import nexus.io.tio.chat.handler.oauth.QrScanHandler;
import nexus.io.tio.chat.handler.oauth.QrStatusQueryHandler;
import nexus.io.tio.chat.handler.oauth.RefreshTokenHandler;
import nexus.io.tio.chat.handler.oauth.RegisterByEmailHandler;
import nexus.io.tio.chat.handler.oauth.RegisterByMobileHandler;
import nexus.io.tio.chat.handler.oauth.SendEmailCodeHandler;
import nexus.io.tio.chat.handler.oauth.SendSmsCodeHandler;
import nexus.io.tio.chat.handler.oauth.UpdateAvatarHandler;
import nexus.io.tio.chat.handler.oauth.UpdateBaseInfoHandler;
import nexus.io.tio.chat.handler.oauth.UpdateEmailHandler;
import nexus.io.tio.chat.handler.oauth.UpdateMobileHandler;
import nexus.io.tio.chat.handler.oauth.UpdatePasswordHandler;
import nexus.io.tio.chat.handler.oauth.VerifyTokenHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class OauthRouteConfig {
  private static final String P = "/api/oauth";

  public void config(HttpRequestRouter r) {
    r.post(P + "/anyTenant/login", new LoginHandler()::handle);
    r.post(P + "/anyTenant/refresh", new RefreshTokenHandler()::handle);
    r.put(P + "/anyTenant/password", new UpdatePasswordHandler()::handle);
    r.post(P + "/anyUser/logout", new LogoutHandler()::handle);
    r.get(P + "/anyTenant/verify", new VerifyTokenHandler()::handle);
    r.post(P + "/check", new CheckTokenHandler()::handle);

    r.post(P + "/anyTenant/registerByEmail", new RegisterByEmailHandler()::handle);
    r.post(P + "/anyTenant/registerByMobile", new RegisterByMobileHandler()::handle);
    r.get(P + "/anyTenant/checkEmail", new CheckEmailHandler()::handle);
    r.get(P + "/anyTenant/checkMobile", new CheckMobileHandler()::handle);

    r.get(P + "/anyTenant/captcha", new CaptchaHandler()::handle);
    r.get(P + "/anyTenant/checkCaptcha", new CheckCaptchaHandler()::handle);
    r.get(P + "/anyTenant/sendSmsCode", new SendSmsCodeHandler()::handle);
    r.post(P + "/anyTenant/sendEmailCode", new SendEmailCodeHandler()::handle);

    r.get(P + "/anyTenant/qr/generate", new QrGenerateHandler()::handle);
    r.get(P + "/anyTenant/qr/status/query", new QrStatusQueryHandler()::handle);
    r.post(P + "/qrcode/scan", new QrScanHandler()::handle);
    r.post(P + "/qrcode/confirm", new QrConfirmHandler()::handle);

    r.get(P + "/anyone/getUserInfo", new GetUserInfoHandler()::handle);
    r.put(P + "/anyone/avatar", new UpdateAvatarHandler()::handle);
    r.put(P + "/anyone/mobile", new UpdateMobileHandler()::handle);
    r.put(P + "/anyone/email", new UpdateEmailHandler()::handle);
    r.put(P + "/anyone/baseInfo", new UpdateBaseInfoHandler()::handle);
  }
}
