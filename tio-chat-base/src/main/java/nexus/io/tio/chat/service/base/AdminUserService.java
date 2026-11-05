package nexus.io.tio.chat.service.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.config.route.BaseCrudRegistry;
import nexus.io.tio.chat.context.LoginContext;
import nexus.io.tio.chat.model.common.IPageResp;
import nexus.io.tio.chat.service.base.crud.CrudDef;
import nexus.io.tio.chat.service.base.crud.CrudService;
import nexus.io.tio.chat.service.common.StorageService;
import nexus.io.tio.chat.service.oauth.DefUserService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * 管理后台（HuLa-Admin）的用户接口。字段 userName 对应 def_user.username，其余字段和后台通用接口相同
 */
public class AdminUserService {
  private static final long DEFAULT_PAGE_SIZE = 10;
  private static final int MAX_AVATAR_BYTES = 5 * 1024 * 1024;
  private static final Set<String> AVATAR_TYPES = Set.of("png", "jpg", "jpeg", "gif", "webp");
  /** 当前用户可以直接修改的字段，手机号和邮箱要通过验证码修改 */
  private static final List<String> SELF_FIELDS = List.of("nickName", "avatar", "sex", "workDescribe");

  private CrudDef def() {
    return BaseCrudRegistry.find("/defUser");
  }

  private CrudService crud() {
    return Aop.get(CrudService.class);
  }

  public IPageResp<Kv> page(String pageNum, String pageSize, String keyword, String state) {
    Map<String, Object> model = new HashMap<>();
    if (state != null && !state.isEmpty()) {
      model.put("state", Boolean.parseBoolean(state) || "1".equals(state));
    }
    Map<String, Object> extra = new HashMap<>();
    if (keyword != null && !keyword.isEmpty()) {
      extra.put("nickName_like", keyword);
    }
    Kv params = Kv.by("model", model).set("extra", extra).set("current", pageNum == null ? 1L : Long.parseLong(pageNum))
        .set("size", pageSize == null ? DEFAULT_PAGE_SIZE : Long.parseLong(pageSize));
    IPageResp<Kv> page = crud().page(def(), params);
    for (Kv user : page.getRecords()) {
      toApi(user);
    }
    return page;
  }

  public Kv detail(Long id) {
    Kv user = crud().detail(def(), id);
    return user == null ? null : toApi(user);
  }

  public Kv add(Map<String, Object> body) {
    return toApi(crud().save(def(), fromApi(body)));
  }

  public Kv edit(Map<String, Object> body) {
    return toApi(crud().update(def(), fromApi(body)));
  }

  /** 参数是 id 或 ids */
  public boolean delete(Map<String, Object> body) {
    List<Long> ids = new ArrayList<>();
    if (body.get("ids") instanceof Collection<?> list) {
      ids.addAll(Values.toLongList(list));
    }
    Long id = Values.toLong(body.get("id"));
    if (id != null) {
      ids.add(id);
    }
    ChatErrors.require(!ids.isEmpty(), "请选择要删除的用户");
    return crud().delete(def(), ids);
  }

  public boolean resetPassword(Map<String, Object> body) {
    Long id = Values.toLong(body.get("id"));
    Object password = body.get("password");
    ChatErrors.require(id != null, "id不能为空");
    ChatErrors.require(password != null && !password.toString().isBlank(), "请输入密码");
    DefUserService users = Aop.get(DefUserService.class);
    Kv user = users.getById(id);
    ChatErrors.require(user != null, "用户不存在");
    users.updatePassword(id, password.toString(), user.getStr("salt"));
    return true;
  }

  public boolean updateState(Map<String, Object> body) {
    Long id = Values.toLong(body.get("id"));
    ChatErrors.require(id != null, "id不能为空");
    ChatErrors.require(body.get("state") != null, "状态不能为空");
    crud().update(def(), Kv.by("id", id).set("state", body.get("state")));
    return true;
  }

  public Kv current() {
    Kv user = crud().get(def(), LoginContext.userId());
    ChatErrors.require(user != null, "用户不存在");
    return toApi(user).set("uid", String.valueOf(LoginContext.uid()));
  }

  public Kv updateCurrent(Map<String, Object> body) {
    Kv update = Kv.by("id", LoginContext.userId());
    for (String field : SELF_FIELDS) {
      if (body.containsKey(field)) {
        update.set(field, body.get(field));
      }
    }
    return toApi(crud().update(def(), update));
  }

  /** @return url：头像的下载地址 */
  public Kv uploadAvatar(String fileName, byte[] data) {
    ChatErrors.require(data != null && data.length > 0, "请选择头像文件");
    ChatErrors.require(data.length <= MAX_AVATAR_BYTES, "头像不能超过 5MB");
    String name = fileName == null ? "" : fileName;
    String type = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    ChatErrors.require(AVATAR_TYPES.contains(type), "头像只支持 png、jpg、jpeg、gif、webp");
    return Kv.by("url", Aop.get(StorageService.class).upload("avatar", LoginContext.userId() + "." + type, data));
  }

  private Kv toApi(Kv user) {
    return user.set("userName", user.get("username"));
  }

  private Map<String, Object> fromApi(Map<String, Object> body) {
    Map<String, Object> result = new HashMap<>(body);
    if (result.containsKey("userName")) {
      result.put("username", result.remove("userName"));
    }
    return result;
  }
}
