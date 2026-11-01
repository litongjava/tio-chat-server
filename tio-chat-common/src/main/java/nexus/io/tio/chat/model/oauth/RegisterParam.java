package nexus.io.tio.chat.model.oauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterParam {
  private String email;
  private String mobile;
  private String key;
  private String code;
  private String password;
  private String confirmPassword;
  private String nickName;
  private String avatar;
  private Integer systemType;
  private Integer sex;
}
