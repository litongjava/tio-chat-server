package nexus.io.tio.chat.model.im.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResp {
  private String uid;
  private String name;
  private String avatar;
  private String account;
}
