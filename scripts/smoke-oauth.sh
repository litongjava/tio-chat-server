#!/usr/bin/env bash
# Smoke test for oauth endpoints. Requires the server on :18760 and redis container "tio-chat-redis".
set -e
BASE=${BASE:-http://localhost:18760/api/oauth}
EMAIL=${EMAIL:-smoke$(date +%s)@example.com}
AUTH=$(printf 'luohuo_web_pro:luohuo_web_pro_secret' | base64)
REDIS="docker exec tio-chat-redis redis-cli"
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1"; }

echo "== captcha"; curl -s "$BASE/anyTenant/captcha?key=k1" | json "d['code'], d['data']['uuid']"
echo "== send email code"; curl -s -XPOST "$BASE/anyTenant/sendEmailCode" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"templateCode\":\"REGISTER_EMAIL\"}" | json "d['code'], d['data']"
CODE=$($REDIS get "luohuo:captcha:REGISTER_EMAIL:$EMAIL")
echo "== register $EMAIL"; curl -s -XPOST "$BASE/anyTenant/registerByEmail" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"code\":\"$CODE\",\"key\":\"REGISTER_EMAIL\",\"password\":\"123456\",\"confirmPassword\":\"123456\",\"nickName\":\"smoke\",\"systemType\":2}" | json "d['code'], d['msg'], d['data']"
echo "== login"; LOGIN=$(curl -s -XPOST "$BASE/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" \
  -d "{\"account\":\"$EMAIL\",\"password\":\"123456\",\"grantType\":\"PASSWORD\",\"systemType\":2,\"deviceType\":\"PC\",\"clientId\":\"c1\"}")
echo "$LOGIN" | json "d['code'], d['msg'], d['data'] and sorted(d['data'].keys())"
TOKEN=$(echo "$LOGIN" | json "d['data']['token']"); RT=$(echo "$LOGIN" | json "d['data']['refreshToken']")
echo "== check"; curl -s -XPOST "$BASE/check" -H "token: $TOKEN" | json "d['code'], d['data']"
echo "== userInfo"; curl -s "$BASE/anyone/getUserInfo" -H "token: $TOKEN" | json "d['code'], d['data']['username'], d['data']['uid']"
echo "== refresh"; NEW=$(curl -s -XPOST "$BASE/anyTenant/refresh" -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$RT\"}")
echo "$NEW" | json "d['code'], d['data']['token'] != '$TOKEN'"
NT=$(echo "$NEW" | json "d['data']['token']")
echo "== old token after refresh"; curl -s -XPOST "$BASE/check" -H "token: $TOKEN" | json "d['code']"
echo "== qr generate"; QR=$(curl -s "$BASE/anyTenant/qr/generate")
QID=$(echo "$QR" | json "d['data']['qrId']"); DH=$(echo "$QR" | json "d['data']['deviceHash']")
curl -s -XPOST "$BASE/qrcode/scan" -H 'Content-Type: application/json' -H "token: $NT" -d "{\"qrId\":\"$QID\"}" | json "d['code'], d['msg']"
curl -s -XPOST "$BASE/qrcode/confirm" -H 'Content-Type: application/json' -H "token: $NT" -d "{\"qrId\":\"$QID\"}" | json "d['code'], d['msg']"
curl -s "$BASE/anyTenant/qr/status/query?qrId=$QID&deviceHash=$DH&deviceType=MOBILE&clientId=m1" | json "d['code'], d['data']['status'], bool(d['data']['data'])"
echo "== logout"; curl -s -XPOST "$BASE/anyUser/logout" -H "token: $NT" | json "d['code'], d['data']"
curl -s -XPOST "$BASE/check" -H "token: $NT" | json "d['code'], d['msg']"
