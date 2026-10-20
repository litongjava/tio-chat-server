package nexus.io.tio.chat.service.im;

import java.time.LocalDateTime;
import java.util.List;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

public class ContactService {

  /**
   * 创建会话，已存在时恢复显示
   */
  public void createContact(long uid, long roomId, long tenantId) {
    String sql = """
        insert into im_contact (id, room_id, uid, is_del, tenant_id, create_by)
        values (?, ?, ?, 0, ?, ?)
        on duplicate key update is_del = 0, hide = 0
        """;
    Db.update(sql, SnowflakeIdUtils.id(), roomId, uid, tenantId, uid);
  }

  /**
   * 新消息到达时刷新或创建会话
   */
  public void refreshOrCreateActiveTime(long roomId, List<Long> uids, long msgId, LocalDateTime activeTime,
      long tenantId) {
    String sql = """
        insert into im_contact (id, room_id, uid, last_msg_id, active_time, is_del, tenant_id)
        values (?, ?, ?, ?, ?, 0, ?)
        on duplicate key update is_del = 0, hide = 0,
          last_msg_id = values(last_msg_id), active_time = values(active_time)
        """;
    for (Long uid : uids) {
      Db.update(sql, SnowflakeIdUtils.id(), roomId, uid, msgId, activeTime, tenantId);
    }
  }
}
