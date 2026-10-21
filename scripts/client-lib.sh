# Shared helpers for the client end-to-end tests (source this file).
# Requires: dsb on PATH, the client served by tools/web-shim/start-client.sh, OUT set to the screenshot dir.
WEB_PORT=${WEB_PORT:-6130}
A_URL=http://127.0.0.1:$WEB_PORT
B_URL=http://localhost:$WEB_PORT

# Define a command function bound to one dsb task: task <name> <id>
task() {
  eval "$1() { dsb --id $2 --summary \"\$@\" 2>&1 | tail -1; }"
  eval "$1_raw() { dsb --id $2 \"\$@\" 2>/dev/null | tail -1; }"
}

# Reset any scroll left by clicks, then capture: shot <task> <file>
shot() {
  "$1" js "(() => { document.scrollingElement.scrollTop = 0; return 1 })()" >/dev/null
  "$1" run screenshot -p "path=$OUT/$2"
}

# Wait until the page text contains <text> and nothing is loading: wait_ready <task> <text>
wait_ready() {
  "$1" run wait_for_function -p "expression=document.body.innerText.includes('$2') && !document.body.innerText.includes('加载')" -p timeoutSeconds=30
  sleep 1
}

# Log in from a clean client state: login <task> <origin> <email> <text expected after login>
login() {
  local d=$1 origin=$2 email=$3 expect=$4
  $d close >/dev/null 2>&1 || true
  $d start --browser chrome --headless
  $d run set_viewport -p width=1100 -p height=760
  $d run go_to_url -p "url=$origin/login"
  # the Chrome profile is shared between tasks, so clear this origin's state first
  $d js "(() => { localStorage.clear(); sessionStorage.clear(); window.name = ''; return 1 })()"
  $d run go_to_url -p "url=$origin/login"
  $d run wait_for_element -p selector=.driver-popover-next-btn -p timeoutMs=20000
  for i in 1 2 3 4; do
    $d run click_element_by_selector -p selector=.driver-popover-next-btn
    sleep 0.6
  done
  $d run input_text_by_selector -p "selector=input[type=text]" -p "text=$email"
  $d run input_text_by_selector -p "selector=input[type=password]" -p text=123456
  $d run click_element_by_text -p text=登录 -p exact=true
  wait_ready "$d" "$expect"
}

# Open a conversation whose name contains <text>: open_chat <task> <text>
open_chat() {
  "$1" run click_element_by_text -p "text=$2"
  "$1" run wait_for_element -p selector=#message-input -p timeoutMs=10000
}

# Type and send a message: send <task> <text>
send() {
  "$1" run click_element_by_selector -p selector=#message-input
  "$1" run input_text_by_selector -p selector=#message-input -p "text=$2"
  "$1" run press_key -p key=Enter
}

# Switch to the tab (secondary windows open as tabs) whose URL path starts with <path>, waiting for it to open:
# use_tab <task> <path>
use_tab() {
  local i idx
  for i in $(seq 1 20); do
    idx=$("$1" run get_tabs | python3 -c "
import sys, json
from urllib.parse import urlparse
tabs = json.loads(sys.stdin.read()).get('tabs', [])
hits = [t['index'] for t in tabs if urlparse(t['url']).path.startswith(sys.argv[1])]
print(hits[-1] if hits else '')" "$2" 2>/dev/null || true)
    # tabs that close meanwhile shift the indexes, so look again when switching fails
    if [ -n "$idx" ] && "$1" run switch_tab -p pageIndex="$idx" | grep -q " OK "; then
      "$1" run set_viewport -p width=1100 -p height=760 >/dev/null
      return 0
    fi
    sleep 0.5
  done
  echo "tab $2 not found" >&2
  return 1
}

# Wait until the page text contains <text>: wait_text <task> <text> [timeoutMs]
wait_text() {
  "$1" run wait_for_text -p "text=$2" -p "timeoutMs=${3:-10000}"
}

# Close a task without leaving pages behind (the shared browser keeps the last task's pages,
# and windows opened as tabs would keep running the client): finish <task>
finish() {
  diagnose "$1" || true
  "$1" run close_other_tabs >/dev/null 2>&1 || true
  "$1" run go_to_url -p url=about:blank >/dev/null 2>&1 || true
  "$1" close
}

# Append the shim's diagnostics of every open tab (commands without a browser implementation and failed
# commands, see window.__TAURI_SHIM__) to $OUT/shim-diagnostics.log: diagnose <task>
diagnose() {
  local n i
  n=$("$1" run get_tabs | python3 -c "import sys, json; print(len(json.loads(sys.stdin.read()).get('tabs', [])))")
  for i in $(seq 0 $((n - 1))); do
    "$1" run switch_tab -p pageIndex="$i" >/dev/null
    "$1_raw" js "JSON.stringify(window.__TAURI_SHIM__ ? {url: location.pathname, unhandled: [...window.__TAURI_SHIM__.unhandled], failures: window.__TAURI_SHIM__.failures} : {url: location.href})" \
      | python3 -c "import sys, json; print('$(basename "$0") $1', json.loads(sys.stdin.read()))" >> "$OUT/shim-diagnostics.log"
  done
}
