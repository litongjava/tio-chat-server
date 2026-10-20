package nexus.io.tio.chat.service.im;

import java.util.Objects;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 用户背包物品发放，按幂等键去重
 */
public class ItemService {
  public static final long REG_TOP10_BADGE = 3L;
  public static final long REG_TOP100_BADGE = 4L;
  public static final int IDEMPOTENT_UID = 1;

  public void acquire(long uid, long itemId, int idempotentType, String businessId) {
    String idempotent = String.format("%d_%d_%s", itemId, idempotentType, businessId);
    if (Db.queryLong("select id from im_user_backpack where idempotent=?", idempotent) != null) {
      return;
    }
    Integer type = Db.queryInt("select type from im_item_config where id=?", itemId);
    if (type == null) {
      return;
    }
    boolean badge = Objects.equals(type, UserSummaryService.ITEM_TYPE_BADGE);
    if (badge && Aop.get(ImUserInfoService.class).countValidItem(uid, itemId) > 0) {
      return;
    }
    Row row = Row.by("id", SnowflakeIdUtils.id()).set("uid", uid).set("item_id", itemId).set("status", 0)
        .set("idempotent", idempotent).set("create_by", uid).set("tenant_id", 1L);
    Db.save("im_user_backpack", row);
  }

  /**
   * 注册奖励：改名卡，以及前 10、前 100 名注册徽章
   */
  public void registerReward(long uid) {
    acquire(uid, ImUserInfoService.MODIFY_NAME_CARD, IDEMPOTENT_UID, String.valueOf(uid));
    long count = Db.queryLong("select count(*) from im_user where is_del=0");
    if (count <= 10) {
      acquire(uid, REG_TOP10_BADGE, IDEMPOTENT_UID, String.valueOf(uid));
    } else if (count <= 100) {
      acquire(uid, REG_TOP100_BADGE, IDEMPOTENT_UID, String.valueOf(uid));
    }
  }
}
