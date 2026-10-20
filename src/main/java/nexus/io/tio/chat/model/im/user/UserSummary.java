package nexus.io.tio.chat.model.im.user;

import java.time.LocalDateTime;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
  private Long uid;
  private Boolean needRefresh;
  private String name;
  private Integer sex;
  private String account;
  private String avatar;
  private String locPlace;
  private String openId;
  private String email;
  private String resume;
  private Long userStateId;
  private Long wearingItemId;
  private List<Long> itemIds;
  private Integer userType;
  private LocalDateTime lastOptTime;
  private String giteeId;
  private String githubId;
  private String gitcodeId;
  private Boolean linkedGitee;
  private Boolean linkedGithub;
  private Boolean linkedGitcode;
}
