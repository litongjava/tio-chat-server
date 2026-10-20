#!/usr/bin/env bash
# Two users chat in the default group and in their bot friend room.
cd "$(dirname "$0")"; source ./lib.sh; set -e
J=${JAVA:-/usr/java/jdk-21.0.6/bin/java}
TS=$(date +%s)
read TA UA < <(register_user "chata$TS@example.com")
read TB UB < <(register_user "chatb$TS@example.com")
echo "A=$UA B=$UB"
echo "== A connects websocket"
$J WsClient.java "ws://localhost:18760/api/ws/ws?clientId=c1&Token=$TA" 8 > /tmp/ws_a_$TS.log &
sleep 3
echo "== B sends text to group room 1"
SEND=$(api POST /api/im/chat/msg "$TB" '{"roomId":1,"msgType":1,"body":{"content":"hello https://example.com"}}')
echo "$SEND" | json "d['code'], d['data']['message']['type'], d['data']['message']['body']['content'], d['data']['message']['body']['urlContentMap']"
MID=$(echo "$SEND" | json "d['data']['message']['id']")
echo "== A marks like"; api PUT /api/im/chat/msg/mark "$TA" "{\"msgId\":$MID,\"markType\":1,\"actType\":1}" | json "d['code']"
echo "== A pages room 1"; api GET "/api/im/chat/msg/page?roomId=1&pageSize=3" "$TA" | json "d['code'], d['data']['isLast'], [m['message']['body'] for m in d['data']['list']][-1], d['data']['list'][-1]['message']['messageMarks'].get('1')"
echo "== B recalls"; api PUT /api/im/chat/msg/recall "$TB" "{\"msgId\":$MID,\"roomId\":1}" | json "d['code'], d['msg']"
echo "== A reads"; api PUT /api/im/chat/msg/read "$TA" '{"roomId":1}' | json "d['code']"
echo "== B read info"; api GET "/api/im/chat/msg/read?msgIds=$MID" "$TB" | json "d['code'], d['data']"
echo "== A sync list"; api POST /api/im/chat/msg/list "$TA" '{"async":true}' | json "d['code'], len(d['data'])"
sleep 6
echo "== A websocket frames"; grep -o '"type":"[a-zA-Z]*"' /tmp/ws_a_$TS.log | sort | uniq -c
