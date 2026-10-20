package nexus.io.tio.chat.service.im;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.consts.ImConst;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;

/**
 * 用户摘要信息，会话、消息、好友列表都依赖它
 */
public class UserSummaryService {

  public static final int ITEM_TYPE_BADGE = 2;

  public UserSummary get(long uid) {
    return getBatch(List.of(uid)).get(uid);
  }

  public Map<Long, UserSummary> getBatch(Collection<Long> uids) {
    Map<Long, UserSummary> result = new LinkedHashMap<>();
    List<Long> ids = new ArrayList<>(new LinkedHashSet<>(uids));
    ids.removeIf(Objects::isNull);
    if (ids.isEmpty()) {
      return result;
    }
    String in = Values.placeholders(ids.size());
    List<Kv> users = Db.findMaps("select * from im_user where is_del=0 and id in (" + in + ")", ids.toArray());
    String badgeSql = """
        select b.uid, b.item_id from im_user_backpack b
        join im_item_config c on c.id=b.item_id and c.type=?
        where b.status=0 and b.is_del=0 and b.uid in (%s)
        """.formatted(in);
    Object[] params = new Object[ids.size() + 1];
    params[0] = ITEM_TYPE_BADGE;
    for (int i = 0; i < ids.size(); i++) {
      params[i + 1] = ids.get(i);
    }
    Map<Long, List<Long>> badges = new LinkedHashMap<>();
    for (Kv row : Db.findMaps(badgeSql, params)) {
      badges.computeIfAbsent(row.getLong("uid"), k -> new ArrayList<>()).add(row.getLong("item_id"));
    }
    for (Kv user : users) {
      UserSummary summary = toSummary(user, badges.getOrDefault(user.getLong("id"), new ArrayList<>()));
      result.put(summary.getUid(), summary);
    }
    return result;
  }

  private UserSummary toSummary(Kv user, List<Long> itemIds) {
    UserSummary s = new UserSummary();
    s.setUid(user.getLong("id"));
    s.setNeedRefresh(true);
    s.setName(user.getStr("name"));
    s.setAvatar(user.getStr("avatar"));
    s.setAccount(user.getStr("account"));
    s.setUserStateId(Values.toLong(user.get("user_state_id")));
    s.setLocPlace(locPlace(user.getStr("ip_info")));
    s.setWearingItemId(Values.toLong(user.get("item_id")));
    s.setItemIds(itemIds);
    s.setUserType(Values.toInt(user.get("user_type")));
    s.setEmail(user.getStr("email"));
    s.setOpenId(user.getStr("open_id"));
    s.setSex(Values.toInt(user.get("sex")));
    s.setResume(user.getStr("resume"));
    s.setLastOptTime(Values.time(user.get("last_opt_time")));
    s.setGiteeId(user.getStr("gitee_id"));
    s.setGithubId(user.getStr("github_id"));
    s.setGitcodeId(user.getStr("gitcode_id"));
    s.setLinkedGitee(notBlank(s.getGiteeId()));
    s.setLinkedGithub(notBlank(s.getGithubId()));
    s.setLinkedGitcode(notBlank(s.getGitcodeId()));
    if (Objects.equals(s.getUserType(), ImConst.USER_TYPE_BOT)) {
      s.setWearingItemId(null);
      s.setItemIds(Collections.emptyList());
      s.setEmail(null);
      s.setResume(null);
    }
    return s;
  }

  @SuppressWarnings("unchecked")
  public static String locPlace(String ipInfo) {
    if (ipInfo == null || ipInfo.isBlank()) {
      return null;
    }
    try {
      Map<String, Object> map = JsonUtils.parseToMap(ipInfo, String.class, Object.class);
      Object detail = map.get("updateIpDetail");
      if (detail instanceof Map) {
        Object city = ((Map<String, Object>) detail).get("city");
        return city == null ? null : city.toString();
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private boolean notBlank(String value) {
    return value != null && !value.isBlank();
  }
}
