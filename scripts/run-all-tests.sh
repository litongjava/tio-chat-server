#!/usr/bin/env bash
# Run every smoke script and, unless SKIP_CLIENT=1, every client end-to-end script against :18760.
#   scripts/run-all-tests.sh <name>    # results in logs/test-results/<name>, one line per script in summary.txt
# Client scripts need the web client (tools/web-client/start-client.sh) and dsb; smoke-ai needs MockLlm on :18799.
cd "$(dirname "$0")/.."
out="$PWD/logs/test-results/${1:?name}"; rm -rf "$out"; mkdir -p "$out"
for s in smoke-oauth smoke-social smoke-chat smoke-feed smoke-history smoke-call smoke-base-crud smoke-base-ext smoke-base-rules smoke-ai smoke-ai-admin smoke-portal smoke-msg smoke-security smoke-admin-client smoke-generator smoke-generator-options smoke-generator-web smoke-parity; do
  timeout 180 bash "scripts/$s.sh" > "$out/$s.out" 2>&1
  echo "$s exit=$?" | tee -a "$out/summary.txt"
done
[ "$SKIP_CLIENT" = 1 ] && exit 0
for s in client-e2e client-group-e2e client-upload-e2e client-friend-e2e client-feed-e2e client-call-e2e client-qrlogin-e2e client-profile-group-e2e client-history-files-e2e client-oauth-e2e client-ai-e2e; do
  start=$(date +%s)
  timeout 900 bash "scripts/$s.sh" "$out/$s" > "$out/$s.log" 2>&1
  echo "$s exit=$? $(( $(date +%s)-start ))s" | tee -a "$out/summary.txt"
done
