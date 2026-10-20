package nexus.io.tio.chat.utils;

import nexus.io.model.exception.BusinessException;
import nexus.io.tio.chat.consts.ChatCode;

/**
 * 业务异常工厂：客户端约定的业务码放在 BusinessException.code 中，status 取合法的 HTTP 状态
 */
public class ChatErrors {

  public static BusinessException of(int chatCode, String message) {
    return new BusinessException(httpStatus(chatCode), String.valueOf(chatCode), message);
  }

  public static BusinessException of(String message) {
    return of(ChatCode.OPERATION_EX, message);
  }

  public static void require(boolean condition, String message) {
    if (!condition) {
      throw of(message);
    }
  }

  public static int chatCode(BusinessException e) {
    String code = e.getCode();
    if (code != null) {
      try {
        return Integer.parseInt(code);
      } catch (NumberFormatException ignored) {
      }
    }
    return e.getStatus() == 400 ? ChatCode.OPERATION_EX : e.getStatus();
  }

  private static int httpStatus(int chatCode) {
    if (chatCode >= 400 && chatCode <= 599) {
      return chatCode;
    }
    return 400;
  }
}
