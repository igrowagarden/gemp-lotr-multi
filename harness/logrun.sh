#!/usr/bin/env bash
#
# The LOG differential -- the game log and chat, which share one transcript --
# then proof that each of the four compared fields can still be seen to move.
#
#   bash logrun.sh             baseline + every control
#   bash logrun.sh baseline    baseline only
#
# Exits non-zero if the baseline reports a DIFF **or if a control does not
# fire**. Unlike fuzzrun and pilerun there is no scope table: every control here
# reaches the single baseline run, so "expected to fire" is unconditional and a
# silent one is always a fault.
#
# Needs `sync.sh` first. Does NOT need a game, a table or bots.

set -uo pipefail
cd "$(dirname "$0")"

# Relative to the CWD, not to `$0`: the cd above already moved us here. See
# fuzzrun.sh for what getting this wrong looked like (a clean-looking run that
# was never locked at all).
source ./harnesslock.sh
harness_lock logrun

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/logfuzz.html"
TMP="${TMPDIR:-/tmp}/logrun.$$"
mkdir -p "$TMP"

CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

# One control per field the comparison makes, so a field that silently stopped
# being compared shows up as a control that stopped firing:
#
#   drophint   the card references a line exposes
#   raw        the words themselves
#   misclass   game / warning / chat / system
#   reorder    arrival order, which is the one property the shared transcript
#              exists to preserve
CONTROLS="drophint raw misclass reorder"

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
printf "  %-12s %s\n" "log" "${line:-NO RESULT — the run did not complete}"
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
