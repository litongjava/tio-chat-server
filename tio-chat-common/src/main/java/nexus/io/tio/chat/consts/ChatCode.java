package nexus.io.tio.chat.consts;

/**
 * 与 客户端约定的响应码
 */
public interface ChatCode {
  int SUCCESS = 200;
  int FAIL = -1;
  int VALID_EX = -9;
  int OPERATION_EX = -10;
  int UNAUTHORIZED = 401;
  int FORBIDDEN = 403;
  int NOT_FOUND = 404;
  int TOKEN_EXPIRED = 406;
  int SERVER_ERROR = 500;
  int JWT_NOT_LOGIN = 40009;
  int JWT_USER_INVALID = 40006;
}
