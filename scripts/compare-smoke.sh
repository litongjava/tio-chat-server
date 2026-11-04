#!/usr/bin/env bash
# Diff the smoke outputs of two runs of run-all-tests.sh, masking ids, tokens, emails, times and growing counts.
#   scripts/compare-smoke.sh logs/test-results/all logs/test-results/split
norm() { sed -E -e 's/[0-9a-f]{32}/<HEX>/g' -e 's/[A-Za-z0-9_.+-]+@example\.com/<EMAIL>/g' -e 's/[0-9]{9,}/<N>/g' \
  -e 's/[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9:.]+/<TIME>/g' -e "s/'[0-9]{1,8}'/'<n>'/g" -e 's/ws_[a-z]_<N>/ws/g' \
  -e 's/([^0-9A-Za-z_<])[0-9]{3,}([^0-9A-Za-z_>]|$)/\1<C>\2/g' "$1"; }
rc=0
for f in "$1"/*.out; do
  n=$(basename "$f")
  if diff <(norm "$f") <(norm "$2/$n") > /tmp/compare-smoke.$$; then echo "SAME $n"; else echo "DIFF $n"; head -20 /tmp/compare-smoke.$$; rc=1; fi
done
rm -f /tmp/compare-smoke.$$; exit $rc
