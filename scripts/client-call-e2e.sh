#!/usr/bin/env bash
# Voice and video calls through the desktop client's frontend, driven by dsb.
# Headless Chrome has no camera or microphone: the shim's fake media (localStorage __shim_fake_media=1)
# hands out a generated tone and an animated canvas. The call windows open as tabs; WebRTC offer, answer
# and ICE candidates travel through the server's WebSocket signaling, and the server writes the call
# record message when a call ends.
#  1. voice call: alice calls, bob (looking at alice's chat) accepts, both connect, alice hangs up
#  2. video call: bob is looking at another chat when the call comes in, accepts, both connect, bob hangs up
#  3. bob rejects a voice call      4. alice cancels a video call before bob answers
# Usage: scripts/client-call-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./lib.sh
source ./client-lib.sh
task dsb_a "${A_ID:-4331}"
task dsb_b "${B_ID:-4332}"

read EA UA EB UB < <(./make-friends.sh | tail -1)
NA=${EA%@*}; NB=${EB%@*}
echo "alice=$EA bob=$EB"

login dsb_a "$A_URL" "$EA" "$NB"
login dsb_b "$B_URL" "$EB" "$NA"
for d in dsb_a dsb_b; do
  $d js "(() => { localStorage.setItem('__shim_fake_media', '1'); return 1 })()"
done
open_chat dsb_a "$NB"
use_tab dsb_b /message
open_chat dsb_b "$NA"

# call <task> <icon>: start a call from the open chat's header and switch to the call window
call() {
  use_tab "$1" /message
  "$1" run click_element_by_selector -p "selector=svg:has(> use[href=\"#$2\"])"
  use_tab "$1" /rtcCall
}

# ringing <task> <text>: the incoming call window shows the caller and the call type
ringing() {
  use_tab "$1" /rtcCall
  wait_text "$1" "$2" 15000
}

# connected <task>: the peer connection is up and the call timer runs (voice calls also show 通话中)
connected() {
  use_tab "$1" /rtcCall
  "$1" run wait_for_function -p "expression=/00:(0[1-9]|[1-5][0-9])/.test(document.body.innerText)" -p timeoutSeconds=20
}

# closed <task>: the call window closed itself (only the main tab is left)
closed() {
  local i n
  for i in $(seq 1 30); do
    n=$("$1" run get_tabs | python3 -c "import sys, json; print(len(json.loads(sys.stdin.read()).get('tabs', [])))")
    [ "$n" = 1 ] && return 0
    sleep 0.5
  done
  echo "call window still open" >&2
  return 1
}

hangup() {
  "$1" run click_element_by_selector -p 'selector=div.cursor-pointer:has(> svg > use[href="#PhoneHangup"])'
}

accept() {
  "$1" run click_element_by_selector -p 'selector=div.cursor-pointer:has(> svg > use[href="#phone-telephone-entity"])'
}

# record <task> <text>: the chat shows the call record message
record() {
  use_tab "$1" /message
  wait_text "$1" "$2" 15000
}

# 1. voice call, bob looks at alice's chat
call dsb_a phone-telephone
wait_text dsb_a 正在呼叫
ringing dsb_b "来电 · 语音通话"
shot dsb_b 41-voice-incoming.png
accept dsb_b
connected dsb_b
connected dsb_a
wait_text dsb_a 通话中
shot dsb_a 42-voice-connected-alice.png
use_tab dsb_b /rtcCall
shot dsb_b 43-voice-connected-bob.png
dsb_b run wait_for_function -p "expression=(() => { const a = document.querySelector('audio'); return !!a && !!a.srcObject && a.srcObject.getAudioTracks().length > 0 })()" -p timeoutSeconds=5
sleep 2
use_tab dsb_a /rtcCall
hangup dsb_a
closed dsb_a
closed dsb_b
record dsb_a 通话时长
record dsb_b 通话时长
shot dsb_a 44-voice-record.png

# 2. video call while bob is looking at another chat
use_tab dsb_b /message
open_chat dsb_b 官方频道
call dsb_a video-one
ringing dsb_b "来电 · 视频通话"
accept dsb_b
connected dsb_b
connected dsb_a
dsb_a run wait_for_function -p "expression=[...document.querySelectorAll('video')].filter(v => v.srcObject && v.videoWidth > 0).length >= 2" -p timeoutSeconds=15
shot dsb_a 45-video-connected-alice.png
use_tab dsb_b /rtcCall
dsb_b run wait_for_function -p "expression=[...document.querySelectorAll('video')].filter(v => v.srcObject && v.videoWidth > 0).length >= 2" -p timeoutSeconds=15
shot dsb_b 46-video-connected-bob.png
hangup dsb_b
closed dsb_b
closed dsb_a
use_tab dsb_b /message
open_chat dsb_b "$NA"
record dsb_b 通话时长
record dsb_a 通话时长

# 3. bob rejects
call dsb_a phone-telephone
ringing dsb_b "来电 · 语音通话"
hangup dsb_b
closed dsb_b
closed dsb_a
# texts follow the original server: a rejected voice call reads 已拒绝 on both sides
record dsb_a 已拒绝
record dsb_b 已拒绝
shot dsb_a 47-rejected-alice.png

# 4. alice cancels before bob answers
call dsb_a video-one
ringing dsb_b "来电 · 视频通话"
use_tab dsb_a /rtcCall
hangup dsb_a
closed dsb_a
closed dsb_b
record dsb_a 已取消
record dsb_b 对方已取消
shot dsb_b 48-cancelled-bob.png

# the server stored the four call records in the chat
read TA2 _ < <(login_user "$EA" MOBILE m1)
RID=$(api GET "/api/im/chat/contact/detail/friend?id=$UB&roomType=2" "$TA2" | json "d['data']['roomId']")
api GET "/api/im/chat/msg/page?roomId=$RID&pageSize=10" "$TA2" \
  | json "[(m['message']['type'], m['message']['body']) for m in d['data']['list'] if m['message']['type'] in (12, 13)]"

finish dsb_a
finish dsb_b
ls -la "$OUT"
