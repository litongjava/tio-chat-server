# tio-chat-server

基于 [tio-boot](https://github.com/litongjava/tio-boot) 的即时通讯服务端，单个进程提供：

- 账号：密码 / 验证码 / 手机号登录、邮箱与手机号注册、扫码登录、Gitee / GitHub / GitCode 第三方登录、token 刷新与同设备互踢
- IM：好友、好友申请、通知、会话、单聊与群聊消息（文本、图片、文件、语音、视频、表情、位置、合并转发、公告、撤回、表情回应、已读回执）
- WebSocket：在线状态、消息实时推送与 ACK、音视频通话信令、多节点之间通过 Redis 转发
- 朋友圈、好友标签、表情包、隐私设置、敏感词过滤、管理端统计
- 系统：初始化配置、对象存储直传凭证（七牛 / MinIO）、地图代理、翻译（含 SSE 流式）
- 后台管理：租户、用户、员工、组织、岗位、角色与资源授权、应用、字典、参数、地区、日志、站内消息
- AI：模型与密钥管理、对话（同步与 SSE 流式）、写作、思维导图、知识库检索、图片生成

接口路径、请求字段、响应结构（`{code, data, msg, path, version, timestamp, success}`）和 WebSocket 消息格式与原 IM 客户端保持一致，客户端只需把服务地址指向本服务即可使用。

## 运行环境

- JDK 21
- MySQL 8
- Redis 6+

### 用 Docker 启动 MySQL 与 Redis

```shell
docker run -d --name tio-chat-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=robot_123456# -e TZ=Asia/Shanghai \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci
docker run -d --name tio-chat-redis -p 6379:6379 redis:7
```

创建数据库并导入原项目 `docs/install/sql` 目录下的两个 SQL 文件（先导入基础库，再导入 IM 库）：

```shell
docker exec -i tio-chat-mysql mysql -uroot -p'robot_123456#' \
  -e "create database tio_chat default charset utf8mb4 collate utf8mb4_general_ci"
docker exec -i tio-chat-mysql mysql -uroot -p'robot_123456#' tio_chat < base.sql
docker exec -i tio-chat-mysql mysql -uroot -p'robot_123456#' tio_chat < im.sql
```

## 配置

`src/main/resources/app.properties` 为公共配置，`app-dev.properties` 为开发环境配置，也可以用环境变量或 `.env` 覆盖。

- `server.port`：服务端口，默认 18760
- `jdbc.url` / `jdbc.user` / `jdbc.pswd`：MySQL 连接
- `redis.host` / `redis.port` / `redis.password`：Redis 连接
- `server.heartbeat.timeout`：WebSocket 心跳超时（毫秒），默认 30000
- `chat.token.timeout`：登录 token 有效期（秒），默认 30 天
- `chat.default.password`：后台重置密码时使用的默认密码
- `chat.group.account.prefix`：新建群号前缀
- `oauth.{gitee,github,gitcode}.client-id` / `client-secret` / `redirect-uri`：第三方登录
- `mail.host` / `mail.user` / `mail.password` / `mail.from`：邮件验证码
- `APP_TIME_ZONE`：进程时区，需与数据库一致，默认 Asia/Shanghai

七牛、MinIO、地图、翻译等第三方密钥保存在 `base_config` 表中，可通过 `/api/system/anyTenant/config/update` 维护。

## 构建与启动

```shell
mvn clean package -DskipTests -Dgpg.skip -Pproduction
java -jar target/tio-chat-server.jar
```

或使用 Docker：

```shell
docker build -t tio-chat-server .
docker run -d -p 18760:18760 -e JDBC_URL=... -e REDIS_HOST=... tio-chat-server
```

客户端配置：HTTP 地址 `http://<host>:18760/api`，WebSocket 地址 `ws://<host>:18760/api/ws/ws`。

## 接口分组

- `/api/oauth`：登录、注册、验证码、扫码登录、个人信息、字典与参数
- `/api/im`：用户、好友、会话、消息、群聊、通知、朋友圈、标签、统计
- `/api/ws/ws`：WebSocket
- `/api/system`：初始化配置、上传凭证、地图、翻译、模型列表
- `/api/base`：后台管理（通用增删改查 + 扩展接口）
- `/api/ai`：AI 能力

后台管理的通用资源统一提供：`POST {path}`、`PUT {path}`、`DELETE {path}`、`GET {path}/{id}`、`GET {path}/detail`、`POST {path}/query`、`POST {path}/findByIds`、`POST {path}/page`、`POST {path}/copy`，分页参数为 `{model, size, current, sort, order, extra}`，`extra` 支持 `_st`、`_ed`、`_like`、`_in` 等后缀条件。

## 测试

`scripts` 目录下是接口冒烟测试脚本，需要先启动服务、MySQL 与 Redis：

```shell
scripts/smoke-oauth.sh      # 登录、注册、刷新、扫码登录
scripts/smoke-chat.sh       # 消息收发、表情回应、撤回、已读、WebSocket 推送
scripts/smoke-social.sh     # 好友、申请、群聊、公告
scripts/smoke-call.sh       # 音视频通话信令
scripts/smoke-feed.sh       # 朋友圈、标签、表情包、隐私
scripts/smoke-base-crud.sh  # 后台通用增删改查
scripts/smoke-base-ext.sh   # 后台扩展接口
scripts/smoke-ai.sh         # AI 对话（配合 MockLlm.java 模拟模型）
```

浏览器测试控制台：启动后访问 `http://localhost:18760/console/index.html`，可以登录、查看会话、收发消息并观察 WebSocket 推送。浏览器测试过程见 [docs/browser-test.md](docs/browser-test.md)。

## 暂未实现

- 代码生成器、工作流（activiti）模块
- 微信公众号门户接口
- 后台的服务器管理与数据源连接测试

## 目录结构

```
src/main/java/nexus/io/tio/chat
├── config        启动配置、路由注册、异常处理、WebSocket 配置
├── handler       每个接口一个 Handler
├── service       业务逻辑（oauth / im / system / base / ai）
├── model         请求与响应实体
├── ws            WebSocket 处理器、推送、在线状态、通话信令
├── interceptor   Token 校验
└── utils         响应包装、参数校验、数据库工具
```
