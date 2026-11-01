package nexus.io.tio.chat.model.im.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlackEditReq {
  private Long id;
  private Integer type;
  private String target;
  private String deadline;
}
