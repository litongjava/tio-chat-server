#!/usr/bin/env bash
# Video call signaling between two friends over WebSocket.
cd "$(dirname "$0")"; source ./lib.sh; set -e
J=${JAVA:-/usr/java/jdk-21.0.6/bin/java}
TS=$(date +%s)
read TA UA < <(register_user "ca$TS@example.com")
read TB UB < <(register_user "cb$TS@example.com")
api POST /api/im/room/apply/apply "$TA" "{\"targetUid\":$UB,\"msg\":\"hi\"}" >/dev/null
APPLY=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$TB" | json "d['data']['list'][0]['applyId']")
api POST /api/im/room/apply/handler/apply "$TB" "{\"applyId\":$APPLY,\"state\":1}" >/dev/null
RID=$(api GET "/api/im/chat/contact/detail/friend?id=$UB&roomType=2" "$TA" | json "d['data']['roomId']")
echo "A=$UA B=$UB room=$RID"
$J WsClient.java "ws://localhost:18760/api/ws/ws?clientId=c1&Token=$TB" 12 'sleep:3000' \
  "{\"type\":6,\"data\":{\"callerUid\":$UA,\"roomId\":$RID,\"accepted\":1}}" > /tmp/ws_cb_$TS.log &
sleep 1
$J WsClient.java "ws://localhost:18760/api/ws/ws?clientId=c1&Token=$TA" 12 'sleep:1000' \
  "{\"type\":5,\"data\":{\"targetUid\":$UB,\"roomId\":$RID,\"isVideo\":true}}" 'sleep:3000' \
  "{\"type\":14,\"data\":{\"roomId\":$RID,\"signal\":\"sdp\",\"signalType\":\"offer\"}}" 'sleep:1000' \
  "{\"type\":6,\"data\":{\"callerUid\":$UB,\"roomId\":$RID,\"accepted\":2}}" > /tmp/ws_ca_$TS.log &
sleep 13
echo "== A frames"; grep -o '"type":"[a-zA-Z_]*"' /tmp/ws_ca_$TS.log | sort | uniq -c
echo "== B frames"; grep -o '"type":"[a-zA-Z_]*"' /tmp/ws_cb_$TS.log | sort | uniq -c
echo "== call record"; api GET "/api/im/chat/msg/page?roomId=$RID&pageSize=1" "$TA" | json "d['data']['list'][0]['message']['type'], d['data']['list'][0]['message']['body']"
