package nexus.io.tio.chat.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * 校验由用户提交、服务端去下载的地址：只允许 http 和 https，并且不能指向本机和内网
 */
public final class PublicUrls {

  private PublicUrls() {
  }

  public static void require(String url) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw ChatErrors.of("地址格式不正确");
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    ChatErrors.require(("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null, "只支持 http 和 https 地址");
    try {
      for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
        ChatErrors.require(isPublic(address), "不允许访问内网地址");
      }
    } catch (UnknownHostException e) {
      throw ChatErrors.of("无法解析地址：" + uri.getHost());
    }
  }

  private static boolean isPublic(InetAddress address) {
    return !(address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
        || address.isMulticastAddress());
  }
}
