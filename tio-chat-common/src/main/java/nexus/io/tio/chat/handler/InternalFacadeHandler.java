package nexus.io.tio.chat.handler;

import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.facade.FacadeClient;
import nexus.io.tio.chat.facade.FacadeInvoker;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /internal/facade/{base|im}：服务间调用入口，只接受带内部密钥的请求，网关不转发 */
public class InternalFacadeHandler {
  private final FacadeInvoker invoker;

  public InternalFacadeHandler(Class<?> facadeType, Object target) {
    this.invoker = new FacadeInvoker(facadeType, target);
  }

  public HttpResponse handle(HttpRequest request) throws Exception {
    if (!FacadeClient.secret().equals(request.getHeader(FacadeClient.SECRET_HEADER))) {
      return ChatResp.fail(ChatCode.UNAUTHORIZED, "forbidden");
    }
    try {
      return ChatResp.ok(invoker.invoke(request.getBodyString()));
    } catch (Exception e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }
}
