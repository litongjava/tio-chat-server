#!/usr/bin/env bash
# Friends, applies, notices, contacts, groups and announcements.
cd "$(dirname "$0")"; source ./lib.sh; set -e
J=${JAVA:-/usr/java/jdk-21.0.6/bin/java}
TS=$(date +%s)
read TA UA < <(register_user "sa$TS@example.com")
read TB UB < <(register_user "sb$TS@example.com")
read TC UC < <(register_user "sc$TS@example.com")
echo "A=$UA B=$UB C=$UC"
$J WsClient.java "ws://localhost:18760/api/ws/ws?clientId=c1&Token=$TB" 12 > /tmp/ws_b_$TS.log &
sleep 3
echo "== A applies B"; api POST /api/im/room/apply/apply "$TA" "{\"targetUid\":$UB,\"msg\":\"hi\"}" | json "d['code'], d['data']['targetId']"
echo "== B unread notices"; api GET /api/im/room/notice/unread "$TB" | json "d['code'], d['data']"
NOTICE=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10&click=true&applyType=friend" "$TB")
echo "$NOTICE" | json "d['code'], d['data']['totalRecords'], d['data']['list'][0]['eventType'], d['data']['list'][0]['senderName']"
APPLY=$(echo "$NOTICE" | json "d['data']['list'][0]['applyId']")
echo "== B accepts"; api POST /api/im/room/apply/handler/apply "$TB" "{\"applyId\":$APPLY,\"state\":1}" | json "d['code'], d['msg']"
echo "== A friend check"; api GET "/api/im/user/friend/check?uidList=$UB,$UC" "$TA" | json "d['code'], d['data']"
echo "== A friend page"; api GET "/api/im/user/friend/page?pageSize=10" "$TA" | json "d['code'], [(f['name'], f['activeStatus']) for f in d['data']['list']]"
echo "== A contact list"; api GET /api/im/chat/contact/list "$TA" | json "d['code'], [(c['name'], c['type'], c['text'], c['unreadCount']) for c in d['data']]"
echo "== A creates group with B, C"; G=$(api POST /api/im/room/group "$TA" "{\"groupName\":\"g$TS\",\"uidList\":[$UB,$UC]}")
echo "$G" | json "d['code'], d['data']"; RID=$(echo "$G" | json "d['data']['id']")
echo "== group detail"; api GET "/api/im/room/group/detail?id=$RID" "$TA" | json "d['code'], d['data']['groupName'], d['data']['memberNum'], d['data']['roleId']"
echo "== list member"; api GET "/api/im/room/group/listMember?roomId=$RID" "$TB" | json "d['code'], sorted(m['roleId'] for m in d['data'])"
echo "== set admin B"; api PUT /api/im/room/group/admin "$TA" "{\"roomId\":$RID,\"uidList\":[$UB]}" | json "d['code'], d['msg']"
echo "== push announcement"; api POST /api/im/room/announcement/push "$TB" "{\"roomId\":$RID,\"content\":\"notice $TS\",\"top\":true}" | json "d['code'], d['data']"
echo "== announcement list"; AL=$(api GET "/api/im/room/announcement/list?roomId=$RID&current=1&size=10" "$TC"); echo "$AL" | json "d['code'], d['data']['total'], d['data']['records'][0]['content']"
AID=$(echo "$AL" | json "d['data']['records'][0]['id']")
echo "== C reads announcement"; api POST /api/im/room/announcement/read "$TC" "{\"roomId\":$RID,\"announcementId\":$AID}" | json "d['code'], d['data']"
echo "== group msg page"; api GET "/api/im/chat/msg/page?roomId=$RID&pageSize=5" "$TC" | json "d['code'], [m['message']['type'] for m in d['data']['list']]"
echo "== update room info"; api POST /api/im/room/updateRoomInfo "$TA" "{\"id\":$RID,\"name\":\"renamed\",\"avatar\":\"002\",\"allowScanEnter\":true}" | json "d['code'], d['data']"
echo "== C exits (3 members -> disband)"; api DELETE /api/im/room/group/member/exit "$TC" "{\"roomId\":$RID}" | json "d['code'], d['msg']"
echo "== A group list"; api GET /api/im/room/group/list "$TA" | json "d['code'], [g['groupName'] for g in d['data']]"
echo "== A deletes friend B"; api DELETE /api/im/user/friend "$TA" "{\"targetUid\":$UB}" | json "d['code']"
sleep 9
echo "== B websocket frames"; grep -o '"type":"[a-zA-Z]*"' /tmp/ws_b_$TS.log | sort | uniq -c
