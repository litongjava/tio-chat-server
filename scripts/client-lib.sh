# Shared helpers for the client end-to-end tests (source this file).
# Requires: dsb on PATH, the client served by tools/web-shim/start-client.sh, OUT set to the screenshot dir.
WEB_PORT=${WEB_PORT:-6130}
A_URL=http://127.0.0.1:$WEB_PORT
B_URL=http://localhost:$WEB_PORT

# Define a command function bound to one dsb task: task <name> <id>
task() {
  eval "$1() { dsb --id $2 --summary \"\$@\" 2>&1 | tail -1; }"
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
