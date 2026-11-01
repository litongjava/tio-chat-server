package nexus.io.tio.chat;

import nexus.io.tio.chat.ai.AiModule;
import nexus.io.tio.chat.base.BaseModule;
import nexus.io.tio.chat.boot.ChatApplication;
import nexus.io.tio.chat.im.ImModule;
import nexus.io.tio.chat.oauth.OauthModule;
import nexus.io.tio.chat.system.SystemModule;
import nexus.io.tio.chat.ws.WsModule;

/**
 * 所有模块打包成一个服务运行，默认端口 18760
 */
public class ChatServerApp {
  public static void main(String[] args) {
    ChatApplication.runAll(ChatServerApp.class, args, new OauthModule(), new ImModule(), new BaseModule(),
        new SystemModule(), new AiModule(), new WsModule());
  }
}
