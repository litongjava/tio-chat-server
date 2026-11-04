package nexus.io.tio.chat.service.base.msg;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 发送结果。title、content 是渲染后的内容，为空时保留消息原来的内容；raw 记入接口日志
 */
@Getter
@AllArgsConstructor
public class MsgResult {
  private final boolean success;
  private final String title;
  private final String content;
  private final String raw;

  public static MsgResult of(boolean success, String raw) {
    return new MsgResult(success, null, null, raw);
  }
}
