package nexus.io.tio.chat.model.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordUpdateReq {
  private Long id;
  private String email;
  private String key;
  private String code;
  private String oldPassword;
  private String password;
  private String confirmPassword;
}
