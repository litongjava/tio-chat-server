package nexus.io.tio.chat.model.ws;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推送给客户端的 WebSocket 消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WsBaseResp {
  private String type;
  private Object data;

  public static WsBaseResp of(String type, Object data) {
    return new WsBaseResp(type, data);
  }
}
