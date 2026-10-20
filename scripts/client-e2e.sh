#!/usr/bin/env bash
# End-to-end test of the desktop client's frontend (served by tools/web-shim/start-client.sh)
# against tio-chat-server, driven by dsb in two Chrome tasks.
# The two users use different origins (127.0.0.1 / localhost) so their localStorage stays separate.
# Usage: scripts/client-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
WEB_PORT=${WEB_PORT:-6130}
A_URL=http://127.0.0.1:$WEB_PORT
B_URL=http://localhost:$WEB_PORT
A_ID=${A_ID:-4101}; B_ID=${B_ID:-4102}

read EA UA EB UB < <(./make-friends.sh | tail -1)
echo "alice=$EA bob=$EB"

dsb_a() { dsb --id "$A_ID" --summary "$@" 2>&1 | tail -1; }
dsb_b() { dsb --id "$B_ID" --summary "$@" 2>&1 | tail -1; }
shot() { # reset any scroll left by clicks, then capture
  "$1" js "(() => { document.scrollingElement.scrollTop = 0; return 1 })()" >/dev/null
  "$1" run screenshot -p "path=$OUT/$2"
}

login() { # login <dsb fn> <origin> <email> <text expected after login>
  local d=$1 origin=$2 email=$3 expect=$4
  $d start --browser chrome --headless
  $d run set_viewport -p width=1100 -p height=760
  $d run go_to_url -p "url=$origin/login"
  # start from a clean client state on this origin (the Chrome profile is shared)
  $d js "(() => { localStorage.clear(); sessionStorage.clear(); window.name = ''; return 1 })()"
  $d run go_to_url -p "url=$origin/login"
  $d run wait_for_element -p selector=.driver-popover-next-btn -p timeoutMs=20000
  for i in 1 2 3 4; do
    $d run click_element_by_selector -p selector=.driver-popover-next-btn
    sleep 0.6
  done
  $d run input_text_by_selector -p "selector=input[type=text]" -p "text=$email"
  $d run input_text_by_selector -p "selector=input[type=password]" -p text=123456
  $d run click_element_by_text -p text=登录 -p exact=true
  $d run wait_for_function -p "expression=document.body.innerText.includes('$expect') && !document.body.innerText.includes('加载')" -p timeoutSeconds=30
  sleep 1
}

login dsb_a "$A_URL" "$EA" "${EB%@*}"
shot dsb_a 01-alice-home.png
dsb_a run click_element_by_text -p "text=${EB%@*}"
dsb_a run wait_for_element -p selector=#message-input -p timeoutMs=10000
dsb_a run click_element_by_selector -p selector=#message-input
dsb_a run input_text_by_selector -p selector=#message-input -p "text=你好 Bob，这条消息来自 tio-chat-server 后端"
dsb_a run press_key -p key=Enter
sleep 2
shot dsb_a 02-alice-sent.png

login dsb_b "$B_URL" "$EB" "${EA%@*}"
shot dsb_b 03-bob-home.png
dsb_b run click_element_by_text -p "text=${EA%@*}"
dsb_b run wait_for_element -p selector=#message-input -p timeoutMs=10000
dsb_b run click_element_by_selector -p selector=#message-input
dsb_b run input_text_by_selector -p selector=#message-input -p "text=收到！WebSocket 实时推送正常"
dsb_b run press_key -p key=Enter
dsb_a run wait_for_text -p "text=WebSocket 实时推送正常" -p timeoutMs=10000
sleep 1
shot dsb_a 04-alice-received.png
shot dsb_b 05-bob-chat.png
dsb_b run click_element_by_selector -p 'selector=svg:has(> use[href="#avatar"])'
dsb_b run wait_for_url -p "url=**/friendsList" -p timeoutMs=10000
shot dsb_b 06-bob-friends.png

dsb_a close
dsb_b close
ls -la "$OUT"
