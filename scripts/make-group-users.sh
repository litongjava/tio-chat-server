#!/usr/bin/env bash
# Register alice, bob and carol; alice becomes friends with bob and carol (password 123456).
# Prints: emailA uidA emailB uidB emailC uidC
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s)
befriend() { # befriend <tokenFrom> <tokenTo> <uidTo>
  api POST /api/im/room/apply/apply "$1" "{\"targetUid\":$3,\"msg\":\"hi\"}" >/dev/null
  local apply
  apply=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$2" | json "d['data']['list'][0]['applyId']")
  api POST /api/im/room/apply/handler/apply "$2" "{\"applyId\":$apply,\"state\":1}" >/dev/null
}
EA=alice$TS@example.com; EB=bob$TS@example.com; EC=carol$TS@example.com
read TA UA < <(register_user "$EA")
read TB UB < <(register_user "$EB")
read TC UC < <(register_user "$EC")
befriend "$TA" "$TB" "$UB"
befriend "$TA" "$TC" "$UC"
echo "$EA $UA $EB $UB $EC $UC"
