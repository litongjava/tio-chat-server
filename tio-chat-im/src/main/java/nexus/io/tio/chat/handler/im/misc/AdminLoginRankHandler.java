package nexus.io.tio.chat.handler.im.misc;

import java.time.LocalDateTime;
import java.time.LocalTime;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImExtraService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.TimeParams;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/im/admin/stats/login-rank */
public class AdminLoginRankHandler {
  public HttpResponse handle(HttpRequest request) {
    LocalDateTime now = LocalDateTime.now();
    int days = ParameterValidationUtils.optionalInt(request, "rangeDays", 30);
    LocalDateTime start = TimeParams.parse(request.getParam("start"), now.minusDays(days).with(LocalTime.MIN));
    LocalDateTime end = TimeParams.parse(request.getParam("end"), now.with(LocalTime.MAX));

    int limit = ParameterValidationUtils.optionalInt(request, "limit", 50);
    ImExtraService service = Aop.get(ImExtraService.class);
    return ChatResp.ok(service.loginRank(start, end, limit));
  }
}
