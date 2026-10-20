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

## 说明

- 垫片不使用本地 SQLite：会话、消息、群成员每次直接从服务端读取，`save_msg`、`sync_messages` 等本地缓存命令为空操作。
- 只在桌面端打开的辅助窗口（加好友、设置等）会以新标签页打开；主窗口（登录、主界面）在当前标签页切换。
- 首次启动时 Vite 会预构建依赖并自动刷新一次页面，垫片会保留当前窗口标签，刷新后状态不受影响。
