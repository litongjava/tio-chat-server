#!/usr/bin/env bash
# Calls every endpoint declared by the original backend (scripts/audit-parity.py): fails on a server error, a missing route or a field difference not checked yet.
cd "$(dirname "$0")"; source ./lib.sh; set -e
ROOT=$(cd .. && pwd); TS=$(date +%s%N | cut -c1-16); OUT="$ROOT/logs/test-results/parity.json"; mkdir -p "$(dirname "$OUT")"
read T U < <(register_user "parity$TS@example.com"); grant_admin "$U"; T=$(login_user "parity$TS@example.com" | cut -d' ' -f1)
python3 audit-parity.py "$ROOT/../HuLa-Server" "$T" "$U" "$OUT" "$HOST" | sed -E '/^response codes/d'
python3 - "$OUT" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))
for e in r['errors']:
    print('server error:', e['endpoint'], e['query'], json.dumps(e['body'])[:80], e['text'][:80])
for e in r['missing_route']:
    print('missing route:', e)
for e in r['missing_fields']:
    print('missing fields:', e['endpoint'], e['result'], e['missing'])
sys.exit(1 if r['errors'] or r['missing_route'] or r['missing_fields'] else 0)
PY
