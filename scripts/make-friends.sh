#!/usr/bin/env bash
# Register two users and make them friends. Prints: emailA uidA emailB uidB (password 123456)
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s)
EA=${1:-alice$TS@example.com}; EB=${2:-bob$TS@example.com}
read TA UA < <(register_user "$EA")
read TB UB < <(register_user "$EB")
api POST /api/im/room/apply/apply "$TA" "{\"targetUid\":$UB,\"msg\":\"hi\"}" >/dev/null
APPLY=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$TB" | json "d['data']['list'][0]['applyId']")
api POST /api/im/room/apply/handler/apply "$TB" "{\"applyId\":$APPLY,\"state\":1}" >/dev/null
echo "$EA $UA $EB $UB"
