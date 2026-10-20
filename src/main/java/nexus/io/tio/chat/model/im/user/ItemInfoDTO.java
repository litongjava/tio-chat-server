package nexus.io.tio.chat.model.im.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemInfoDTO {
  private Long itemId;
  private Boolean needRefresh;
  private String img;
  private String describe;
}
