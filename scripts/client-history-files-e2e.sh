#!/usr/bin/env bash
# End-to-end test of the chat history window and the file manager window in the desktop client's frontend
# (browser shim) against tio-chat-server. Both windows query the client's local SQLite in the desktop app;
# the shim answers them from /api/im/chat/msg/search and /api/im/chat/msg/files.
#  - history: all messages, keyword search, the image/video and file tabs, a date range (today / a past day)
#  - file manager: my files, filter by sender, filter by session, keyword search, download from the list
# Bob and Carol send their messages over the API (files are uploaded to MinIO first), Alice uses the client.
# Needs the MinIO storage engine. Usage: scripts/client-history-files-e2e.sh [output-dir]  (run under sg docker)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./lib.sh
source ./client-lib.sh
task dsb_a "${A_ID:-4371}"

befriend() { # befriend <tokenFrom> <tokenTo> <uidTo>
  api POST /api/im/room/apply/apply "$1" "{\"targetUid\":$3,\"msg\":\"hi\"}" >/dev/null
  local apply
  apply=$(api GET "/api/im/room/notice/page?pageNo=1&pageSize=10" "$2" | json "d['data']['list'][0]['applyId']")
  api POST /api/im/room/apply/handler/apply "$2" "{\"applyId\":$apply,\"state\":1}" >/dev/null
}
upload() { # upload <file> <content-type> -> download url
  local r
  r=$(curl -s "$HOST/api/system/anyTenant/ossToken?scene=chat&fileName=$(basename "$1")")
  curl -s -f -X PUT -H "Content-Type: $2" --data-binary @"$1" "$(echo "$r" | json "d['data']['uploadUrl']")" >/dev/null
  echo "$r" | json "d['data']['downloadUrl']"
}
room_with() { # room_with <token> <friend uid> -> room id
  api GET /api/im/chat/contact/list "$1" | json "[c['roomId'] for c in d['data'] if str(c['detailId'])=='$2'][0]"
}
msg() { # msg <token> <json>
  local code
  code=$(api POST /api/im/chat/msg "$1" "$2" | json "d['code']")
  [ "$code" = 200 ] || { echo "send failed: $2" >&2; exit 1; }
}
# Count what a window shows: the number of elements matching <selector> whose text includes <text>
count() {
  dsb_a js "Array.from(document.querySelectorAll('$1')).filter(e => e.innerText.includes('$2')).length" | grep -o '[0-9]*$'
}
# Files listed by the file manager whose card text includes <text>
files() {
  dsb_a js "Array.from(document.querySelectorAll('.file-meta-info')).filter(e => e.parentElement.innerText.includes('$1')).length" | grep -o '[0-9]*$'
}
expect_eq() { # expect_eq <label> <actual> <expected>
  if [ "$2" != "$3" ]; then
    echo "FAIL $1: got '$2', expected '$3'" >&2
    exit 1
  fi
  echo "ok   $1 = $2"
}

TS=$(date +%s)
EA=alice$TS@example.com
read TA UA < <(register_user "$EA" MOBILE m1)
read TB UB < <(register_user "bob$TS@example.com" MOBILE m1)
read TC UC < <(register_user "carol$TS@example.com" MOBILE m1)
befriend "$TA" "$TB" "$UB"
befriend "$TA" "$TC" "$UC"
RB=$(room_with "$TA" "$UB"); RC=$(room_with "$TA" "$UC")
echo "alice=$EA room(bob)=$RB room(carol)=$RC"

# Test files: a 160x100 PNG, a text file per sender
python3 -I - "$OUT" "$TS" <<'EOF'
import sys, zlib, struct
d, ts = sys.argv[1], sys.argv[2]; w, h = 160, 100
raw = b''.join(b'\x00' + bytes(sum(([x * 255 // w, 120, y * 255 // h] for x in range(w)), [])) for y in range(h))
def chunk(t, b):
    return struct.pack('>I', len(b)) + t + b + struct.pack('>I', zlib.crc32(t + b) & 0xffffffff)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
open(d + '/photo.png', 'wb').write(png)
open(d + '/budget-%s.txt' % ts, 'w').write('budget\n' * 300)
open(d + '/notes-%s.txt' % ts, 'w').write('notes\n' * 100)
EOF
IMG=$(upload "$OUT/photo.png" image/png)
BUDGET=$(upload "$OUT/budget-$TS.txt" text/plain)
NOTES=$(upload "$OUT/notes-$TS.txt" text/plain)
msg "$TB" "{\"roomId\":$RB,\"msgType\":1,\"body\":{\"content\":\"kiwi report is ready\"}}"
msg "$TA" "{\"roomId\":$RB,\"msgType\":1,\"body\":{\"content\":\"thanks, any KIWI numbers?\"}}"
msg "$TB" "{\"roomId\":$RB,\"msgType\":1,\"body\":{\"content\":\"lunch at noon\"}}"
msg "$TB" "{\"roomId\":$RB,\"msgType\":3,\"body\":{\"url\":\"$IMG\",\"size\":$(stat -c %s "$OUT/photo.png"),\"width\":160,\"height\":100}}"
msg "$TB" "{\"roomId\":$RB,\"msgType\":4,\"body\":{\"url\":\"$BUDGET\",\"size\":$(stat -c %s "$OUT/budget-$TS.txt"),\"fileName\":\"budget-$TS.txt\"}}"
msg "$TC" "{\"roomId\":$RC,\"msgType\":4,\"body\":{\"url\":\"$NOTES\",\"size\":$(stat -c %s "$OUT/notes-$TS.txt"),\"fileName\":\"notes-$TS.txt\"}}"

login dsb_a "$A_URL" "$EA" "carol$TS"
open_chat dsb_a "bob$TS"

# ---------- chat history window ----------
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#history"])'
use_tab dsb_a /chat-history
wait_text dsb_a "lunch at noon"
sleep 1
ITEM='.chat-history-container .px-4px'
expect_eq "history: all messages" "$(count "$ITEM" '')" 6
shot dsb_a 70-history-all.png

dsb_a run input_text_by_selector -p 'selector=.search-section input' -p text=kiwi
dsb_a run wait_for_function -p "expression=!document.body.innerText.includes('lunch at noon')" -p timeoutSeconds=10
sleep 1
expect_eq "history: keyword kiwi (case-insensitive)" "$(count "$ITEM" '')" 2
shot dsb_a 71-history-keyword.png
dsb_a run click_element_by_selector -p 'selector=.search-section .n-base-clear__clear'
wait_text dsb_a "lunch at noon"

dsb_a run click_element_by_selector -p 'selector=.n-tabs-tab[data-name=image]'
dsb_a run wait_for_function -p "expression=!document.body.innerText.includes('lunch at noon') && Array.from(document.querySelectorAll('.chat-history-container img')).some(i => i.complete && i.naturalWidth === 160)" -p timeoutSeconds=10
expect_eq "history: image/video tab" "$(count "$ITEM" '')" 1
shot dsb_a 72-history-images.png

dsb_a run click_element_by_selector -p 'selector=.n-tabs-tab[data-name=file]'
wait_text dsb_a "budget-"
expect_eq "history: file tab" "$(count "$ITEM" "budget-")" 1
shot dsb_a 73-history-files.png

# date range: today shows the messages, a single past day shows none
dsb_a run click_element_by_selector -p 'selector=.n-tabs-tab[data-name=all]'
wait_text dsb_a "lunch at noon"
pick_day() { # pick_day <selector of the day cell>
  dsb_a run click_element_by_selector -p 'selector=.tab-section .n-date-picker'
  dsb_a run wait_for_element -p 'selector=.n-date-panel' -p timeoutMs=5000
  dsb_a run click_element_by_selector -p "selector=$1"
  dsb_a run click_element_by_selector -p "selector=$1"
  dsb_a run click_element_by_selector -p 'selector=.n-date-panel-actions .n-button--primary-type'
}
pick_day '.n-date-panel-calendar--start .n-date-panel-date--current'
sleep 1.5
expect_eq "history: today" "$(count "$ITEM" '')" 6
shot dsb_a 74-history-today.png
dsb_a run click_element_by_selector -p 'selector=.tab-section .n-date-picker'
dsb_a run click_element_by_text -p text=清除 -p exact=true
sleep 1
pick_day '.n-date-panel-calendar--start .n-date-panel-date:not(.n-date-panel-date--excluded):not(.n-date-panel-date--current)'
dsb_a run wait_for_function -p "expression=!document.body.innerText.includes('lunch at noon')" -p timeoutSeconds=10
expect_eq "history: a past day" "$(count "$ITEM" '')" 0
shot dsb_a 75-history-past-day.png

# ---------- file manager window ----------
use_tab dsb_a /message
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#file"])'
use_tab dsb_a /fileManager
wait_text dsb_a "notes-"
expect_eq "files: my files (budget)" "$(files budget-)" 1
expect_eq "files: my files (notes)" "$(files notes-)" 1
shot dsb_a 80-files-all.png

# keyword search in the file list
dsb_a run input_text_by_selector -p 'selector=.n-input input[placeholder*=搜索我的文件]' -p text=notes
dsb_a run wait_for_function -p "expression=!document.body.innerText.includes('budget-')" -p timeoutSeconds=10
expect_eq "files: keyword notes" "$(files '')" 1
dsb_a run click_element_by_selector -p 'selector=.n-input:has(input[placeholder*=搜索我的文件]) .n-base-clear__clear'
wait_text dsb_a "budget-"

# by sender: carol -> files of the conversations carol takes part in
dsb_a run click_element_by_text -p text=按发送人 -p exact=true
dsb_a run click_element_by_text -p "text=carol$TS"
dsb_a run wait_for_function -p "expression=!document.body.innerText.includes('budget-') && document.body.innerText.includes('notes-')" -p timeoutSeconds=10
expect_eq "files: sender carol" "$(files '')" 1
shot dsb_a 81-files-sender.png

# by session: the conversation with bob
dsb_a run click_element_by_text -p text=按会话 -p exact=true
dsb_a run click_element_by_text -p "text=bob$TS"
dsb_a run wait_for_function -p "expression=!document.body.innerText.includes('notes-') && document.body.innerText.includes('budget-')" -p timeoutSeconds=10
expect_eq "files: session bob" "$(files '')" 1
shot dsb_a 82-files-session.png

# open: double-click downloads the file into the shim's file system and opens it in a new tab
XY=$(dsb_a js "(() => { const r = document.querySelector('.file-meta-info').previousElementSibling.getBoundingClientRect(); return Math.round(r.x + 60) + 'x' + Math.round(r.y + 30) })()" | grep -o '[0-9]*x[0-9]*' | tail -1)
dsb_a run mouse_click -p x="${XY%x*}" -p y="${XY#*x}" -p clickCount=2
dsb_a run wait_for_function -p "expression=window.__TAURI_SHIM__.opened.some(p => p.endsWith('budget-$TS.txt'))" -p timeoutSeconds=15
# save as: the save dialog hands out a downloads path, writing it starts a browser download
dsb_a run mouse_click_by_selector -p 'selector=.file-meta-info' -p button=right
dsb_a run click_element_by_text -p text=另存为
dsb_a run wait_for_function -p "expression=window.__TAURI_SHIM__.downloads.some(d => d.name === 'budget-$TS.txt' && d.size > 2000)" -p timeoutSeconds=15
shot dsb_a 83-files-saved.png
echo "shim diagnostics: $(dsb_a js "JSON.stringify({unhandled: [...window.__TAURI_SHIM__.unhandled], failures: window.__TAURI_SHIM__.failures})")"

# the opened file's tab shows its content
BLOB=$(dsb_a run get_tabs | python3 -c "import sys, json; print([t['index'] for t in json.loads(sys.stdin.read())['tabs'] if t['url'].startswith('blob:')][-1])")
dsb_a run switch_tab -p pageIndex="$BLOB"
dsb_a run wait_for_text -p text=budget -p timeoutMs=5000
dsb_a run screenshot -p "path=$OUT/84-files-opened.png"

finish dsb_a
echo "screenshots in $OUT"
