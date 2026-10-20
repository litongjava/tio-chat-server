#!/usr/bin/env bash
# Feed, targets, emoji, privacy, user state and admin stats.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s)
read TA UA < <(register_user "fa$TS@example.com")
echo "A=$UA"
echo "== push feed"; api POST /api/im/feed/pushFeed "$TA" '{"content":"hello feed","mediaType":1,"permission":"open","images":["https://example.com/a.png"]}' | json "d['code'], d['data']"
L=$(api POST /api/im/feed/list "$TA" '{"pageSize":5}'); echo "$L" | json "d['code'], d['data']['list'][0]['content'], d['data']['list'][0]['urls']"
FID=$(echo "$L" | json "d['data']['list'][0]['id']")
echo "== like"; api POST /api/im/feed/like/toggle "$TA" "{\"feedId\":$FID,\"actType\":1}" | json "d['code']"
echo "== comment"; api POST /api/im/feed/comment/add "$TA" "{\"feedId\":$FID,\"content\":\"nice\"}" | json "d['code']"
echo "== detail"; api GET "/api/im/feed/detail?feedId=$FID" "$TA" | json "d['code'], d['data']['likeCount'], d['data']['hasLiked'], d['data']['commentCount']"
echo "== target"; api POST /api/im/target/add "$TA" '{"name":"family","icon":"x"}' | json "d['code'], d['data']"
echo "== emoji"; api POST /api/im/user/emoji "$TA" '{"expressionUrl":"https://example.com/e.gif"}' | json "d['code'], 'id' in d['data']"
api GET /api/im/user/emoji/list "$TA" | json "d['code'], len(d['data'])"
echo "== privacy"; api GET "/api/im/privacy?uid=$UA" "$TA" | json "d['code'], d['data']['allowTempSession']"
api POST "/api/im/privacy?uid=$UA" "$TA" '{"allowTempSession":false}' | json "d['code'], d['data']"
echo "== states"; api GET /api/im/user/state/list "$TA" | json "d['code'], len(d['data'])"
echo "== admin stats"; api GET /api/im/admin/stats/home "$TA" | json "d['code'], d['data']['totalGroup'], d['data']['monthlyLogin3PlusUserCount']"
api GET "/api/im/admin/stats/login-rank?rangeDays=30&limit=3" "$TA" | json "d['code'], len(d['data'])"
echo "== badges"; api GET /api/im/user/badges "$TA" | json "d['code'], [(b['id'], b['obtain']) for b in d['data']]"
echo "== userInfo"; api GET /api/im/user/userInfo "$TA" | json "d['code'], d['data']['name'], d['data']['modifyNameChance']"
