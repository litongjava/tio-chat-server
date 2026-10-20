package nexus.io.tio.chat.utils;

import com.jfinal.kit.Kv;

import nexus.io.model.body.RespBodyVo;
import nexus.io.tio.boot.http.TioRequestContext;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;
import nexus.io.tio.utils.hutool.StrUtil;

/**
 * 把 RespBodyVo 转成 客户端使用的响应结构：
 * {code, data, msg, path, version, timestamp, success}
 */
public class ChatResp {

  public static final String VERSION = "1.0.0";

  public static HttpResponse respond(RespBodyVo vo) {
    HttpRequest request = TioRequestContext.getRequest();
    String path = request == null ? null : request.getRequestURI();
    return TioRequestContext.getResponse().setJson(toR(vo, path));
  }

  public static HttpResponse ok(Object data) {
    return respond(RespBodyVo.ok(data));
  }

  public static HttpResponse ok() {
    return respond(RespBodyVo.ok());
  }

  public static HttpResponse fail(int code, String msg) {
    return respond(RespBodyVo.fail(code, msg));
  }

  public static Kv toR(RespBodyVo vo, String path) {
    int code = toChatCode(vo);
    String msg = vo.getMsg();
    if (StrUtil.isBlank(msg)) {
      msg = code == ChatCode.SUCCESS ? "ok" : "";
    }
    Kv kv = Kv.by("code", code).set("data", vo.getData()).set("msg", msg);
    kv.set("path", path).set("version", VERSION).set("timestamp", System.currentTimeMillis());
    kv.set("success", code == ChatCode.SUCCESS);
    return kv;
  }

  private static int toChatCode(RespBodyVo vo) {
    Integer code = vo.getCode();
    if (vo.isOk()) {
      return ChatCode.SUCCESS;
    }
    if (code == null || code == 0) {
      return ChatCode.OPERATION_EX;
    }
    return code;
  }
}
