package nexus.io.tio.chat.model.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanResp {
  private Long expireTime;
  private String ip;
  private String deviceType;
  private String locPlace;
}
