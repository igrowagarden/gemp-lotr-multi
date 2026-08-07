#!/usr/bin/env bash
#
# The PILE differential over every viewer configuration, then proof that the
# comparison can still fail.
#
#   bash pilerun.sh              baseline + every control
#   bash pilerun.sh baseline     baseline only
#   CONFIGS=player-public bash pilerun.sh
#
# Exits non-zero if any configuration reports a DIFF **or if a control that
# should fire does not**. The second half is the point, and it is the same
# argument fuzzrun.sh makes: a control that cannot fire is worse than none,
# because its silence reads as success.
#
# Needs `sync.sh` first -- the page is served from the GEMP origin because
# oldharness.html loads the old client from /gemp-lotr/js/. It does NOT need a
# game, a table or bots.

set -uo pipefail
cd "$(dirname "$0")"

# Sourced by a path relative to the CWD rather than to `$0`, because the cd
# above has already moved us here. See fuzzrun.sh, where getting this wrong left
# the script running unlocked while reporting a clean baseline.
source ./harnesslock.sh
harness_lock pilerun

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/pilefuzz.html"
TMP="${TMPDIR:-/tmp}/pilerun.$$"
mkdir -p "$TMP"

# Same CDN block as fuzzrun, for the same reason: Chrome's virtual clock is
# paused while any request is pending, so unresolved card art freezes the run.
CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

ALL_CONFIGS="player-private player-public spectator-private spectator-public"
CONFIGS="${CONFIGS:-$ALL_CONFIGS}"

PLAYER_CONFIGS="player-private player-public"

# Which control reaches which configuration, and why.
#
#   dropdeck   removes DECK from what the new client offers. The draw deck is
#              only ever offered for a SEATED viewer's own seat, so this cannot
#              touch a spectator configuration -- that is its scope, not a
#              defect, and recording it here is what stops a future reader
#              reading two clean spectator runs as coverage.
#   extrapile  adds an ADVENTURE_DECK nobody should offer. Reaches every
#              configuration, because there is always a seat that must not have
#              one.
#   contents   drops a card from every pile. Reaches everything: DEAD and
#              REMOVED are browsable in every configuration.
declare -A CONTROL_SCOPE=(
  [dropdeck]="$PLAYER_CONFIGS"
  [extrapile]="$ALL_CONFIGS"
  [contents]="$ALL_CONFIGS"
)

run() {   # run <config> [sabotage] -> prints the RESULT line
  local cfg="$1" sab="${2:-}" url="$BASE?config=$1"
  [ -n "$sab" ] && url="$url&sabotage=$sab"
  timeout 90 "$CHROME" --headless --disable-gpu "$CDN" \
    --user-data-dir="$TMP/cr_${cfg}_${sab:-base}" \
    --dump-dom --virtual-time-budget=30000 "$url" 2>/dev/null \
    | sed -e 's/<[^>]*>//g' | grep -E "^RESULT:" | head -1
}

fail=0

echo "=== baseline"
for c in $CONFIGS; do
  line="$(run "$c")"
  printf "  %-20s %s\n" "$c" "${line:-NO RESULT — the run did not complete}"
  # No RESULT line means the page died, not that it passed. That distinction
  # cost a whole fuzzrun once; see the traps in HANDOFF.
  case "$line" in
    *"ALL PASS"*) ;;
    *) fail=1 ;;
  esac
done

if [ "${1:-}" = "baseline" ]; then
  [ "$fail" = 0 ] && echo "RESULT: BASELINE CLEAN" || echo "RESULT: BASELINE HAS FAILURES"
  exit "$fail"
fi

for sab in dropdeck extrapile contents; do
  echo "=== control: sabotage=$sab   (expected to fire on: ${CONTROL_SCOPE[$sab]})"
  for c in $CONFIGS; do
    case " ${CONTROL_SCOPE[$sab]} " in *" $c "*) expect_fire=1 ;; *) expect_fire=0 ;; esac
    line="$(run "$c" "$sab")"
    fired=0
    case "$line" in *"DIFF"*) fired=1 ;; esac
    if [ "$expect_fire" = 1 ] && [ "$fired" = 0 ]; then
      printf "  %-20s %s  <-- CONTROL DID NOT FIRE; this config's pass proves nothing\n" "$c" "$line"
      fail=1
    elif [ "$expect_fire" = 0 ] && [ "$fired" = 1 ]; then
      printf "  %-20s %s  (fired outside its recorded scope — widen CONTROL_SCOPE)\n" "$c" "$line"
    else
      printf "  %-20s %s\n" "$c" "$line"
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
