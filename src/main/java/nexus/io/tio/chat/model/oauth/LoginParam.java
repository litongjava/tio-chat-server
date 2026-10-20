package nexus.io.tio.chat.model.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginParam {
  private String key;
  private String code;
  private String account;
  private String password;
  private String mobile;
  private String clientId;
  private Integer systemType;
  private String deviceType;
  private String grantType;
  private String refreshToken;
  private String redirectUri;
}
