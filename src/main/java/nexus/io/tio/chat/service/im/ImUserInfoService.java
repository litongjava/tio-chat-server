package nexus.io.tio.chat.service.im;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.model.common.PageBaseResp;
import nexus.io.tio.chat.model.im.user.BadgeResp;
import nexus.io.tio.chat.model.im.user.ItemInfoDTO;
import nexus.io.tio.chat.model.im.user.ModifyInfoReq;
import nexus.io.tio.chat.model.im.user.UserInfoResp;
import nexus.io.tio.chat.model.im.user.UserSearchResp;
import nexus.io.tio.chat.model.im.user.UserSummary;
import nexus.io.tio.chat.service.oauth.CaptchaService;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.Values;

/**
 * IM 用户资料、徽章与搜索
 */
public class ImUserInfoService {
  public static final long MODIFY_NAME_CARD = 1L;

  public UserInfoResp getUserInfo(long uid) {
    UserSummary summary = Aop.get(UserSummaryService.class).get(uid);
    ChatErrors.require(summary != null, "用户不存在");
    Kv user = Aop.get(ImUserService.class).getById(uid);
    UserInfoResp resp = new UserInfoResp();
    resp.setUid(summary.getUid());
    resp.setWearingItemId(summary.getWearingItemId());
    resp.setItemIds(summary.getItemIds());
    resp.setAccount(summary.getAccount());
    resp.setEmail(summary.getEmail());
    resp.setName(summary.getName());
    resp.setAvatar(summary.getAvatar());
    resp.setSex(summary.getSex());
    resp.setResume(summary.getResume());
    resp.setUserStateId(summary.getUserStateId());
    resp.setModifyNameChance(countValidItem(uid, MODIFY_NAME_CARD));
    resp.setAvatarUpdateTime(Values.time(user.get("avatar_update_time")));
    resp.setContext(Values.toBool(user.get("context")));
    resp.setNum(Values.toInt(user.get("num")));
    resp.setLinkedGitee(summary.getLinkedGitee());
    resp.setLinkedGithub(summary.getLinkedGithub());
    resp.setLinkedGitcode(summary.getLinkedGitcode());
    return resp;
  }

  public List<UserSummary> getUserByIds(Collection<Long> uids) {
    return new ArrayList<>(Aop.get(UserSummaryService.class).getBatch(uids).values());
  }

  public void modifyInfo(long uid, ModifyInfoReq req) {
    if (req.getName() != null) {
      ChatErrors.require(!Aop.get(SensitiveWordService.class).hasSensitiveWord(req.getName()), "名字中包含敏感词，请重新输入");
    }
    Kv user = Aop.get(ImUserService.class).getById(uid);
    ChatErrors.require(user != null, "用户不存在");
    boolean avatarChanged = req.getAvatar() != null && !req.getAvatar().isEmpty()
        && !req.getAvatar().equals(user.getStr("avatar"));
    String sql = """
        update im_user set sex=coalesce(?, sex), name=coalesce(?, name), resume=coalesce(?, resume),
          avatar=if(?, ?, avatar), avatar_update_time=if(?, now(3), avatar_update_time), update_by=?
        where id=?
        """;
    Db.update(sql, req.getSex(), req.getName(), req.getResume(), avatarChanged, req.getAvatar(), avatarChanged, uid,
        uid);
  }

  public void modifyAvatar(long uid, String avatar) {
    ChatErrors.require(avatar != null && !avatar.isBlank(), "头像不能为空");
    Db.update("update im_user set avatar=?, avatar_update_time=now(3) where id=?", avatar, uid);
  }

  public List<BadgeResp> badges(long uid) {
    List<Kv> items = Db.findMaps("select * from im_item_config where type=? and is_del=0 order by id",
        UserSummaryService.ITEM_TYPE_BADGE);
    Set<Long> obtained = new HashSet<>(Db.query("select item_id from im_user_backpack where uid=? and status=0 and is_del=0", uid));
    Kv user = Aop.get(ImUserService.class).getById(uid);
    if (user == null) {
      return new ArrayList<>();
    }
    Long wearing = Values.toLong(user.get("item_id"));
    List<BadgeResp> list = new ArrayList<>();
    for (Kv item : items) {
      Long id = item.getLong("id");
      list.add(new BadgeResp(id, item.getStr("img"), item.getStr("describe"), obtained.contains(id) ? 1 : 0,
          Objects.equals(id, wearing) ? 1 : 0));
    }
    list.sort(Comparator.comparing(BadgeResp::getWearing, Comparator.reverseOrder())
        .thenComparing(BadgeResp::getObtain, Comparator.reverseOrder()));
    return list;
  }

  public void wearingBadge(long uid, Long badgeId) {
    ChatErrors.require(badgeId != null, "badgeId不能为空");
    ChatErrors.require(countValidItem(uid, badgeId) > 0, "您没有这个徽章哦，快去达成条件获取吧");
    Integer type = Db.queryInt("select type from im_item_config where id=?", badgeId);
    ChatErrors.require(Objects.equals(type, UserSummaryService.ITEM_TYPE_BADGE), "该徽章不可佩戴");
    Db.update("update im_user set item_id=? where id=?", badgeId, uid);
  }

  /**
   * reqList 为空时返回全部物品；lastModifyTime 不早于更新时间的物品无需刷新
   */
  public List<ItemInfoDTO> getItemInfo(List<Kv> reqList) {
    List<ItemInfoDTO> result = new ArrayList<>();
    if (reqList == null || reqList.isEmpty()) {
      for (Kv item : Db.findMaps("select * from im_item_config where is_del=0 order by id")) {
        result.add(new ItemInfoDTO(item.getLong("id"), true, item.getStr("img"), item.getStr("describe")));
      }
      return result;
    }
    for (Kv req : reqList) {
      Long itemId = Values.toLong(req.get("itemId"));
      Kv item = Db.findFirstMap("select * from im_item_config where id=?", itemId);
      if (item == null) {
        continue;
      }
      Long lastModifyTime = Values.toLong(req.get("lastModifyTime"));
      LocalDateTime updateTime = Values.time(item.get("update_time"));
      long updateMillis = updateTime == null ? 0
          : updateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
      if (lastModifyTime != null && lastModifyTime >= updateMillis) {
        result.add(new ItemInfoDTO(itemId, false, null, null));
      } else {
        result.add(new ItemInfoDTO(itemId, true, item.getStr("img"), item.getStr("describe")));
      }
    }
    return result;
  }

  public boolean bindEmail(long uid, String email, String key, String code) {
    Aop.get(CaptchaService.class).check(email, key == null ? CaptchaService.EMAIL_EDIT : key, code);
    Long other = Db.queryLong("select id from im_user where email=? and id<>? and is_del=0 limit 1", email, uid);
    ChatErrors.require(other == null, "该邮箱已被其他账号绑定");
    return Db.update("update im_user set email=? where id=?", email, uid) > 0;
  }

  public boolean changeUserState(long uid, Long stateId) {
    return Db.update("update im_user set user_state_id=? where id=?", stateId, uid) > 0;
  }

  public boolean refreshIpInfo(long uid, String ipInfoJson) {
    return Db.update("update im_user set ip_info=? where id=?", ipInfoJson, uid) > 0;
  }

  public PageBaseResp<UserSearchResp> search(Long id, String keyword, int pageNo, int pageSize) {
    StringBuilder where = new StringBuilder(" from im_user where is_del=0");
    List<Object> params = new ArrayList<>();
    if (id != null) {
      where.append(" and id=?");
      params.add(id);
    } else if (keyword != null && !keyword.isBlank()) {
      where.append(" and name like ?");
      params.add("%" + keyword.trim() + "%");
    }
    long total = Db.queryLong("select count(*)" + where, params.toArray());
    params.add((pageNo - 1) * pageSize);
    params.add(pageSize);
    List<UserSearchResp> list = new ArrayList<>();
    String sql = "select id, name, avatar, account" + where + " order by last_opt_time desc limit ?, ?";
    for (Kv user : Db.findMaps(sql, params.toArray())) {
      list.add(new UserSearchResp(String.valueOf(user.getLong("id")), user.getStr("name"), user.getStr("avatar"),
          user.getStr("account")));
    }
    return PageBaseResp.init(pageNo, pageSize, total, list);
  }

  public int countValidItem(long uid, long itemId) {
    Long count = Db.queryLong("select count(*) from im_user_backpack where uid=? and item_id=? and status=0 and is_del=0",
        uid, itemId);
    return count == null ? 0 : count.intValue();
  }
}
