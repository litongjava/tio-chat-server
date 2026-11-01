package nexus.io.tio.chat.ws.call;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.chat.ws.WsMessageDispatcher;

/**
 * 注册音视频相关的上行消息处理：
 * 4 通话心跳、5 呼叫、6 应答、7/8 媒体控制、9 全体静音、10 屏幕共享、11 关闭房间、12 踢人、13 网络质量、14 WebRTC 信令
 */
public class CallProcessors {

  public static void register() {
    WsMessageDispatcher.register(4, (ctx, s, d) -> Aop.get(CallService.class).refreshActivity(Values.toLong(d.get("roomId"))));
    WsMessageDispatcher.register(5, (ctx, s, d) -> Aop.get(CallService.class).request(s.getUid(), Values.toLong(d.get("roomId")),
        Values.toLong(d.get("targetUid")), Values.isTrue(d.get("isVideo")) || Values.isTrue(d.get("video"))));
    WsMessageDispatcher.register(6, (ctx, s, d) -> Aop.get(CallService.class).respond(s.getUid(), Values.toLong(d.get("callerUid")),
        Values.toLong(d.get("roomId")), Values.toInt(d.get("accepted"))));
    WsMessageDispatcher.register(7, (ctx, s, d) -> Aop.get(CallService.class).forwardMediaControl(s.getUid(), control(d)));
    WsMessageDispatcher.register(8, (ctx, s, d) -> Aop.get(CallService.class).forwardMediaControl(s.getUid(), control(d)));
    WsMessageDispatcher.register(9, (ctx, s, d) -> Aop.get(CallService.class).muteAll(s.getUid(), Values.toLong(d.get("roomId")),
        Values.isTrue(d.get("muted"))));
    WsMessageDispatcher.register(10, (ctx, s, d) -> Aop.get(CallService.class).screenSharing(s.getUid(), Values.toLong(d.get("roomId")),
        Values.isTrue(d.get("sharing"))));
    WsMessageDispatcher.register(11, (ctx, s, d) -> Aop.get(CallService.class).closeRoom(s.getUid(), Values.toLong(d.get("roomId"))));
    WsMessageDispatcher.register(12, (ctx, s, d) -> Aop.get(CallService.class).kick(s.getUid(), Values.toLong(d.get("roomId")),
        Values.toLong(d.get("targetUid")), d.getStr("reason")));
    WsMessageDispatcher.register(13, (ctx, s, d) -> {
      Object quality = d.get("quality");
      double value = quality == null ? 1.0 : Double.parseDouble(quality.toString());
      Aop.get(CallService.class).networkReport(s.getUid(), Values.toLong(d.get("roomId")), value);
    });
    WsMessageDispatcher.register(14, (ctx, s, d) -> Aop.get(CallService.class).forwardSignal(s.getUid(), Values.toLong(d.get("roomId")),
        d.getStr("signal"), d.getStr("signalType")));
  }

  private static Kv control(Kv data) {
    return Kv.by("roomId", Values.toLong(data.get("roomId"))).set("audioMuted", Values.isTrue(data.get("audioMuted")))
        .set("videoMuted", Values.isTrue(data.get("videoMuted")));
  }
}
