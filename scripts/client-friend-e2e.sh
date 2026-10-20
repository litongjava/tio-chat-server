#!/usr/bin/env bash
# Friend requests and group join requests through the desktop client's frontend, driven by dsb.
# alice finds bob by account in the "add friend / group" window and sends a request; bob sees it
# live in his friend notices and accepts; both get the new conversation. Then bob finds alice's group
# by its number and applies to join; alice approves in the group notices and bob gets the group.
# Usage: scripts/client-friend-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./lib.sh
source ./client-lib.sh
task dsb_a "${A_ID:-4301}"
task dsb_b "${B_ID:-4302}"

# alice and bob are strangers; carol and dave are alice's friends so alice can own a group
befriend() { # befriend <tokenFrom> <tokenTo> <uidTo>
  api POST /api/im/room/apply/apply "$1" "{\"targetUid\":$3,\"msg\":\"hi\"}" >/dev/null
  local apply
  apply=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$2" | json "d['data']['list'][0]['applyId']")
  api POST /api/im/room/apply/handler/apply "$2" "{\"applyId\":$apply,\"state\":1}" >/dev/null
}
TS=$(date +%s)
EA=alice$TS@example.com; EB=bob$TS@example.com
read TA UA < <(register_user "$EA")
read TB UB < <(register_user "$EB")
read TC UC < <(register_user "carol$TS@example.com")
read TD UD < <(register_user "dave$TS@example.com")
befriend "$TA" "$TC" "$UC"
befriend "$TA" "$TD" "$UD"
GROUP_NAME=读书会$((TS % 10000))
RID=$(api POST /api/im/room/group "$TA" "{\"groupName\":\"$GROUP_NAME\",\"uidList\":[$UC,$UD]}" | json "d['data']['id']")
GROUP_ACCOUNT=$(api GET "/api/im/room/group/detail?id=$RID" "$TA" | json "d['data']['account']")
BOB_ACCOUNT=$(api GET /api/im/user/userInfo "$TB" | json "d['data']['account']")
NA=${EA%@*}; NB=${EB%@*}
echo "alice=$EA bob=$EB (account $BOB_ACCOUNT) group=$GROUP_NAME ($GROUP_ACCOUNT)"

login dsb_a "$A_URL" "$EA" "$GROUP_NAME"
login dsb_b "$B_URL" "$EB" "小管家"
dsb_b run click_element_by_selector -p 'selector=svg:has(> use[href="#avatar"])'
wait_text dsb_b 好友通知

# alice: "+" -> 加好友/群 opens the search window in a new tab
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#plus"])'
dsb_a run click_element_by_text -p text=加好友/群
use_tab dsb_a /searchFriend
wait_text dsb_a 找好友
dsb_a run click_element_by_text -p text=找好友 -p exact=true
dsb_a run input_text_by_selector -p selector=input -p "text=$BOB_ACCOUNT"
dsb_a run press_key -p key=Enter
wait_text dsb_a "账号：$BOB_ACCOUNT"
shot dsb_a 21-search-user.png
dsb_a run click_element_by_text -p text=添加 -p exact=true
use_tab dsb_a /addFriendVerify
wait_text dsb_a "账号: $BOB_ACCOUNT"
dsb_a run input_text_by_selector -p selector=textarea -p "text=我是 alice，通过账号搜索到你"
shot dsb_a 22-add-friend-verify.png
dsb_a run click_element_by_text -p text=添加好友 -p exact=true
wait_text dsb_a 已发送好友申请

# bob gets the request live (newApply) and accepts it in the friend notices
dsb_b run wait_for_function -p "expression=document.querySelector('.n-badge-sup') !== null" -p timeoutSeconds=10
dsb_b run click_element_by_text -p text=好友通知 -p exact=true
wait_text dsb_b "留言：我是 alice，通过账号搜索到你"
shot dsb_b 23-bob-friend-notice.png
dsb_b run click_element_by_text -p text=接受 -p exact=true
wait_text dsb_b 已同意
wait_text dsb_b "$NA"
shot dsb_b 24-bob-accepted.png

# both see the new conversation without reloading
use_tab dsb_a /message
wait_text dsb_a "$NB"
wait_text dsb_a 我们已经成为好友了
shot dsb_a 25-alice-new-chat.png
dsb_b run click_element_by_selector -p 'selector=svg:has(> use[href="#message-action"]), svg:has(> use[href="#message"])'
wait_text dsb_b 我们已经成为好友了
open_chat dsb_b "$NA"
send dsb_b "你好 alice，已经通过了你的好友申请"
open_chat dsb_a "$NB"
wait_text dsb_a 已经通过了你的好友申请
shot dsb_a 26-alice-chat-with-bob.png

# bob: find alice's group by its number and apply to join
dsb_b run click_element_by_selector -p 'selector=svg:has(> use[href="#plus"])'
dsb_b run click_element_by_text -p text=加好友/群
use_tab dsb_b /searchFriend
wait_text dsb_b 找群聊
dsb_b run click_element_by_text -p text=找群聊 -p exact=true
dsb_b run input_text_by_selector -p selector=input -p "text=$GROUP_ACCOUNT"
dsb_b run press_key -p key=Enter
wait_text dsb_b "$GROUP_NAME"
shot dsb_b 27-search-group.png
dsb_b run click_element_by_text -p text=添加 -p exact=true
use_tab dsb_b /addGroupVerify
wait_text dsb_b "群号: $GROUP_ACCOUNT"
dsb_b run input_text_by_selector -p selector=textarea -p "text=我是 bob，想加入$GROUP_NAME"
dsb_b run click_element_by_text -p text=申请加入 -p exact=true
wait_text dsb_b 已发送群聊申请

# alice approves in the group notices; bob gets the group conversation
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#avatar"])'
dsb_a run click_element_by_text -p text=群通知 -p exact=true
wait_text dsb_a "我是 bob，想加入$GROUP_NAME"
shot dsb_a 28-alice-group-notice.png
dsb_a run click_element_by_text -p text=接受 -p exact=true
wait_text dsb_a 已同意
use_tab dsb_b /message
wait_text dsb_b "$GROUP_NAME" 15000
open_chat dsb_b "$GROUP_NAME"
send dsb_b "大家好，我是新成员 bob"
shot dsb_b 29-bob-joined-group.png
# the browser login replaced alice's earlier PC token; check through a second device session
read TA2 _ < <(login_user "$EA" MOBILE m1)
MEMBERS=$(api GET "/api/im/room/group/listMember?roomId=$RID" "$TA2" | json "len(d['data'])")
echo "group members: $MEMBERS"
[ "$MEMBERS" = 4 ]

dsb_a close
dsb_b close
ls -la "$OUT"
