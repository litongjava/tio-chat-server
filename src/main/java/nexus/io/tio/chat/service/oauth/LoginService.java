package nexus.io.tio.chat.service.oauth;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.context.LoginSession;
import nexus.io.tio.chat.model.oauth.LoginParam;
import nexus.io.tio.chat.model.oauth.LoginResult;
import nexus.io.tio.chat.service.im.BlackService;
import nexus.io.tio.chat.service.im.ImUserService;
import nexus.io.tio.chat.utils.AccountUtils;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.ParameterValidationUtils;

/**
 * 登录、刷新与登出
 */
public class LoginService {

  public LoginResult login(LoginParam param, String authorization, String ip) {
    String grantType = param.getGrantType() == null ? "PASSWORD" : param.getGrantType().toUpperCase();
    checkParam(grantType, param);
    Aop.get(ClientService.class).checkAuthorization(authorization);

    DefUserService defUserService = Aop.get(DefUserService.class);
    Kv user;
    switch (grantType) {
    case "CAPTCHA":
      Aop.get(CaptchaService.class).check(param.getKey(), CaptchaService.GRANT_CAPTCHA, param.getCode());
      user = findByAccount(param.getSystemType(), param.getAccount());
      checkPassword(user, param.getPassword());
      break;
    case "MOBILE":
      Aop.get(CaptchaService.class).check(param.getMobile(), CaptchaService.MOBILE_LOGIN, param.getCode());
      user = defUserService.getByMobile(param.getSystemType(), param.getMobile());
      if (user == null) {
        throw ChatErrors.of(ChatCode.JWT_USER_INVALID, "用户不存在");
      }
      break;
    case "PASSWORD":
      user = findByAccount(param.getSystemType(), param.getAccount());
      checkPassword(user, param.getPassword());
      break;
    default:
      throw ChatErrors.of(ChatCode.OPERATION_EX, "不支持的登录方式");
    }
    checkState(user);
    defUserService.loginSuccess(user.getLong("id"), ip);
    return issue(user, param.getDeviceType(), param.getClientId(), ip);
  }

  /**
   * 为已认证用户签发 token，供密码登录、扫码登录和第三方登录共用
   */
  public LoginResult issue(Kv user, String deviceType, String clientId, String ip) {
    long userId = user.getLong("id");
    long tenantId = user.getLong("tenant_id") == null ? 1L : user.getLong("tenant_id");
    Integer systemType = user.getInt("system_type");
    Long uid = userId;
    if (systemType == null || systemType == ImConst.SYSTEM_TYPE_IM) {
      ImUserService imUserService = Aop.get(ImUserService.class);
      uid = imUserService.findUidByUserId(userId, tenantId);
      if (uid == null) {
        throw ChatErrors.of(ChatCode.JWT_USER_INVALID, "IM 用户不存在");
      }
      if (Aop.get(BlackService.class).isBlack(uid, ip)) {
        throw ChatErrors.of("你已被拉黑");
      }
      imUserService.refreshLoginInfo(uid, ip);
    }
    LoginSession session = LoginSession.builder().uid(uid).userId(userId).tenantId(tenantId).systemType(systemType)
        .deviceType(deviceType).clientId(clientId).build();
    return createTokens(session, ip);
  }

  public LoginResult refresh(String refreshToken, String ip) {
    TokenService tokenService = Aop.get(TokenService.class);
    LoginSession old = tokenService.consumeRefreshToken(refreshToken);
    if (old == null || old.getUserId() == null) {
      throw ChatErrors.of(ChatCode.TOKEN_EXPIRED, "token已过期");
    }
    Kv user = Aop.get(DefUserService.class).getById(old.getUserId());
    if (user == null) {
      throw ChatErrors.of(ChatCode.JWT_USER_INVALID, "用户不存在");
    }
    checkState(user);
    LoginSession session = LoginSession.builder().uid(old.getUid()).userId(old.getUserId())
        .tenantId(old.getTenantId()).systemType(old.getSystemType()).deviceType(old.getDeviceType())
        .clientId(old.getClientId()).build();
    return createTokens(session, ip);
  }

  public boolean logout(LoginSession session) {
    if (session == null) {
      throw ChatErrors.of(ChatCode.TOKEN_EXPIRED, "token已过期");
    }
    TokenService tokenService = Aop.get(TokenService.class);
    tokenService.logout(session);
    if (!tokenService.hasAnyToken(session.getUserId())) {
      Aop.get(LoginEventService.class).onOffline(session);
    }
    return true;
  }

  private LoginResult createTokens(LoginSession session, String ip) {
    TokenService tokenService = Aop.get(TokenService.class);
    String kicked = tokenService.login(session);
    if (kicked != null) {
      Aop.get(LoginEventService.class).onKickout(session, kicked, ip);
    }
    String refreshToken = tokenService.createRefreshToken(session);
    LoginResult result = new LoginResult();
    result.setUid(session.getUid());
    result.setToken(session.getToken());
    result.setRefreshToken(refreshToken);
    result.setClient(session.getDeviceType());
    result.setExpire(tokenService.tokenTimeout());
    result.setExpiration(System.currentTimeMillis() + tokenService.tokenTimeout() * 1000);
    return result;
  }

  private void checkParam(String grantType, LoginParam param) {
    if ("MOBILE".equals(grantType)) {
      ParameterValidationUtils.isTrue(notBlank(param.getMobile()) && notBlank(param.getCode()), "请输入手机号或验证码");
      return;
    }
    ParameterValidationUtils.isTrue(notBlank(param.getAccount()) && notBlank(param.getPassword()), "请输入用户名或密码");
    if ("CAPTCHA".equals(grantType)) {
      ParameterValidationUtils.isTrue(notBlank(param.getKey()) && notBlank(param.getCode()), "请输入验证码");
    }
  }

  private Kv findByAccount(Integer systemType, String account) {
    DefUserService defUserService = Aop.get(DefUserService.class);
    if (AccountUtils.isEmail(account)) {
      return defUserService.getByEmail(systemType, account);
    }
    Kv user = defUserService.getByUsername(systemType, account);
    if (user == null) {
      user = defUserService.getByMobile(systemType, account);
    }
    return user;
  }

  private void checkPassword(Kv user, String password) {
    if (user == null) {
      throw ChatErrors.of(ChatCode.JWT_USER_INVALID, "用户名或密码错误");
    }
    String hashed = AccountUtils.hashPassword(password, user.getStr("salt"));
    if (!hashed.equalsIgnoreCase(user.getStr("password"))) {
      Aop.get(DefUserService.class).passwordError(user.getLong("id"));
      throw ChatErrors.of(ChatCode.OPERATION_EX, "用户名或密码错误");
    }
  }

  private void checkState(Kv user) {
    Object state = user.get("state");
    boolean enabled = state == null || Boolean.TRUE.equals(state) || "1".equals(state.toString());
    if (!enabled) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "您已被禁用，请联系管理员开通账号！");
    }
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
