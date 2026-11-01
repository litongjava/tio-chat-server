package nexus.io.tio.chat.handler.im.misc;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.SensitiveWordService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/sensitiveWord/check */
public class SensitiveWordCheckHandler {
  public HttpResponse handle(HttpRequest request) {
    String text = request.getParam("text");
    ParameterValidationUtils.notBlank(text, "text不能为空");
    SensitiveWordService service = Aop.get(SensitiveWordService.class);
    return ChatResp.ok(Kv.by("hit", service.hasSensitiveWord(text)).set("filtered", service.filter(text)));
  }
}
