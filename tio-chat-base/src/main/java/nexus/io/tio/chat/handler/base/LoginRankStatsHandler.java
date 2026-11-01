package nexus.io.tio.chat.handler.base;

import java.time.LocalDateTime;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.oauth.LoginLogService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.TimeParams;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/base/baseLoginLog/stats/login-rank */
public class LoginRankStatsHandler {
  public HttpResponse handle(HttpRequest request) {
    LocalDateTime now = LocalDateTime.now();
    int days = ParameterValidationUtils.optionalInt(request, "rangeDays", 30);
    LocalDateTime start = TimeParams.parse(request.getParam("start"), now.minusDays(days));
    LocalDateTime end = TimeParams.parse(request.getParam("end"), now);
    int limit = ParameterValidationUtils.optionalInt(request, "limit", 50);
    return ChatResp.ok(Aop.get(LoginLogService.class).loginRank(start, end, limit));
  }
}
