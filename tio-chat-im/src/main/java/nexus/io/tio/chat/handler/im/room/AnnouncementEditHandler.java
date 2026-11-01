package nexus.io.tio.chat.handler.im.room;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.service.im.group.AnnouncementService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/room/announcement/edit */
public class AnnouncementEditHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    AnnouncementService service = Aop.get(AnnouncementService.class);
    boolean ok = service.edit(LoginContext.uid(), Values.toLong(body.get("id")), Values.toLong(body.get("roomId")),
        body.getStr("content"), Values.toBool(body.get("top")));
    return ok ? ChatResp.ok(true) : ChatResp.fail(ChatCode.OPERATION_EX, "编辑失败");
  }
}
