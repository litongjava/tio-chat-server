package nexus.io.tio.chat.service.oauth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.model.oauth.LoginResult;
import nexus.io.tio.chat.service.im.ImUserService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.HttpClients;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * Gitee、GitHub、GitCode 第三方登录：授权地址、code 换取用户信息、首次登录自动注册或按邮箱绑定
 */
public class ThirdPartyAuthService {

  private String clientId(String provider) {
    return EnvUtils.get("oauth." + provider + ".client-id", "");
  }

  private String clientSecret(String provider) {
    return EnvUtils.get("oauth." + provider + ".client-secret", "");
  }

  private String redirectUri(String provider) {
    String uri = EnvUtils.get("oauth." + provider + ".redirect-uri", "").trim();
    return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
  }

  private static String enc(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public String authorizeUrl(String provider, String redirect) {
    String state = redirect == null || redirect.isBlank() ? "" : "&state=" + enc(redirect);
    String uri = enc(redirectUri(provider));
    return switch (provider) {
    case "gitee" -> "https://gitee.com/oauth/authorize?client_id=" + clientId(provider) + "&redirect_uri=" + uri
        + "&response_type=code" + state;
    case "github" -> "https://github.com/login/oauth/authorize?client_id=" + clientId(provider) + "&redirect_uri=" + uri
        + "&scope=user:email" + state;
    case "gitcode" -> "https://gitcode.com/oauth/authorize?client_id=" + clientId(provider) + "&redirect_uri=" + uri
        + "&response_type=code" + state;
    default -> throw ChatErrors.of("授权类型不支持");
    };
  }

  private Map<String, Object> userInfo(String provider, String code) {
    String form = "grant_type=authorization_code&code=" + enc(code) + "&client_id=" + enc(clientId(provider))
        + "&client_secret=" + enc(clientSecret(provider)) + "&redirect_uri=" + enc(redirectUri(provider));
    String tokenUrl = switch (provider) {
    case "gitee" -> "https://gitee.com/oauth/token";
    case "github" -> "https://github.com/login/oauth/access_token";
    default -> "https://gitcode.com/oauth/token";
    };
    String tokenResp = HttpClients.post(tokenUrl, "application/x-www-form-urlencoded", form, Map.of("Accept", "application/json"));
    Object accessToken = JsonUtils.parseToMap(tokenResp, String.class, Object.class).get("access_token");
    ChatErrors.require(accessToken != null, "获取 Access Token 失败");
    String userUrl = switch (provider) {
    case "gitee" -> "https://gitee.com/api/v5/user?access_token=" + accessToken;
    case "github" -> "https://api.github.com/user";
    default -> "https://api.gitcode.com/api/v5/user?access_token=" + accessToken;
    };
    String userResp = "github".equals(provider)
        ? HttpClients.get(userUrl, Map.of("Authorization", "Bearer " + accessToken, "Accept", "application/json"))
        : HttpClients.get(userUrl);
    return JsonUtils.parseToMap(userResp, String.class, Object.class);
  }

  /**
   * 第三方登录：已绑定直接登录；否则按邮箱绑定已有账号；都没有则自动注册
   */
  public LoginResult login(String provider, String code, String ip) {
    ChatErrors.require(code != null && !code.isBlank(), "授权码不能为空");
    Map<String, Object> info = userInfo(provider, code);
    Object openIdValue = info.get("id");
    ChatErrors.require(openIdValue != null, "获取第三方用户 ID 失败");
    String openId = String.valueOf(openIdValue);
    String column = provider + "_open_id";
    String imColumn = provider + "_id";
    Kv user = Db.findFirstMap("select * from def_user where " + column + "=? and is_del=0 limit 1", openId);
    if (user == null) {
      String login = str(info.get("login"));
      String name = str(info.get("name"));
      String avatar = str(info.get("avatar_url"));
      String email = str(info.get("email"));
      Kv byEmail = email == null ? null : Db.findFirstMap("select * from def_user where email=? and is_del=0 limit 1", email);
      if (byEmail != null) {
        Db.update("update def_user set " + column + "=?, avatar=coalesce(?, avatar), nick_name=coalesce(?, nick_name) where id=?", openId,
            avatar, name, byEmail.getLong("id"));
        Db.update("update im_user set " + imColumn + "=? where email=?", openId, email);
        user = Db.findFirstMap("select * from def_user where id=?", byEmail.getLong("id"));
      } else {
        String mail = email == null ? openId + "@" + provider + ".com" : email;
        DefUserService defUserService = Aop.get(DefUserService.class);
        Kv created = Db.txResult(() -> {
          Kv u = defUserService.create(ImConst.SYSTEM_TYPE_IM, mail, null, login == null ? openId : login,
              name == null ? login : name, avatar, 1, 1L);
          Db.update("update def_user set " + column + "=? where id=?", openId, u.getLong("id"));
          long uid = Aop.get(ImUserService.class).register(u.getLong("id"), u.getStr("username"), u.getStr("nick_name"), mail,
              u.getStr("avatar"), 1, 1L);
          Db.update("update im_user set " + imColumn + "=? where id=?", openId, uid);
          return u;
        });
        user = Db.findFirstMap("select * from def_user where id=?", created.getLong("id"));
      }
    }
    return Aop.get(LoginService.class).issue(user, "PC", "WEB", ip);
  }

  private String str(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }
}
