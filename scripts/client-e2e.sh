#!/usr/bin/env bash
# End-to-end test of the desktop client's frontend (served by tools/web-shim/start-client.sh)
# against tio-chat-server, driven by dsb in two Chrome tasks.
# The two users use different origins (127.0.0.1 / localhost) so their localStorage stays separate.
# Usage: scripts/client-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./client-lib.sh
A_ID=${A_ID:-4101}; B_ID=${B_ID:-4102}
task dsb_a "$A_ID"
task dsb_b "$B_ID"

read EA UA EB UB < <(./make-friends.sh | tail -1)
echo "alice=$EA bob=$EB"

login dsb_a "$A_URL" "$EA" "${EB%@*}"
shot dsb_a 01-alice-home.png
open_chat dsb_a "${EB%@*}"
send dsb_a "你好 Bob，这条消息来自 tio-chat-server 后端"
sleep 2
shot dsb_a 02-alice-sent.png

login dsb_b "$B_URL" "$EB" "${EA%@*}"
shot dsb_b 03-bob-home.png
open_chat dsb_b "${EA%@*}"
send dsb_b "收到！WebSocket 实时推送正常"
dsb_a run wait_for_text -p "text=WebSocket 实时推送正常" -p timeoutMs=10000
sleep 1
shot dsb_a 04-alice-received.png
shot dsb_b 05-bob-chat.png
dsb_b run click_element_by_selector -p 'selector=svg:has(> use[href="#avatar"])'
dsb_b run wait_for_url -p "url=**/friendsList" -p timeoutMs=10000
shot dsb_b 06-bob-friends.png

finish dsb_a
finish dsb_b
ls -la "$OUT"
