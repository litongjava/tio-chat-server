#!/usr/bin/env bash
# Group chat and message recall through the desktop client's frontend, driven by dsb.
# alice creates a group with bob and carol from the UI, alice and bob chat in it,
# then alice recalls a message and bob sees the recall live and after reloading.
# Usage: scripts/client-group-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./client-lib.sh
task dsb_a "${A_ID:-4201}"
task dsb_b "${B_ID:-4202}"

read EA UA EB UB EC UC < <(./make-group-users.sh | tail -1)
echo "alice=$EA bob=$EB carol=$EC"
GROUP=的群组
RECALL_TEXT=这条消息将被撤回

# alice creates the group from the "+" menu
login dsb_a "$A_URL" "$EA" "${EC%@*}"
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#plus"])'
dsb_a run click_element_by_text -p text=发起群聊
dsb_a run wait_for_text -p text=创建群聊 -p timeoutMs=10000
dsb_a run click_element_by_text -p text=全选
shot dsb_a 11-group-create.png
dsb_a run click_element_by_text -p text=创建 -p exact=true
dsb_a run wait_for_text -p text=创建群聊成功 -p timeoutMs=10000
dsb_a run wait_for_element -p selector=#message-input -p timeoutMs=10000
shot dsb_a 12-group-created.png
send dsb_a "大家好，这是群聊测试消息"

# bob sees the new group, reads alice's message and replies
login dsb_b "$B_URL" "$EB" "$GROUP"
open_chat dsb_b "$GROUP"
dsb_b run wait_for_text -p text=这是群聊测试消息 -p timeoutMs=10000
send dsb_b "收到，群消息实时推送正常"
dsb_a run wait_for_text -p text=群消息实时推送正常 -p timeoutMs=10000
sleep 1
shot dsb_a 13-group-alice.png
shot dsb_b 14-group-bob.png

# alice recalls a message from the context menu
send dsb_a "$RECALL_TEXT"
dsb_b run wait_for_text -p "text=$RECALL_TEXT" -p timeoutMs=10000
dsb_a run mouse_click_by_selector -p "selector=.bubble-oneself:has-text(\"$RECALL_TEXT\")" -p button=right
dsb_a run wait_for_element -p 'selector=.menu-item:has-text("撤回")' -p timeoutMs=5000
shot dsb_a 15-recall-menu.png
dsb_a run click_element_by_selector -p 'selector=.menu-item:has-text("撤回")'
dsb_a run wait_for_text -p text=你撤回了一条消息 -p timeoutMs=10000
dsb_b run wait_for_function -p "expression=document.body.innerText.includes('撤回了一条消息') && !document.body.innerText.includes('$RECALL_TEXT')" -p timeoutSeconds=10
sleep 1
shot dsb_a 16-recall-alice.png
shot dsb_b 17-recall-bob.png

# the recall is stored on the server: bob still sees it after reloading
dsb_b js "(() => { setTimeout(() => location.reload(), 10); return 1 })()"
sleep 2
wait_ready dsb_b "$GROUP"
open_chat dsb_b "$GROUP"
dsb_b run wait_for_function -p "expression=document.body.innerText.includes('撤回了一条消息') && !document.body.innerText.includes('$RECALL_TEXT')" -p timeoutSeconds=10
shot dsb_b 18-recall-bob-reload.png

dsb_a close
dsb_b close
ls -la "$OUT"
