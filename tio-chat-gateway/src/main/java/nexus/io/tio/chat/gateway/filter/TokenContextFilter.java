package nexus.io.tio.chat.gateway.filter;

import nexus.io.tio.boot.cloud.gateway.GatewayExchange;
import nexus.io.tio.boot.cloud.gateway.filter.GatewayFilterChain;
import nexus.io.tio.boot.cloud.gateway.filter.GlobalFilter;
import nexus.io.tio.chat.gateway.model.GatewaySession;
import nexus.io.tio.chat.gateway.service.SessionService;
import nexus.io.tio.http.common.HttpResponse;

/**
 * 解析 Token（请求头或 Token 参数），把登录用户写入 userId、uid、tenantId、systemType 请求头
 * 传给下游服务；客户端自带的这些请求头先删除，下游可以信任它们。是否需要登录仍由各服务判断，
 * 错误码和提示与单进程运行时一致。
 */
public class TokenContextFilter implements GlobalFilter {
  private static final String[] IDENTITY_HEADERS = { "userid", "uid", "tenantid", "systemtype" };

  private final SessionService sessionService;

  public TokenContextFilter(SessionService sessionService) {
    this.sessionService = sessionService;
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public HttpResponse filter(GatewayExchange exchange, GatewayFilterChain chain) throws Exception {
    for (String header : IDENTITY_HEADERS) {
      exchange.removeRequestHeader(header);
    }
    GatewaySession session = sessionService.find(token(exchange));
    if (session != null) {
      setHeader(exchange, "userId", session.getUserId());
      setHeader(exchange, "uid", session.getUid());
      setHeader(exchange, "tenantId", session.getTenantId());
      setHeader(exchange, "systemType", session.getSystemType());
    }
    return chain.filter(exchange);
  }

  private static void setHeader(GatewayExchange exchange, String name, Object value) {
    if (value != null) {
      exchange.setRequestHeader(name, String.valueOf(value));
    }
  }

  private static String token(GatewayExchange exchange) {
    String token = exchange.getRequestHeader("token");
    if (token == null || token.isEmpty()) {
      token = exchange.getRequest().getParam("Token");
    }
    if (token == null || token.isEmpty()) {
      token = exchange.getRequest().getParam("token");
    }
    if (token != null && token.startsWith("Bearer ")) {
      token = token.substring(7);
    }
    return token;
  }
}
