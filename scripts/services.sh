#!/usr/bin/env bash
# Start or stop every module as its own service plus the gateway on :18760.
#   scripts/services.sh start    # needs MySQL, Redis and Nacos (namespace hula-dev)
#   scripts/services.sh stop
#   scripts/services.sh status
cd "$(dirname "$0")/.."
JAVA=${JAVA:-java}
LOG=${LOG_DIR:-logs/services}
SERVICES="oauth base system im ai ws"
mkdir -p "$LOG"

start_one() { # start_one <name> <jar> [args...]
  local name=$1 jar=$2; shift 2
  if [ -f "$LOG/$name.pid" ] && kill -0 "$(cat "$LOG/$name.pid")" 2>/dev/null; then
    echo "$name already running"; return
  fi
  nohup "$JAVA" -jar "$jar" "$@" > "$LOG/$name.log" 2>&1 &
  echo $! > "$LOG/$name.pid"
  echo "started $name pid $!"
}

wait_port() { # wait_port <port>
  for _ in $(seq 1 90); do
    (echo > /dev/tcp/127.0.0.1/$1) 2>/dev/null && return 0
    sleep 1
  done
  echo "port $1 did not open"; return 1
}

case "$1" in
  start)
    for s in $SERVICES; do start_one "$s" "tio-chat-$s/target/tio-chat-$s.jar"; done
    for p in 18773 18764 19877 18778 18877 19778; do wait_port $p; done
    start_one gateway tio-chat-gateway/target/tio-chat-gateway.jar
    wait_port 18760
    # give the gateway a moment to receive the instance lists from Nacos
    sleep 3
    ;;
  stop)
    for s in gateway $SERVICES; do
      if [ -f "$LOG/$s.pid" ]; then kill "$(cat "$LOG/$s.pid")" 2>/dev/null && echo "stopped $s"; rm -f "$LOG/$s.pid"; fi
    done
    ;;
  status)
    for s in gateway $SERVICES; do
      if [ -f "$LOG/$s.pid" ] && kill -0 "$(cat "$LOG/$s.pid")" 2>/dev/null; then echo "$s running"; else echo "$s stopped"; fi
    done
    ;;
  *) echo "usage: $0 start|stop|status"; exit 1 ;;
esac
