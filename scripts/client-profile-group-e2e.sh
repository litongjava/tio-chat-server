#!/usr/bin/env bash
# Settings, profile, online state and group management through the desktop client's frontend, driven by dsb.
#  - settings window: every tab opens, the storage tab scans (the shim answers with the browser's quota)
#  - profile: change the nickname and upload a new avatar (cropped, uploaded to MinIO)
#  - online state: alice switches to "busy" and bob sees it live
#  - group: announcement, invite a friend, set an admin, remove members from the context menu and
#    from the "manage members" dialog
# Needs the MinIO storage engine for the avatar. Usage: scripts/client-profile-group-e2e.sh [output-dir]
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./lib.sh
source ./client-lib.sh
task dsb_a "${A_ID:-4351}"
task dsb_b "${B_ID:-4352}"

befriend() { # befriend <tokenFrom> <tokenTo> <uidTo>
  api POST /api/im/room/apply/apply "$1" "{\"targetUid\":$3,\"msg\":\"hi\"}" >/dev/null
  local apply
  apply=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$2" | json "d['data']['list'][0]['applyId']")
  api POST /api/im/room/apply/handler/apply "$2" "{\"applyId\":$apply,\"state\":1}" >/dev/null
}
TS=$(date +%s)
EA=alice$TS@example.com; EB=bob$TS@example.com
read TA UA < <(register_user "$EA" MOBILE m1)
read TB UB < <(register_user "$EB" MOBILE m1)
read TC UC < <(register_user "carol$TS@example.com" MOBILE m1)
read TD UD < <(register_user "dave$TS@example.com" MOBILE m1)
read TE UE < <(register_user "eve$TS@example.com" MOBILE m1)
for f in "$TB $UB" "$TC $UC" "$TD $UD" "$TE $UE"; do
  set -- $f
  befriend "$TA" "$1" "$2"
done
NB=bob$TS; NC=carol$TS; ND=dave$TS; NE=eve$TS
GROUP=项目组$((TS % 1000))
RID=$(api POST /api/im/room/group "$TA" "{\"groupName\":\"$GROUP\",\"uidList\":[$UB,$UC,$UD]}" | json "d['data']['id']")
NEW_NAME=爱丽丝$((TS % 100))
echo "alice=$EA group=$GROUP"
DIAG="JSON.stringify({unhandled: [...window.__TAURI_SHIM__.unhandled], failures: window.__TAURI_SHIM__.failures})"

# a 120x120 avatar image
python3 -I - "$OUT/avatar.png" <<'EOF'
import sys, zlib, struct
w = h = 120
raw = b''.join(b'\x00' + bytes(sum(([40 + x, 120, 200 - y] for x in range(w)), [])) for y in range(h))
def chunk(t, b):
    return struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
open(sys.argv[1], 'wb').write(png)
EOF

login dsb_a "$A_URL" "$EA" "$GROUP"
login dsb_b "$B_URL" "$EB" "alice$TS"
open_chat dsb_b "alice$TS"

# settings window: every tab, the storage scan, and no command the shim does not know
use_tab dsb_a /message
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#hamburger-button"])'
dsb_a run click_element_by_text -p text=设置 -p exact=true
use_tab dsb_a /general
wait_text dsb_a 外观
for tab in 通知 快捷键 登录 存储; do
  dsb_a run click_element_by_text -p "text=$tab" -p exact=true
  sleep 1
done
dsb_a run click_element_by_text -p text=开始扫描 -p exact=true
wait_text dsb_a 磁盘分区总容量
shot dsb_a 61-settings-storage.png
dsb --id "${A_ID:-4351}" js "$DIAG" --select data.result | tee /dev/stderr | grep -q '\[\],\\"failures\\":\[\]'
finish_tab() { "$1" js "(() => { window.close(); return 1 })()" >/dev/null; }
finish_tab dsb_a

# profile: nickname and avatar
use_tab dsb_a /message
dsb_a run click_element_by_selector -p 'selector=main.left .n-avatar'
dsb_a run click_element_by_text -p text=编辑资料 -p exact=true
dsb_a run wait_for_element -p 'selector=.n-modal input' -p timeoutMs=5000
dsb_a run input_text_by_selector -p 'selector=.n-modal input' -p "text=$NEW_NAME"
dsb_a run click_element_by_text -p text=保存 -p exact=true
wait_text dsb_a 保存成功
dsb_a run upload_file -p 'selector=input[type=file]' -p "contentBase64=$(base64 -w0 "$OUT/avatar.png")" -p filename=avatar.png -p contentType=image/png
wait_text dsb_a 裁剪头像
dsb_a run click_element_by_text -p text=确定 -p exact=true
dsb_a run wait_for_function -p "expression=!document.body.innerText.includes('裁剪头像')" -p timeoutSeconds=15
sleep 1
shot dsb_a 62-profile-edited.png
read TA2 _ < <(login_user "$EA" MOBILE m2)
api GET /api/im/user/userInfo "$TA2" | json "d['data']['name'] + ' ' + d['data']['avatar']" | tee /dev/stderr \
  | grep -q "^$NEW_NAME http.*/avatar/"

# online state: alice is busy, bob sees it in the chat header
dsb_a run go_to_url -p "url=$A_URL/message"
wait_ready dsb_a "$GROUP"
dsb_a run click_element_by_selector -p 'selector=main.left .n-avatar'
dsb_a run click_element_by_text -p text=在线 -p exact=true
use_tab dsb_a /onlineStatus
wait_text dsb_a 请勿打扰
dsb_a run click_element_by_text -p text=忙碌 -p exact=true
use_tab dsb_b /message
wait_text dsb_b 忙碌
shot dsb_b 63-bob-sees-busy.png

# group announcement
use_tab dsb_a /message
open_chat dsb_a "$GROUP"
dsb_a run click_element_by_text -p text=群公告须知
use_tab dsb_a /announList
dsb_a run wait_for_element -p selector=textarea -p timeoutMs=10000
dsb_a run input_text_by_selector -p selector=textarea -p "text=本周五下午三点项目例会，请准时参加"
dsb_a run click_element_by_text -p text=发布 -p exact=true
use_tab dsb_b /message
open_chat dsb_b "$GROUP"
wait_text dsb_b 本周五下午三点项目例会
shot dsb_b 64-bob-announcement.png

# invite eve from the chat header; eve accepts on her phone
use_tab dsb_a /message
dsb_a run click_element_by_selector -p 'selector=div.options-box:has(svg > use[href="#launch"])'
use_tab dsb_a /modal-invite
wait_text dsb_a "$NE"
dsb_a run click_element_by_text -p "text=$NE"
shot dsb_a 65-invite.png
dsb_a run click_element_by_text -p text=确定 -p exact=true
INVITE=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$TE" | json "[n['applyId'] for n in d['data']['list'] if n['roomId'] == '$RID'][0]")
api POST /api/im/room/apply/handler/apply "$TE" "{\"applyId\":$INVITE,\"state\":1}" | json "d['code']" | grep -qx 200
use_tab dsb_a /message
wait_text dsb_a "邀请${NE}加入群聊"

# set bob as admin from the member list's context menu; bob sees the badge live
dsb_a run mouse_click_by_selector -p "selector=text=$NB >> nth=-1" -p button=right
dsb_a run click_element_by_selector -p 'selector=.menu-item:has-text("设为管理员")'
wait_text dsb_a 设置管理员成功
use_tab dsb_b /message
wait_text dsb_b 管理员
shot dsb_b 66-bob-admin.png

# remove carol from the context menu, dave from the "manage members" dialog
use_tab dsb_a /message
dsb_a run mouse_click_by_selector -p "selector=text=$NC >> nth=-1" -p button=right
dsb_a run click_element_by_selector -p 'selector=.menu-item:has-text("移出群聊")'
wait_text dsb_a 移出群聊成功
dsb_a run click_element_by_selector -p 'selector=div.options-box:has(> svg > use[href="#more"])'
dsb_a run click_element_by_text -p text=管理群成员
dsb_a run wait_for_element -p 'selector=.n-modal .n-checkbox' -p timeoutMs=5000
dsb_a run click_element_by_selector -p "selector=.n-modal .n-checkbox:has-text(\"$ND\")"
dsb_a run click_element_by_text -p text=踢出群聊 -p exact=true
dsb_a run click_element_by_selector -p 'selector=.n-popover button:has-text("确定")'
dsb_a run wait_for_function -p "expression=!document.querySelector('.n-modal')" -p timeoutSeconds=10
sleep 1
shot dsb_a 67-members-managed.png
for i in $(seq 1 10); do
  MEMBERS=$(api GET "/api/im/room/group/listMember?roomId=$RID" "$TA2" | json "sorted((m['roleId'], m['name']) for m in d['data'])")
  [ "$MEMBERS" = "[(1, '$NEW_NAME'), (2, '$NB'), (3, '$NE')]" ] && break
  sleep 1
done
echo "members: $MEMBERS"
[ "$MEMBERS" = "[(1, '$NEW_NAME'), (2, '$NB'), (3, '$NE')]" ]

finish dsb_a
finish dsb_b
ls -la "$OUT"
