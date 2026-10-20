package nexus.io.tio.chat.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录会话，保存在 Redis 中
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginSession {
  private String token;
  /** im_user.id */
  private Long uid;
  /** def_user.id */
  private Long userId;
  private Long tenantId;
  private Integer systemType;
  private String deviceType;
  private String clientId;
  private Long loginTime;
}
