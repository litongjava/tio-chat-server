#!/usr/bin/env bash
# Two ws and two im instances. Start the normal services first, this script adds and kills the second ones:
# - messages reach a client connected to either ws instance
# - a call whose two sides are on different ws instances
# - the timeout of a call runs after the ws instance that started the call died
# - an unacked message is pushed again after the im instance that sent it died
cd "$(dirname "$0")"; source ./lib.sh; set -e
JAVA=${JAVA:-java}; ROOT=$(cd .. && pwd); TS=$(date +%s%N | cut -c1-16); WORK=$(mktemp -d)
ARGS="--wx.mp.app-id=wxtest --wx.mp.token=smoketoken"
"$JAVA" -jar "$ROOT/tio-chat-ws/target/tio-chat-ws.jar" --server.port=19779 $ARGS > "$ROOT/logs/services/ws2.log" 2>&1 & WS2=$!
"$JAVA" -jar "$ROOT/tio-chat-im/target/tio-chat-im.jar" --server.port=18779 $ARGS > "$ROOT/logs/services/im2.log" 2>&1 & IM2=$!
trap 'kill -9 $WS2 $IM2 2>/dev/null; rm -rf "$WORK"' EXIT
for p in 19779 18779; do
  for _ in $(seq 1 60); do (echo > /dev/tcp/127.0.0.1/$p) 2>/dev/null && break; sleep 1; done
done
sleep 5
WS1URL="ws://localhost:19778/api/ws/ws"; WS2URL="ws://localhost:19779/api/ws/ws"
friends() { # friends <token a> <token b> <uid b> -> room id
  api POST /api/im/room/apply/apply "$1" "{\"targetUid\":$3,\"msg\":\"hi\"}" >/dev/null
  local apply=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$2" | json "d['data']['list'][0]['applyId']")
  api POST /api/im/room/apply/handler/apply "$2" "{\"applyId\":$apply,\"state\":1}" >/dev/null
  api GET "/api/im/chat/contact/detail/friend?id=$3&roomType=2" "$1" | json "d['data']['roomId']"
}
ws_script() { # ws_script <url> <seconds> <frame or sleep:ms>... : prints the type of every received frame
  node - "$@" <<'JS'
const [url, seconds, ...steps] = process.argv.slice(2);
const ws = new WebSocket(url);
ws.onmessage = (e) => console.log(JSON.parse(String(e.data)).type);
ws.onopen = async () => {
  for (const step of steps) {
    if (step.startsWith('sleep:')) { await new Promise((r) => setTimeout(r, Number(step.slice(6)))); } else { ws.send(step); }
  }
};
setTimeout(() => process.exit(0), Number(seconds) * 1000);
JS
}
read TA UA < <(register_user "haa$TS@example.com" PC ca); read TB UB < <(register_user "hab$TS@example.com" PC cb)
ROOM=$(friends "$TA" "$TB" "$UB")

echo "== message to a client on the second ws instance"
node PushAckClient.mjs "$WS2URL?clientId=cb&Token=$TB" 4 ack > "$WORK/push" & P=$!
sleep 2
api POST /api/im/chat/msg "$TA" "{\"roomId\":$ROOM,\"msgType\":1,\"body\":{\"content\":\"ha $TS\"}}" | json "d['code']"
wait $P; echo "deliveries: $(cat "$WORK/push")"

echo "== call with the two sides on different ws instances"
ws_script "$WS1URL?clientId=cb&Token=$TB" 11 'sleep:3000' "{\"type\":6,\"data\":{\"callerUid\":$UA,\"roomId\":$ROOM,\"accepted\":1}}" > "$WORK/b" & P1=$!
sleep 1
ws_script "$WS2URL?clientId=ca&Token=$TA" 10 'sleep:1000' "{\"type\":5,\"data\":{\"targetUid\":$UB,\"roomId\":$ROOM,\"isVideo\":true}}" 'sleep:3000' \
  "{\"type\":14,\"data\":{\"roomId\":$ROOM,\"signal\":\"sdp\",\"signalType\":\"offer\"}}" 'sleep:1000' \
  "{\"type\":6,\"data\":{\"callerUid\":$UB,\"roomId\":$ROOM,\"accepted\":2}}" > "$WORK/a" & P2=$!
wait $P1 $P2
echo "A: $(sort "$WORK/a" | uniq -c | tr -s ' ' | tr '\n' ';')"
echo "B: $(sort "$WORK/b" | uniq -c | tr -s ' ' | tr '\n' ';')"
record() { api GET "/api/im/chat/msg/page?roomId=$ROOM&pageSize=1" "$TA" | json "d['data']['list'][0]['message']['type'], d['data']['list'][0]['message']['body']"; }
echo "record: $(record)"

echo "== call timeout after the ws instance that started the call died"
node PushAckClient.mjs "$WS1URL?clientId=cb&Token=$TB" 45 frames > "$WORK/timeout_b" & P=$!
sleep 1
ws_script "$WS2URL?clientId=ca&Token=$TA" 40 "{\"type\":5,\"data\":{\"targetUid\":$UB,\"roomId\":$ROOM,\"isVideo\":false}}" > /dev/null &
BEFORE=$(api GET "/api/im/chat/msg/page?roomId=$ROOM&pageSize=1" "$TA" | json "d['data']['list'][0]['message']['id']")
sleep 3
kill -9 $WS2; WS2=
for _ in $(seq 1 40); do
  NOW=$(api GET "/api/im/chat/msg/page?roomId=$ROOM&pageSize=1" "$TA" | json "d['data']['list'][0]['message']['id']")
  [ "$NOW" != "$BEFORE" ] && break; sleep 1
done
[ "$NOW" != "$BEFORE" ] && echo "record written: $(record)" || echo "NO RECORD"
kill $P 2>/dev/null || true; wait $P 2>/dev/null || true
echo "B frames: $(sort "$WORK/timeout_b" | uniq -c | tr -s ' ' | tr '\n' ';')"

echo "== unacked message after the im instance that sent it died"
read TC UC < <(register_user "hac$TS@example.com" PC cc); read TD UD < <(register_user "had$TS@example.com" PC cd)
R2=$(friends "$TC" "$TD" "$UD")
node PushAckClient.mjs "$WS1URL?clientId=cd&Token=$TD" 25 noack > "$WORK/retry" & P=$!
sleep 2
curl -s -XPOST "http://localhost:18779/api/im/chat/msg" -H 'Content-Type: application/json' -H "token: $TC" \
  -d "{\"roomId\":$R2,\"msgType\":1,\"body\":{\"content\":\"retry $TS\"}}" | json "d['code']"
sleep 1
kill -9 $IM2; IM2=
wait $P; echo "deliveries: $(cat "$WORK/retry")"
