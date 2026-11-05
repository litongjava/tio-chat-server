# tio-chat-server

基于 [tio-boot](https://github.com/litongjava/tio-boot) 与 [tio-boot-cloud](https://github.com/litongjava/tio-boot-cloud) 的即时通讯服务端。按原系统的微服务划分为 oauth、base、system、im、ai、ws 六个模块和一个网关，支持两种运行方式：

- **所有模块一个服务**：`tio-chat-all` 一个进程提供全部接口，部署简单，不需要 Nacos。
- **微服务**：每个模块单独运行并注册到 Nacos，客户端通过 `tio-chat-gateway` 访问，各服务可以独立扩容和发布。

两种方式使用同一份代码、提供相同的接口，全部接口冒烟测试和客户端端到端测试在两种方式下都通过（`scripts/run-both-modes.sh`）。提供的功能：

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

`tio-chat-common/src/main/resources/app.properties` 为所有服务共用的配置，`app-dev.properties` 为开发环境配置，也可以用命令行参数、环境变量或 `.env` 覆盖。网关的配置在 `tio-chat-gateway/src/main/resources/app.properties`。

- `server.port`：服务端口。所有模块一个服务时默认 18760；单独运行时 oauth 18773、base 18764、system 19877、generator 18780、im 18778、ai 18877、ws 19778，网关 18760
- `nacos.server-addr` / `nacos.namespace` / `nacos.username` / `nacos.password`：Nacos，微服务方式使用
- `nacos.discovery.enabled`：是否注册到 Nacos，单独运行的服务默认 `true`，所有模块一个服务时默认 `false`
- `nacos.config.data-ids`：从 Nacos 加载的配置，例如 `common.yml`，数据库、Redis 等公共配置可以放在这里
- `chat.internal.secret`：服务间内部调用的密钥，生产环境请修改
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

### 文件存储（MinIO）

图片、文件、语音、视频由客户端直传对象存储：客户端调用 `/api/system/anyTenant/storage/provider` 得到存储引擎，再调用 `/api/system/anyTenant/ossToken?scene=chat&fileName=...` 取得预签名 PUT 地址 `uploadUrl` 和下载地址 `downloadUrl`，把文件 PUT 到 `uploadUrl` 后，消息里只保存 `downloadUrl`。服务端不经手文件内容。

本地用 MinIO 时，先启动一个容器并建一个允许匿名下载的桶（官方镜像已不在 Docker Hub 发布，这里使用社区维护的同名构建，命令行参数与官方一致）：

```shell
docker run -d --name tio-chat-minio -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin -e MINIO_ROOT_PASSWORD=minioadmin123 \
  -v tio-chat-minio-data:/data pgsty/minio:latest server /data --console-address :9001
docker exec tio-chat-minio sh -c 'mc alias set local http://127.0.0.1:9000 minioadmin minioadmin123 \
  && mc mb -p local/chat && mc anonymous set download local/chat'
```

然后把 `base_config` 中的存储配置指向它：

```sql
update base_config set config_value='http://127.0.0.1:9000' where config_key='minioEndpoint';
update base_config set config_value='minioadmin' where config_key='minioAccessKey';
update base_config set config_value='minioadmin123' where config_key='minioSecretKey';
update base_config set config_value='chat' where config_key='minioBucket';
update base_config set config_value='http://127.0.0.1:9000/chat' where config_key='minioUrlPrefix';
update base_config set config_value='minio' where config_key='storageDefault';
```

用 curl 验证预签名地址：

```shell
R=$(curl -s "localhost:18760/api/system/anyTenant/ossToken?scene=chat&fileName=hello.txt")
U=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['uploadUrl'])")
D=$(echo "$R" | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['downloadUrl'])")
echo hello > hello.txt
curl -X PUT -H "Content-Type: text/plain" --data-binary @hello.txt "$U"
curl "$D"
```

注意：

- 预签名签的是 `host`，`minioEndpoint` 必须是客户端实际访问的地址（容器内外、域名与 IP 不能混用）。
- 桌面客户端只允许向 `https` 地址上传，生产环境需要给 MinIO 配置 HTTPS（或放在 HTTPS 反向代理之后），`minioUrlPrefix` 填对外的下载地址；浏览器联调可以直接用 `http`。
- MinIO 默认允许任意来源的跨域请求，浏览器直传无需额外配置；如果放在反向代理后面，代理需要放行 `PUT` 与 `OPTIONS`。

### AI 模型（DeepSeek 等 OpenAI 兼容平台）

客户端的 AI 助手通过 `/api/ai` 调用 OpenAI 兼容的对话接口，模型和密钥保存在数据库中，不写在配置文件里：

- `ai_api_key`：平台名（如 `DeepSeek`）、密钥、可选的自定义地址 `url`（为空时使用平台默认地址，DeepSeek 为 `https://api.deepseek.com/v1`）。`publicStatus=true` 的密钥对所有用户可选，未指定时为创建者私有。
- `ai_model`：关联密钥 `keyId`，`model` 填平台的模型标识（以平台 `/models` 接口返回的为准），`type=1` 为对话模型，`publicStatus=0` 为公开模型、`1` 为创建者私有，`supportsReasoning=1` 时客户端显示“深度思考”开关。
- `ai_chat_role`：客户端新建对话前必须有一个可用角色（`publicStatus=true` 为公开），角色的 `modelId` 是新对话的默认模型。

`scripts/setup-deepseek.sh` 用一个账号通过上述接口登记 DeepSeek：一个公开密钥、平台返回的每个模型各一个公开对话模型，以及一个公开角色。密钥只从环境变量读取：

```shell
export DEEPSEEK_API_KEY=<你的密钥>          # 不要写进仓库
sg docker -c "scripts/setup-deepseek.sh"   # 可选 DEEPSEEK_BASE_URL、OWNER（默认 ai-admin@example.com）
```

脚本每次运行都会新建记录，重新登记前先在客户端的“模型管理”“API 密钥”中删除旧记录。其他 OpenAI 兼容平台同理：在客户端的“API 密钥”里添加平台密钥，再在“模型管理”里添加模型。

说明：

- 流式回答（`/api/ai/chat/message/send-stream`）是 SSE，每个事件的 `data` 是一条 `{code, data: {send, receive}}` 响应，事件以 `\n\n` 结尾。
- 客户端打开“深度思考”时（`reasoningEnabled=true`），DeepSeek 请求带 `thinking: {type: enabled}`，思考内容放在 `receive.reasoningContent` 中流式返回并保存；关闭时带 `thinking: {type: disabled}`，不返回思考内容。
- 点击“停止思考”时客户端断开连接，服务端随即取消上游请求，已显示的内容保存为回答。
- 会话的 `tokenUsage` 按平台返回的用量累计（没有用量时按字数估算）；客户端在累计值达到模型的 `maxTokens` 后不再发送。
- API 密钥只返回给创建者，其他接口（包括后台列表）返回掩码。
- 后台管理接口只允许管理员（拥有 `TENANT_ADMIN` 或 `SUPER_ADMIN` 角色）调用：`/api/ai/**/admin/*`、`chat/message/page`、对话与消息的 `delete-by-admin`，以及绘图、视频、音乐、写作、导图的管理接口 `page` / `update` / `delete`。`chat/conversation/page` 仍对所有登录用户开放（客户端的 AI 助手用它列出自己的对话，只返回本人的对话）。其他用户调用上述接口收到与原网关相同的 `code=406`、`msg=无此权限：<路径>`。角色按登录用户的 uid 在 `base_employee_role_rel` 中查找，`setup-deepseek.sh` 会给 `OWNER` 账号授予 `TENANT_ADMIN`；手工授予：

```sql
insert into base_employee_role_rel(id, role_id, employee_id, create_by, create_time, is_del, tenant_id)
select <新编号>, id, <用户 uid>, 1, now(), 0, 1 from base_role where code='TENANT_ADMIN';
```

## 构建与启动

```shell
mvn clean package -DskipTests -Dgpg.skip
```

### 所有模块一个服务

```shell
java -jar tio-chat-all/target/tio-chat-server.jar
```

或使用 Docker：

```shell
docker build -t tio-chat-server .
docker run -d -p 18760:18760 -e JDBC_URL=... -e REDIS_HOST=... tio-chat-server
```

### 微服务

先按 tio-boot 文档“微服务”一章启动 Nacos 并创建命名空间 `hula-dev`，然后：

```shell
scripts/services.sh start    # 启动 6 个服务和网关，日志在 logs/services
scripts/services.sh status
scripts/services.sh stop
```

也可以分别启动。除 ws 外每个服务可以运行多个实例（换端口即可），网关按服务名负载均衡：

```shell
java -jar tio-chat-oauth/target/tio-chat-oauth.jar
java -jar tio-chat-im/target/tio-chat-im.jar
java -jar tio-chat-im/target/tio-chat-im.jar --server.port=18779
java -jar tio-chat-gateway/target/tio-chat-gateway.jar
```

各服务注册到 Nacos 的服务名与原系统相同：`luohuo-oauth-server`、`luohuo-base-server`、`luohuo-system-server`、`luohuo-generator-server`、`luohuo-im-server`、`luohuo-ai-server`、`luohuo-ws-server`，网关为 `luohuo-gateway-server`。网关路由：

- `/api/oauth/**` → luohuo-oauth-server
- `/api/im/**` → luohuo-im-server
- `/api/base/**` → luohuo-base-server
- `/api/system/**` → luohuo-system-server
- `/api/generator/**` → luohuo-generator-server
- `/api/ai/**` → luohuo-ai-server（含 SSE 流式接口）
- `/api/ws/**`、`/ws/**` → luohuo-ws-server（WebSocket）

网关还会解析 Token，把 `userId`、`uid`、`tenantId`、`systemType` 写入请求头传给后端，并统一处理跨域。

模块之间的调用（如 oauth 注册 IM 用户、ws 发送通话记录消息）通过 `BaseFacade`、`ImFacade` 完成：所有模块一个服务时直接调用本地实现，分开运行时按服务名调用对方的内部接口 `/internal/facade/*`，该接口需要 `chat.internal.secret`，网关不转发。WebSocket 推送与踢下线通过 Redis 频道送达所有 ws 实例。

客户端配置：HTTP 地址 `http://<host>:18760/api`，WebSocket 地址 `ws://<host>:18760/api/ws/ws`，两种运行方式相同。

## 接口分组

- `/api/oauth`：登录、注册、验证码、扫码登录、个人信息、字典与参数
- `/api/im`：用户、好友、会话、消息、群聊、通知、朋友圈、标签、统计
  - 扩展接口：`GET /api/im/chat/msg/search`（单个会话的聊天记录，按 `keyword`、`types`、`startTime`/`endTime` 毫秒、`sort` 筛选，`pageNo`/`pageSize` 分页）与 `GET /api/im/chat/msg/files`（可访问会话中的文件、视频消息，按 `roomId`、`senderUid`、`keyword` 筛选，附发送人昵称与头像）。桌面客户端从本地 SQLite 查询这两类数据，没有本地库的客户端（如浏览器）用它们代替。两者都校验会话成员身份，不返回已删除的消息，关键词不匹配已撤回消息的原文。
- `/api/ws/ws`：WebSocket
- `/api/system`：初始化配置、上传凭证、地图、翻译、模型列表
- `/api/base`：后台管理（通用增删改查 + 扩展接口）
- `/api/ai`：AI 能力

后台管理的通用资源统一提供：`POST {path}`、`PUT {path}`、`DELETE {path}`、`GET {path}/{id}`、`GET {path}/detail`、`POST {path}/query`、`POST {path}/findByIds`、`POST {path}/page`、`POST {path}/copy`，分页参数为 `{model, size, current, sort, order, extra}`，`extra` 支持 `_st`、`_ed`、`_like`、`_in` 等后缀条件。

## 测试

`scripts` 目录下是接口冒烟测试脚本，需要先启动服务（任一种运行方式，地址都是 18760）、MySQL 与 Redis：

```shell
scripts/smoke-oauth.sh      # 登录、注册、刷新、扫码登录
scripts/smoke-chat.sh       # 消息收发、表情回应、撤回、已读、WebSocket 推送
scripts/smoke-social.sh     # 好友、申请、群聊、公告
scripts/smoke-call.sh       # 音视频通话信令
scripts/smoke-feed.sh       # 朋友圈、标签、表情包、隐私
scripts/smoke-base-crud.sh  # 后台通用增删改查
scripts/smoke-base-ext.sh   # 后台扩展接口
scripts/smoke-ai.sh         # AI 对话（配合 MockLlm.java 模拟模型）
scripts/smoke-ai-admin.sh   # AI 后台管理接口的管理员校验
scripts/smoke-history.sh    # 聊天记录检索、文件消息列表
scripts/smoke-portal.sh     # 回显查询、权限、服务监控、数据源、微信门户、消息重推、徽章、拉黑
scripts/smoke-msg.sh        # 消息中心：站内通知、邮件、短信渠道、接口日志、定时发送
scripts/smoke-security.sh   # 密码锁定、接口权限、按所有者隔离的 anyone 接口、脚本执行
scripts/smoke-base-rules.sh # 基础数据的业务规则：密码、唯一性、树路径、级联、菜单接口、字典
scripts/smoke-admin-client.sh # 管理后台 HuLa-Admin 专用的接口：用户、菜单、禁言、删除评论、修改密码
scripts/smoke-generator.sh  # 代码生成：导入、预览、覆盖策略、生成项目
scripts/smoke-generator-options.sh # 生成代码的各选项，编译并调用；从 PostgreSQL 导入表
scripts/smoke-generator-web.sh     # 生成的前端代码通过 Vue 和 TypeScript 编译器
scripts/smoke-parity.sh     # 调用原后端声明的每个接口，不能有服务器错误和缺失的路由
scripts/smoke-ha.sh         # 多实例：跨实例投递和通话、实例退出后的定时任务（不在完整回归中）
scripts/setup-deepseek.sh   # 登记 DeepSeek 模型（见“AI 模型”一节）
```

浏览器测试控制台：启动后访问 `http://localhost:18760/console/index.html`，可以登录、查看会话、收发消息并观察 WebSocket 推送。浏览器测试过程见 [docs/browser-test.md](docs/browser-test.md)。

原桌面客户端的前端也可以直接在浏览器中对接本服务，并用 dsb 做端到端测试，见 [docs/client-test.md](docs/client-test.md)。

## 与原后端的差异

用 `scripts/audit-routes.py` 可以对比原后端声明的接口和这里注册的路由，`scripts/audit-parity.py` 会实际调用这些接口，并把响应字段和原后端的返回类对比。`scripts/compare-original.py` 把同样的请求发给运行中的原后端，比较是否成功和响应字段，`scripts/client-admin-e2e.py` 用 HuLa-Admin 前端驱动本服务，见 [docs/verify-original.md](docs/verify-original.md)。

有意不同：

- 黑名单管理、服务监控、数据源配置、代码生成和 `/api/im/user/register`、`bindOAuth` 仅管理员可用。`/api/base` 下除路径中带 `anyone`、`anyUser`、`anyTenant` 的接口外也都仅管理员可用，其中用户、角色、消息接口和模板等操作不能开放给普通用户。这些接口也可以通过角色授权：角色被授予的资源配置了接口（`def_resource_api`）时可以访问。管理端用户（`systemType=10`）的每个接口都按这个规则校验，没有配置的接口不能访问，与原网关相同。
- 比原后端严格的接口：`/anyone/defTenant` 和 `/anyone/extendNotice` 只能访问本人的数据，普通用户不能修改租户状态和通知内容，也不能新增通知；`/anyone/defTenant/updateStatus` 和 `/extendMsg/anyUser/sendByTemplate` 需要管理员或授权（原后端任何人都能审核租户、无需登录发送消息）；`/baseLoginLog/anyone/page` 普通用户只能看到自己的日志；`/anyone/extendNotice/deleteMyNotice` 只删除发给自己的通知。知识库文档的地址不能指向本机和内网，域名解析和跳转也不能绕过这个限制。网关在服务连续失败时熔断，返回和 `/api/fallback` 相同的响应。
- 增加了原后端没有、HuLa-Admin 会调用的接口，`scripts/audit-admin-client.py` 可以检查它的调用是否都有路由：`/api/base/user/*`、`/api/base/defResource/{add,edit,del,visible,isRouteExist}`、`/api/im/room/group/{mute,unmute}`、`/api/im/feed/comment/del`、`/api/oauth/anyUser/updatePassword`、`/api/oauth/anyTenant/menu/initRoute`。其中 `/user/current`、`updateCurrent`、`uploadAvatar` 登录即可使用，其余 `/user/*` 和菜单接口按后台权限校验。其中大部分在原后端有名字不同的对应接口，规则相同；`group/mute`、`unmute`、`menu/initRoute`、`isRouteExist` 原后端没有实现，是按 HuLa-Admin 的调用代码写的。禁言保存在 Redis 中，到期自动解除，变化时在群里发系统消息，成员列表返回 `muted`。头像上传只支持 MinIO。
- 请求缺少必填字段或格式不对时返回校验错误（`-9`），原后端是服务器错误。原后端的安装脚本没有 `ai_workflow` 表，这里在 AI 服务启动时创建。
- 代码生成的后端模板生成 tio-boot 代码（实体、Service、每个接口一个 Handler、路由配置），不生成 MyBatis 的 Mapper、Manager 和 VO；生成的项目是 tio-boot 项目。父类决定生成哪些接口。导入导出是 CSV，导出预览是 HTML 表格（原后端是 Excel）。缓存保存在 Redis。使用行级租户的表只信任带内部密钥（`chat.internal.secret`）的请求，也就是经过网关的请求，生产环境要修改这个密钥的默认值。回显的常量生成在每张表自己的 `XxxEcho` 类中，原后端写入公共的 `EchoApi`、`EchoDictType`。选择 seata 时只加入依赖，与原后端相同。前端模板来自原后端，修正了在 lamp-web 中编译不过的地方，主从结构同时生成从表的接口定义和语言包。因此 `getFieldTemplate`、`getDefFileOverrideStrategy` 返回的后端模板项不同。
- 代码生成的数据源支持 MySQL、MariaDB 和 PostgreSQL，不支持 Oracle 和 SQL Server，`dsId` 为空或 0 时读取当前服务的数据库。连接串不能包含读取本地文件或加载类的参数。
- 再次导入已经导入过的表会覆盖原来的配置，原后端会多出一份。
- 生成代码时任何一个模板出错都会中止并返回错误，原后端只记日志，压缩包中缺少文件。
- `/api/generator/defGenProject/anno/getProperties` 只返回 Java 版本、系统和编码，原后端返回全部系统属性。
- 定时消息由 base 服务轮询发送（`chat.msg.schedule.interval-seconds`，默认 30 秒），用 Redis 锁保证多实例只发送一次。
- 消息未确认时的重推和通话定时器保存在 Redis 中，进程崩溃后由其他实例继续执行。ws 和 im 都可以运行多个实例。
- `/api/doc.html` 是路由总览页。

原后端自带的 `TEST` 消息接口脚本引用了不存在的类，无法编译，这一点两边相同：发送时这条消息记为失败，不影响其他消息。

## 目录结构

```
tio-chat-common    公共部分：实体、上下文、工具、Token 校验、推送与在线状态、通用增删改查、模块启动框架、跨模块调用
tio-chat-oauth     登录、注册、token、验证码、扫码与第三方登录        OauthServerApp
tio-chat-base      后台管理：租户、用户、组织、角色资源、字典参数、日志  BaseServerApp
tio-chat-system    系统配置、对象存储、地图与翻译                      SystemServerApp
tio-chat-im        好友、会话、消息、群聊、朋友圈与表情                ImServerApp
tio-chat-ai        AI 模型、对话、写作、思维导图、知识库与绘图          AiServerApp
tio-chat-ws        WebSocket 连接、在线状态、推送与音视频信令          WsServerApp
tio-chat-generator 在线代码生成                                      GeneratorServerApp
tio-chat-all       所有模块一个服务                                    ChatServerApp
tio-chat-gateway   网关                                                GatewayApp
```

每个业务模块内部按 `handler`（每个接口一个 Handler）、`service`、`config/route`（路由注册）分包，模块入口是实现 `ChatModule` 的 `XxxModule`。
