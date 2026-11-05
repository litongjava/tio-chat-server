#!/usr/bin/env bash
# Business rules of the base data resources: password hash, unique values, tree paths, cascades, menu interfaces, dictionaries.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s%N | cut -c1-16); read TA UA < <(register_user "rules$TS@example.com"); grant_admin "$UA"
TA=$(login_user "rules$TS@example.com" | cut -d' ' -f1); B=/api/base
post() { api POST "$B$1" "$TA" "$2"; }
put() { api PUT "$B$1" "$TA" "$2"; }
del() { api DELETE "$B$1" "$TA" "$2"; }
get() { api GET "$B$1" "$TA"; }
q() { $MYSQL -e "$1" 2>/dev/null; }
msg() { json "d['code'], d['msg']"; }

echo "== user"
U=$(post /defUser "{\"username\":\"r$TS\",\"nickName\":\"n\",\"password\":\"secret1\",\"mobile\":\"1${TS:6:10}\"}"); UID1=$(echo "$U" | json "d['data']['id']"); echo "$U" | json "d['code'], 'password' in d['data']"
echo "password stored hashed: $(q "select password<>'secret1' and length(password)=64 and salt<>'' from def_user where id=$UID1")"
curl -s -XPOST "$HOST/api/oauth/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" -d "{\"account\":\"r$TS\",\"password\":\"secret1\",\"deviceType\":\"PC\",\"clientId\":\"c\"}" | json "'login with it:', d['code'] != -10 or 'IM' in d['msg']"
post /defUser "{\"username\":\"r$TS\",\"nickName\":\"n\"}" | json "d['code'], d['msg'].replace('r$TS','U')"
put /defUser "{\"id\":\"$UID1\",\"password\":\"plain\",\"nickName\":\"m\"}" | json "d['code']"
echo "update keeps the hash: $(q "select password<>'plain' from def_user where id=$UID1")"

echo "== org"
O1=$(post /baseOrg "{\"name\":\"o$TS\"}" | json "d['data']['id']")
O2=$(post /baseOrg "{\"name\":\"c$TS\",\"parentId\":\"$O1\"}" | json "d['data']['id']")
echo "tree: $(q "select tree_path='/$O1/', tree_grade from base_org where id=$O2")"
post /baseOrg "{\"name\":\"c$TS\",\"parentId\":\"$O1\"}" | json "d['code'], d['msg'].replace('c$TS','O')"
post /baseOrg "{\"name\":\"x\",\"parentId\":\"999999\"}" | msg
del /baseOrg "[\"$O1\"]" | msg

echo "== employee"
E=$(post /baseEmployee "{\"name\":\"e$TS\",\"userId\":\"$UID1\",\"positionId\":\"0\",\"orgIdList\":[\"$O2\"]}"); EID=$(echo "$E" | json "d['data']['id']"); echo "$E" | json "d['code']"
echo "org relation: $(q "select count(*) from base_employee_org_rel where employee_id=$EID and org_id=$O2 and is_del=0")"
E2=$(post /baseEmployee "{\"realName\":\"真名\",\"mobile\":\"2${TS:6:10}\",\"orgIdList\":[]}"); echo "$E2" | json "d['code'], d['data']['name'], d['data']['activeStatus']"
echo "user created: $(q "select nick_name from def_user where id=$(echo "$E2" | json "d['data']['userId']")")"
post /baseEmployee "{\"realName\":\"真名\",\"mobile\":\"2${TS:6:10}\"}" | msg
post /baseEmployee '{"realName":"x"}' | msg
del /baseEmployee "[\"$(echo "$E2" | json "d['data']['id']")\"]" >/dev/null
get "/baseEmployee/$EID" | json "d['data']['orgIdList'] == ['$O2']"
post /baseEmployee/page "{\"size\":5,\"model\":{\"orgIdList\":[\"$O2\"]}}" | json "d['data']['total'], d['data']['records'][0]['orgIdList'] == ['$O2']"
post /baseEmployee/page "{\"size\":5,\"model\":{\"username\":\"r$TS\"}}" | json "'by username:', d['data']['total']"
del /baseOrg "[\"$O2\"]" | msg
put /baseEmployee "{\"id\":\"$EID\",\"orgIdList\":[]}" | json "d['code']"
echo "after clearing: $(q "select count(*) from base_employee_org_rel where employee_id=$EID and is_del=0")"
del /baseOrg "[\"$O2\"]" | json "d['code']"; del /baseOrg "[\"$O1\"]" | json "d['code']"

echo "== role"
R=$(post /baseRole "{\"name\":\"r\",\"code\":\"RC$TS\",\"state\":true}" | json "d['data']['id']")
echo "type and readonly: $(q "select type_, readonly_ from base_role where id=$R")"
post /baseRole "{\"name\":\"r\",\"code\":\"RC$TS\"}" | json "d['code'], d['msg'].replace('RC$TS','C')"
post /baseEmployee/page "{\"size\":5,\"model\":{\"roleId\":\"$R\",\"scope\":\"1\"}}" | json "'with the role:', d['data']['total']"
q "insert into base_employee_role_rel(id,role_id,employee_id,create_by,create_time,is_del,tenant_id) values ($((TS)),$R,$EID,1,now(),0,1)" >/dev/null
post /baseEmployee/page "{\"size\":5,\"model\":{\"roleId\":\"$R\",\"scope\":\"1\"}}" | json "'with the role:', d['data']['total']"
q "update base_role set readonly_=1 where id=$R" >/dev/null
del /baseRole "[\"$R\"]" | msg
q "update base_role set readonly_=0 where id=$R" >/dev/null
del /baseRole "[\"$R\"]" | json "d['code']"
echo "relations removed: $(q "select count(*) from base_employee_role_rel where role_id=$R and is_del=0")"
del /baseEmployee "[\"$EID\"]" | json "d['code']"

echo "== resource"
M1=$(post /defResource "{\"applicationId\":\"1\",\"code\":\"m$TS\",\"name\":\"m$TS\",\"resourceType\":\"20\",\"path\":\"/m$TS\",\"component\":\"/c\"}" | json "d['data']['id']")
post /defResource "{\"applicationId\":\"1\",\"code\":\"x$TS\",\"name\":\"x\",\"resourceType\":\"20\"}" | msg
post /defResource "{\"applicationId\":\"1\",\"code\":\"m$TS\",\"name\":\"y\",\"resourceType\":\"40\"}" | json "d['code'], d['msg'].replace('m$TS','M')"
BT=$(post /defResource "{\"applicationId\":\"1\",\"code\":\"b$TS\",\"name\":\"b\",\"resourceType\":\"40\",\"parentId\":\"$M1\",\"resourceApiList\":[{\"controller\":\"C\",\"springApplicationName\":\"s\",\"requestMethod\":\"GET\",\"name\":\"n\",\"uri\":\"/base/x$TS\"}]}" | json "d['data']['id']")
get "/defResource/detail?id=$BT" | json "[(a['requestMethod'], a['uri'].replace('$TS','')) for a in d['data']['resourceApiList']], d['data']['treeGrade']"
post /defResource "{\"applicationId\":\"1\",\"code\":\"n$TS\",\"name\":\"n$TS\",\"resourceType\":\"20\",\"path\":\"/n$TS\",\"component\":\"/c\",\"parentId\":\"$BT\"}" | msg
put /defResource "{\"id\":\"$BT\",\"resourceApiList\":[{\"controller\":\"C\",\"springApplicationName\":\"s\",\"requestMethod\":\"POST\",\"name\":\"n\",\"uri\":\"/base/y$TS\"}]}" | json "d['code']"
get "/defResource/detail?id=$BT" | json "[(a['requestMethod'], a['uri'].replace('$TS','')) for a in d['data']['resourceApiList']]"
del /defResource "[\"$M1\"]" | msg
del /defResource "[\"$BT\"]" | json "d['code']"; del /defResource "[\"$M1\"]" | json "d['code']"
echo "interfaces removed: $(q "select count(*) from def_resource_api where resource_id=$BT and is_del=0")"

echo "== resource rules on update"
PA=$(post /defResource "{\"applicationId\":\"1\",\"code\":\"pa$TS\",\"name\":\"pa$TS\",\"resourceType\":\"20\",\"path\":\"/pa$TS\",\"component\":\"/c\",\"state\":true,\"isGeneral\":false}" | json "d['data']['id']")
PB=$(post /defResource "{\"applicationId\":\"1\",\"code\":\"pb$TS\",\"name\":\"pb$TS\",\"resourceType\":\"20\",\"path\":\"/pb$TS\",\"component\":\"/c\",\"state\":true}" | json "d['data']['id']")
PC=$(post /defResource "{\"applicationId\":\"1\",\"code\":\"pc$TS\",\"name\":\"pc$TS\",\"resourceType\":\"20\",\"path\":\"/pc$TS\",\"component\":\"/c\",\"state\":true,\"parentId\":\"$PA\",\"metaJson\":\"{ \\\"a\\\" : 1 }\"}" | json "d['data']['id']")
PD=$(post /defResource "{\"applicationId\":\"1\",\"code\":\"pd$TS\",\"name\":\"pd$TS\",\"resourceType\":\"40\",\"state\":true,\"parentId\":\"$PC\"}" | json "d['data']['id']")
echo "meta normalized: $(q "select meta_json from def_resource where id=$PC")"
post /defResource "{\"applicationId\":\"1\",\"code\":\"pe$TS\",\"name\":\"pe\",\"resourceType\":\"40\",\"metaJson\":\"{bad\"}" | msg
post /defResource "{\"applicationId\":\"1\",\"code\":\"pf$TS\",\"name\":\"pf\",\"resourceType\":\"40\",\"isGeneral\":true,\"parentId\":\"$PA\"}" | json "d['code'], d['msg'].replace('pa$TS','P')"
put /defResource "{\"id\":\"$PA\",\"state\":false}" | msg
put /defResource "{\"id\":\"$PB\",\"name\":\"pa$TS\"}" | json "d['code'], d['msg'].replace('pa$TS','P')"
put /defResource "{\"id\":\"$PB\",\"path\":\"/pa$TS\"}" | json "d['code'], d['msg'].replace('/pa$TS','P')"
echo "-- moving a node takes its descendants along"
put /defResource "{\"id\":\"$PC\",\"parentId\":\"$PB\"}" | json "d['code']"
echo "moved: $(q "select tree_path='/$PB/', tree_grade from def_resource where id=$PC") descendant: $(q "select tree_path='/$PB/$PC/', tree_grade from def_resource where id=$PD")"
put /defResource "{\"id\":\"$PB\",\"parentId\":\"$PD\"}" | msg
put /defResource "{\"id\":\"$PB\",\"parentId\":\"$PB\"}" | msg
api PUT "$B/defResource/moveResource?id=$PC&parentId=$PA" "$TA" | json "d['code']"
echo "after move endpoint: $(q "select tree_path='/$PA/$PC/', tree_grade from def_resource where id=$PD")"
api PUT "$B/defResource/moveResource?id=$PA&parentId=$PD" "$TA" | msg
del /defResource "[\"$PD\"]" >/dev/null; del /defResource "[\"$PC\"]" >/dev/null; del /defResource "[\"$PA\",\"$PB\"]" >/dev/null
OA=$(post /baseOrg "{\"name\":\"oa$TS\"}" | json "d['data']['id']"); OB=$(post /baseOrg "{\"name\":\"ob$TS\"}" | json "d['data']['id']")
OC=$(post /baseOrg "{\"name\":\"oc$TS\",\"parentId\":\"$OA\"}" | json "d['data']['id']"); OD=$(post /baseOrg "{\"name\":\"od$TS\",\"parentId\":\"$OC\"}" | json "d['data']['id']")
put /baseOrg "{\"id\":\"$OC\",\"parentId\":\"$OB\"}" | json "d['code']"
echo "org descendant: $(q "select tree_path='/$OB/$OC/', tree_grade from base_org where id=$OD")"
put /baseOrg "{\"id\":\"$OB\",\"parentId\":\"$OD\"}" | msg
del /baseOrg "[\"$OD\"]" >/dev/null; del /baseOrg "[\"$OC\"]" >/dev/null; del /baseOrg "[\"$OA\",\"$OB\"]" >/dev/null

echo "== logo"
LG=$(date +%s%N | cut -c1-18)
AP=$(post /defApplication "{\"name\":\"lg$TS\",\"version\":\"1\",\"type\":\"10\",\"appendixIcon\":\"$LG\"}" | json "d['data']['id']")
get "/defApplication/detail?id=$AP" | json "d['data']['appendixIcon'] == '$LG'"
echo "appendix: $(q "select biz_type from base_com_appendix where biz_id=$AP")"
del /defApplication "[\"$AP\"]" >/dev/null
echo "removed: $(q "select count(*) from base_com_appendix where biz_id=$AP")"
TN=$(post /defTenant "{\"name\":\"t$TS\",\"contactName\":\"c\",\"logo\":\"$LG\"}" | json "d['data']['id']")
get "/defTenant/detail?id=$TN" | json "d['data']['logo'] == '$LG', d['data']['status']"
del /defTenant "[\"$TN\"]" >/dev/null

echo "== login log"
TA=$(curl -s -XPOST "$HOST/api/oauth/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" -H 'User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36' -d "{\"account\":\"rules$TS@example.com\",\"password\":\"123456\",\"systemType\":2,\"deviceType\":\"PC\",\"clientId\":\"cl\"}" | json "d['data']['token']")
sleep 1
echo "logged in: $(q "select browser, browser_version, operating_system from def_login_log where username in (select username from def_user where email='rules$TS@example.com') order by id desc limit 1")"
LL=$(post /defLoginLog "{\"userId\":\"$UID1\",\"ua\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15\"}")
echo "$LL" | json "d['code'], d['data']['username'] == 'r$TS', d['data']['browser'], d['data']['operatingSystem'], len(d['data']['loginDate'])"

echo "== dictionary"
D=$(post /defDict "{\"key\":\"dk$TS\",\"name\":\"d\",\"state\":true,\"insertList\":[{\"key\":\"a\",\"name\":\"A\",\"state\":true},{\"key\":\"b\",\"name\":\"B\",\"state\":true}]}" | json "d['data']['id']")
echo "items: $(q "select group_concat(key_ order by key_), min(parent_key)='dk$TS' from def_dict where parent_id=$D and is_del=0")"
post /defDict "{\"key\":\"dk$TS\",\"name\":\"d\"}" | json "d['code'], d['msg'].replace('dk$TS','K')"
post /defDict/page "{\"size\":5,\"model\":{\"key\":\"dk$TS\"}}" | json "'dict page has no items:', d['data']['total']"
IA=$(q "select id from def_dict where parent_id=$D and key_='a'"); IB=$(q "select id from def_dict where parent_id=$D and key_='b'")
put /defDict "{\"id\":\"$D\",\"name\":\"d2\",\"insertList\":[{\"key\":\"c\",\"name\":\"C\",\"state\":true}],\"updateList\":[{\"id\":\"$IA\",\"name\":\"A2\"}],\"deleteList\":[\"$IB\"]}" | json "d['code']"
echo "items after update: $(q "select group_concat(concat(key_,'=',name) order by key_) from def_dict where parent_id=$D and is_del=0")"
post /defDictItem "{\"parentId\":\"$D\",\"key\":\"a\",\"name\":\"dup\"}" | msg
I=$(post /defDictItem "{\"parentId\":\"$D\",\"key\":\"e\",\"name\":\"E\",\"state\":true}" | json "d['data']['id']")
echo "item parent key: $(q "select parent_key='dk$TS' from def_dict where id=$I")"
post /defDictItem/page "{\"size\":9,\"model\":{\"parentId\":\"$D\"}}" | json "'item page:', d['data']['total']"
CP=$(post "/defDict/copy?id=$D" | json "d['data']['id']")
echo "copy: $(q "select key_='dk${TS}_copy' from def_dict where id=$CP") items $(q "select count(*) from def_dict where parent_id=$CP and is_del=0")"
del /defDict "[\"$D\",\"$CP\"]" | json "d['code']"
echo "items removed: $(q "select count(*) from def_dict where parent_id in ($D,$CP) and is_del=0")"

echo "== others"
A=$(post /defApplication "{\"name\":\"a$TS\",\"version\":\"1\",\"type\":\"10\"}"); AID=$(echo "$A" | json "d['data']['id']"); echo "$A" | json "len(d['data']['appKey']), len(d['data']['appSecret'])"
post /defApplication "{\"name\":\"a$TS\"}" | json "d['code'], d['msg'].replace('a$TS','A')"; del /defApplication "[\"$AID\"]" >/dev/null
C=$(post /defClient "{\"name\":\"c\",\"clientId\":\"mine\",\"clientSecret\":\"mine\"}"); echo "$C" | json "d['code'], len(d['data']['clientId']), len(d['data']['clientSecret'])"; del /defClient "[\"$(echo "$C" | json "d['data']['id']")\"]" >/dev/null
P=$(post /defParameter "{\"key\":\"pk$TS\",\"value\":\"v\",\"name\":\"n\"}"); echo "$P" | json "d['code'], d['data']['paramType']"; post /defParameter "{\"key\":\"pk$TS\",\"value\":\"v\",\"name\":\"n\"}" | json "d['code'], d['msg'].replace('pk$TS','P')"; del /defParameter "[\"$(echo "$P" | json "d['data']['id']")\"]" >/dev/null
Z=$(post /defArea "{\"code\":\"z$TS\",\"name\":\"z\",\"fullName\":\"z\"}" | json "d['data']['id']"); ZC=$(post /defArea "{\"code\":\"zc$TS\",\"name\":\"zc\",\"fullName\":\"zc\",\"parentId\":\"$Z\"}" | json "d['data']['id']")
echo "area tree: $(q "select tree_path='/$Z/', tree_grade from def_area where id=$ZC")"; post /defArea "{\"code\":\"z$TS\",\"name\":\"z\"}" | json "d['code'], d['msg'].replace('z$TS','Z')"; del /defArea "[\"$ZC\",\"$Z\"]" >/dev/null
I=$(post /defInterface "{\"code\":\"if$TS\",\"name\":\"i\",\"execMode\":\"01\",\"implClass\":\"x.Y\",\"state\":true}" | json "d['data']['id']")
post /defInterface "{\"code\":\"if$TS\",\"name\":\"i\",\"execMode\":\"01\",\"implClass\":\"x.Y\"}" | json "d['code'], d['msg'].replace('if$TS','I')"; post /defInterface "{\"code\":\"j$TS\",\"name\":\"i\",\"execMode\":\"02\"}" | msg
IP=$(post /defInterfaceProperty "{\"interfaceId\":\"$I\",\"name\":\"n\",\"key\":\"k\",\"value\":\"v\"}" | json "d['data']['id']"); del /defInterface "[\"$I\"]" >/dev/null
echo "properties removed: $(q "select is_del from def_interface_property where id=$IP")"
T1=$(post /defMsgTemplate "{\"code\":\"t$TS\",\"name\":\"t\",\"type\":\"01\",\"interfaceId\":\"$I\",\"title\":\"hi \${name}\",\"content\":\"\${name} \${code}\",\"state\":true,\"remindMode\":\"01\",\"target\":\"01\",\"autoRead\":true}"); echo "$T1" | json "d['code'], d['data']['param'] if d['data'] else d['msg']"; post /defMsgTemplate "{\"code\":\"t$TS\",\"name\":\"t\"}" | json "d['code'], d['msg'].replace('t$TS','T')"; del /defMsgTemplate "[\"$(echo "$T1" | json "d['data']['id']")\"]" >/dev/null
L=$(post /baseOperationLog "{\"description\":\"d\",\"params\":\"p\",\"result\":\"r\",\"exDetail\":\"e\"}" | json "d['data']['id']"); get "/baseOperationLog/detail?id=$L" | json "d['data']['params'], d['data']['result'], d['data']['exDetail']"; del /baseOperationLog "[\"$L\"]" >/dev/null

curl -s -XPOST "$HOST/api/system/anyTenant/translate/text" -H 'Content-Type: application/json' -d '{}' | json "'translate fields:', sorted(d['data'].keys())"

echo "== bad input is a validation error, not a server error"
post /baseOrg '{}' | msg
post /defTenant '{}' | msg
api POST /api/ai/tool/create "$TA" '{"name":"x"}' | msg
api GET '/api/im/chat/contact/page?pageSize=10&cursor=x' "$TA" | msg
curl -s "$HOST/api/system/anyTenant/map/geocoder/reverse?lat=x&lng=x" | msg
api GET '/api/ai/workflow/page?pageNo=1&pageSize=1' "$TA" | json "d['code']"
W=$(api POST /api/ai/workflow/create "$TA" '{"code":"w","name":"w","graph":"{}","status":0}' | json "d['code'], d['data']"); echo "workflow: ${W%%,*}"

echo "== the server does not fetch internal addresses for users"
for u in 'http://127.0.0.1:18760/health' 'http://10.0.0.1/' 'http://169.254.169.254/latest/meta-data' 'file:///etc/passwd' 'x'; do
  api GET "/api/ai/knowledge/segment/split?segmentMaxTokens=10&url=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$u")" "$TA" | msg
done
