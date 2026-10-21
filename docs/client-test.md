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
- `scripts/client-call-e2e.sh`：语音、视频通话的接通、挂断、拒绝、取消与通话记录。
- `scripts/client-qrlogin-e2e.sh`：扫码登录（手机端的扫码、确认用 curl 模拟）。
- `scripts/client-profile-group-e2e.sh`：设置窗口、修改昵称和头像、在线状态、群公告、邀请进群、设置管理员、移出成员（需要 MinIO）。
- `scripts/client-ai-e2e.sh`：AI 助手对话（需要先用 `scripts/setup-deepseek.sh` 登记模型，见 README“AI 模型”一节）。
- `scripts/client-oauth-e2e.sh`：第三方登录（Gitee）的授权页打开与回调登录。
- `scripts/client-history-files-e2e.sh`：聊天记录窗口（关键词、图片/视频、文件、日期筛选）与文件管理窗口（按发送人、按会话、搜索、打开与另存为），需要 MinIO。

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
sg docker -c "scripts/client-call-e2e.sh client-e2e-out"
sg docker -c "scripts/client-qrlogin-e2e.sh client-e2e-out"
sg docker -c "scripts/client-profile-group-e2e.sh client-e2e-out"
sg docker -c "scripts/client-history-files-e2e.sh client-e2e-out"
sg docker -c "scripts/client-oauth-e2e.sh client-e2e-out"
```

每个脚本结束时（`finish`）把各标签页中垫片记录的未实现命令和失败命令追加到输出目录的 `shim-diagnostics.log`。

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

## 语音与视频通话

通话窗口 `/rtcCall` 以新标签页打开：主叫方点击聊天头部的电话或摄像机图标，被叫方的主窗口收到 `VideoCallRequest` 推送后打开来电窗口。通话窗口没有自己的 WebSocket，WebRTC 的 offer、answer、ICE 候选通过垫片转给主窗口所在标签页发送（类型 14），服务端在两人之间转发；应答（类型 6）、挂断、拒绝、取消同样如此，通话结束时服务端生成通话记录消息（语音 12、视频 13）。

无头 Chrome 没有摄像头和麦克风。垫片提供模拟设备：`localStorage.__shim_fake_media` 为 `1` 时，`getUserMedia` 返回由振荡器生成的音轨和动画 canvas 生成的视频轨，`enumerateDevices` 返回模拟的麦克风、摄像头和扬声器。两个浏览器任务在同一台机器上，WebRTC 直接用主机候选地址连通。垫片还实现了 `push_window_payload` / `get_window_payload`（保存在 localStorage，供另一个标签页的窗口读取）。

`scripts/client-call-e2e.sh` 用 `make-friends.sh` 生成一对好友，登录后打开模拟设备，然后：

1. 语音通话：bob 打开着与 alice 的会话。alice 点击电话图标，通话窗口显示“正在呼叫”；bob 弹出来电窗口“来电 · 语音通话”，点击接听；双方连接成功，显示“通话中”并开始计时，bob 的 `<audio>` 播放对方的音轨。alice 挂断，双方的通话窗口自动关闭，聊天中出现“通话时长 0:05”。
2. 视频通话：bob 正在看“官方频道”。来电窗口仍然弹出，接听后双方都显示两路视频（本地和对方的模拟摄像头画面，`videoWidth > 0`），bob 挂断，双方出现视频通话记录。
3. 拒绝：alice 发起语音通话，bob 在来电窗口点击拒绝，双方窗口关闭，记录为“已拒绝”（与原服务端的文案规则一致）。
4. 取消：alice 发起视频通话，bob 接听前 alice 挂断，bob 的来电窗口关闭，alice 看到“已取消”，bob 看到“对方已取消”。
5. 用接口核对聊天中保存了 4 条通话记录：`(13, 通话时长)`、`(12, 通话时长)`、`(13, 已拒绝)`、`(12, 已取消)`。

截图：`41-voice-incoming.png`、`42-voice-connected-alice.png`、`43-voice-connected-bob.png`、`44-voice-record.png`、`45-video-connected-alice.png`、`46-video-connected-bob.png`、`47-rejected-alice.png`、`48-cancelled-bob.png`。

测试中修复的服务端问题：桌面客户端的来电窗口把被叫方“当前打开的会话”当作通话房间，被叫方正在看其他会话时，应答和信令带的是那个会话的房间号，服务端会把信令推给无关的成员，通话无法接通。现在应答时如果房间不是该主叫方发起的通话，改用主叫方发起、被叫方所在的通话房间；信令的发送方不在所带房间的通话中时，改用发送方已加入的通话房间。被叫方没有打开任何会话时，房间号为空，客户端在处理 offer 时自己报“房间号不存在”，这是客户端的问题，服务端无法补救。

## 扫码登录

登录页的“扫码登录”调用 `oauth/anyTenant/qr/generate` 取得 `qrId` 和 `deviceHash`，二维码内容为 `{"type":"login","qrId":"…"}`，之后每 2 秒用 `oauth/anyTenant/qr/status/query` 查询状态：`SCANNED` 时显示“扫码成功，等待授权”，`CONFIRMED` 时响应里带有新签发的令牌，页面调用 `update_token` 保存后走自动登录进入主界面（客户端用轮询，不依赖 WebSocket 推送）。手机端用自己的登录会话依次调用 `oauth/qrcode/scan` 和 `oauth/qrcode/confirm`，测试用 curl 和 alice 的 `MOBILE` 令牌模拟。

`scripts/client-qrlogin-e2e.sh`：

1. 打开登录页，点击“扫码登录”，页面显示服务端生成的二维码（从页面组件读取二维码内容并解析出 `qrId`）。二维码 30 秒内有效，脚本用 DOM 点击进入页面，避免等待页面空闲时二维码过期。
2. 手机端扫码，页面变为“扫码成功，等待授权”。
3. 手机端确认，页面自动进入主界面，显示 alice 的会话列表；在与 bob 的会话中发送一条消息，bob 一侧用接口能查到这条消息。
4. 已使用的二维码再次确认会失败。

截图：`51-qr-code.png`、`52-qr-scanned.png`、`53-qr-logged-in.png`。

## 设置、个人资料、在线状态与群管理

`scripts/client-profile-group-e2e.sh` 注册 alice 及 4 个好友（bob、carol、dave、eve），alice 用 bob、carol、dave 建群，然后：

1. 设置窗口（左下角菜单 →“设置”，新标签页）：依次打开通用、通知、快捷键、登录、存储；存储页点击“开始扫描”，显示磁盘分区总容量等信息。检查垫片记录的未实现命令和失败命令都为空（`window.__TAURI_SHIM__.unhandled` / `failures`）。
2. 个人资料：点击头像 →“编辑资料”，修改昵称后保存，提示“保存成功”；点击头像选择图片，裁剪后上传到 MinIO。接口返回的昵称和头像地址都已更新。
3. 在线状态：点击头像弹出框中的“在线”，在线状态窗口选择“忙碌”；bob 打开着与 alice 的会话，头部实时显示“忙碌”。
4. 群公告：alice 在群聊右侧点击“群公告须知”，在公告窗口填写并发布，窗口自动关闭；bob 的群聊中出现公告消息，右侧公告栏同步更新。
5. 邀请进群：alice 点击聊天头部的“邀请进群”，邀请窗口（`modal-invite`，通过窗口载荷取得群号）列出好友，选择 eve 并确定；eve 用接口同意邀请后，群里出现“…邀请 eve 加入群聊”。
6. 设置管理员：alice 在右侧成员列表右键 bob，菜单包含发送消息、查看信息、设为管理员、移出群聊等，点击“设为管理员”；bob 一侧实时显示“管理员”标识。
7. 移出成员：右键 carol →“移出群聊”；再通过“…”→“管理群成员”勾选 dave，点击“踢出群聊”并确认。接口核对群成员为 alice（群主）、bob（管理员）、eve。

截图：`61-settings-storage.png`、`62-profile-edited.png`、`63-bob-sees-busy.png`、`64-bob-announcement.png`、`65-invite.png`、`66-bob-admin.png`、`67-members-managed.png`。

垫片补充：存储设置页调用的 `get_directory_usage_info_with_progress` 用内存文件系统统计目录大小、用 `navigator.storage.estimate()` 作为磁盘容量，并发送 `directory-scan-complete` 事件；`cancel_directory_scan` 为空操作。

与原服务端一致、未改动的行为：修改昵称和头像不推送给好友，好友刷新后才看到新资料；群成员不超过 3 人时移出成员会直接解散群聊；在另一台设备上建群不会推送到建群人自己的其他设备。

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

## 聊天记录与文件管理

桌面客户端的“聊天记录”窗口（聊天输入框上方的历史图标，`/chat-history?roomId=...`）和左下角的“文件”窗口（`/fileManager`）都只查本地 SQLite：Rust 命令 `query_chat_history` 按会话、消息类型（`image` 为图片 3 和视频 6，`file` 为文件 4）、关键词（匹配正文、文件名、链接）、发送时间范围、排序和页码查询，返回 `{messages: MessageResp[], hasMore, currentPage}`；`query_files` 按导航（我的文件、按发送人、按会话、按群组）查询文件和视频消息，返回按日期分组的 `{timeGroupedFiles: [{date, displayDate, files: FileInfo[]}], userList, hasMore, currentPage}`，`get_navigation_items` 返回四个导航项。浏览器没有本地库，服务端新增两个接口代替（见 README“接口分组”），垫片按 Rust 的参数和返回结构转换：

- `query_chat_history` → `GET /api/im/chat/msg/search`，消息转换为 `MessageResp`（`createId`、`createTime`、`fromUser`、`message`、`timeBlock`）。日期选择器给出的结束时间是当天零点，Rust 实现按 `send_time <= 结束时间` 过滤，选同一天时查不到任何消息；垫片把零点的结束时间延长到当天结束。`hasMore` 取服务端的 `isLast`，而不是“本页是否满页”。
- `query_files` → `GET /api/im/chat/msg/files`，“按发送人”与 Rust 一致：列出与该联系人有消息往来的会话中的全部文件。`FileInfo.sender` 带服务端返回的昵称与头像（Rust 只有本地昵称，头像固定为默认图）；文件列表中的“来自”由客户端按当前会话的群成员显示，不在当前群里的发送人显示为“未知用户”，与桌面端相同。
- 打开与保存文件：双击文件时客户端先下载到缓存目录再调用 opener 插件的 `open_path`，垫片在新标签页打开内存文件系统中的文件；“在文件夹中显示”（`reveal_item_in_dir`）改为浏览器下载。右键“另存为”时 `dialog` 插件的 `save` 返回虚拟的下载目录路径，客户端写入该路径时垫片触发浏览器下载。

`scripts/client-history-files-e2e.sh` 注册 alice、bob、carol，alice 与两人互为好友；bob 和 carol 通过接口发消息（文件先用预签名地址上传到 MinIO）：bob 发两条含 kiwi 的文本（大小写不同，其中一条由 alice 发）、一条普通文本、一张图片、一个文件 `budget-*.txt`，carol 发一个文件 `notes-*.txt`。alice 在客户端：

1. 打开与 bob 的会话，点击历史图标，聊天记录窗口显示全部 6 条消息（含加好友提示），按日期分组。
2. 搜索 `kiwi`，只剩两条，关键词高亮（不区分大小写）；清空后恢复。
3. “图片/视频”页只有那张图片（160×100，已加载）；“文件”页只有 `budget-*.txt`。
4. 日期选择“今天 ~ 今天”仍是 6 条；选择本月 1 日（过去的一天）为 0 条。
5. 打开文件窗口，“我的文件”列出 `budget-*` 与 `notes-*`；搜索 `notes` 只剩一个。
6. “按发送人”选 carol 只剩 `notes-*`；“按会话”选 bob 只剩 `budget-*`。
7. 双击文件，新标签页显示文件内容；右键“另存为”，浏览器下载该文件（`window.__TAURI_SHIM__.downloads` 记录文件名和大小）。

截图：`70-history-all.png`、`71-history-keyword.png`、`72-history-images.png`、`73-history-files.png`、`74-history-today.png`、`75-history-past-day.png`、`80-files-all.png`、`81-files-sender.png`、`82-files-session.png`、`83-files-saved.png`、`84-files-opened.png`。

接口层面的检查见 `scripts/smoke-history.sh`：关键词匹配正文和文件名、已撤回消息不参与匹配、类型与时间范围、升序分页、不是会话成员时报错、文件列表按会话、发送人和关键词筛选。

## 第三方登录（Gitee / GitHub / GitCode）

桌面端点击第三方登录时，Rust 命令 `start_oauth_server` 在 `127.0.0.1:36677` 监听并生成一次性 `secret`，客户端请求 `/api/oauth/anyTenant/{provider}/authorize-url?redirect=http://127.0.0.1:36677/?secret=...` 取得授权地址并在浏览器中打开；授权后服务端的 `callback` 用授权码登录，再重定向到 `redirect?token=...&refreshToken=...&uid=...`，本地监听校验 `secret` 后发出 `oauth-token` 事件，登录窗口据此保存令牌并进入主界面。

浏览器中垫片的 `start_oauth_server` 返回当前页面的端口和一个保存在 localStorage 的一次性 `secret`，因此重定向地址就是前端自身（`http://127.0.0.1:<端口>/?secret=...`）。回调标签页加载垫片时识别出 `secret` 与 `token`（或 `code`），校验通过后通过 BroadcastChannel 向登录页发送 `oauth-token`，显示“登录成功”并自动关闭；`secret` 不符时显示错误，不发送事件。客户端把回调主机写死为 `127.0.0.1`，所以第三方登录只能在 `http://127.0.0.1:<端口>` 打开的前端中使用（`localhost` 源收不到回调）。服务端需要配置 `oauth.<provider>.client-id`、`client-secret` 和 `redirect-uri`（指向本服务的 `/api/oauth/anyTenant/<provider>/callback`），本地没有第三方应用凭据，`scripts/client-oauth-e2e.sh` 验证除第三方授权页以外的整个流程：

1. 登录页切到“扫码登录”，点击 Gitee 图标：垫片生成一次性 `secret`，客户端取得授权地址并通过 `shell.open` 在新标签页打开 gitee.com（`state` 中带着垫片给出的回调地址）。
2. 用错误的 `secret` 打开回调地址，页面显示 “invalid or missing secret”，不会登录。
3. 用密码登录接口取得令牌，按服务端 `callback` 的格式打开 `/?secret=<secret>?token=...&refreshToken=...&uid=...`：回调页显示“登录成功”，登录页收到 `oauth-token` 后进入主界面，当前用户为该账号。

截图：`90-oauth-callback.png`、`91-oauth-logged-in.png`。


## 覆盖情况

各脚本最近一次全部通过；脚本结束时收集的 `shim-diagnostics.log` 中所有标签页的未实现命令和失败命令都为空。

- 账号：密码登录、扫码登录、第三方登录回调（`client-e2e.sh`、`client-qrlogin-e2e.sh`、`client-oauth-e2e.sh`）。
- 单聊与群聊：会话列表、消息收发与实时推送、撤回、已读、好友列表（`client-e2e.sh`、`client-group-e2e.sh`）。
- 图片、文件、视频：MinIO 预签名直传、接收方实时显示、下载、另存为、打开（`client-upload-e2e.sh`、`client-history-files-e2e.sh`）；视频首帧缩略图用页面内调用验证。
- 好友与群：按账号搜索加好友、申请加群、群公告、邀请、管理员、移出成员（`client-friend-e2e.sh`、`client-profile-group-e2e.sh`）。
- 朋友圈：发布、图片动态、点赞、评论、通知（`client-feed-e2e.sh`）。
- 语音、视频通话：呼叫、接通、挂断、拒绝、取消、通话记录（`client-call-e2e.sh`，浏览器用伪造的摄像头和麦克风）。
- 设置、个人资料、在线状态、存储扫描（`client-profile-group-e2e.sh`）。
- AI 助手：模型与角色、流式回答、深度思考、停止生成、历史（`client-ai-e2e.sh`，需要 DeepSeek 密钥）。
- 聊天记录与文件管理：关键词、类型、日期筛选，按发送人、按会话（`client-history-files-e2e.sh`）。
- 接口层面另有 `scripts/smoke-*.sh`，其中 `smoke-history.sh` 覆盖聊天记录检索与文件列表，`smoke-ai-admin.sh` 覆盖 AI 后台管理接口的管理员校验（普通用户 `406 无此权限`，`TENANT_ADMIN` 正常返回）。

已知限制：

- 截图（`screenshot`，捕获整个桌面的原始像素）没有浏览器实现：浏览器只能通过 `getDisplayMedia` 让用户手动选择屏幕，而截图窗口是新打开的标签页，没有用户手势，无法发起屏幕共享。
- `qiniu_upload_resumable`（七牛分片上传）不实现：它由 Rust 直接按七牛的分片接口上传本地文件，浏览器联调统一使用 MinIO 预签名直传。
- `start_oauth_server` 不在本地监听端口，而是以前端自身作为回调地址；客户端写死回调主机 `127.0.0.1`，第三方登录只能在 `127.0.0.1` 源中使用。真实的第三方授权需要在服务端配置应用凭据，本地未验证。
- 移动端、iOS 专用命令（`set_complete`、`hide_splash_screen`、`set_ios_badge`、`trigger_haptic_feedback` 等）和移动端的会话内搜索页（`searchContent`）不在测试范围内。
- 本地 SQLite 相关命令（`save_msg`、`sync_messages`、`delete_message`、`delete_room_messages` 等）为空操作，数据每次从服务端读取，因此“删除本地聊天记录”在浏览器中没有效果。
- `audio`（播放应用内置提示音）、`debug_message_stats` 等未被桌面界面调用的命令没有实现。

## 说明

- 垫片不使用本地 SQLite：会话、消息、群成员每次直接从服务端读取，`save_msg`、`sync_messages` 等本地缓存命令为空操作；聊天记录检索和文件管理改为查询服务端。
- 原生能力的浏览器替代：`shell.open` 与 opener 的 `open_url` 在新标签页打开链接；`open_path` 在新标签页打开内存文件系统中的文件，`reveal_item_in_dir` 改为浏览器下载；“另存为”对话框返回虚拟下载目录，写入时触发浏览器下载；`get_video_thumbnail` 用 `<video>` 和 canvas 截取首帧（JPEG base64，字段与 Rust 一致）。
- 只在桌面端打开的辅助窗口（加好友、设置等）会以新标签页打开；主窗口（登录、主界面）在当前标签页切换。
- dsb 的任务共用一个 Chrome，最后一个任务关闭时浏览器会保留它的页面。遗留的页面仍在运行客户端，和新页面同源时会通过 BroadcastChannel 和 localStorage 互相干扰（例如收到旧账号的来电）。测试脚本结束时用 `finish` 关闭新标签页并把主页面切到空白页；如果之前有中断的测试，先在没有其他任务时执行 `dsb close --close-browser` 关闭浏览器再测。
- 浏览器登录会使同一设备类型（PC）之前签发的令牌失效，测试脚本在浏览器登录后需要调用接口时，用另一个设备类型（如 `MOBILE`）重新登录取得令牌。
- 首次启动时 Vite 会预构建依赖并自动刷新一次页面，垫片会保留当前窗口标签，刷新后状态不受影响。
