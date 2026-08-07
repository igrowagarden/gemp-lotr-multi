#!/usr/bin/env bash
#
# The CARD INFO differential -- which cards can be inspected, and which are
# worth asking the server about -- then proof the comparison can still fail.
#
#   bash inforun.sh             baseline + every control
#   bash inforun.sh baseline    baseline only
#   MODES=replay bash inforun.sh
#
# Exits non-zero on a baseline DIFF or on a control that should fire and does
# not. Needs `sync.sh` first; does NOT need a game, a table or bots.

set -uo pipefail
cd "$(dirname "$0")"

# Relative to the CWD, not `$0`: the cd above already moved us here.
source ./harnesslock.sh
harness_lock inforun

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/infofuzz.html"
TMP="${TMPDIR:-/tmp}/inforun.$$"
mkdir -p "$TMP"
# Cleaned on ANY exit, not just a clean one: `baseline` returns early and a
# killed run never reaches the tail, which is how these piled up in Temp.
# Registered rather than trapped -- see harnesslock.sh, bash has one EXIT trap.
harness_at_exit 'rm -rf "$TMP"'

CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

ALL_MODES="live replay"
MODES="${MODES:-$ALL_MODES}"

# Which control reaches which mode, and why.
#
#   alwaysask  asks about every id. Fires wherever the reference declines to
#              ask -- four ids live, all seven in replay.
#   neverask   asks about none. Fires only where the reference DOES ask, which
#              in replay is nowhere: a recording has no live game to query, so
#              this control CANNOT fire there. That is its scope, not a defect,
#              and recording it is what stops a clean replay run being read as
#              coverage it is not.
#   neveropen  refuses to open the dialog. Fires everywhere, because there is no
#              id for which the reference declines to show the card -- which is
#              the rule this client used to have backwards.
declare -A CONTROL_SCOPE=(
  [alwaysask]="$ALL_MODES"
  [neverask]="live"
  [neveropen]="$ALL_MODES"
)

run() {   # run <mode> [sabotage] -> prints the RESULT line
  local mode="$1" sab="${2:-}" url="$BASE?mode=$1"
  [ -n "$sab" ] && url="$url&sabotage=$sab"
  timeout 90 "$CHROME" --headless --disable-gpu "$CDN" \
    --user-data-dir="$TMP/cr_${mode}_${sab:-base}" \
    --dump-dom --virtual-time-budget=30000 "$url" 2>/dev/null \
    | sed -e 's/<[^>]*>//g' | grep -E "^RESULT:" | head -1
}

fail=0

echo "=== baseline"
for m in $MODES; do
  line="$(run "$m")"
  printf "  %-10s %s\n" "$m" "${line:-NO RESULT — the run did not complete}"
  case "$line" in
    *"ALL PASS"*) ;;
    *) fail=1 ;;
  esac
done

if [ "${1:-}" = "baseline" ]; then
  [ "$fail" = 0 ] && echo "RESULT: BASELINE CLEAN" || echo "RESULT: BASELINE HAS FAILURES"
  exit "$fail"
fi

for sab in alwaysask neverask neveropen; do
  echo "=== control: sabotage=$sab   (expected to fire on: ${CONTROL_SCOPE[$sab]})"
  for m in $MODES; do
    case " ${CONTROL_SCOPE[$sab]} " in *" $m "*) expect_fire=1 ;; *) expect_fire=0 ;; esac
    line="$(run "$m" "$sab")"
    fired=0
    case "$line" in *"DIFF"*) fired=1 ;; esac
    if [ "$expect_fire" = 1 ] && [ "$fired" = 0 ]; then
      printf "  %-10s %s  <-- CONTROL DID NOT FIRE; this mode's pass proves nothing\n" "$m" "$line"
      fail=1
    elif [ "$expect_fire" = 0 ] && [ "$fired" = 1 ]; then
      printf "  %-10s %s  (fired outside its recorded scope — widen CONTROL_SCOPE)\n" "$m" "$line"
    else
      printf "  %-10s %s\n" "$m" "$line"
    fi
  done
done

rm -rf "$TMP"
if [ "$fail" = 0 ]; then
  echo "RESULT: CLEAN, and every control fired where it should"
else
  echo "RESULT: PROBLEMS — see above"
fi
exit "$fail"
