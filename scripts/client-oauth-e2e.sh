#!/usr/bin/env bash
# Third-party login (Gitee) callback flow in the desktop client's frontend (browser shim).
# Clicking the Gitee icon calls start_oauth_server (the shim hands out this origin and a one-time secret),
# fetches /api/oauth/anyTenant/gitee/authorize-url and opens the provider page in a new tab. Without
# provider credentials the provider step is skipped: the test opens the redirect the server would send after
# the provider callback (redirect?token=...&refreshToken=...&uid=...) with a token from a password login.
# The callback tab must check the secret, pass the tokens to the login page and close itself; the login
# page then enters the main window. A wrong secret must be rejected.
# Usage: scripts/client-oauth-e2e.sh [output-dir]   (run with docker access, e.g. sg docker -c ...)
cd "$(dirname "$0")"; set -eo pipefail
OUT=$(mkdir -p "${1:-client-e2e-out}" && cd "${1:-client-e2e-out}" && pwd)
source ./lib.sh
source ./client-lib.sh
task dsb_a "${A_ID:-4381}"

read EA UA EB UB < <(./make-friends.sh | tail -1)
echo "alice=$EA"

dsb_a close >/dev/null 2>&1 || true
dsb_a start --browser chrome --headless
dsb_a run set_viewport -p width=1100 -p height=760
dsb_a run go_to_url -p "url=$A_URL/login"
dsb_a js "(() => { localStorage.clear(); sessionStorage.clear(); window.name = ''; return 1 })()"
dsb_a run go_to_url -p "url=$A_URL/login"
dsb_a run wait_for_element -p selector=.driver-popover-next-btn -p timeoutMs=20000
for i in 1 2 3 4; do
  dsb_a run click_element_by_selector -p selector=.driver-popover-next-btn
  sleep 0.6
done
# the third-party icons are on the QR code login view
dsb_a js "(() => { [...document.querySelectorAll('*')].find(e => e.childElementCount === 0 && e.textContent.trim() === '扫码登录').click(); return 1 })()"
dsb_a run wait_for_element -p 'selector=svg:has(> use[href="#gitee-login"])' -p timeoutMs=10000
dsb_a run click_element_by_selector -p 'selector=svg:has(> use[href="#gitee-login"])'
dsb_a run wait_for_function -p "expression=!!localStorage.getItem('__shim_oauth_secret')" -p timeoutSeconds=10
SECRET=$(dsb_a_raw js "localStorage.getItem('__shim_oauth_secret')" | python3 -c "import sys, json; print(json.loads(sys.stdin.read()))")
# the provider authorize page the client opened (shell.open -> new tab), with the shim's redirect as state
for i in $(seq 1 20); do
  PROVIDER=$(dsb_a run get_tabs | python3 -c "import sys, json; print(next((t['url'] for t in json.loads(sys.stdin.read())['tabs'] if 'gitee.com' in t['url']), ''))")
  [ -n "$PROVIDER" ] && break
  sleep 0.5
done
[ -n "$PROVIDER" ] || { echo "FAIL the gitee authorize page was not opened" >&2; exit 1; }
echo "ok   provider page: ${PROVIDER:0:120}"

# a wrong secret is rejected and does not log in
read T R < <(curl -s -XPOST "$HOST/api/oauth/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" \
  -d "{\"account\":\"$EA\",\"password\":\"123456\",\"systemType\":2,\"deviceType\":\"PC\",\"clientId\":\"oauth\"}" \
  | json "d['data']['token'] + ' ' + d['data']['refreshToken']")
dsb_a js "(() => { window.open('/?secret=wrong?token=$T&refreshToken=$R&uid=$UA', '_blank'); return 1 })()" >/dev/null
use_tab dsb_a /
dsb_a run wait_for_text -p "text=invalid or missing secret" -p timeoutMs=10000
dsb_a run close_tab >/dev/null 2>&1 || true

# the redirect the server sends after the provider callback: <redirect>?token=...&refreshToken=...&uid=...
use_tab dsb_a /qrCode
dsb_a js "(() => { window.open('/?secret=$SECRET?token=$T&refreshToken=$R&uid=$UA', '_blank'); return 1 })()" >/dev/null
use_tab dsb_a /
dsb_a run wait_for_text -p "text=登录成功" -p timeoutMs=10000
shot dsb_a 90-oauth-callback.png
use_tab dsb_a /message
wait_ready dsb_a "${EB%@*}"
shot dsb_a 91-oauth-logged-in.png
expect=$(dsb_a_raw js "window.__TAURI_SHIM__.auth.get().uid" | python3 -c "import sys, json; print(json.loads(sys.stdin.read()))")
[ "$expect" = "$UA" ] || { echo "FAIL logged in as $expect, expected $UA" >&2; exit 1; }
echo "ok   logged in as alice ($UA) through the oauth callback"

finish dsb_a
ls -la "$OUT" | grep oauth || true
