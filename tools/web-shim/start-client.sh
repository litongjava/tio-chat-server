#!/usr/bin/env bash
# Run the desktop client's frontend in a browser against tio-chat-server.
# Usage: CLIENT_DIR=/path/to/client tools/web-shim/start-client.sh
# Env: WEB_PORT (default 6130), API_TARGET (default http://127.0.0.1:18760), CLIENT_LANG (default zh-CN)
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
CLIENT_DIR=${CLIENT_DIR:?set CLIENT_DIR to the client source directory}
mkdir -p "$CLIENT_DIR/public/__shim"
{
  printf 'window.__SHIM_IM_URLS__ = '
  cat "$HERE/im-urls.json"
  printf ';\nwindow.__SHIM_LANG__ = "%s";\n' "${CLIENT_LANG:-zh-CN}"
  cat "$HERE/tauri-shim.js"
} > "$CLIENT_DIR/public/__shim/tauri-shim.js"
cp "$HERE/vite.web.config.ts" "$CLIENT_DIR/vite.web.config.ts"
cd "$CLIENT_DIR"
export TAURI_ENV_PLATFORM=linux
exec pnpm exec vite --config vite.web.config.ts
