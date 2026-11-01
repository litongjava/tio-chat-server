package nexus.io.tio.chat.model.im.user;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlackPageResp {
  private Long id;
  private Integer type;
  private String target;
  private LocalDateTime deadline;
  private LocalDateTime createTime;
  private String userName;
}
