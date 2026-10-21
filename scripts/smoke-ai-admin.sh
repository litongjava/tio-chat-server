#!/usr/bin/env bash
# AI admin endpoints: a normal user gets 406 "无此权限", a TENANT_ADMIN user gets data.
cd "$(dirname "$0")"; source ./lib.sh; set -e
TS=$(date +%s)
read TU UU < <(register_user "aiuser$TS@example.com")
read TA UA < <(register_user "aiadmin$TS@example.com")
grant_admin "$UA"
for p in "/api/ai/model/admin/page?pageNo=1&pageSize=5" "/api/ai/api-key/admin/all-list" \
  "/api/ai/chat/message/page?pageNo=1&pageSize=5" "/api/ai/chat/conversation/page?pageNo=1&pageSize=5" \
  "/api/ai/image/page?pageNo=1&pageSize=5"; do
  echo "== GET ${p%%\?*}"
  echo -n "user:  "; api GET "$p" "$TU" | json "d['code'], d['msg']"
  echo -n "admin: "; api GET "$p" "$TA" | json "d['code'], d['msg']"
done
echo "== DELETE /api/ai/chat/message/delete-by-admin"
echo -n "user:  "; api DELETE "/api/ai/chat/message/delete-by-admin?id=1" "$TU" | json "d['code'], d['msg']"
echo "== own endpoints stay open for normal users"
api GET "/api/ai/model/simple-list?type=1" "$TU" | json "d['code']"
api GET /api/ai/chat/conversation/my-list "$TU" | json "d['code']"
