#!/usr/bin/env bash
# Register DeepSeek as a public AI provider through the server's own AI endpoints:
# one public API key, one public chat model per model id that DeepSeek's /models returns,
# and a public chat role bound to the first model (the client needs a role to start a conversation).
# The owner account gets the TENANT_ADMIN role so it can also use the AI admin endpoints (/api/ai/**/admin/*).
# Usage: DEEPSEEK_API_KEY=sk-... [DEEPSEEK_BASE_URL=https://api.deepseek.com] [OWNER=ai-admin@example.com] scripts/setup-deepseek.sh
# Needs the server on :18760 and docker access to the redis container (run under `sg docker -c ...`).
cd "$(dirname "$0")"; source ./lib.sh; set -eo pipefail
: "${DEEPSEEK_API_KEY:?set DEEPSEEK_API_KEY}"
BASE=${DEEPSEEK_BASE_URL:-https://api.deepseek.com}
OWNER=${OWNER:-ai-admin@example.com}

read T U < <(register_user "$OWNER")
grant_admin "$U"
MODELS=$(curl -s -m 20 "$BASE/models" -H "Authorization: Bearer $DEEPSEEK_API_KEY" | json "' '.join(m['id'] for m in d['data'])")
[ -n "$MODELS" ] || { echo "no models returned by $BASE/models" >&2; exit 1; }
echo "owner=$OWNER models: $MODELS"

body=$(python3 -c 'import json,sys; print(json.dumps({"name":"DeepSeek","apiKey":sys.argv[1],"platform":"DeepSeek","url":sys.argv[2],"status":0,"publicStatus":True}))' "$DEEPSEEK_API_KEY" "$BASE")
KEY=$(api POST /api/ai/api-key/create "$T" "$body" | json "d['data']")
echo "api key id=$KEY"

sort=0
FIRST=
for m in $MODELS; do
  MODEL=$(api POST /api/ai/model/create "$T" "{\"keyId\":$KEY,\"name\":\"$m\",\"model\":\"$m\",\"platform\":\"DeepSeek\",\"type\":1,\"sort\":$sort,\"status\":0,\"publicStatus\":0,\"temperature\":0.7,\"maxTokens\":32768,\"maxContexts\":10,\"supportsReasoning\":1}" | json "d['data']")
  echo "model $m id=$MODEL"
  FIRST=${FIRST:-$MODEL}
  sort=$((sort + 1))
done
api GET "/api/ai/model/simple-list?type=1" "$T" | json "[(m['name'], m['platform']) for m in d['data']]"
ROLE=$(api POST /api/ai/chat-role/create "$T" "{\"name\":\"DeepSeek 助手\",\"avatar\":\"\",\"category\":\"通用\",\"sort\":0,\"description\":\"由 DeepSeek 驱动的通用助手\",\"systemMessage\":\"你是一个乐于助人的助手，回答简洁准确。\",\"modelId\":$FIRST,\"publicStatus\":true,\"status\":0}" | json "d['data']")
echo "chat role id=$ROLE"
api GET "/api/ai/model/admin/page?pageNo=1&pageSize=50&platform=DeepSeek" "$T" | json "'admin view:', d['code'], [m['name'] for m in d['data']['list']]"
