package nexus.io.tio.chat.handler.im.chat;

import java.util.ArrayList;
import java.util.List;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.chat.MessageSearchService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/**
 * GET /api/im/chat/msg/search
 * 参数：roomId、types（逗号分隔的消息类型）、keyword、startTime/endTime（毫秒）、sort（asc/desc）、pageNo、pageSize
 */
public class MsgSearchHandler {
  public HttpResponse handle(HttpRequest request) {
    Long roomId = ParameterValidationUtils.requiredLong(request, "roomId");
    int pageNo = Math.max(1, ParameterValidationUtils.optionalInt(request, "pageNo", 1));
    int pageSize = Math.min(100, Math.max(1, ParameterValidationUtils.optionalInt(request, "pageSize", 20)));
    boolean asc = "asc".equalsIgnoreCase(request.getParam("sort"));
    MessageSearchService service = Aop.get(MessageSearchService.class);
    return ChatResp.ok(service.search(LoginContext.uid(), roomId, parseTypes(request.getParam("types")),
        request.getParam("keyword"), ParameterValidationUtils.optionalLong(request, "startTime"),
        ParameterValidationUtils.optionalLong(request, "endTime"), asc, pageNo, pageSize));
  }

  static List<Integer> parseTypes(String value) {
    List<Integer> types = new ArrayList<>();
    if (value == null || value.isBlank()) {
      return types;
    }
    for (String part : value.split(",")) {
      String text = part.trim();
      if (text.isEmpty()) {
        continue;
      }
      try {
        types.add(Integer.parseInt(text));
      } catch (NumberFormatException e) {
        ParameterValidationUtils.isTrue(false, "types格式不正确");
      }
    }
    return types;
  }
}
