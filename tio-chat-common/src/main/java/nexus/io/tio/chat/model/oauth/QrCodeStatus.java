package nexus.io.tio.chat.model.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeStatus {
  private String status;
  private String deviceHash;
  private Long userId;
  private Long uid;
}
