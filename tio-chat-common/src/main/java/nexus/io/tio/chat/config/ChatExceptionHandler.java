package nexus.io.tio.chat.config;

import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private static final Pattern REQUIRED_COLUMN = Pattern.compile("Field '(\\w+)' doesn't have a default value|Column '(\\w+)' cannot be null");
  private static final Pattern BAD_COLUMN_VALUE = Pattern.compile("(?:Data truncated for column|Data too long for column|Incorrect \\w+ value: .* for column) '(\\w+)'");

  @Override
  public Object handler(HttpRequest request, Throwable e) {
    String path = request.getRequestURI();
    if (e instanceof ParameterValidationException || e.getClass().getName().endsWith("JSONException")) {
      return ChatResp.toR(RespBodyVo.fail(ChatCode.VALID_EX, e instanceof ParameterValidationException ? e.getMessage() : "请求参数格式错误"), path);
    }
    if (e instanceof BusinessException) {
      // 客户端约定 HTTP 状态恒为 200，业务码放在响应体 code 中
      BusinessException business = (BusinessException) e;
      return ChatResp.toR(RespBodyVo.fail(ChatErrors.chatCode(business), business.getMessage()), path);
    }
    if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
      return ChatResp.toR(RespBodyVo.fail(ChatCode.OPERATION_EX, "数据已存在"), path);
    }
    String invalid = invalidDataMessage(e);
    if (invalid != null) {
      log.info("invalid data: {} {}", path, e.getMessage());
      return ChatResp.toR(RespBodyVo.fail(ChatCode.VALID_EX, invalid), path);
    }
    log.error("request failed: {}", path, e);
    return ChatResp.toR(RespBodyVo.fail(ChatCode.SERVER_ERROR, "系统繁忙，请稍后再试"), path);
  }

  /** 请求内容不符合表结构或者参数格式不对是调用方的问题，告诉调用方是哪个字段，而不是服务器错误 */
  private String invalidDataMessage(Throwable e) {
    if (e instanceof NumberFormatException || e instanceof DateTimeParseException) {
      return "请求参数格式错误";
    }
    if (e.getMessage() == null) {
      return null;
    }
    Matcher required = REQUIRED_COLUMN.matcher(e.getMessage());
    if (required.find()) {
      return (required.group(1) != null ? required.group(1) : required.group(2)) + "不能为空";
    }
    Matcher badValue = BAD_COLUMN_VALUE.matcher(e.getMessage());
    return badValue.find() ? badValue.group(1) + "的值不正确" : null;
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
