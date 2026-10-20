# 桌面客户端前端联调测试

原 IM 桌面客户端基于 Tauri：界面是 Vue 页面，登录、接口请求、WebSocket、消息存储由 Rust 层完成，页面通过 `window.__TAURI_INTERNALS__.invoke` 调用。`tools/web-shim` 在浏览器里提供这个对象，把 Rust 命令直接实现为对本服务的 HTTP 和 WebSocket 调用，客户端前端代码不需要任何修改就能在 Chrome 中运行，再由 deepseek-browser-use 的 `dsb` 驱动做端到端测试。

## 组成

- `tools/web-shim/tauri-shim.js`：浏览器垫片。实现 `invoke`、`transformCallback`、事件（`listen` / `emit`，多标签页之间通过 BroadcastChannel 转发）、Channel 回调，以及登录、`im_request_command`、会话、消息分页、发送消息、群成员、WebSocket 连接与推送分发等命令；窗口、文件、通知等原生能力返回浏览器下的合理默认值。
- `tools/web-shim/im-urls.json`：客户端接口名到 `[方法, 路径]` 的映射，由客户端 Rust 源码生成。
- `tools/web-shim/vite.web.config.ts`：复用客户端自己的 Vite 配置，把 `/api`（含 WebSocket）代理到本服务，并在页面最前面注入垫片。
- `tools/web-shim/start-client.sh`：把垫片和配置放进客户端目录并启动 Vite。
- `scripts/make-friends.sh`：注册两个用户并互加好友。
- `scripts/make-group-users.sh`：注册 alice、bob、carol，alice 与另外两人互为好友。
- `scripts/client-lib.sh`：测试脚本共用的 dsb 操作（登录、打开会话、发消息、截图）。
- `scripts/client-e2e.sh`：单聊，两个浏览器任务完成登录、收发消息、查看好友列表并截图。
- `scripts/client-group-e2e.sh`：群聊与消息撤回。
- `scripts/client-upload-e2e.sh`：发送图片和文件（需要 MinIO，见 README“文件存储”一节）。
- `scripts/client-friend-e2e.sh`：按账号搜索加好友、按群号申请加群。
- `scripts/client-feed-e2e.sh`：朋友圈发布、图片动态、点赞评论与实时通知（需要 MinIO）。
- `scripts/client-ai-e2e.sh`：AI 助手对话（需要先用 `scripts/setup-deepseek.sh` 登记模型，见 README“AI 模型”一节）。

## 运行

先启动 MySQL、Redis 和本服务，安装 Node 20+ 与 pnpm，在客户端源码目录执行一次 `pnpm install`，然后：

```shell
CLIENT_DIR=<客户端源码目录> tools/web-shim/start-client.sh
```

前端运行在 `http://127.0.0.1:6130`。可用环境变量：`WEB_PORT`（默认 6130）、`API_TARGET`（默认 `http://127.0.0.1:18760`）、`CLIENT_LANG`（界面语言，默认 zh-CN）。

安装并启动 dsb 后执行端到端测试：

```shell
sg docker -c "scripts/client-e2e.sh client-e2e-out"
sg docker -c "scripts/client-group-e2e.sh client-e2e-out"
sg docker -c "scripts/client-upload-e2e.sh client-e2e-out"
sg docker -c "scripts/client-ai-e2e.sh client-e2e-out"
sg docker -c "scripts/client-friend-e2e.sh client-e2e-out"
sg docker -c "scripts/client-feed-e2e.sh client-e2e-out"
```

两个用户分别使用 `127.0.0.1` 和 `localhost` 两个源打开页面，各自的 localStorage 互不影响；每次登录前会清空该源的本地状态，保证首次使用引导、登录表单的流程一致。

## 单聊

1. alice 登录：完成首次使用引导，输入邮箱和密码登录，进入主窗口，会话列表显示与 bob 的单聊、官方频道和小管家，未读数正确。
2. alice 打开与 bob 的会话：历史消息通过 `page_msg` 加载，未读清零；发送消息后状态为成功，会话摘要更新。
3. bob 登录：离线期间收到的消息出现在会话列表中，未读数为 1。
4. bob 回复：alice 的页面通过 WebSocket 实时收到 `receiveMessage` 推送，无需刷新；双方都显示对方“在线”。
5. bob 打开通讯录：好友通知、好友列表和在线状态正常显示。

截图保存在输出目录：`01-alice-home.png`、`02-alice-sent.png`、`03-bob-home.png`、`04-alice-received.png`、`05-bob-chat.png`、`06-bob-friends.png`。

## 群聊与消息撤回

1. alice 点击“+”→“发起群聊”，在创建群聊对话框中全选 bob 和 carol，点击“创建”。提示“创建群聊成功”，会话列表出现新群，默认群名为“alice 的昵称 + 的群组”，右侧成员列表显示三人，alice 标记为群主。
2. alice 在群里发消息；bob 登录后会话列表出现该群，打开即看到 alice 的消息，并显示群主标识。
3. bob 回复，alice 实时收到，在线成员数为 2。
4. alice 再发一条消息，bob 收到后，alice 右键该消息，菜单中包含复制、翻译、选择、转发、回复、撤回、删除，点击“撤回”。
5. alice 一侧显示“你撤回了一条消息 重新编辑”；bob 通过 WebSocket 收到 `msgRecall` 推送，原消息立即替换为“…撤回了一条消息”，会话摘要同步更新。
6. bob 刷新页面后重新打开群聊，历史消息里仍是撤回提示，内容为服务端生成的“群主 + 昵称 + 撤回了一条消息”，原文不再出现。

截图：`11-group-create.png`、`12-group-created.png`、`13-group-alice.png`、`14-group-bob.png`、`15-recall-menu.png`、`16-recall-alice.png`、`17-recall-bob.png`、`18-recall-bob-reload.png`。

测试中发现并修复了一个服务端问题：默认群名为“创建者昵称的群组”，昵称较长时超过 `im_room_group.name` 的 16 个字符，建群失败。现在默认群名会截断昵称；手动指定或修改群名超过 16 个字符时返回“群名称不能超过16个字符”。

## 图片与文件消息

桌面客户端发送图片和文件时，先把文件写到本地缓存目录（`plugin-fs`），再把路径交给 Rust 命令 `upload_file_put`，由它 PUT 到服务端返回的预签名地址；收到的图片先下载到本地缓存，再用 `convertFileSrc` 显示。垫片在浏览器里补齐了这条链路：

- 内存文件系统：`plugin:fs` 的 `create` / `open` / `write` / `write_file` / `read_file` / `stat` / `exists` / `copy_file` / `remove` 等命令读写同一个内存表，相对路径和 `resolve_directory` 拼出的绝对路径指向同一个文件。
- 文件选择：`plugin:dialog|open` 用隐藏的 `<input type=file id="__shim_file_input">` 实现，选中的文件放进内存文件系统并返回路径；无头浏览器会立即取消文件选择框，垫片会保留这个输入框，测试脚本用 dsb 的 `upload_file` 给它设置文件。
- `get_files_meta`：返回内存文件的名称、扩展名和 MIME 类型。
- `upload_file_put`：用 XMLHttpRequest 把内存文件 PUT 到预签名地址，并通过 Channel 回报上传进度。
- `convertFileSrc`：内存中的文件（下载好的缩略图、粘贴的图片）转换成 `blob:` 地址。
- `plugin:http`：`fetch` / `fetch_send` / `fetch_read_body` / `fetch_cancel` 基于浏览器 `fetch` 实现，目标地址需要允许跨域。
- `qiniu_upload_resumable`（七牛分片上传）在浏览器中不可用，联调时把存储引擎配置为 MinIO。

测试步骤：

1. bob 登录并打开与 alice 的会话；alice 登录并打开与 bob 的会话。
2. alice 点击输入框上方的“文件”按钮，选择一张 160×100 的 PNG。图片插入输入框，按回车发送：客户端调用 `ossToken` 取得预签名地址，上传到 MinIO，消息体的 `url` 为 MinIO 下载地址。
3. bob 实时收到图片消息，图片从 MinIO 下载后正常显示（`naturalWidth` 为 160）。
4. alice 再次点击“文件”，选择 `report.txt`，在“发送文件”对话框中点击“发送 (1)”。
5. bob 实时收到文件消息，显示文件名 `report.txt` 和大小 `6.64 KB`；alice 一侧图片和文件消息都发送成功。

截图：`01-alice-sent-image-file.png`、`02-bob-received-image-file.png`。

测试中修复了一个服务端问题：MinIO 下载地址中的对象名没有做 URL 编码，文件名含空格、`#`、`%` 等字符时下载地址无效；现在下载地址与上传地址使用同样的路径编码，文件名中的 `/`、`\` 也会替换为 `_`，避免对象落到其他目录。

## 加好友与申请加群

桌面客户端的“加好友/群”、“申请加好友”、“申请加群”都是独立窗口，垫片把它们作为新标签页打开（`window.open(url, label)`），测试脚本用 dsb 的 `get_tabs` / `switch_tab` 切换（`client-lib.sh` 的 `use_tab`）。多个标签页之间的配合方式与 Rust 层一致：

- 窗口之间共享的 Pinia 状态（例如要添加的用户 `addFriendModalInfo`）由客户端自带的 `pinia-shared-state` 通过 BroadcastChannel 同步。
- WebSocket 只在主窗口（`home`）所在标签页中建立。推送按 Rust 层的规则分发：聊天、联系人、群、朋友圈类推送只发给 `home`，通话、登录二维码、令牌失效等推送发给所有窗口；其他标签页通过 BroadcastChannel 收到。
- 其他标签页调用 `ws_send_message`（例如通话窗口发送信令）时，垫片把消息转给持有连接的标签页发送；连接状态写在 localStorage 中，`ws_get_state` / `ws_is_connected` 在所有标签页返回一致的结果。
- 窗口的 `close` / `destroy` 关闭对应的标签页；关闭其他窗口（按标签名）时转发给那个标签页自己关闭。

`scripts/client-friend-e2e.sh` 注册 alice、bob（互不认识）和 alice 的两个好友，alice 用这两个好友建一个群，然后：

1. alice 点击“+”→“加好友/群”，在“找好友”中输入 bob 的账号搜索，结果显示 bob 的昵称和账号，点击“添加”。
2. “申请加好友”窗口显示 bob 的资料，填写留言后点击“添加好友”，提示“已发送好友申请”。
3. bob 停留在通讯录页面，通过 `newApply` 推送实时出现未读角标；打开“好友通知”，显示 alice 的申请和留言，点击“接受”后变为“已同意”，好友列表出现 alice。
4. alice 不刷新页面，会话列表出现与 bob 的单聊，摘要为“我们已经成为好友了，开始聊天吧”；bob 发消息，alice 实时收到。
5. bob 在“找群聊”中输入群号，搜到 alice 的群，点击“添加”，在“申请加群”窗口填写留言并点击“申请加入”，提示“已发送群聊申请”。
6. alice 打开“群通知”，显示 bob 的申请和留言，点击“接受”；bob 的会话列表出现该群，打开后显示“bob 加入群聊”，可以发言，群成员为 4 人。

截图：`21-search-user.png`、`22-add-friend-verify.png`、`23-bob-friend-notice.png`、`24-bob-accepted.png`、`25-alice-new-chat.png`、`26-alice-chat-with-bob.png`、`27-search-group.png`、`28-alice-group-notice.png`、`29-bob-joined-group.png`。

## 朋友圈

朋友圈是独立窗口 `/dynamic`（左侧“动态”图标），以新标签页打开。桌面端的发布框只能发纯文本，图片和视频动态由移动端发布：先用 `ossToken` 取得预签名地址上传素材，再调用 `pushFeed`。`scripts/client-feed-e2e.sh` 用 `make-friends.sh` 生成一对好友，然后：

1. alice 打开朋友圈窗口（显示“暂无动态”），点击“+”，输入文字后点击“发布”，提示“发布成功”，列表出现新动态。
2. alice 以 `MOBILE` 设备登录取得令牌，按移动端的流程把一张 160×100 的 PNG 上传到 MinIO 并发布图片动态。
3. bob 的主窗口通过 `feedSendMsg` 推送实时显示“动态”图标上的未读数；打开朋友圈，两条动态按时间倒序显示，图片从 MinIO 加载成功。
4. bob 对文字动态点“赞”（变为“已赞”），再点“评论”输入内容并发送，评论显示在动态下方。
5. alice 一直打开着的朋友圈窗口收到 `feedNotify` 推送（主窗口处理后经共享状态同步到朋友圈窗口），不刷新即显示 bob 的点赞和评论，“评论通知”角标为 2。
6. alice 点击动态进入详情页，显示点赞的人和评论列表。

截图：`31-feed-publish.png`、`32-bob-feed-badge.png`、`33-bob-feed.png`、`34-bob-liked-commented.png`、`35-alice-feed-notified.png`、`36-alice-feed-detail.png`。

测试中修复的服务端问题：

- 发布接口只从 `images` 读取素材，客户端的类型定义使用 `urls`（视频为 `videoUrl`），按客户端定义传参会提示“请至少上传一条素材”。现在三个字段都可以；视频动态在列表中以 `videoUrl` 返回，客户端才会显示为视频。
- 同一秒内发布的两条动态顺序不确定，现在按发布时间和编号倒序。

客户端自身的限制：桌面端朋友圈窗口的“评论通知”弹窗没有数据来源，始终显示“暂无评论通知”；点赞人自己也会收到 `feedNotify`（与原服务端一致，用于多端同步），所以操作者的角标同样会增加。

## AI 助手（DeepSeek）

桌面客户端的 AI 助手是 `/robot` 窗口。发送消息时页面调用 Rust 命令 `ai_message_send_stream`，参数是请求体 `body`（`conversationId`、`content`、`useContext`、`reasoningEnabled`）、`requestId` 和一个 Channel；Rust 层 POST `ai/chat/message/send-stream`，收到响应头后命令立即返回，随后在后台按 `\n\n` 切分 SSE 事件，每个 `data:` 行作为一条 `{eventType: "chunk", data, requestId}` 发到 Channel，流结束时发送 `done`（`data` 为全部数据拼接），出错时发送 `error`。停止生成调用 `ai_message_cancel_stream`，中止后台任务后不再发送任何事件。垫片按同样的顺序和字段实现这两个命令：用 `fetch` 读取响应流，`TextDecoder` 按流式方式解码（多字节汉字被拆在两个数据块中也能正确拼接），取消时中止 `fetch`。

测试前用 `scripts/setup-deepseek.sh` 登记 DeepSeek（公开密钥、`deepseek-flash` 与 `deepseek-v4-pro` 两个公开模型、一个公开角色“DeepSeek 助手”），然后执行 `scripts/client-ai-e2e.sh`。脚本注册新用户并完成以下步骤：

1. 登录后打开 `/robot`，点击“新的聊天”，以“DeepSeek 助手”角色创建对话，当前模型为 `deepseek-flash`（官网模型）；打开模型选择框，列出两个公开模型，选择 `deepseek-flash`。
2. 提问“中国的首都是哪座城市？……”，回答逐段显示，结束后包含“北京”，Token 用量更新。
3. 刷新页面，对话和消息从服务端重新加载。
4. 打开“深度思考”开关后提问“9.11 和 9.9 哪个大？”，回答上方显示“思考过程”，内容为模型返回的 `reasoning_content`。
5. 关闭开关，要求写一篇两千字的文章，回答流式显示到一半时点击“停止思考”，提示“已停止生成”，已显示的内容保留。
6. 用接口核对：对话中共 6 条消息，每条都有内容，第 2 个回答带思考内容；再次刷新后停止生成的回答仍在。

截图：`01-ai-models.png`、`02-ai-answer.png`、`03-ai-history.png`、`04-ai-reasoning.png`、`05-ai-streaming.png`、`06-ai-stopped.png`、`07-ai-history-after-stop.png`。

用 curl 也可以直接验证（中文 UTF-8 正常，事件以 `\n\n` 结尾）：

```shell
curl -N -XPOST localhost:18760/api/ai/chat/message/send-stream -H 'Content-Type: application/json' -H "token: $T" \
  -d '{"conversationId":"<对话编号>","content":"写一首关于西湖的四行短诗","reasoningEnabled":false}'
```

测试中发现并修复的服务端问题：

- SSE 事件以 `\r\n\r\n` 结尾，客户端只按 `\n\n` 切分事件，一个分片都收不到，界面一直停在“正在思考中…”；现在事件以 LF 分行。
- 上游请求默认带 `Accept-Encoding: gzip`，DeepSeek 的 CDN 会把压缩后的事件流攒批下发，首字延迟 3～5 秒、内容成段跳出；流式请求现在要求不压缩，首字约 1 秒。
- `reasoningEnabled` 被忽略：DeepSeek 新模型默认开启思考，关闭开关时也要等思考结束才出正文。现在按开关传 `thinking` 参数，关闭时不返回思考内容。
- 停止生成后服务端仍在等上游输出，回答保存为空；`save-generated-content` 读取的参数名与客户端不一致（客户端在查询串里传 `prompt`、`generatedContent`），保存失败。现在客户端断开时取消上游请求并保存已发送的部分，`save-generated-content` 遇到对同一提问的最新回答时更新它，否则插入提问和回答两条消息。
- 对话列表 `chat/conversation/page` 返回所有用户的对话；模型列表 `model/page` 只返回自己的模型，看不到公开模型；`model/simple-list`、角色列表不区分公开与私有；`api-key/simple-list`、`api-key/get` 和后台密钥列表返回密钥明文。现在按“公开或自己的”过滤，密钥只对创建者显示明文。
- 模型的 `publicStatus` 以布尔返回，客户端按数字 `0` 判断“官网模型”，公开模型被显示为“自建模型”；对话的 `tokenUsage` 以字符串返回，客户端不更新 Token 用量。
- 回答内容超过 `ai_chat_message.content` 的 10240 字符时保存失败，现在截断保存。

## 说明

- 垫片不使用本地 SQLite：会话、消息、群成员每次直接从服务端读取，`save_msg`、`sync_messages` 等本地缓存命令为空操作。
- 只在桌面端打开的辅助窗口（加好友、设置等）会以新标签页打开；主窗口（登录、主界面）在当前标签页切换。
- 浏览器登录会使同一设备类型（PC）之前签发的令牌失效，测试脚本在浏览器登录后需要调用接口时，用另一个设备类型（如 `MOBILE`）重新登录取得令牌。
- 首次启动时 Vite 会预构建依赖并自动刷新一次页面，垫片会保留当前窗口标签，刷新后状态不受影响。
