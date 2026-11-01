package nexus.io.tio.chat.gateway.model;

/**
 * 登录会话中网关需要的字段
 */
public class GatewaySession {
  private Long uid;
  private Long userId;
  private Long tenantId;
  private Integer systemType;

  public Long getUid() {
    return uid;
  }

  public void setUid(Long uid) {
    this.uid = uid;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Integer getSystemType() {
    return systemType;
  }

  public void setSystemType(Integer systemType) {
    this.systemType = systemType;
  }
}
