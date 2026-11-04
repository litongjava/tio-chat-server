#!/usr/bin/env bash
# Password lock, admin only message interfaces and templates, Groovy scripts of interfaces and templates.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s%N | cut -c1-16)
E="lock$TS@example.com"
read T U < <(register_user "$E"); read TA UA < <(register_user "secadmin$TS@example.com"); grant_admin "$UA"
pw() { # pw <password>: login attempt, prints code and message
  curl -s -XPOST "$HOST/api/oauth/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" \
    -d "{\"account\":\"$E\",\"password\":\"$1\",\"systemType\":2,\"deviceType\":\"PC\",\"clientId\":\"c1\",\"grantType\":\"PASSWORD\"}" | json "d['code'], d['msg'][:30]"
}
echo "== password lock"
pw wrong
for i in 2 3 4 5 6 7 8 9 10; do pw wrong >/dev/null; done
echo "right password while locked:"; pw 123456
$MYSQL -e "update def_user set password_error_last_time=now() - interval 16 minute where email='$E'" 2>/dev/null
echo "right password after the lock expired:"; pw 123456
$MYSQL -e "select password_error_num from def_user where email='$E'" 2>/dev/null

# logging in again replaced the session of the first token
read T U < <(login_user "$E")
echo "== scripts and templates are admin only"
for req in "POST /api/base/defInterface" "POST /api/base/defInterface/page" "POST /api/base/defMsgTemplate" "PUT /api/base/defInterfaceProperty"; do
  api ${req% *} "${req#* }" "$T" '{}' | json "d['code'], d['msg']"
done

echo "== the base admin APIs are admin only"
api PUT /api/base/defUser "$T" "{\"id\":\"$UA\",\"nickName\":\"x\"}" | json "d['code']"
api POST /api/base/defUser/page "$T" '{"size":1}' | json "d['code']"
api POST /api/base/baseRole "$T" '{"code":"x","name":"x"}' | json "d['code']"
api POST /api/base/baseEmployee/employeeRole "$T" '{}' | json "d['code']"
api POST /api/base/defUser/page "$TA" '{"size":1}' | json "d['code']"
echo "an ordinary user keeps the anyone APIs:"
api POST /api/base/anyone/extendNotice/myNotice "$T" '{"size":1}' | json "d['code']"
api POST /api/base/baseLoginLog/anyone/page "$T" '{"size":1}' | json "d['code']"

echo "== groovy interface and template script"
MYSQLI=${MYSQL/docker exec/docker exec -i}
IID=$(date +%s%N | cut -c1-18)
SCRIPT='import nexus.io.tio.chat.service.base.msg.*
class SmokeStrategy implements MsgStrategy { MsgResult exec(MsgParam p) { MsgResult.of(true, "groovy:" + p.phoneNumbers()) } }'
python3 - "$IID" "$TS" "$SCRIPT" <<'PY' > "/tmp/sec_$TS.sql"
import sys
i,t,s=sys.argv[1:4]
s=s.replace("\\","\\\\").replace("'","''")
print(f"insert into def_interface(id,code,name,exec_mode,script,state,create_by,create_time,update_time,tenant_id) values ({i},'G{t}','g','02','{s}',1,1,now(),now(),1);")
print(f"insert into def_msg_template(id,interface_id,type,code,name,state,title,content,remind_mode,create_by,create_time,update_time,tenant_id) values ({i}1,{i},'03','G{t}','g',1,'t','c','01',1,now(),now(),1);")
PY
$MYSQLI < "/tmp/sec_$TS.sql" 2>/dev/null; rm -f "/tmp/sec_$TS.sql"
api POST /api/base/extendMsg/sendByTemplate "$TA" "{\"code\":\"G$TS\",\"recipientList\":[{\"recipient\":\"x1\"}]}" | json "d['code']"
sleep 2
$MYSQL -e "select g.status, g.result from extend_interface_logging g join extend_interface_log l on l.id=g.log_id where l.interface_id=$IID" 2>/dev/null
NID=$((IID + 7))
$MYSQL -e "insert into def_interface(id,code,name,exec_mode,impl_class,state,create_by,create_time,update_time,tenant_id) values ($NID,'GN$TS','n','01','noticeMsgStrategyImpl',1,1,now(),now(),1)" 2>/dev/null
$MYSQL -e "insert into def_msg_template(id,interface_id,type,code,name,state,title,content,script,remind_mode,auto_read,create_by,create_time,update_time,tenant_id) values (${NID}1,$NID,'04','TS$TS','g',1,'title','hello \${upper}','[upper: name.toUpperCase()]','02',1,1,now(),now(),1)" 2>/dev/null
api POST /api/base/extendMsg/sendByTemplate "$TA" "{\"code\":\"TS$TS\",\"recipientList\":[{\"recipient\":\"$U\"}],\"paramList\":[{\"key\":\"name\",\"value\":\"tio\"}]}" | json "d['code']"
sleep 2
$MYSQL -e "select content from extend_notice where recipient_id=$U order by id desc limit 1" 2>/dev/null
echo "== a script that does not compile fails that send only"
BID=$((IID + 9))
$MYSQL -e "insert into def_interface(id,code,name,exec_mode,script,state,create_by,create_time,update_time,tenant_id) values ($BID,'GB$TS','b','02','class {',1,1,now(),now(),1)" 2>/dev/null
$MYSQL -e "insert into def_msg_template(id,interface_id,type,code,name,state,title,content,remind_mode,create_by,create_time,update_time,tenant_id) values (${BID}1,$BID,'03','GB$TS','g',1,'t','c','01',1,now(),now(),1)" 2>/dev/null
api POST /api/base/extendMsg/sendByTemplate "$TA" "{\"code\":\"GB$TS\",\"recipientList\":[{\"recipient\":\"x1\"}]}" | json "d['code']"
sleep 2
$MYSQL -e "select status from extend_msg where template_code='GB$TS'" 2>/dev/null
echo "== small endpoints"
# the fallback and doc pages belong to the gateway
if [ "$GATEWAY" = 1 ]; then
  curl -s "$HOST/api/fallback" | json "d['code'], d['msg']"
  curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "$HOST/api/gate/doc.html"
  curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' "$HOST/api/"
  curl -s "$HOST/api/doc.html" | grep -c '/api/im'
fi
curl -s "$HOST/api/base/anyone/base/test" -H "Token: $T" | json "d['code'], d['msg']"
