package nexus.io.tio.chat.model.im.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModifyInfoReq {
  private Integer sex;
  private String phone;
  private String avatar;
  private String name;
  private String resume;
}
