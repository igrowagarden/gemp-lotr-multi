#!/usr/bin/env bash
#
# The standalone suite: every assertion page, no Docker, no reference client.
#
#   bash fastrun.sh                everything + the three controls  (~1 min)
#   ONLY=shapecheck bash fastrun.sh    one page, for iterating
#
# Serves src/ directly with devserver.py -- no sync.sh, no GEMP origin, no
# server process at all. This is the fast inner loop; it is NOT the
# differential. It answers "is the client internally consistent", not "does it
# match the reference". Anything touching view/ or state/ still runs
# diffrun.sh FIRST, per CLAUDE.md -- this suite is what you run every few
# minutes while working, not what you cite as verification of equivalence.
#
# Exits non-zero if any page fails, any page prints no RESULT, **or any of the
# three controls fails to fire**:
#   shapecheck?sabotage=nolight        an eligible card not lit -> must FAIL
#   shapecheck?sabotage=phantomlight   a card lit beyond the contract -> must FAIL
#   shapecheck?sabotage=mute           page prints no RESULT -> the dead-page
#                                      detector must report it, because a dying
#                                      module import looks exactly like this
#
# What is NOT here, deliberately: statcheck (a measuring ruler, prints no
# RESULT by design), the *fuzz pages, diff and livediff (all need the
# reference client and the server), and autopasscheck's 2 gameId measurements
# (they need an engine; the page SKIPs them on its own and still passes 47).

set -uo pipefail
cd "$(dirname "$0")"

source ./harnesslock.sh
harness_lock fastrun

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
PORT="${PORT:-17919}"
BASE="http://127.0.0.1:$PORT"
SRC="$(cd ../src && pwd)"
TMP="${TMPDIR:-/tmp}/fastrun.$$"
mkdir -p "$TMP"
harness_at_exit 'rm -rf "$TMP"'

# BLOCK THE CARD-ART CDN for shapecheck: its fixture uses real-shaped
# blueprints, Chrome's virtual clock pauses while any request is pending, and
# a paused clock is a page that never prints. NOT applied to cardstatecheck
# or flipcheck -- blocked images change card geometry and those two measure
# pixels. Everything else never fetches art.
CDN='--host-resolver-rules=MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost'

python devserver.py "$PORT" "$SRC" >"$TMP/server.log" 2>&1 &
SERVER_PID=$!
harness_at_exit 'kill "$SERVER_PID" 2>/dev/null'
for i in $(seq 1 40); do
  curl -s -o /dev/null "$BASE/tests.html" && break
  sleep 0.25
done
if ! curl -s -o /dev/null "$BASE/tests.html"; then
  echo "devserver never came up -- $TMP/server.log:" >&2
  cat "$TMP/server.log" >&2
  exit 1
fi

run_page() {   # run_page <name> <path> [cdnflag] -> RESULT line or empty
  local name="$1" path="$2" cdn="${3:-}"
  # --window-size: headless defaults to 800x600, where the geometry suites
  # measure DEGENERATE layouts -- assigncheck's fightbox rendered 2px-wide
  # cards there and its overlap assertion judged noise, not the design.
  # --disable-popup-blocking: headless blocks window.open without a user
  # gesture, and detachcheck's whole subject is a popped-out window.
  timeout 60 "$CHROME" --headless --disable-gpu --window-size=1500,950 \
    --disable-popup-blocking ${cdn:+"$CDN"} \
    --user-data-dir="$TMP/cr_$name" \
    --dump-dom --virtual-time-budget=20000 "$BASE/$path" 2>/dev/null \
    | sed -e 's/<[^>]*>//g' | grep -E "^RESULT:" | head -1
}

# name|path|cdn  -- cdn column is "block" only where art would hang the clock.
PAGES="
tests|tests.html|
actioncheck|dev/actioncheck.html|
assigncheck|dev/assigncheck.html|
attachcheck|dev/attachcheck.html|
autopasscheck|dev/autopasscheck.html|
cardstatecheck|dev/cardstatecheck.html|
chatcheck|dev/chatcheck.html|
detachcheck|dev/detachcheck.html|block
flipcheck|dev/flipcheck.html|
navcheck|dev/navcheck.html|
pathcheck|dev/pathcheck.html|
pickcheck|dev/pickcheck.html|
promptcheck|dev/promptcheck.html|
replaycheck|dev/replaycheck.html|
selectioncheck|dev/selectioncheck.html|
sessioncheck|dev/sessioncheck.html|
wirecheck|dev/wirecheck.html|
zoomcheck|dev/zoomcheck.html|
shapecheck|dev/shapecheck.html|block
"

fail=0

echo "=== baseline (no server, no reference -- internal consistency only)"
for row in $PAGES; do
  name="${row%%|*}"; rest="${row#*|}"; path="${rest%%|*}"; cdn="${rest#*|}"
  [ -n "${ONLY:-}" ] && [ "$name" != "$ONLY" ] && continue
  line="$(run_page "$name" "$path" "$cdn")"
  printf "  %-16s %s\n" "$name" "${line:-NO RESULT — the page did not complete}"
  case "$line" in
    "RESULT: ALL PASS"*) ;;
    *) fail=1 ;;
  esac
done

if [ -z "${ONLY:-}" ]; then
  echo "=== controls (each must FAIL, or the checker cannot see)"
  for sab in nolight phantomlight; do
    line="$(run_page "ctl_$sab" "dev/shapecheck.html?sabotage=$sab" block)"
    printf "  %-16s %s\n" "$sab" "${line:-NO RESULT}"
    case "$line" in
      RESULT:\ [1-9]*FAILED*) ;;
      *) echo "  CONTROL DID NOT FIRE: $sab" ; fail=1 ;;
    esac
  done
  line="$(run_page "ctl_mute" "dev/shapecheck.html?sabotage=mute" block)"
  if [ -z "$line" ]; then
    printf "  %-16s %s\n" "mute" "no RESULT, and the runner saw that — dead-page detector works"
  else
    printf "  %-16s %s\n" "mute" "printed a RESULT it must not print: $line"
    fail=1
  fi
fi

echo
if [ "$fail" -eq 0 ]; then
  echo "RESULT: ALL PASS (standalone), controls fired"
else
  echo "RESULT: FAILURES — read the lines above"
fi
exit "$fail"
