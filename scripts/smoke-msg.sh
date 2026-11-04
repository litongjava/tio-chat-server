#!/usr/bin/env bash
# Message center: template -> interface -> strategy, delivery through each channel, interface logs, scheduled sends.
# Mail and Chuanglan SMS go to MsgChannelMock.py. The cloud SDKs call the real providers with fake keys, so a
# rejected signature proves the request reached them. Start the base service with --chat.msg.schedule.interval-seconds=2.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s%N | cut -c1-16)
OUT=$(mktemp); python3 -I -W ignore MsgChannelMock.py "$OUT" & MOCK=$!
trap 'kill $MOCK 2>/dev/null; rm -f "$OUT"' EXIT
sleep 1
read T U < <(register_user "msg$TS@example.com"); grant_admin "$U"

iface() { # iface <code> <impl> key=value...
  local code=$1 impl=$2 id=$(date +%s%N | cut -c1-18); shift 2
  # a property that cannot be saved must fail the test, not disappear
  $MYSQL -e "insert into def_interface(id,code,name,exec_mode,impl_class,state,create_by,create_time,update_time,tenant_id) values ($id,'$code$TS','smoke','01','$impl',1,1,now(),now(),1)" 2>/dev/null
  for kv in "$@"; do
    $MYSQL -e "insert into def_interface_property(id,interface_id,name,key_,value,sort_value,create_by,create_time,update_time,tenant_id) values ($(date +%s%N | cut -c1-18),$id,'p','${kv%%=*}','${kv#*=}',0,1,now(),now(),1)"
  done
  echo "$id"
}
tpl() { # tpl <code> <type> <interface> <title> <content>
  $MYSQL -e "insert into def_msg_template(id,interface_id,type,code,name,state,template_code,sign,title,content,remind_mode,auto_read,create_by,create_time,update_time,tenant_id) values ($(date +%s%N | cut -c1-18),$3,'$2','$1$TS','smoke',1,'SMS_1','sign','$4','$5','02',1,1,now(),now(),1)" 2>/dev/null
}
send() { # send <code> <recipient> -> code
  api POST /api/base/extendMsg/sendByTemplate "$T" "{\"code\":\"$1$TS\",\"recipientList\":[{\"recipient\":\"$2\"}],\"paramList\":[{\"key\":\"code\",\"value\":\"8866\"}]}" | json "d['code']"
}
status() { # status <code>: wait for the send to finish, print status
  for _ in $(seq 1 40); do
    s=$($MYSQL -e "select status from extend_msg where template_code='$1$TS' order by id desc limit 1" 2>/dev/null)
    [ "$s" != WAITING ] && break; sleep 0.5
  done
  echo "$1 status=$s"
}
logging() { # logging <interface id>: status and error of the last call
  $MYSQL -e "select g.status, left(coalesce(g.error_msg,g.result),90) from extend_interface_logging g join extend_interface_log l on l.id=g.log_id where l.interface_id=$1 order by g.id desc limit 1" 2>/dev/null
}

echo "== notice"
I=$(iface NOTICE noticeMsgStrategyImpl); tpl N 04 "$I" 'hi' 'code ${code}'
send N "$U"; status N
$MYSQL -e "select title, content from extend_notice where recipient_id=$U order by id desc limit 1" 2>/dev/null

echo "== mail over smtp"
I=$(iface MAIL tencentMailMsgStrategyImpl hostName=127.0.0.1 smtpPort=18025 fromEmail=no-reply@example.com fromName=HuLa)
tpl M 02 "$I" '验证码 ${code}' '<b>${code}</b>'
send M "to$TS@example.com"; status M; sleep 1; grep -c "验证码 8866" "$OUT"

echo "== chuanglan sms"
I=$(iface CL clSmsMsgStrategyImpl account=a password=p endPoint=http://127.0.0.1:18026/send variableEndPoint=http://127.0.0.1:18026/variable)
tpl C 01 "$I" '' '您的验证码是 ${code}'
send C 13800000000; status C; grep -c '"phone": "13800000000", "params": null, "msg": "【sign】您的验证码是 8866"' "$OUT"
I=$(iface CLV clSmsMsgStrategyImpl account=a password=p variable=true variableEndPoint=http://127.0.0.1:18026/variable)
tpl V 01 "$I" '' '您的验证码是 ${code}'
send V 13800000001; status V; grep -c '"params": "13800000001,8866", "msg": "【sign】您的验证码是 {$var}"' "$OUT"

echo "== debug mode sends nothing"
I=$(iface DBG aliSmsMsgStrategyImpl debug=true); tpl D 01 "$I" '' 'x'
send D 13800000002; status D; logging "$I"

echo "== cloud sdks reach the providers and are rejected for the fake keys"
I=$(iface ALI aliSmsMsgStrategyImpl accessKeyId=fake accessKeySecret=fake); tpl A 01 "$I" '' 'x'
send A 13800000003; status A; logging "$I"
I=$(iface BD baiduSmsMsgStrategyImpl accessKeyId=fake secretKey=fake); tpl B 01 "$I" '' 'x'
send B 13800000004; status B; logging "$I"
I=$(iface TX tencentSmsMsgStrategyImpl secretId=fake secretKey=fake sdkAppId=1400000000); tpl X 01 "$I" '' 'x'
send X 13800000005; status X; logging "$I"

echo "== failures are recorded"
I=$(iface BAD noSuchStrategy); tpl Z 04 "$I" 't' 'c'
send Z "$U"; status Z; logging "$I"
api POST /api/base/extendMsg/sendByTemplate "$T" "{\"code\":\"none$TS\",\"recipientList\":[{\"recipient\":\"1\"}]}" | json "d['code'], d['msg']"

echo "== publish: draft, immediate and scheduled"
api POST /api/base/extendMsg/publish "$T" "{\"title\":\"d$TS\",\"content\":\"c\",\"draft\":true,\"recipientList\":[\"$U\"]}" | json "d['code']"
$MYSQL -e "select status from extend_msg where title='d$TS'" 2>/dev/null
api POST /api/base/extendMsg/publish "$T" "{\"title\":\"p$TS\",\"content\":\"c\",\"recipientList\":[\"$U\"]}" | json "d['code']"
$MYSQL -e "select m.status, count(n.id) from extend_msg m left join extend_notice n on n.msg_id=m.id where m.title='p$TS' group by m.id" 2>/dev/null
api POST /api/base/extendMsg/publish "$T" "{\"title\":\"soon$TS\",\"content\":\"c\",\"sendTime\":\"$(TZ=Asia/Shanghai date -d '+1 minute' '+%F %T')\",\"recipientList\":[\"$U\"]}" | json "d['code'], d['msg']"
api POST /api/base/extendMsg/publish "$T" "{\"title\":\"s$TS\",\"content\":\"c\",\"sendTime\":\"$(TZ=Asia/Shanghai date -d '+10 minutes' '+%F %T')\",\"recipientList\":[\"$U\"]}" | json "d['code']"
$MYSQL -e "select m.status, count(n.id) from extend_msg m left join extend_notice n on n.msg_id=m.id where m.title='s$TS' group by m.id" 2>/dev/null
$MYSQL -e "update extend_msg set send_time=now() - interval 1 minute where title='s$TS'" 2>/dev/null
for _ in $(seq 1 30); do [ "$($MYSQL -e "select status from extend_msg where title='s$TS'" 2>/dev/null)" = SUCCESS ] && break; sleep 1; done
$MYSQL -e "select m.status, count(n.id) from extend_msg m left join extend_notice n on n.msg_id=m.id where m.title='s$TS' group by m.id" 2>/dev/null
