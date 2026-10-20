package nexus.io.tio.chat.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import nexus.io.http.common.HttpResponsePacket;

/**
 * 以 LF 分行的 SSE 事件：客户端按 "\n\n" 切分事件，CRLF 结尾的事件它无法识别
 */
public class LfSsePacket extends HttpResponsePacket {
  private static final long serialVersionUID = 1L;
  private final byte[] bytes;

  public LfSsePacket(String event, String data) {
    StringBuilder text = new StringBuilder();
    if (event != null) {
      text.append("event:").append(event).append('\n');
    }
    // 多行数据按 SSE 规范拆成多个 data 行
    for (String line : data.split("\n", -1)) {
      text.append("data:").append(line).append('\n');
    }
    text.append('\n');
    this.bytes = text.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public ByteBuffer toByteBuffer(ByteOrder byteOrder) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.order(byteOrder);
    return buffer;
  }
}
