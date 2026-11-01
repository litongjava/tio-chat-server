package nexus.io.tio.chat.handler.base;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.base.BaseExtService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/anyone/extendNotice/myNotice */
public class MyNoticeHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    long size = Values.toLong(body.get("size")) == null ? 10 : Values.toLong(body.get("size"));
    long current = Values.toLong(body.get("current")) == null ? 1 : Values.toLong(body.get("current"));
    return ChatResp.ok(Aop.get(BaseExtService.class).myNotice(current, size));
  }
}
