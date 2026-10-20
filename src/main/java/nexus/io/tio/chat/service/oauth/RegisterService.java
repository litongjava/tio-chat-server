package nexus.io.tio.chat.service.oauth;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.model.oauth.PasswordUpdateReq;
import nexus.io.tio.chat.model.oauth.RegisterParam;
import nexus.io.tio.chat.service.im.ImUserService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.ParameterValidationUtils;

public class RegisterService {

  private static final long DEFAULT_TENANT_ID = 1L;

  public String registerByEmail(RegisterParam param) {
    ParameterValidationUtils.notBlank(param.getEmail(), "邮箱不能为空");
    ParameterValidationUtils.notBlank(param.getPassword(), "密码不能为空");
    ParameterValidationUtils.isTrue(param.getPassword().equals(param.getConfirmPassword()), "密码和确认密码不一致");
    Aop.get(CaptchaService.class).check(param.getEmail(), keyOrDefault(param.getKey(), CaptchaService.REGISTER_EMAIL),
        param.getCode());

    DefUserService defUserService = Aop.get(DefUserService.class);
    ImUserService imUserService = Aop.get(ImUserService.class);
    if (defUserService.existsEmail(param.getEmail())) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "账号已存在！");
    }
    int systemType = param.getSystemType() == null ? ImConst.SYSTEM_TYPE_IM : param.getSystemType();
    if (systemType == ImConst.SYSTEM_TYPE_IM && imUserService.existsEmail(param.getEmail())) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "该邮箱已被其他账号绑定");
    }
    Db.txResult(() -> {
      Kv user = defUserService.create(systemType, param.getEmail(), null, param.getPassword(), param.getNickName(),
          param.getAvatar(), param.getSex(), DEFAULT_TENANT_ID);
      if (systemType == ImConst.SYSTEM_TYPE_IM) {
        imUserService.register(user.getLong("id"), user.getStr("username"), user.getStr("nick_name"),
            param.getEmail(), param.getAvatar(), param.getSex(), DEFAULT_TENANT_ID);
      }
      return user;
    });
    return param.getEmail();
  }

  public String registerByMobile(RegisterParam param) {
    ParameterValidationUtils.notBlank(param.getMobile(), "手机号不能为空");
    ParameterValidationUtils.notBlank(param.getPassword(), "密码不能为空");
    ParameterValidationUtils.isTrue(param.getPassword().equals(param.getConfirmPassword()), "密码和确认密码不一致");
    Aop.get(CaptchaService.class).check(param.getMobile(), keyOrDefault(param.getKey(), CaptchaService.REGISTER_SMS),
        param.getCode());
    DefUserService defUserService = Aop.get(DefUserService.class);
    if (defUserService.existsMobile(param.getMobile())) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "手机号：" + param.getMobile() + "已经存在");
    }
    int systemType = param.getSystemType() == null ? ImConst.SYSTEM_TYPE_IM : param.getSystemType();
    Db.txResult(() -> {
      Kv user = defUserService.create(systemType, null, param.getMobile(), param.getPassword(), param.getMobile(),
          param.getAvatar(), param.getSex(), DEFAULT_TENANT_ID);
      if (systemType == ImConst.SYSTEM_TYPE_IM) {
        Aop.get(ImUserService.class).register(user.getLong("id"), user.getStr("username"), user.getStr("nick_name"),
            null, param.getAvatar(), param.getSex(), DEFAULT_TENANT_ID);
      }
      return user;
    });
    return param.getMobile();
  }

  /**
   * 通过邮箱验证码重置密码
   */
  public boolean updatePassword(PasswordUpdateReq req) {
    ParameterValidationUtils.notBlank(req.getEmail(), "邮箱不能为空");
    ParameterValidationUtils.notBlank(req.getPassword(), "请输入密码");
    if (req.getConfirmPassword() != null) {
      ParameterValidationUtils.isTrue(req.getPassword().equals(req.getConfirmPassword()), "密码和确认密码不一致");
    }
    DefUserService defUserService = Aop.get(DefUserService.class);
    Kv user = defUserService.getByEmail(ImConst.SYSTEM_TYPE_IM, req.getEmail());
    if (user == null) {
      throw ChatErrors.of(ChatCode.OPERATION_EX, "用户不存在");
    }
    Aop.get(CaptchaService.class).check(req.getEmail(), keyOrDefault(req.getKey(), "PASSWORD_EDIT"), req.getCode());
    defUserService.updatePassword(user.getLong("id"), req.getPassword(), user.getStr("salt"));
    return true;
  }

  public boolean checkEmail(String email) {
    return Aop.get(DefUserService.class).existsEmail(email) || Aop.get(ImUserService.class).existsEmail(email);
  }

  private String keyOrDefault(String key, String defaultValue) {
    return key == null || key.isBlank() ? defaultValue : key;
  }
}
