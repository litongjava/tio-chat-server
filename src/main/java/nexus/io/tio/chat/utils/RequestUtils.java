package nexus.io.tio.chat.utils;

import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.utils.HttpIpUtils;

public class RequestUtils {

  public static String ip(HttpRequest request) {
    if (request == null) {
      return "";
    }
    return HttpIpUtils.getRealIp(request);
  }
}
