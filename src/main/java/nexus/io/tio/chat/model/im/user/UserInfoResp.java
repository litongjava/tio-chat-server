package nexus.io.tio.chat.model.im.user;

import java.time.LocalDateTime;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResp {
  private Long uid;
  private Long wearingItemId;
  private List<Long> itemIds;
  private String account;
  private String email;
  private String name;
  private String avatar;
  private Integer sex;
  private String resume;
  private Long userStateId;
  private Integer modifyNameChance;
  private LocalDateTime avatarUpdateTime;
  private Boolean context;
  private Integer num;
  private Boolean linkedGitee;
  private Boolean linkedGithub;
  private Boolean linkedGitcode;
}
