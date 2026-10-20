package nexus.io.tio.chat.model.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {
  private Long uid;
  private String uuid;
  private String token;
  private String refreshToken;
  private String client;
  private Long expire;
  private Long expiration;
}
