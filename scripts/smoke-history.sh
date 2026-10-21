#!/usr/bin/env bash
# Chat history search and file list: keyword, type, date range and paging, membership, recalled messages.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s)
read TA UA < <(register_user "hista$TS@example.com")
read TB UB < <(register_user "histb$TS@example.com")
send() { api POST /api/im/chat/msg "$1" "$2" | json "d['data']['message']['id']"; }
send "$TA" "{\"roomId\":1,\"msgType\":1,\"body\":{\"content\":\"needle$TS first\"}}" >/dev/null
send "$TB" "{\"roomId\":1,\"msgType\":1,\"body\":{\"content\":\"NEEDLE$TS second\"}}" >/dev/null
RECALL=$(send "$TB" "{\"roomId\":1,\"msgType\":1,\"body\":{\"content\":\"needle$TS recalled\"}}")
api PUT /api/im/chat/msg/recall "$TB" "{\"msgId\":$RECALL,\"roomId\":1}" >/dev/null
send "$TA" "{\"roomId\":1,\"msgType\":4,\"body\":{\"url\":\"http://127.0.0.1:9000/chat/chat/${TS}_plan.pdf\",\"size\":1234,\"fileName\":\"plan$TS.pdf\"}}" >/dev/null
send "$TB" "{\"roomId\":1,\"msgType\":3,\"body\":{\"url\":\"http://127.0.0.1:9000/chat/chat/${TS}_pic.png\",\"size\":99,\"width\":10,\"height\":10}}" >/dev/null
echo "== keyword (case-insensitive, recalled excluded)"
api GET "/api/im/chat/msg/search?roomId=1&keyword=needle$TS&pageSize=10" "$TA" | json "d['code'], d['data']['totalRecords'], [m['message']['body']['content'] for m in d['data']['list']]"
echo "== keyword matches file name"
api GET "/api/im/chat/msg/search?roomId=1&keyword=plan$TS" "$TA" | json "d['code'], [(m['message']['type'], m['message']['body']['fileName']) for m in d['data']['list']]"
echo "== image/video tab (types=3,6) with keyword on url"
api GET "/api/im/chat/msg/search?roomId=1&types=3,6&keyword=${TS}_pic" "$TA" | json "d['code'], [m['message']['type'] for m in d['data']['list']]"
echo "== date range: future window is empty, today has results"
NOW=$(($(date +%s) * 1000))
api GET "/api/im/chat/msg/search?roomId=1&keyword=needle$TS&startTime=$((NOW + 3600000))" "$TA" | json "d['code'], d['data']['totalRecords']"
api GET "/api/im/chat/msg/search?roomId=1&keyword=needle$TS&startTime=$((NOW - 3600000))&endTime=$((NOW + 60000))" "$TA" | json "d['code'], d['data']['totalRecords']"
echo "== paging: pageSize=1 page 2, ascending"
api GET "/api/im/chat/msg/search?roomId=1&keyword=needle$TS&pageSize=1&pageNo=2&sort=asc" "$TA" | json "d['code'], d['data']['isLast'], [m['message']['body']['content'] for m in d['data']['list']]"
echo "== a room the caller is not in"
read TC UC < <(register_user "histc$TS@example.com")
FRIEND_ROOM=$(api GET /api/im/chat/contact/list "$TA" | json "[c['roomId'] for c in d['data'] if str(c['roomId'])!='1'][0]")
api GET "/api/im/chat/msg/search?roomId=$FRIEND_ROOM&keyword=x" "$TC" | json "d['code'], d['msg']"
echo "== files: all rooms, by room, by sender, by keyword"
api GET "/api/im/chat/msg/files?keyword=plan$TS" "$TB" | json "d['code'], [(f['message']['body']['fileName'], f['fromUser']['name']) for f in d['data']['list']]"
api GET "/api/im/chat/msg/files?roomId=1&pageSize=5" "$TB" | json "d['code'], d['data']['list'][0]['message']['type'] in (4, 6)"
api GET "/api/im/chat/msg/files?senderUid=$UA&keyword=plan$TS" "$TB" | json "d['code'], len(d['data']['list'])"
api GET "/api/im/chat/msg/files?roomId=$FRIEND_ROOM" "$TC" | json "d['code'], d['msg']"
