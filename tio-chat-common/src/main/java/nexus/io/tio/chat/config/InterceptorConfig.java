package nexus.io.tio.chat.config;

import nexus.io.tio.boot.http.interceptor.HttpInteceptorConfigure;
import nexus.io.tio.boot.http.interceptor.HttpInterceptorModel;
import nexus.io.tio.boot.server.TioBootServer;
import nexus.io.tio.chat.interceptor.ChatTokenInterceptor;

public class InterceptorConfig {

  public void config() {
    HttpInterceptorModel model = new HttpInterceptorModel();
    model.setName("chat-token");
    model.setInterceptor(new ChatTokenInterceptor());
    model.addBlockUrl("/api/**");
    model.setAlloweStaticFile(true);

    HttpInteceptorConfigure configure = TioBootServer.me().getHttpInteceptorConfigure();
    if (configure == null) {
      configure = new HttpInteceptorConfigure();
    }
    configure.add(model);
    TioBootServer.me().setHttpInteceptorConfigure(configure);
  }
}
