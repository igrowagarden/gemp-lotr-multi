#!/usr/bin/env bash
#
# The REORDER differential -- which cards may be dragged into your own order --
# then proof the comparison can still fail.
#
#   bash reorderrun.sh             baseline + every control
#   bash reorderrun.sh baseline    baseline only
#
# Needs `sync.sh` first; does NOT need a game, a table or bots.

set -uo pipefail
cd "$(dirname "$0")"
source ./harnesslock.sh
harness_lock reorderrun

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/reorderfuzz.html"
TMP="${TMPDIR:-/tmp}/reorderrun.$$"
mkdir -p "$TMP"
# Cleaned on ANY exit, not just a clean one: `baseline` returns early and a
# killed run never reaches the tail, which is how these piled up in Temp.
# Registered rather than trapped -- see harnesslock.sh, bash has one EXIT trap.
harness_at_exit 'rm -rf "$TMP"'
CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

# dragall  makes everything draggable -- fires on every fixed zone.
# dragnone makes nothing draggable -- fires on every reorderable one.
# nohand   drops HAND alone, so a rule that stopped distinguishing zones at all
#          would still be caught by the other two but not located.
CONTROLS="dragall dragnone nohand"

run() {
  local sab="${1:-}" url="$BASE"
  [ -n "$sab" ] && url="$url?sabotage=$sab"
  timeout 90 "$CHROME" --headless --disable-gpu "$CDN" \
    --user-data-dir="$TMP/cr_${sab:-base}" \
    --dump-dom --virtual-time-budget=30000 "$url" 2>/dev/null \
    | sed -e 's/<[^>]*>//g' | grep -E "^RESULT:" | head -1
}

fail=0
echo "=== baseline"
line="$(run)"
printf "  %-12s %s\n" "reorder" "${line:-NO RESULT — the run did not complete}"
case "$line" in *"ALL PASS"*) ;; *) fail=1 ;; esac

if [ "${1:-}" = "baseline" ]; then
  [ "$fail" = 0 ] && echo "RESULT: BASELINE CLEAN" || echo "RESULT: BASELINE HAS FAILURES"
  exit "$fail"
fi

echo "=== controls   (every one is expected to fire)"
for sab in $CONTROLS; do
  line="$(run "$sab")"
  case "$line" in
    *"DIFF"*) printf "  %-12s %s\n" "$sab" "$line" ;;
    *) printf "  %-12s %s  <-- CONTROL DID NOT FIRE; the baseline proves nothing\n" \
              "$sab" "${line:-NO RESULT}"; fail=1 ;;
  esac
done

rm -rf "$TMP"
[ "$fail" = 0 ] && echo "RESULT: CLEAN, and every control fired" \
                || echo "RESULT: PROBLEMS — see above"
exit "$fail"
