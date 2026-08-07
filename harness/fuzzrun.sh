#!/usr/bin/env bash
#
# Run the decision-space differential over every decision type, then prove the
# comparison can still fail.
#
#   bash fuzzrun.sh            baseline + every control
#   bash fuzzrun.sh baseline   baseline only, no controls
#   TYPES=INTEGER bash fuzzrun.sh
#
# Exits non-zero if the baseline reports a DIFF **or if a control that should
# fire does not**. The second half is the point. Two controls were carried for a
# whole session that could not fire on three of the seven types, because they
# were written when this page compared OFFERS only and driving the ANSWERS
# changed what needed covering without changing them. A control that cannot fire
# is worse than none: its silence reads as success. So the expectation of which
# control reaches which type is written down here and checked, rather than being
# something a person has to remember to re-derive.
#
# Needs `sync.sh` first -- the page is served from the GEMP origin because
# oldharness.html loads the old client from /gemp-lotr/js/. It does NOT need a
# game, a table or bots.

set -uo pipefail
cd "$(dirname "$0")"

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/decisionfuzz.html"
TMP="${TMPDIR:-/tmp}/fuzzrun.$$"
mkdir -p "$TMP"

# BLOCK THE CARD-ART CDN. Chrome's virtual clock is PAUSED while any request is
# pending, so unresolved image loads freeze it and the run never finishes -- the
# same trap as a parked long poll, wearing different clothes. Note this flag must
# NOT be used for cardstatecheck.html: blocked images change card geometry and
# that suite measures drag distances in pixels.
CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

ALL_TYPES="CARD_SELECTION CARD_ACTION_CHOICE ARBITRARY_CARDS ASSIGN_MINIONS INTEGER MULTIPLE_CHOICE ACTION_CHOICE"
TYPES="${TYPES:-$ALL_TYPES}"

# Which control is expected to reach which type. `dropoffer` and `phantom`
# mutate card-id lists, so they cannot touch the types answered by an index or a
# number -- that is not a defect in them, it is their scope, and pretending
# otherwise is how the gap hid. `answer` perturbs the answer itself and reaches
# everything.
CARD_TYPES="CARD_SELECTION CARD_ACTION_CHOICE ARBITRARY_CARDS ASSIGN_MINIONS"
declare -A CONTROL_SCOPE=(
  [dropoffer]="$CARD_TYPES"
  [phantom]="$CARD_TYPES"
  [answer]="$ALL_TYPES"
)

run() {   # run <type> [sabotage] -> prints the RESULT line
  local type="$1" sab="${2:-}" url="$BASE?only=$1"
  [ -n "$sab" ] && url="$url&sabotage=$sab"
  timeout 90 "$CHROME" --headless --disable-gpu "$CDN" \
    --user-data-dir="$TMP/cr_${type}_${sab:-base}" \
    --dump-dom --virtual-time-budget=30000 "$url" 2>/dev/null \
    | sed -e 's/<[^>]*>//g' | grep -E "^RESULT:" | head -1
}

fail=0

echo "=== baseline"
for t in $TYPES; do
  line="$(run "$t")"
  printf "  %-20s %s\n" "$t" "${line:-NO RESULT — the run did not complete}"
  # A run that produces no RESULT line is a failure, not a pass. The report is
  # only rendered once the case loop finishes, so silence means it stopped.
  case "$line" in
    *"ALL PASS"*) ;;
    *) fail=1 ;;
  esac
done

if [ "${1:-}" = "baseline" ]; then
  [ "$fail" = 0 ] && echo "RESULT: BASELINE CLEAN" || echo "RESULT: BASELINE HAS FAILURES"
  exit "$fail"
fi

for sab in dropoffer phantom answer; do
  echo "=== control: sabotage=$sab   (expected to fire on: ${CONTROL_SCOPE[$sab]})"
  for t in $TYPES; do
    case " ${CONTROL_SCOPE[$sab]} " in *" $t "*) expect_fire=1 ;; *) expect_fire=0 ;; esac
    line="$(run "$t" "$sab")"
    fired=0
    case "$line" in *"DIFF"*) fired=1 ;; esac
    if [ "$expect_fire" = 1 ] && [ "$fired" = 0 ]; then
      printf "  %-20s %s  <-- CONTROL DID NOT FIRE; this type's pass proves nothing\n" "$t" "$line"
      fail=1
    elif [ "$expect_fire" = 0 ] && [ "$fired" = 1 ]; then
      # Not a failure, but the scope table above is now wrong and should be
      # widened -- a control reaching further than recorded is good news that
      # still needs writing down.
      printf "  %-20s %s  (fired outside its recorded scope — widen CONTROL_SCOPE)\n" "$t" "$line"
    else
      printf "  %-20s %s\n" "$t" "$line"
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
