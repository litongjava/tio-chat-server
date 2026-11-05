#!/usr/bin/env bash
# Endpoints only the HuLa-Admin front end calls: users, menus, group mute, comment delete, change password, public routes.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s%N | cut -c1-16); B=/api/base
read TA UA < <(register_user "cadmin$TS@example.com"); grant_admin "$UA"; TA=$(login_user "cadmin$TS@example.com" | cut -d' ' -f1)
read T U < <(register_user "cuser$TS@example.com"); read T2 U2 < <(register_user "cuser2$TS@example.com")
q() { $MYSQL -e "$1" 2>/dev/null; }
msg() { json "d['code'], d['msg']"; }

echo "== users"
ADD=$(api POST $B/user/add "$TA" "{\"userName\":\"au$TS\",\"nickName\":\"甲\",\"password\":\"pw1\"}"); ID=$(echo "$ADD" | json "d['data']['id']"); echo "$ADD" | json "d['code'], d['data']['userName'] == 'au$TS', 'password' in d['data']"
echo "hashed: $(q "select password<>'pw1' from def_user where id=$ID")"
api GET "$B/user/page?pageNum=1&pageSize=2&keyword=%E7%94%B2" "$TA" | json "d['code'], len(d['data']['records'])>0, 'userName' in d['data']['records'][0], 'total' in d['data']"
api GET "$B/user/detail?id=$ID" "$TA" | json "d['data']['nickName']"
api POST $B/user/edit "$TA" "{\"id\":\"$ID\",\"nickName\":\"乙\"}" | json "d['data']['nickName']"
api POST $B/user/updateState "$TA" "{\"id\":\"$ID\",\"state\":false}" | json "d['data']"
echo "state: $(q "select state from def_user where id=$ID")"
api POST $B/user/resetPassword "$TA" "{\"id\":\"$ID\",\"password\":\"pw2\"}" | json "d['data']"
api POST $B/user/resetPassword "$TA" "{\"id\":\"$ID\"}" | msg
api POST $B/user/del "$TA" "{\"id\":\"$ID\"}" | json "d['data']"
echo "deleted: $(q "select is_del from def_user where id=$ID")"
api POST $B/user/del "$TA" '{}' | msg
echo "-- an ordinary user only reaches itself"
api GET "$B/user/page?pageNum=1&pageSize=1" "$T" | json "d['code']"
api POST $B/user/resetPassword "$T" "{\"id\":\"$U2\",\"password\":\"x\"}" | json "d['code']"
api GET $B/user/current "$T" | json "d['code'], d['data']['uid'] == '$U', 'password' in d['data']"
MOBILE=$(q "select coalesce(mobile,'') from def_user where email='cuser$TS@example.com'")
api POST $B/user/updateCurrent "$T" '{"nickName":"改名","mobile":"19900000000","state":false,"password":"x"}' | json "d['code'], d['data']['nickName'], d['data']['state']"
echo "mobile kept: $(q "select coalesce(mobile,'')='$MOBILE' from def_user where email='cuser$TS@example.com'")"
curl -s -o /dev/null -w 'no login: %{http_code} ' "$HOST$B/user/current"; curl -s "$HOST$B/user/current" | json "d['code']"
echo "-- avatar"
printf 'png' > "/tmp/av$TS.png"; printf 'x' > "/tmp/av$TS.exe"
curl -s -XPOST "$HOST$B/user/uploadAvatar" -H "token: $T" -F "file=@/tmp/av$TS.exe" | msg
curl -s -XPOST "$HOST$B/user/uploadAvatar" -H "token: $T" -F "file=@/tmp/av$TS.png" | json "d['code'], ('avatar/' in d['data']['url']) if d['data'] else d['msg']"
rm -f "/tmp/av$TS.png" "/tmp/av$TS.exe"

echo "== menus"
api GET "$B/defResource/isRouteExist?routeName=m$TS" "$TA" | json "d['data']"
R=$(api POST $B/defResource/add "$TA" "{\"applicationId\":\"1\",\"code\":\"m$TS\",\"name\":\"m$TS\",\"type\":\"20\",\"sort\":7,\"path\":\"/m$TS\",\"component\":\"/c\",\"isGeneral\":true,\"state\":true}"); RID=$(echo "$R" | json "d['data']['id']"); echo "$R" | json "d['code'], d['data']['resourceType'], d['data']['sortValue']"
api POST $B/defResource/add "$TA" "{\"applicationId\":\"1\",\"code\":\"m$TS\",\"name\":\"x\",\"type\":\"40\"}" | json "d['code'], d['msg'].replace('m$TS','M')"
api GET "$B/defResource/isRouteExist?routeName=m$TS" "$TA" | json "d['data']"
api GET "$B/defResource/isRouteExist?routeName=/m$TS" "$TA" | json "d['data']"
api POST $B/defResource/edit "$TA" "{\"id\":\"$RID\",\"name\":\"n$TS\"}" | json "d['data']['name'] == 'n$TS'"
has() { python3 -c "
import sys,json
def walk(n):
    return any(i.get('name')=='m$TS' or walk(i.get('children') or []) for i in n)
print(walk(json.load(sys.stdin)['data']))"; }
api GET "$B/defResource/visible?applicationId=1" "$TA" | has
curl -s "$HOST/api/oauth/anyTenant/menu/initRoute?applicationId=1" | has
api POST "$B/defResource/tree?applicationId=999999" "$TA" | json "d['code'], len(d['data'])"
api POST $B/defResource/del "$T" "{\"id\":\"$RID\"}" | json "d['code']"
api POST $B/defResource/del "$TA" "{\"ids\":[\"$RID\"]}" | json "d['data']"
curl -s "$HOST/api/oauth/anyTenant/menu/initRoute?applicationId=1" | has

echo "== group mute"
read TO UO < <(register_user "cowner$TS@example.com"); read TM UM < <(register_user "cmgr$TS@example.com")
ROOM=$(api POST /api/im/room/group "$TO" "{\"groupName\":\"mute${TS: -6}\",\"uidList\":[$U,$U2,$UM]}" | json "d['data']['id']")
api PUT /api/im/room/group/admin "$TO" "{\"roomId\":$ROOM,\"uidList\":[$UM]}" >/dev/null
send() { api POST /api/im/chat/msg "$1" "{\"roomId\":$ROOM,\"msgType\":1,\"body\":{\"content\":\"hi\"}}" | json "d['code'], d['msg']"; }
mute() { api POST /api/im/room/group/mute "$1" "{\"roomId\":\"$ROOM\",\"uid\":\"$2\",\"duration\":${3:-60}}" | msg; }
send "$T"
echo "member mutes:"; mute "$T2" "$U"
echo "owner mutes member:"; mute "$TO" "$U"
OUT=$(send "$T"); echo "$OUT"; case "$OUT" in *禁言*) ;; *) echo "a muted member could send"; exit 1 ;; esac
send "$T2"
echo "ttl: $($REDIS ttl luohuo:group:mute:$ROOM:$U)"
api GET "/api/im/room/group/listMember?roomId=$ROOM" "$TO" | json "'muted in the member list:', sorted(str(m['uid'])=='$U' for m in d['data'] if m['muted'])"
api GET "/api/im/chat/msg/page?roomId=$ROOM&pageSize=3" "$TO" | json "'system message:', any('禁言' in str(m['message'].get('body')) for m in d['data']['list'])"
echo "manager mutes a member:"; mute "$TM" "$U2"
api POST /api/im/room/group/unmute "$TM" "{\"roomId\":\"$ROOM\",\"uid\":\"$U2\"}" >/dev/null
echo "member mutes a manager:"; mute "$T2" "$UM"
echo "bad duration:"; mute "$TO" "$U2" 0
echo "mute the owner:"; mute "$TA" "$UO"
echo "mute a non member:"; mute "$TA" "$UA"
echo "system admin mutes:"; mute "$TA" "$U2"
api POST /api/im/room/group/unmute "$TO" "{\"roomId\":\"$ROOM\",\"uid\":\"$U\"}" | msg
api POST /api/im/room/group/unmute "$TO" "{\"roomId\":\"$ROOM\",\"uid\":\"$U2\"}" >/dev/null
send "$T"
echo "ttl after unmute: $($REDIS ttl luohuo:group:mute:$ROOM:$U)"

echo "== comment delete"
FEED=$(api POST /api/im/feed/pushFeed "$T" '{"content":"f","mediaType":0,"permission":"open"}' | json "d['data'] if isinstance(d['data'],(int,str)) else d['data'].get('id')")
[ "$FEED" = "True" ] && FEED=$(q "select id from im_feed where uid=$U order by id desc limit 1")
cmt() { api POST /api/im/feed/comment/add "$T" "{\"feedId\":\"$FEED\",\"content\":\"c\"}" >/dev/null; q "select id from im_feed_comment where feed_id=$FEED and is_del=0 order by id desc limit 1"; }
C=$(cmt)
api POST /api/im/feed/comment/del "$T2" "{\"commentId\":\"$C\"}" | msg
api POST /api/im/feed/comment/del "$TA" "{\"commentId\":\"$C\"}" | json "d['data']"
C=$(cmt)
api POST /api/im/feed/comment/del "$T" "{\"commentId\":\"$C\"}" | json "d['data']"

echo "== change password"
P=/api/oauth/anyUser/updatePassword
curl -s -XPOST "$HOST$P" -H 'Content-Type: application/json' -d '{"oldPassword":"123456","newPassword":"n","confirmPassword":"n"}' | json "d['code']"
api POST $P "$T2" '{"oldPassword":"wrong","newPassword":"new123","confirmPassword":"new123"}' | msg
api POST $P "$T2" '{"oldPassword":"123456","newPassword":"new123","confirmPassword":"other"}' | msg
api POST $P "$T2" '{"oldPassword":"123456","newPassword":"new123","confirmPassword":"new123"}' | json "d['data']"
curl -s -XPOST "$HOST/api/oauth/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" -d "{\"account\":\"cuser2$TS@example.com\",\"password\":\"new123\",\"systemType\":2,\"deviceType\":\"PC\",\"clientId\":\"c1\"}" | json "'login with the new one:', d['code']"
