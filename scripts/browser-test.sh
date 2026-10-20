#!/usr/bin/env bash
# Browser test through deepseek-browser-use (POST /playwright/command on :10049):
# two users log in to the test console in separate tabs and chat in real time.
cd "$(dirname "$0")"; source ./lib.sh; set -e
DSB=${DSB:-http://localhost:10049/playwright/command}
ASSETS=$(cd .. && pwd)/docs/assets
CONSOLE=${CONSOLE:-http://localhost:18760/console/index.html}
cmd() { # cmd <taskId> <method> <params-json>
  curl -s --compressed -XPOST "$DSB" -H 'Content-Type: application/json' -d "{\"id\":$1,\"method\":\"$2\",\"params\":$3}" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print(sys.argv[1], d.get('ok'), (d.get('msg') or '')[:120])" "$2"
}
TS=$(date +%s)
EA="alice$TS@example.com"; EB="bob$TS@example.com"
read TA UA < <(register_user "$EA")
read TB UB < <(register_user "$EB")
api POST /api/im/room/apply/apply "$TA" "{\"targetUid\":$UB,\"msg\":\"hi\"}" >/dev/null
APPLY=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$TB" | json "d['data']['list'][0]['applyId']")
api POST /api/im/room/apply/handler/apply "$TB" "{\"applyId\":$APPLY,\"state\":1}" >/dev/null
ROOM=$(api GET "/api/im/chat/contact/detail/friend?id=$UB&roomType=2" "$TA" | json "d['data']['roomId']")
echo "alice=$UA bob=$UB room=$ROOM"

A=3001; B=3002
cmd $A start '{"headless":true}'
cmd $B start '{"headless":true}'
for T in $A $B; do cmd $T set_viewport '{"width":1200,"height":760}'; cmd $T go_to_url "{\"url\":\"$CONSOLE\"}"; done
cmd $A screenshot "{\"path\":\"$ASSETS/01-console-login.png\"}"

cmd $A input_text_by_selector "{\"selector\":\"#account\",\"text\":\"$EA\"}"
cmd $A input_text_by_selector '{"selector":"#password","text":"123456"}'
cmd $A click_element_by_selector '{"selector":"#loginBtn"}'
cmd $A wait_for_text '{"text":"WebSocket 已连接","timeoutMs":10000}'
cmd $B input_text_by_selector "{\"selector\":\"#account\",\"text\":\"$EB\"}"
cmd $B input_text_by_selector '{"selector":"#password","text":"123456"}'
cmd $B click_element_by_selector '{"selector":"#loginBtn"}'
cmd $B wait_for_text '{"text":"WebSocket 已连接","timeoutMs":10000}'
cmd $A bring_to_front '{}'
cmd $A screenshot "{\"path\":\"$ASSETS/02-alice-contacts.png\",\"timeoutMs\":30000}"

cmd $A click_element_by_selector "{\"selector\":\".contact[data-room='$ROOM']\"}"
cmd $B click_element_by_selector "{\"selector\":\".contact[data-room='$ROOM']\"}"
cmd $A wait_for_text '{"text":"我们已经成为好友了","timeoutMs":10000}'
cmd $A input_text_by_selector '{"selector":"#input","text":"你好 Bob，这条消息来自浏览器测试"}'
cmd $A click_element_by_selector '{"selector":"#sendBtn"}'
cmd $B wait_for_text '{"text":"这条消息来自浏览器测试","timeoutMs":10000}'
cmd $B input_text_by_selector '{"selector":"#input","text":"收到，WebSocket 实时推送正常"}'
cmd $B click_element_by_selector '{"selector":"#sendBtn"}'
cmd $A wait_for_text '{"text":"WebSocket 实时推送正常","timeoutMs":10000}'
sleep 1
cmd $A bring_to_front '{}'
cmd $A screenshot "{\"path\":\"$ASSETS/03-alice-chat.png\",\"timeoutMs\":30000}"
cmd $B bring_to_front '{}'
cmd $B screenshot "{\"path\":\"$ASSETS/04-bob-chat.png\",\"timeoutMs\":30000}"
cmd $A close '{}'
cmd $B close '{}'
ls -la "$ASSETS"
