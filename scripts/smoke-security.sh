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

echo "== anyone APIs only reach the data of the user"
read T2 U2 < <(register_user "other$TS@example.com")
api POST /api/base/anyone/defTenant "$T" "{\"name\":\"t$TS\"}" | json "'no contact:', d['code'], d['msg']"
TEN=$(api POST /api/base/anyone/defTenant "$T" "{\"name\":\"t$TS\",\"contactName\":\"c\",\"status\":0}")
TID=$(echo "$TEN" | json "d['data']['id']"); echo "$TEN" | json "'new tenant is waiting:', d['code'], d['data']['status']"
api POST /api/base/anyone/defTenant/page "$T" '{"size":5}' | json "'mine:', d['data']['total']"
api POST /api/base/anyone/defTenant/page "$T2" '{"size":5}' | json "'other:', d['data']['total']"
api GET "/api/base/anyone/defTenant/$TID" "$T2" | json "'other get:', d['data']"
api POST /api/base/anyone/defTenant/findByIds "$T2" "[\"$TID\"]" | json "'other findByIds:', d['data']"
api PUT /api/base/anyone/defTenant "$T2" "{\"id\":\"$TID\",\"name\":\"hack\"}" | json "'other update:', d['msg']"
api DELETE /api/base/anyone/defTenant "$T2" "[\"$TID\"]" >/dev/null
echo "name after the attempts: $($MYSQL -e "select name='t$TS', is_del from def_tenant where id=$TID" 2>/dev/null)"
api PUT /api/base/anyone/defTenant "$T" "{\"id\":\"$TID\",\"name\":\"t2$TS\",\"status\":0}" | json "'own update:', d['code'], d['data']['status']"
api POST "/api/base/anyone/defTenant/updateStatus?id=$TID&status=0" "$T" | json "'approve own:', d['code']"
api POST "/api/base/anyone/defTenant/updateStatus?id=$TID&status=0" "$TA" | json "'approve by admin:', d['code'], d['data']"
echo "status: $($MYSQL -e "select status from def_tenant where id=$TID" 2>/dev/null)"
api POST "/api/base/anyone/defTenant/updateStatus?id=$TID" "$TA" | json "'no status:', d['code'], d['msg']"
$MYSQL -e "update def_tenant set is_del=1 where id=$TID" 2>/dev/null
api POST /api/base/anyone/extendNotice "$T" "{\"recipientId\":\"$U2\",\"title\":\"fake\"}" | json "'forge a notice:', d['msg']"
NOT=$(api POST /api/base/anyone/extendNotice "$TA" "{\"recipientId\":\"$U\",\"title\":\"hello\",\"remindMode\":\"02\"}" | json "d['data']['id']")
api POST /api/base/anyone/extendNotice/page "$T" '{"size":5}' | json "'own notices:', d['data']['total']"
api POST /api/base/anyone/extendNotice/page "$T2" '{"size":5}' | json "'other notices:', d['data']['total']"
api GET "/api/base/anyone/extendNotice/$NOT" "$T2" | json "'other reads it:', d['data']"
api PUT /api/base/anyone/extendNotice "$T" "{\"id\":\"$NOT\",\"title\":\"changed\",\"isRead\":true}" | json "'own update:', d['data']['title'], d['data']['isRead']"
api DELETE /api/base/anyone/extendNotice "$T2" "[\"$NOT\"]" >/dev/null
api DELETE /api/base/anyone/extendNotice/deleteMyNotice "$T2" "[\"$NOT\"]" >/dev/null
echo "kept after delete by the other user: $($MYSQL -e "select is_del from extend_notice where id=$NOT" 2>/dev/null)"
api DELETE /api/base/anyone/extendNotice/deleteMyNotice "$T" "[\"$NOT\"]" >/dev/null
echo "deleted by the recipient: $($MYSQL -e "select is_del from extend_notice where id=$NOT" 2>/dev/null)"
sleep 1
api POST /api/base/baseLoginLog/anyone/page "$T2" '{"size":50}' | json "'login log of others:', sum(1 for r in d['data']['records'] if str(r['employeeId'])!='$U2'), 'own:', int(d['data']['total'])>0"
api POST /api/base/baseLoginLog/anyone/page "$T2" "{\"size\":50,\"model\":{\"employeeId\":\"$U\"}}" | json "'asking for another user:', sum(1 for r in d['data']['records'] if str(r['employeeId'])!='$U2')"
api POST /api/base/baseLoginLog/anyone/page "$TA" "{\"size\":1,\"model\":{\"employeeId\":\"$U\"}}" | json "'admin:', int(d['data']['total'])>0"
api POST "/api/base/anyone/updateDefApp?applicationId=999999" "$T" | json "'default app, no such app:', d['code']"
curl -s -XPOST "$HOST/api/base/extendMsg/anyUser/sendByTemplate" -H 'Content-Type: application/json' -d '{}' | json "'send without login:', d['code']"
api POST /api/base/extendMsg/anyUser/sendByTemplate "$T" '{}' | json "'send as a user:', d['code']"

echo "== the role of a user grants the interfaces of its resources"
PR=$(date +%s%N | cut -c1-18); RS=$((PR+1)); AP=$((PR+2)); RR=$((PR+3)); ER=$((PR+4))
$MYSQL -e "insert into base_role(id,code,name,state,create_time,create_by,update_time,update_by,tenant_id) values ($PR,'R$TS','r',1,now(),1,now(),1,1)" 2>/dev/null
$MYSQL -e "insert into def_resource(id,application_id,code,name,resource_type,parent_id,state,create_time,create_by,update_time,update_by) values ($RS,1,'sec:$TS','r','40',0,1,now(),1,now(),1)" 2>/dev/null
$MYSQL -e "insert into def_resource_api(id,resource_id,controller,spring_application_name,request_method,name,uri,is_input,create_by,create_time,update_by,update_time) values ($AP,$RS,'c','s','GET','n','/base/baseRole/{id}',0,1,now(),1,now())" 2>/dev/null
$MYSQL -e "insert into base_role_resource_rel(id,resource_id,application_id,role_id,create_time,create_by,update_time,update_by,tenant_id) values ($RR,$RS,1,$PR,now(),1,now(),1,1)" 2>/dev/null
api GET /api/base/baseRole/$PR "$T" | json "'before the grant:', d['code']"
$MYSQL -e "insert into base_employee_role_rel(id,role_id,employee_id,create_by,create_time,is_del,tenant_id) values ($ER,$PR,$U,1,now(),0,1)" 2>/dev/null
api GET /api/base/baseRole/$PR "$T" | json "'granted GET:', d['code'], d['data']['code'] == 'R$TS'"
api DELETE /api/base/baseRole "$T" "[\"$PR\"]" | json "'other method:', d['code']"
api POST /api/base/baseRole/page "$T" '{"size":1}' | json "'other path:', d['code']"
api GET /api/base/anyone/getDefApp "$T" | json "'anyone still open:', d['code']"
$MYSQL -e "update base_role_resource_rel set is_del=1 where id=$RR" 2>/dev/null
api GET /api/base/baseRole/$PR "$T" | json "'after revoking:', d['code']"
$MYSQL -e "update base_role_resource_rel set is_del=0 where id=$RR" 2>/dev/null

echo "== users of the management side need a grant for every interface"
ME="mgr$TS@example.com"; read TM UM < <(register_user "$ME")
MU=$($MYSQL -e "select id from def_user where email='$ME'" 2>/dev/null)
$MYSQL -e "update def_user set system_type=10 where id=$MU" 2>/dev/null
LOGIN=$(curl -s -XPOST "$HOST/api/oauth/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" -d "{\"account\":\"$ME\",\"password\":\"123456\",\"systemType\":10,\"deviceType\":\"PC\",\"clientId\":\"cm\"}")
echo "$LOGIN" | json "'login:', d['code']"
TM=$(echo "$LOGIN" | json "d['data']['token']")
api GET "/api/im/room/group/list?pageNo=1&pageSize=1" "$TM" | json "'not configured:', d['code']"
api GET /api/oauth/anyone/getUserInfo "$TM" | json "'anyone:', d['code'] != 406"
api GET /api/base/baseRole/$PR "$TM" | json "'no role:', d['code']"
$MYSQL -e "insert into base_employee_role_rel(id,role_id,employee_id,create_by,create_time,is_del,tenant_id) values ($((PR+5)),$PR,$MU,1,now(),0,1)" 2>/dev/null
api GET /api/base/baseRole/$PR "$TM" | json "'with the role:', d['code']"
api GET "/api/im/room/group/list?pageNo=1&pageSize=1" "$T" | json "'ordinary user, not configured:', d['code'] != 406"
$MYSQL -e "update base_role set is_del=1 where id=$PR; update def_resource set is_del=1 where id=$RS; update def_resource_api set is_del=1 where id=$AP; update base_role_resource_rel set is_del=1 where id=$RR; update base_employee_role_rel set is_del=1 where role_id=$PR" 2>/dev/null

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
