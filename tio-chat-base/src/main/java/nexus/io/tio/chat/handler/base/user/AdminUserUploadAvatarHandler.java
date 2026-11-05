package nexus.io.tio.chat.handler.base.user;

import nexus.io.jfinal.aop.Aop;
import nexus.io.model.upload.UploadFile;
import nexus.io.tio.chat.service.base.AdminUserService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/base/user/uploadAvatar */
public class AdminUserUploadAvatarHandler {
  public HttpResponse handle(HttpRequest request) {
    UploadFile file = request.getUploadFile("file");
    ParameterValidationUtils.required(file, "请选择头像文件");
    return ChatResp.ok(Aop.get(AdminUserService.class).uploadAvatar(file.getName(), file.getData()));
  }
}
