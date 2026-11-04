#!/usr/bin/env bash
# Rebuild, then run every test against the split services and against one process, and compare the smoke outputs.
#   scripts/run-both-modes.sh    # needs MySQL, Redis, Nacos, MinIO, the web client and dsb; results in logs/test-results
# MODEL selects the AI model for client-ai-e2e, default mock-e2e (see setup-mock-llm.sh).
cd "$(dirname "$0")/.."
export MODEL=${MODEL:-mock-e2e} JAVA=${JAVA:-java}
ARGS="--wx.mp.app-id=wxtest --wx.mp.token=smoketoken"
free_port() {
  for p in $(ss -ltnp "( sport = :$1 )" | grep -o 'pid=[0-9]*' | cut -d= -f2); do kill "$p"; done
}
wait_http() {
  for _ in $(seq 1 90); do curl -s -o /dev/null "$1" && return 0; sleep 1; done; echo "$1 did not answer"; return 1
}
scripts/services.sh stop; free_port 18760; free_port 18799; sleep 3
mvn -q clean package -DskipTests -Dgpg.skip || exit 1
(cd scripts && nohup "$JAVA" MockLlm.java 18799 > ../logs/mockllm.log 2>&1 &)
sleep 3

SERVICE_ARGS="$ARGS" scripts/services.sh start
scripts/setup-mock-llm.sh
GATEWAY=1 scripts/run-all-tests.sh split
scripts/services.sh stop; sleep 8

nohup "$JAVA" -jar tio-chat-all/target/tio-chat-server.jar $ARGS > logs/all-in-one.log 2>&1 &
ALL=$!
wait_http http://localhost:18760/health
scripts/run-all-tests.sh all
kill $ALL
scripts/compare-smoke.sh logs/test-results/all logs/test-results/split | tee logs/test-results/compare.txt
echo ALL-DONE
