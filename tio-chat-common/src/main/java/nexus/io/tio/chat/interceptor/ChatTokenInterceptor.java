package nexus.io.tio.chat.interceptor;

import nexus.io.tio.chat.facade.Facades;
import java.util.regex.Pattern;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.context.LoginSession;
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
      |^/internal/facade/.*\
      """.strip());

  /**
   * 下面的接口只有管理员，或者角色被授予了对应接口（def_resource_api）的用户可以访问：消息接口与模板（其中的脚本在服务端执行）、服务器监控、数据源配置、黑名单管理和内部的用户开通接口（可以操作其他用户或暴露系统信息），以及
   * AI 后台管理接口（原系统归属"管理后台"菜单，跨用户读写）。原系统放开的审核租户和无需登录发送消息也在其中：
   * 前者任何人都能通过自己的租户，后者可以冒用模板给任意收件人发送邮件和短信。
   * chat/conversation/page 除外：客户端的 AI 助手用它列出自己的对话，服务端只返回本人的对话
   */
  private static final Pattern AI_ADMIN_PATH = Pattern.compile(
      """
      ^/api/ai/.+/admin/.+\
      |^/api/ai/chat/conversation/delete-by-admin$\
      |^/api/ai/chat/message/(page|delete-by-admin)$\
      |^/api/ai/(image|video|music|write|mind-map)/(page|update|delete)$\
      |^/api/im/defServer$\
      |^/api/generator/.+$\
      |^/api/im/defDatasourceConfig(/.*)?$\
      |^/api/base/(?!(.*/)?(anyone|anyUser|anyTenant)/)(?!user/(current|updateCurrent|uploadAvatar)$).+$\
      |^/api/base/anyone/defTenant/updateStatus$\
      |^/api/base/extendMsg/anyUser/sendByTemplate$\
      |^/api/im/user/(register|bindOAuth)$\
      |^/api/im/user/black(/page|/edit|/remove)?$\
      """.strip());

  /** 管理端（systemType=10）的用户除这些接口外，都要校验接口权限，与原网关的 ignore 配置一致 */
  private static final Pattern NO_URI_CHECK_PATH = Pattern.compile("^/api/([^/]+/)*(anyone|anyUser|anyTenant|anno)/.*|^/api/base/user/(current|updateCurrent|uploadAvatar)$");
  private static final int SYSTEM_TYPE_MANAGE = 10;
  private static final String API_PREFIX = "/api";

  @Override
  public HttpResponse doBeforeHandler(HttpRequest request, RequestLine requestLine, HttpResponse httpResponse)
      throws Exception {
    String token = getToken(request);
    TokenService tokenService = Aop.get(TokenService.class);
    LoginSession session = tokenService.getSession(token);
    if (session != null) {
      LoginContext.set(request, session);
      String path = requestLine.getPath();
      if (needsUriCheck(session, path) && !allowed(session, requestLine, path)) {
        // 与原网关一致：权限不足返回 406 与 "无此权限：uri"
        return httpResponse.setJson(ChatResp.toR(RespBodyVo.fail(ChatCode.TOKEN_EXPIRED, "无此权限：" + path), path));
      }
      return null;
    }
    String path = requestLine.getPath();
    if (PUBLIC_PATH.matcher(path).matches() && !AI_ADMIN_PATH.matcher(path).matches()) {
      return null;
    }
    if (token == null || token.isBlank()) {
      return httpResponse.setJson(ChatResp.toR(RespBodyVo.fail(ChatCode.UNAUTHORIZED, "请先登录"), path));
    }
    return httpResponse.setJson(ChatResp.toR(RespBodyVo.fail(ChatCode.TOKEN_EXPIRED, "token已过期"), path));
  }

  private boolean needsUriCheck(LoginSession session, String path) {
    if (AI_ADMIN_PATH.matcher(path).matches()) {
      return true;
    }
    return session.getSystemType() != null && session.getSystemType() == SYSTEM_TYPE_MANAGE && path.startsWith(API_PREFIX + "/")
        && !NO_URI_CHECK_PATH.matcher(path).matches() && !PUBLIC_PATH.matcher(path).matches();
  }

  private boolean allowed(LoginSession session, RequestLine requestLine, String path) {
    return Facades.base().hasApiPermission(session.getUid(), requestLine.getMethod().name(), path.substring(API_PREFIX.length()));
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
