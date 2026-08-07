#!/usr/bin/env bash
#
# The REPLAY CONTROLS differential -- pacing and the play/pause contract --
# then proof the comparison can still fail.
#
#   bash replayrun.sh             baseline + every control
#   bash replayrun.sh baseline    baseline only
#
# Exits non-zero on a baseline DIFF or a control that does not fire. Every
# control reaches the single baseline run, so there is no scope table.
#
# Needs `sync.sh` first; does NOT need a game, a table or bots.

set -uo pipefail
cd "$(dirname "$0")"

# Relative to the CWD, not `$0`: the cd above already moved us here.
source ./harnesslock.sh
harness_lock replayrun

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/replayfuzz.html"
TMP="${TMPDIR:-/tmp}/replayrun.$$"
mkdir -p "$TMP"

CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

# One control per thing the comparison can get wrong:
#
#   noclamp   removes the speed bounds. Fires on 4 -- both six-press sequences
#             and both named limits.
#   inverted  swaps slower and faster. Fires on 3, and is the one that matters:
#             replaySpeed is a duration MULTIPLIER, so a client built from the
#             button labels alone comes out backwards and still looks like it
#             works.
#   autoplay  starts the replay playing. Fires on all 3 play/pause checks.
CONTROLS="noclamp inverted autoplay"

run() {   # run [sabotage] -> prints the RESULT line
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
printf "  %-12s %s\n" "replay" "${line:-NO RESULT — the run did not complete}"
case "$line" in
  *"ALL PASS"*) ;;
  *) fail=1 ;;
esac

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
if [ "$fail" = 0 ]; then
  echo "RESULT: CLEAN, and every control fired"
else
  echo "RESULT: PROBLEMS — see above"
fi
exit "$fail"
