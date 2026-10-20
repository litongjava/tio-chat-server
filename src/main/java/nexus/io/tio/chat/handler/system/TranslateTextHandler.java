package nexus.io.tio.chat.handler.system;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.system.TranslateService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/system/anyTenant/translate/text */
public class TranslateTextHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    TranslateService service = Aop.get(TranslateService.class);
    String provider = service.resolveProvider(body.getStr("provider"));
    String result = service.translate(body.getStr("text"), provider, body.getStr("source"), body.getStr("target"));
    return ChatResp.ok(Kv.by("result", result).set("provider", provider));
  }
}
