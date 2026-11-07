#!/usr/bin/env bash
# Serve the desktop client's browser build (vite --mode web) against this server.
# Usage: CLIENT_DIR=/path/to/client tools/web-client/start-client.sh
# Env: WEB_PORT (default 6131), API_TARGET (default http://127.0.0.1:18760)
set -e
CLIENT_DIR=${CLIENT_DIR:?set CLIENT_DIR to the client source directory}
cd "$CLIENT_DIR"
# this server serves its API under /api, so the client's dev proxy keeps the prefix
export WEB_API_TARGET=${API_TARGET:-http://127.0.0.1:18760} WEB_API_STRIP_PREFIX=false WEB_PORT=${WEB_PORT:-6131}
# use the client's own vite when dependencies are installed, so a broken global pnpm does not matter
if [ -x node_modules/.bin/vite ]; then
  exec node_modules/.bin/vite --mode web
fi
exec pnpm exec vite --mode web
