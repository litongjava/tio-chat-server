#!/usr/bin/env bash
# Same as run-both-modes.sh without rebuilding: runs the jars already in target/.
cd "$(dirname "$0")/.."
export MODEL=${MODEL:-mock-e2e} JAVA=${JAVA:-java}
ARGS="--wx.mp.app-id=wxtest --wx.mp.token=smoketoken --chat.msg.schedule.interval-seconds=2"
scripts/services.sh stop; sleep 3
SERVICE_ARGS="$ARGS" scripts/services.sh start
GATEWAY=1 scripts/run-all-tests.sh split
scripts/services.sh stop; sleep 8
nohup "$JAVA" -jar tio-chat-all/target/tio-chat-server.jar $ARGS > logs/all-in-one.log 2>&1 &
ALL=$!
for _ in $(seq 1 90); do curl -s -o /dev/null http://localhost:18760/health && break; sleep 1; done
scripts/run-all-tests.sh all
kill $ALL
scripts/compare-smoke.sh logs/test-results/all logs/test-results/split | tee logs/test-results/compare.txt
echo ALL-DONE
