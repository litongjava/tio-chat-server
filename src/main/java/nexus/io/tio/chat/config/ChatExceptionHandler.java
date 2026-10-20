package nexus.io.tio.chat.config;

import lombok.extern.slf4j.Slf4j;
import nexus.io.model.body.RespBodyVo;
import nexus.io.model.exception.BusinessException;
import nexus.io.model.exception.ParameterValidationException;
import nexus.io.tio.boot.exception.TioBootExceptionHandler;
import nexus.io.tio.chat.consts.ChatCode;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.core.ChannelContext;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.websocket.common.WebSocketRequest;

@Slf4j
public class ChatExceptionHandler implements TioBootExceptionHandler {

  @Override
  public Object handler(HttpRequest request, Throwable e) {
    String path = request.getRequestURI();
    if (e instanceof ParameterValidationException) {
      return ChatResp.toR(RespBodyVo.fail(ChatCode.VALID_EX, e.getMessage()), path);
    }
    if (e instanceof BusinessException) {
      // 客户端约定 HTTP 状态恒为 200，业务码放在响应体 code 中
      BusinessException business = (BusinessException) e;
      return ChatResp.toR(RespBodyVo.fail(ChatErrors.chatCode(business), business.getMessage()), path);
    }
    log.error("request failed: {}", path, e);
    return ChatResp.toR(RespBodyVo.fail(ChatCode.SERVER_ERROR, "系统繁忙，请稍后再试"), path);
  }

  @Override
  public Object wsTextHandler(WebSocketRequest webSokcetRequest, String text, ChannelContext channelContext,
      HttpRequest httpRequest, Throwable e) {
    log.error("websocket text failed: {}", text, e);
    return null;
  }

  @Override
  public Object wsBytesHandler(WebSocketRequest webSokcetRequest, byte[] bytes, ChannelContext channelContext,
      HttpRequest httpRequest, Throwable e) {
    log.error("websocket bytes failed", e);
    return null;
  }
}
