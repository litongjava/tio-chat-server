package nexus.io.tio.chat.interceptor;

import java.util.regex.Pattern;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.service.base.BaseAdminService;
import nexus.io.tio.chat.service.oauth.TokenService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.http.common.RequestLine;
import nexus.io.tio.http.server.intf.HttpRequestInterceptor;

/**
 * 解析 Token 请求头，写入登录会话；对需要登录的接口做校验。
 * 白名单规则与 原网关一致：/{module}/anyTenant/**、/{module}/anyUser/** 无需登录。
 */
public class ChatTokenInterceptor implements HttpRequestInterceptor {

  private static final Pattern PUBLIC_PATH = Pattern.compile(
      """
      ^/api/[^/]+/(anyTenant|anyUser|anno)/.*\
      |^/api/im/wx/portal/public.*\
      |^/api/ai/image/midjourney/notify$\
      |^/api/ai/video/giteeai/notify$\
      |^/api/system/model/list$\
      |^/api/ws/.*\
      |^/health$\
      """.strip());

  /**
   * AI 后台管理接口（原系统归属"管理后台"菜单，跨用户读写），仅管理员可访问
   */
  private static final Pattern AI_ADMIN_PATH = Pattern.compile(
      """
      ^/api/ai/.+/admin/.+\
      |^/api/ai/chat/(conversation|message)/(page|delete-by-admin)$\
      |^/api/ai/(image|video|music|write|mind-map)/(page|update|delete)$\
      """.strip());

  @Override
  public HttpResponse doBeforeHandler(HttpRequest request, RequestLine requestLine, HttpResponse httpResponse)
      throws Exception {
    String token = getToken(request);
    TokenService tokenService = Aop.get(TokenService.class);
    LoginSession session = tokenService.getSession(token);
    if (session != null) {
      LoginContext.set(request, session);
      String path = requestLine.getPath();
      if (AI_ADMIN_PATH.matcher(path).matches() && !Aop.get(BaseAdminService.class).isAdmin(session.getUid())) {
        // 与原网关一致：权限不足返回 406 与 "无此权限：uri"
        return httpResponse.setJson(ChatResp.toR(RespBodyVo.fail(ChatCode.TOKEN_EXPIRED, "无此权限：" + path), path));
      }
      return null;
    }
    String path = requestLine.getPath();
    if (PUBLIC_PATH.matcher(path).matches()) {
      return null;
    }
    if (token == null || token.isBlank()) {
      return httpResponse.setJson(ChatResp.toR(RespBodyVo.fail(ChatCode.UNAUTHORIZED, "请先登录"), path));
    }
    return httpResponse.setJson(ChatResp.toR(RespBodyVo.fail(ChatCode.TOKEN_EXPIRED, "token已过期"), path));
  }

  @Override
  public void doAfterHandler(HttpRequest request, RequestLine requestLine, HttpResponse response, long cost)
      throws Exception {
  }

  public static String getToken(HttpRequest request) {
    String token = request.getHeader("token");
    if (token == null || token.isBlank()) {
      token = request.getParam("Token");
    }
    if (token == null || token.isBlank()) {
      token = request.getParam("token");
    }
    if (token != null && token.startsWith("Bearer ")) {
      token = token.substring(7);
    }
    return token;
  }
}
