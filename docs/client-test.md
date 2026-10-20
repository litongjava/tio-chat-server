# 桌面客户端前端联调测试

原 IM 桌面客户端基于 Tauri：界面是 Vue 页面，登录、接口请求、WebSocket、消息存储由 Rust 层完成，页面通过 `window.__TAURI_INTERNALS__.invoke` 调用。`tools/web-shim` 在浏览器里提供这个对象，把 Rust 命令直接实现为对本服务的 HTTP 和 WebSocket 调用，客户端前端代码不需要任何修改就能在 Chrome 中运行，再由 deepseek-browser-use 的 `dsb` 驱动做端到端测试。

## 组成

- `tools/web-shim/tauri-shim.js`：浏览器垫片。实现 `invoke`、`transformCallback`、事件（`listen` / `emit`，多标签页之间通过 BroadcastChannel 转发）、Channel 回调，以及登录、`im_request_command`、会话、消息分页、发送消息、群成员、WebSocket 连接与推送分发等命令；窗口、文件、通知等原生能力返回浏览器下的合理默认值。
- `tools/web-shim/im-urls.json`：客户端接口名到 `[方法, 路径]` 的映射，由客户端 Rust 源码生成。
- `tools/web-shim/vite.web.config.ts`：复用客户端自己的 Vite 配置，把 `/api`（含 WebSocket）代理到本服务，并在页面最前面注入垫片。
- `tools/web-shim/start-client.sh`：把垫片和配置放进客户端目录并启动 Vite。
- `scripts/make-friends.sh`：注册两个用户并互加好友。
- `scripts/client-e2e.sh`：用两个浏览器任务完成登录、收发消息、查看好友列表并截图。

## 运行

先启动 MySQL、Redis 和本服务，安装 Node 20+ 与 pnpm，在客户端源码目录执行一次 `pnpm install`，然后：

```shell
CLIENT_DIR=<客户端源码目录> tools/web-shim/start-client.sh
```

前端运行在 `http://127.0.0.1:6130`。可用环境变量：`WEB_PORT`（默认 6130）、`API_TARGET`（默认 `http://127.0.0.1:18760`）、`CLIENT_LANG`（界面语言，默认 zh-CN）。

安装并启动 dsb 后执行端到端测试：

```shell
sg docker -c "scripts/client-e2e.sh client-e2e-out"
```

两个用户分别使用 `127.0.0.1` 和 `localhost` 两个源打开页面，各自的 localStorage 互不影响；每次登录前会清空该源的本地状态，保证首次使用引导、登录表单的流程一致。

## 测试流程与结果

1. alice 登录：完成首次使用引导，输入邮箱和密码登录，进入主窗口，会话列表显示与 bob 的单聊、官方频道和小管家，未读数正确。
2. alice 打开与 bob 的会话：历史消息通过 `page_msg` 加载，未读清零；发送消息后状态为成功，会话摘要更新。
3. bob 登录：离线期间收到的消息出现在会话列表中，未读数为 1。
4. bob 回复：alice 的页面通过 WebSocket 实时收到 `receiveMessage` 推送，无需刷新；双方都显示对方“在线”。
5. bob 打开通讯录：好友通知、好友列表和在线状态正常显示。

截图保存在输出目录：`01-alice-home.png`、`02-alice-sent.png`、`03-bob-home.png`、`04-alice-received.png`、`05-bob-chat.png`、`06-bob-friends.png`。

## 说明

- 垫片不使用本地 SQLite：会话、消息、群成员每次直接从服务端读取，`save_msg`、`sync_messages` 等本地缓存命令为空操作。
- 只在桌面端打开的辅助窗口（加好友、设置等）会以新标签页打开；主窗口（登录、主界面）在当前标签页切换。
- 首次启动时 Vite 会预构建依赖并自动刷新一次页面，垫片会保留当前窗口标签，刷新后状态不受影响。
