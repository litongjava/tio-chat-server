package nexus.io.tio.chat.model.im.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlackAddReq {
  private Integer type;
  private String target;
  private Long deadline;
}
