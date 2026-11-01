package nexus.io.tio.chat.model.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BindEmailReq {
  private String email;
  private String code;
  private String clientId;
  private String uuid;
  private String operationType;
  private String templateCode;
}
