package nexus.io.tio.chat.model.im.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BadgeResp {
  private Long id;
  private String img;
  private String describe;
  private Integer obtain;
  private Integer wearing;
}
