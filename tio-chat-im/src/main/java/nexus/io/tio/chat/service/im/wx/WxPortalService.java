package nexus.io.tio.chat.service.im.wx;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.jfinal.kit.Kv;

import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.api.WxConsts;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.redis.Redis;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.DbRows;
import nexus.io.tio.utils.environment.EnvUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 微信公众号门户：服务器配置校验、接收消息与事件、网页授权回调。配置项 wx.mp.app-id、
 * wx.mp.secret、wx.mp.token、wx.mp.aes-key、wx.mp.callback。
 */
@Slf4j
public class WxPortalService {
  private static final String AUTH_URL = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_userinfo&state=STATE#wechat_redirect";
  private static final String EVENT_KEY_PREFIX = "qrscene_";
  private static final String CALLBACK_PATH = "/api/im/wx/portal/public/callBack";
  private static final String OPEN_ID_KEY = "luohuo:wx:openid:";
  private static final long OPEN_ID_EXPIRE_SECONDS = 3600;

  private volatile WxMpService service;

  public String verify(String signature, String timestamp, String nonce, String echostr) {
    if (isBlank(signature) || isBlank(timestamp) || isBlank(nonce) || isBlank(echostr)) {
      throw ChatErrors.of("请求参数非法，请核实!");
    }
    return service().checkSignature(timestamp, nonce, signature) ? echostr : "非法请求";
  }

  /**
   * @return 回复微信的 XML，不需要回复时为空字符串
   */
  public String receive(String body, String signature, String timestamp, String nonce, String encType,
      String msgSignature) {
    WxMpService wx = service();
    if (!wx.checkSignature(timestamp, nonce, signature)) {
      throw ChatErrors.of("非法请求，可能属于伪造的请求！");
    }
    if (encType == null) {
      WxMpXmlOutMessage out = route(WxMpXmlMessage.fromXml(body), wx);
      return out == null ? "" : out.toXml();
    }
    if ("aes".equalsIgnoreCase(encType)) {
      WxMpXmlMessage in = WxMpXmlMessage.fromEncryptedXml(body, wx.getWxMpConfigStorage(), timestamp, nonce, msgSignature);
      WxMpXmlOutMessage out = route(in, wx);
      return out == null ? "" : out.toEncryptedXml(wx.getWxMpConfigStorage());
    }
    return "";
  }

  /** 网页授权回调：记录用户信息，完善未填写昵称的 IM 用户 */
  public void authorize(String code) {
    try {
      WxMpService wx = service();
      WxOAuth2AccessToken token = wx.getOAuth2Service().getAccessToken(code);
      WxOAuth2UserInfo info = wx.getOAuth2Service().getUserInfo(token, "zh_CN");
      Kv user = Db.findFirstMap("select id, name, user_type from im_user where open_id=? and is_del=0 limit 1", info.getOpenid());
      if (user == null || Integer.valueOf(ImConst.USER_TYPE_BOT).equals(user.getInt("user_type"))) {
        return;
      }
      String name = user.getStr("name");
      if (name == null || name.isEmpty()) {
        String account = UUID.randomUUID().toString();
        Db.update("update im_user set avatar=?, account=?, sex=?, name=? where id=?", info.getHeadImgUrl(), account,
            info.getSex(), "hula用户_" + account.substring(account.length() - 6), user.getLong("id"));
      }
    } catch (WxErrorException e) {
      log.error("wx callback failed: {}", e.getMessage());
    }
  }

  private WxMpXmlOutMessage route(WxMpXmlMessage message, WxMpService wx) {
    if (WxConsts.XmlMsgType.EVENT.equals(message.getMsgType())) {
      if (WxConsts.EventType.SUBSCRIBE.equals(message.getEvent())) {
        WxMpXmlOutMessage scanned = scanQuietly(message, wx);
        return scanned != null ? scanned : text("感谢关注", message);
      }
      if (WxConsts.EventType.SCAN.equals(message.getEvent())) {
        return scanQuietly(message, wx);
      }
    }
    saveMessage(message);
    return null;
  }

  private WxMpXmlOutMessage scanQuietly(WxMpXmlMessage message, WxMpService wx) {
    try {
      return scan(message, wx);
    } catch (RuntimeException e) {
      log.error("wx scan failed: {}", e.getMessage());
      return null;
    }
  }

  /** 扫码：已完善资料的用户不需要授权，其他用户收到授权链接 */
  private WxMpXmlOutMessage scan(WxMpXmlMessage message, WxMpService wx) {
    String openId = message.getFromUser();
    int loginCode = Integer.parseInt(message.getEventKey().replace(EVENT_KEY_PREFIX, ""));
    Kv user = Db.findFirstMap("select id, avatar, user_type from im_user where open_id=? and is_del=0 limit 1", openId);
    if (user != null && !isBlank(user.getStr("avatar"))) {
      if (Integer.valueOf(ImConst.USER_TYPE_BOT).equals(user.getInt("user_type"))) {
        return text("机器人账号不允许登录", message);
      }
      return null;
    }
    Redis.call(jedis -> jedis.setex(OPEN_ID_KEY + openId, OPEN_ID_EXPIRE_SECONDS, String.valueOf(loginCode)));
    String callback = URLEncoder.encode(EnvUtils.get("wx.mp.callback", "") + CALLBACK_PATH, StandardCharsets.UTF_8);
    String url = String.format(AUTH_URL, wx.getWxMpConfigStorage().getAppId(), callback);
    return text("请点击链接授权：<a href=\"" + url + "\">登录</a>", message);
  }

  private void saveMessage(WxMpXmlMessage message) {
    DbRows.save("im_wx_msg", Row.by("id", SnowflakeIdUtils.id()).set("open_id", message.getFromUser())
        .set("msg", message.getContent()).set("tenant_id", 1L));
  }

  private WxMpXmlOutMessage text(String content, WxMpXmlMessage message) {
    return WxMpXmlOutMessage.TEXT().content(content).fromUser(message.getToUser()).toUser(message.getFromUser()).build();
  }

  private WxMpService service() {
    if (service == null) {
      synchronized (this) {
        if (service == null) {
          String appId = EnvUtils.get("wx.mp.app-id");
          if (isBlank(appId)) {
            throw ChatErrors.of("未配置微信公众号");
          }
          WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
          config.setAppId(appId);
          config.setSecret(EnvUtils.get("wx.mp.secret"));
          config.setToken(EnvUtils.get("wx.mp.token"));
          config.setAesKey(EnvUtils.get("wx.mp.aes-key"));
          WxMpServiceImpl impl = new WxMpServiceImpl();
          impl.setWxMpConfigStorage(config);
          service = impl;
        }
      }
    }
    return service;
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
