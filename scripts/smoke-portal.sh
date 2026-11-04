#!/usr/bin/env bash
# Echo lookups, internal user provisioning, server monitor, datasource config, WeChat portal, gateway online
# services and the unacked message retry. Start the im module with --wx.mp.app-id=wxtest --wx.mp.token=smoketoken.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s%N)
read T U < <(register_user "portal$TS@example.com")
read TA UA < <(register_user "portaladmin$TS@example.com")
grant_admin "$UA"

echo "== echo"
api POST "/api/oauth/echo/dict/findByIds?ids=GLOBAL_SEX" "$T" | json "sorted(d.keys())[0].split('###')[0], '###' in sorted(d.keys())[0]"
USERID=$($MYSQL -e "select user_id from im_user where id=$U" 2>/dev/null)
api POST "/api/oauth/echo/user/findByIds?ids=$USERID" "$T" | json "len(d)"

echo "== admin only: normal user is refused"
for req in "GET /api/im/defServer" "POST /api/im/defDatasourceConfig/page" "POST /api/im/user/bindOAuth" "POST /api/im/user/register" "PUT /api/im/user/black" "POST /api/im/user/black/remove" "GET /api/im/user/black/page"; do
  api ${req% *} "${req#* }" "$T" '{}' | json "d['code']"
done

echo "== server monitor"
api GET /api/im/defServer "$TA" | json "d['code'], sorted(d['data'].keys()), d['data']['cpu']['cpuNum']>0, d['data']['mem']['total']>0, len(d['data']['sysFiles'])>0"

echo "== datasource config"
ID=$(api POST /api/im/defDatasourceConfig "$TA" "{\"name\":\"smoke$TS\",\"username\":\"root\",\"password\":\"robot_123456#\",\"url\":\"jdbc:mysql://127.0.0.1:3306/tio_chat\",\"driverClassName\":\"com.mysql.cj.jdbc.Driver\"}" | json "d['data']['id']")
api POST "/api/im/defDatasourceConfig/testConnect?id=$ID" "$TA" | json "d['code'], d['data']"
api PUT /api/im/defDatasourceConfig "$TA" "{\"id\":$ID,\"password\":\"wrong-password\"}" | json "d['code']"
api POST "/api/im/defDatasourceConfig/testConnect?id=$ID" "$TA" | json "d['code'], d['data']"
api DELETE /api/im/defDatasourceConfig "$TA" "[$ID]" | json "d['code'], d['data']"

echo "== internal user provisioning"
NEWUSER=$(date +%s%N | cut -c1-18)
api POST /api/im/user/register "$TA" "{\"userId\":$NEWUSER,\"account\":\"acc$TS\",\"name\":\"n$TS\",\"email\":\"reg$TS@example.com\",\"tenantId\":1,\"giteeId\":\"g$TS\"}" | json "d['code'], d['data']"
api POST /api/im/user/register "$TA" "{\"userId\":$NEWUSER,\"account\":\"acc$TS\",\"name\":\"n$TS\",\"email\":\"reg$TS@example.com\",\"tenantId\":1}" | json "d['code'], d['data']"
api POST /api/im/user/bindOAuth "$TA" "{\"email\":\"reg$TS@example.com\",\"githubId\":\"h$TS\"}" | json "d['code'], d['data']"
api POST /api/im/user/bindOAuth "$TA" "{\"email\":\"nobody$TS@example.com\",\"githubId\":\"h$TS\"}" | json "d['code'], d['data']"
$MYSQL -e "select gitee_id='g$TS', github_id='h$TS' from im_user where email='reg$TS@example.com'" 2>/dev/null

echo "== wechat portal"
WXTS=1700000000; NONCE=abc
SIG=$(python3 -c "import hashlib;print(hashlib.sha1(''.join(sorted(['smoketoken','$WXTS','$NONCE'])).encode()).hexdigest())")
curl -s "$HOST/api/im/wx/portal/public?signature=$SIG&timestamp=$WXTS&nonce=$NONCE&echostr=hello"; echo
curl -s "$HOST/api/im/wx/portal/public?signature=bad&timestamp=$WXTS&nonce=$NONCE&echostr=hello"; echo
Q="signature=$SIG&timestamp=$WXTS&nonce=$NONCE&openid=o$TS"
XML="<xml><ToUserName>gh</ToUserName><FromUserName>o$TS</FromUserName><CreateTime>1</CreateTime><MsgType>text</MsgType><Content>hi$TS</Content></xml>"
curl -s -XPOST "$HOST/api/im/wx/portal/public?$Q" -H 'Content-Type: text/xml' -d "$XML" | wc -c
$MYSQL -e "select msg='hi$TS' from im_wx_msg where open_id='o$TS'" 2>/dev/null
EVT="<xml><ToUserName>gh</ToUserName><FromUserName>o$TS</FromUserName><CreateTime>1</CreateTime><MsgType>event</MsgType><Event>subscribe</Event></xml>"
curl -s -XPOST "$HOST/api/im/wx/portal/public?$Q" -H 'Content-Type: text/xml' -d "$EVT" | grep -o '感谢关注'
EVT="<xml><ToUserName>gh</ToUserName><FromUserName>o$TS</FromUserName><CreateTime>1</CreateTime><MsgType>event</MsgType><Event>SCAN</Event><EventKey>123</EventKey></xml>"
curl -s -XPOST "$HOST/api/im/wx/portal/public?$Q" -H 'Content-Type: text/xml' -d "$EVT" | grep -o '请点击链接授权'

echo "== unacked messages are pushed again, acked ones are not"
ROOM=1
node PushAckClient.mjs "ws://localhost:18760/api/ws/ws?clientId=c1&Token=$T" 7 noack > /tmp/push_noack_$TS.log &
node PushAckClient.mjs "ws://localhost:18760/api/ws/ws?clientId=c1&Token=$TA" 7 ack > /tmp/push_ack_$TS.log &
sleep 2
api POST /api/im/chat/msg "$T" "{\"roomId\":$ROOM,\"msgType\":1,\"body\":{\"content\":\"retry $TS\"}}" | json "d['code']"
wait
echo "noack deliveries: $(cat /tmp/push_noack_$TS.log)"
echo "ack deliveries: $(cat /tmp/push_ack_$TS.log)"
rm -f /tmp/push_noack_$TS.log /tmp/push_ack_$TS.log

if [ "$GATEWAY" = 1 ]; then
  echo "== gateway online services"
  api GET /api/gateway/findOnlineServicePrefix "$T" | json "d['code'], d['data']['luohuo-im-server'], d['data']['luohuo-gateway-server']"
  api GET /api/gateway/findOnlineService "$T" | json "d['code'], sorted(o['label'] for o in d['data'])"
  curl -s "$HOST/api/gateway/findOnlineService" | json "d['code']"
  echo "== Connection: close (sent by reverse proxies) does not cut responses of unknown length"
  curl -s -m 6 -H 'Connection: close' "$HOST/api/oauth/anyTenant/captcha?key=k1" | json "d['code']"
  curl -s -m 8 -o /dev/null -H 'Connection: close' -w 'stream exit=%{exitcode} has body=' -XPOST "$HOST/api/ai/write/generate-stream" -H 'Content-Type: application/json' -H "token: $TA" -d '{"type":1,"prompt":"p","format":1,"tone":1,"language":1,"length":1}'
  curl -s -m 8 -H 'Connection: close' -XPOST "$HOST/api/ai/write/generate-stream" -H 'Content-Type: application/json' -H "token: $TA" -d '{"type":1,"prompt":"p","format":1,"tone":1,"language":1,"length":1}' | grep -c '^data:' | awk '{print ($1>0)}'
fi

echo "== like badge at 10 likes, worn automatically"
read TB UB < <(register_user "badge$TS@example.com")
MID=$(api POST /api/im/chat/msg "$TB" "{\"roomId\":1,\"msgType\":1,\"body\":{\"content\":\"badge $TS\"}}" | json "d['data']['message']['id']")
for i in 1 2 3 4 5 6 7 8 9; do
  $MYSQL -e "insert into im_message_mark(id,msg_id,uid,type,status,create_by,tenant_id) values ($(date +%s%N | cut -c1-18)$i,$MID,$i,1,0,1,1)" 2>/dev/null
done
echo "before: $($MYSQL -e "select count(*) from im_user_backpack where uid=$UB and item_id=2" 2>/dev/null)"
api PUT /api/im/chat/msg/mark "$T" "{\"msgId\":$MID,\"markType\":1,\"actType\":1}" | json "d['code']"
echo "after: $($MYSQL -e "select count(*) from im_user_backpack where uid=$UB and item_id=2" 2>/dev/null) worn: $($MYSQL -e "select item_id from im_user where id=$UB" 2>/dev/null)"

echo "== blacklisting a user invalidates messages, notifies and logs out"
node PushAckClient.mjs "ws://localhost:18760/api/ws/ws?clientId=c1&Token=$TB" 4 frames > /tmp/black_$TS.log &
sleep 2
api PUT /api/im/user/black "$TA" "{\"type\":2,\"target\":\"$UB\",\"deadline\":0}" | json "d['code']"
wait
cat /tmp/black_$TS.log; rm -f /tmp/black_$TS.log
echo "messages left: $($MYSQL -e "select count(*) from im_message where from_uid=$UB and status=0" 2>/dev/null)"
api POST /api/oauth/check "$TB" '{}' | json "d['code']"
