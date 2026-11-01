package nexus.io.tio.chat.context;

import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.http.common.HttpRequest;

/**
 * 从当前请求中获取登录会话
 */
public class LoginContext {
  public static final String ATTR = "chat.login.session";
  public static final long DEFAULT_TENANT_ID = 1L;

  public static void set(HttpRequest request, LoginSession session) {
    request.setLocalAttribute(ATTR, session);
  }

  public static LoginSession get() {
    HttpRequest request = TioRequestContext.getRequest();
    if (request == null) {
      return null;
    }
    return (LoginSession) request.getLocalAttribute(ATTR);
  }

  public static LoginSession required() {
    LoginSession session = get();
    if (session == null) {
      throw ChatErrors.of(ChatCode.UNAUTHORIZED, "请先登录");
    }
    return session;
  }

  public static Long uid() {
    return required().getUid();
  }

  public static Long userId() {
    return required().getUserId();
  }

  public static Long tenantId() {
    LoginSession session = get();
    if (session != null && session.getTenantId() != null) {
      return session.getTenantId();
    }
    HttpRequest request = TioRequestContext.getRequest();
    if (request != null) {
      String header = request.getHeader("tenantid");
      if (header != null && !header.isBlank()) {
        try {
          return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
          return DEFAULT_TENANT_ID;
        }
      }
    }
    return DEFAULT_TENANT_ID;
  }
}
