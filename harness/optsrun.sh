#!/usr/bin/env bash
#
# The GAME OPTIONS differential -- concede and "request cancel": whether each is
# offered, and what each sends -- then proof the comparison can still fail.
#
#   bash optsrun.sh             baseline + every control
#   bash optsrun.sh baseline    baseline only
#   ROLES=spectator bash optsrun.sh
#
# Needs `sync.sh` first; does NOT need a game, a table or bots.

set -uo pipefail
cd "$(dirname "$0")"

# Relative to the CWD, not `$0`: the cd above already moved us here.
source ./harnesslock.sh
harness_lock optsrun

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/optsfuzz.html"
TMP="${TMPDIR:-/tmp}/optsrun.$$"
mkdir -p "$TMP"

CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

ALL_ROLES="player spectator"
ROLES="${ROLES:-$ALL_ROLES}"

# Which control reaches which role, and why. The two offer controls are
# one-sided by construction:
#
#   offerall   forces both controls ON, so it can only differ where the truth
#              is OFF -- the spectator. A player run passing under it is its
#              scope, not a miss.
#   offernone  forces both OFF, so it only differs where the truth is ON -- the
#              player.
#   wrongverb  sends GET instead of POST. Reaches both roles: what a control
#              SENDS does not depend on who is looking.
#   wrongpath  posts to /quit. Same.
declare -A CONTROL_SCOPE=(
  [offerall]="spectator"
  [offernone]="player"
  [wrongverb]="$ALL_ROLES"
  [wrongpath]="$ALL_ROLES"
)

run() {   # run <role> [sabotage] -> prints the RESULT line
  local role="$1" sab="${2:-}" url="$BASE?role=$1"
  [ -n "$sab" ] && url="$url&sabotage=$sab"
  timeout 90 "$CHROME" --headless --disable-gpu "$CDN" \
    --user-data-dir="$TMP/cr_${role}_${sab:-base}" \
    --dump-dom --virtual-time-budget=30000 "$url" 2>/dev/null \
    | sed -e 's/<[^>]*>//g' | grep -E "^RESULT:" | head -1
}

fail=0

echo "=== baseline"
for r in $ROLES; do
  line="$(run "$r")"
  printf "  %-12s %s\n" "$r" "${line:-NO RESULT — the run did not complete}"
  case "$line" in
    *"ALL PASS"*) ;;
    *) fail=1 ;;
  esac
done

if [ "${1:-}" = "baseline" ]; then
  [ "$fail" = 0 ] && echo "RESULT: BASELINE CLEAN" || echo "RESULT: BASELINE HAS FAILURES"
  exit "$fail"
fi

for sab in offerall offernone wrongverb wrongpath; do
  echo "=== control: sabotage=$sab   (expected to fire on: ${CONTROL_SCOPE[$sab]})"
  for r in $ROLES; do
    case " ${CONTROL_SCOPE[$sab]} " in *" $r "*) expect_fire=1 ;; *) expect_fire=0 ;; esac
    line="$(run "$r" "$sab")"
    fired=0
    case "$line" in *"DIFF"*) fired=1 ;; esac
    if [ "$expect_fire" = 1 ] && [ "$fired" = 0 ]; then
      printf "  %-12s %s  <-- CONTROL DID NOT FIRE; this role's pass proves nothing\n" "$r" "$line"
      fail=1
    elif [ "$expect_fire" = 0 ] && [ "$fired" = 1 ]; then
      printf "  %-12s %s  (fired outside its recorded scope — widen CONTROL_SCOPE)\n" "$r" "$line"
    else
      printf "  %-12s %s\n" "$r" "$line"
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
