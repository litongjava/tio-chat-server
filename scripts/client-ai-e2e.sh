#!/usr/bin/env bash
# End-to-end test of the client's AI chat (robot page) against tio-chat-server with a real model provider.
# Needs a public chat model and chat role, e.g. from scripts/setup-deepseek.sh, plus the client served by
# tools/web-shim/start-client.sh. Covers: new conversation, model selection, streamed answer, history after
# reload, deep-thinking output and stopping a running answer.
# Usage: scripts/client-ai-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./lib.sh
source ./client-lib.sh
D_ID=${D_ID:-4301}
MODEL=${MODEL:-deepseek-flash}
task d "$D_ID"
trap 'd close >/dev/null 2>&1 || true' EXIT

E="ai$(date +%s)@example.com"
read T U < <(register_user "$E")
api GET "/api/ai/model/simple-list?type=1" "$T" | json "[m['name'] for m in d['data']]" | grep -q "$MODEL" \
  || { echo "model $MODEL is not configured, run scripts/setup-deepseek.sh first" >&2; exit 1; }
echo "user=$E model=$MODEL"

# js_true <expression> <timeout seconds>: wait until the expression is true in the page
js_true() {
  d run wait_for_function -p "expression=$1" -p "timeoutSeconds=${2:-60}"
}
# the text of the last assistant bubble
LAST_ANSWER="(() => { const m = document.querySelectorAll('.bubble-ai'); return m.length ? m[m.length - 1].innerText : '' })()"
NOT_STREAMING="!document.body.innerText.includes('停止思考') && !document.body.innerText.includes('正在思考中')"

# 1. log in, open the AI window and start a conversation
d start --browser chrome --headless
d run set_viewport -p width=1100 -p height=760
d run go_to_url -p "url=$A_URL/login"
d js "(() => { localStorage.clear(); sessionStorage.clear(); window.name = ''; return 1 })()"
d run go_to_url -p "url=$A_URL/login"
d run wait_for_element -p selector=.driver-popover-next-btn -p timeoutMs=20000
for i in 1 2 3 4; do
  d run click_element_by_selector -p selector=.driver-popover-next-btn
  sleep 0.6
done
d run input_text_by_selector -p "selector=input[type=text]" -p "text=$E"
d run input_text_by_selector -p "selector=input[type=password]" -p text=123456
d run click_element_by_text -p text=登录 -p exact=true
d run wait_for_url -p "url=**/message" -p timeoutMs=20000
# the robot plugin is its own window in the desktop app; in the browser it is the /robot page
d js "(() => { window.name = 'robot'; return 1 })()"
d run go_to_url -p "url=$A_URL/robot"
wait_ready d 新的聊天
d run click_element_by_text -p text=新的聊天
wait_ready d 会话创建成功
js_true "document.body.innerText.includes('$MODEL')" 20

# 2. pick the model in the model selector
d js "(() => { const t = Array.from(document.querySelectorAll('span')).find(e => e.innerText.trim() === '$MODEL' && e.closest('.cursor-pointer')); t.click(); return 1 })()"
js_true "document.querySelectorAll('.model-item').length > 0" 10
shot d 01-ai-models.png
d js "(() => { Array.from(document.querySelectorAll('.model-item')).find(e => e.innerText.includes('$MODEL')).click(); return 1 })()"
sleep 1

# 3. ask a question; the answer streams in and is rendered
send d "中国的首都是哪座城市？请用一句中文回答，并顺带说一个它的著名景点。"
js_true "$NOT_STREAMING && document.body.innerText.includes('北京')" 90
sleep 1
shot d 02-ai-answer.png

# 4. reload: the conversation and its messages come back from the server
d run go_to_url -p "url=$A_URL/robot"
js_true "document.body.innerText.includes('中国的首都是哪座城市') && document.body.innerText.includes('北京')" 30
sleep 1
shot d 03-ai-history.png

# 5. deep thinking: the reasoning shows up above the answer
d run click_element_by_selector -p selector=.n-switch
js_true "document.body.innerText.includes('深度思考')" 5
send d "9.11 和 9.9 哪个大？一句话回答。"
js_true "$NOT_STREAMING && document.body.innerText.includes('思考过程')" 120
sleep 1
shot d 04-ai-reasoning.png
d run click_element_by_selector -p selector=.n-switch

# 6. stop a long answer while it streams; what was shown is kept
send d "请写一篇两千字的文章，详细介绍中国古代四大发明的历史和影响。"
js_true "document.body.innerText.includes('停止思考') && $LAST_ANSWER.length > 40" 60
shot d 05-ai-streaming.png
d run click_element_by_text -p text=停止思考
js_true "document.body.innerText.includes('已停止生成')" 15
sleep 1
shot d 06-ai-stopped.png
SHOWN=$(d js "$LAST_ANSWER.length" | grep -o 'result=[0-9]*' | head -1 | cut -d= -f2)
sleep 3

# 7. server side: the conversation holds every question and answer, the stopped answer is not empty
# (a separate device type, so the browser session stays logged in)
read T _ < <(login_user "$E" API c9)
CONV=$(api GET "/api/ai/chat/conversation/my-list" "$T" | json "d['data'][0]['id']")
api GET "/api/ai/chat/message/list-by-conversation-id?conversationId=$CONV" "$T" \
  | json "[(m['type'], len(m['content']), len(m['reasoningContent'] or '')) for m in d['data']]"
api GET "/api/ai/chat/message/list-by-conversation-id?conversationId=$CONV" "$T" \
  | json "len(d['data']) == 6 and all(m['content'] for m in d['data']) and bool(d['data'][3]['reasoningContent'])" | grep -q True
echo "stopped answer: ${SHOWN:-?} chars shown in the client"
d run go_to_url -p "url=$A_URL/robot"
js_true "document.body.innerText.includes('四大发明')" 30
sleep 1
shot d 07-ai-history-after-stop.png

finish d
ls -la "$OUT"
