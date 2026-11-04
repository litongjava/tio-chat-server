#!/usr/bin/env bash
# Register MockLlm as a public AI provider, so client-ai-e2e.sh can run without a real model provider:
#   java scripts/MockLlm.java 18799 &
#   scripts/setup-mock-llm.sh
#   MODEL=mock-e2e scripts/client-ai-e2e.sh out
cd "$(dirname "$0")"; source ./lib.sh; set -eo pipefail
OWNER=${OWNER:-ai-admin@example.com}
read T U < <(register_user "$OWNER")
grant_admin "$U"
if api GET "/api/ai/model/simple-list?type=1" "$T" | json "[m['name'] for m in d['data']]" | grep -q mock-e2e; then
  echo "mock-e2e is already registered"; exit 0
fi
KEY=$(api POST /api/ai/api-key/create "$T" '{"name":"mock-e2e","apiKey":"sk-mock","platform":"DeepSeek","url":"http://127.0.0.1:18799/v1","status":0,"publicStatus":true}' | json "d['data']")
MODEL=$(api POST /api/ai/model/create "$T" "{\"keyId\":$KEY,\"name\":\"mock-e2e\",\"model\":\"mock-e2e\",\"platform\":\"DeepSeek\",\"type\":1,\"sort\":-100,\"status\":0,\"publicStatus\":0,\"temperature\":0.7,\"maxTokens\":32768,\"maxContexts\":10,\"supportsReasoning\":1}" | json "d['data']")
ROLE=$(api POST /api/ai/chat-role/create "$T" "{\"name\":\"测试助手\",\"avatar\":\"\",\"category\":\"通用\",\"sort\":-100,\"description\":\"测试模型\",\"systemMessage\":\"你是测试助手。\",\"modelId\":$MODEL,\"publicStatus\":true,\"status\":0}" | json "d['data']")
echo "key=$KEY model=$MODEL role=$ROLE"
