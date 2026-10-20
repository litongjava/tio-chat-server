#!/usr/bin/env bash
# AI module: api key, model, conversation, sync and streamed chat against a mock LLM on :18799.
cd "$(dirname "$0")"; source ./lib.sh; set -e
read T U < <(register_user "ai$(date +%s)@example.com")
KEY=$(api POST /api/ai/api-key/create "$T" '{"name":"mock","apiKey":"sk-test","platform":"OpenAI","url":"http://127.0.0.1:18799/v1","status":0}' | json "d['data']")
MODEL=$(api POST /api/ai/model/create "$T" "{\"keyId\":$KEY,\"name\":\"mock-model\",\"model\":\"gpt-mock\",\"platform\":\"OpenAI\",\"type\":1,\"sort\":-1,\"status\":0,\"temperature\":0.7,\"maxTokens\":4096,\"maxContexts\":10}" | json "d['data']")
echo key=$KEY model=$MODEL
api GET "/api/ai/model/simple-list?type=1" "$T" | json "d['code'], [m['name'] for m in d['data']][:3]"
CONV=$(api POST /api/ai/chat/conversation/create-my "$T" '{}' | json "d['data']['id']"); echo conversation=$CONV
api POST /api/ai/chat/message/send "$T" "{\"conversationId\":$CONV,\"content\":\"你好\"}" | json "d['code'], d['data']['receive']['content']"
echo "== stream"; curl -s -N -XPOST "$HOST/api/ai/chat/message/send-stream" -H 'Content-Type: application/json' -H "token: $T" \
  -d "{\"conversationId\":$CONV,\"content\":\"再说一次\"}" --max-time 15 | grep -o '"content":"[^"]*"' | head -6
api GET "/api/ai/chat/message/list-by-conversation-id?conversationId=$CONV" "$T" | json "d['code'], [(m['type'], m['content']) for m in d['data']]"
api GET /api/ai/chat/conversation/my-list "$T" | json "d['code'], d['data'][0]['messageCount']"
api GET "/api/ai/api-key/page?pageNo=1&pageSize=5" "$T" | json "d['code'], d['data']['total']"
echo "== write stream"; curl -s -N -XPOST "$HOST/api/ai/write/generate-stream" -H 'Content-Type: application/json' -H "token: $T" \
  -d '{"type":1,"prompt":"春天","format":1,"tone":1,"language":1,"length":1}' --max-time 15 | head -2
api GET /api/ai/platform/list "$T" | json "d['code'], len(d['data'])"
api GET /api/ai/chat-role/category-list "$T" | json "d['code'], len(d['data'])"
