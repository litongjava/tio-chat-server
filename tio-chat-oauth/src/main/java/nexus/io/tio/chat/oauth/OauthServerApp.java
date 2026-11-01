package nexus.io.tio.chat.oauth;

import nexus.io.tio.chat.boot.ChatApplication;

/**
 * 单独运行 luohuo-oauth-server，默认端口 18773，启动后注册到 Nacos
 */
public class OauthServerApp {
  public static void main(String[] args) {
    ChatApplication.runService(OauthServerApp.class, args, OauthModule.SERVICE_NAME, OauthModule.DEFAULT_PORT, new OauthModule());
  }
}
