#!/usr/bin/env bash
# Play random games for hours, unattended, and leave a diagnosable trail.
#
#   bash soak.sh [decks] [decisions]      # default 2000 decks = 400 games
#
# Each game seats FIVE players and generates a fresh deck for every seat, so the
# deck count is five times the game count. A game costs roughly 60-90s end to
# end, so 2000 decks is about eight hours.
#
# `livediffrun.sh` on its own is not safe to leave running for that long. Three
# things grow without bound and each one eventually poisons the results rather
# than stopping the run:
#
#   the hall      every finished game leaves a table behind. `seat_table.sh`
#                 fetches the hall about ten times per game, so a listing that
#                 has grown to 200 tables silently adds minutes per game.
#   chrome dirs   `--user-data-dir` is unique per round; 255 were already lying
#                 in Temp before this script existed.
#   seeds         a batch restarted at round 1 rebuilds the SAME decks. Without
#                 SEED_BASE a long soak is one short run repeated -- which is
#                 the exact weakness the deck generator was rewritten to fix.
#
# So the work is done in batches with housekeeping in between, and the running
# tally is written after every batch. If this is killed halfway, the log up to
# that point is still complete and still true.
set -u

DECKS="${1:-2000}"
MAX="${2:-60}"
SEATS=5
BATCH=25                       # games per batch, then housekeeping
OFFSET="${OFFSET:-0}"          # resume: seed base to start from
API="http://localhost:17002/gemp-lotr-server/hall"
DOCKER=/c/Users/emers/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe

# WAIT FOR THE SERVER RATHER THAN BURNING THE BUDGET AGAINST A DEAD ONE.
#
# This is not hypothetical. Docker Desktop died mid-soak; the daemon went with
# it, and the run kept going. A game against a dead server fails to seat in
# milliseconds instead of taking ~21s, so batches 3, 4 and 5 -- 75 games --
# were consumed in 40 SECONDS and logged as "no result". Left alone the whole
# 400-game budget would have been spent in about four minutes, and the log would
# have read like a harness fault rather than an outage.
#
# So: never start a batch without checking, and if the server is gone, try to
# bring it back rather than pressing on.
server_up() { [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$API" 2>/dev/null)" != "000" ]; }

wait_for_server() {  # $1 = minutes to wait before giving up
  local mins="${1:-20}" i
  server_up && return 0
  say "!!! server not answering at $(date '+%H:%M:%S') -- pausing"

  # The daemon itself may be gone, not just the container.
  if ! "$DOCKER" ps >/dev/null 2>&1; then
    say "    docker daemon is down; starting Docker Desktop"
    powershell.exe -NoProfile -Command \
      'Start-Process "C:\Users\emers\AppData\Local\Programs\DockerDesktop\Docker Desktop.exe"' \
      >/dev/null 2>&1 || true
  fi

  for i in $(seq 1 $(( mins * 6 ))); do
    sleep 10
    if "$DOCKER" ps >/dev/null 2>&1; then
      "$DOCKER" start gemp_db_2 gemp_app_2 >/dev/null 2>&1 || true
    fi
    if server_up; then
      say "    server back after $(( i * 10 ))s; resuming"
      sleep 20                 # GEMP needs a moment after it starts answering
      return 0
    fi
  done
  say "!!! server still down after ${mins}m -- stopping so the log stays honest"
  return 1
}

GAMES=$(( (DECKS + SEATS - 1) / SEATS ))
BATCHES=$(( (GAMES + BATCH - 1) / BATCH ))
HERE="$(cd "$(dirname "$0")" && pwd)"
LOG="${LOG:-/tmp/soak.log}"
: > "$LOG"

say() { echo "$*" | tee -a "$LOG"; }

say "soak: $DECKS decks = $GAMES games in $BATCHES batches of $BATCH, $MAX decisions each"
say "started $(date '+%Y-%m-%d %H:%M:%S')   log: $LOG"
say ""

clean=0; problems=0; failed=0; done_games=0

for b in $(seq 1 "$BATCHES"); do
  left=$(( GAMES - done_games ))
  size=$(( left < BATCH ? left : BATCH ))
  [ "$size" -le 0 ] && break

  wait_for_server 20 || break

  base=$(( OFFSET + done_games ))
  say "--- batch $b/$BATCHES  ($size games, seed base $base)  $(date '+%H:%M:%S')"

  started=$(date +%s)
  # SEED_BASE keeps every batch on fresh decks and fresh client seeds.
  SEED_BASE="$base" bash "$HERE/livediffrun.sh" "$size" "$MAX" 2>&1 | tee -a "$LOG" > /tmp/soak_batch.txt
  elapsed=$(( $(date +%s) - started ))

  c=$(grep -c 'RESULT: CLEAN'  /tmp/soak_batch.txt || true)
  p=$(grep -c 'PROBLEM'        /tmp/soak_batch.txt || true)
  f=$(grep -cE 'RESULT: NONE|could not seat' /tmp/soak_batch.txt || true)
  clean=$((clean + c)); problems=$((problems + p)); failed=$((failed + f))
  done_games=$((done_games + size))

  # A batch that finished far too fast did not play anything. A real game takes
  # ~21s; 25 of them cannot finish in 13 seconds, which is exactly what happened
  # when the server died. Treat it as an outage rather than counting it.
  if [ "$elapsed" -lt $(( size * 5 )) ] && [ "$c" -eq 0 ]; then
    say "    batch took ${elapsed}s for $size games with 0 clean -- server is not playing"
    wait_for_server 20 || break
  fi

  # Housekeeping. Conceding genuinely ends the games; the listing itself only
  # shrinks on a container restart, which is NOT done here -- restarting under a
  # running soak would drop the games in flight.
  bash "$HERE/cleanhall.sh" >/dev/null 2>&1 || true
  rm -rf /c/Users/emers/AppData/Local/Temp/cr_live* 2>/dev/null || true

  say "    running total: clean $clean   problems $problems   no result $failed" \
      "  (${done_games}/${GAMES} games, $((done_games * SEATS)) decks)"
  say ""
done

say "=========================================================="
say "soak finished $(date '+%Y-%m-%d %H:%M:%S')"
say "  games:   $done_games   decks: $((done_games * SEATS))"
say "  clean:   $clean"
say "  problems:$problems"
say "  no result:$failed"
say ""
# The failure lines themselves, deduped and counted. Matched on the exact
# messages the client emits -- a looser pattern also matches this script's own
# explanation of the pattern, once it has been written to the log.
say "Failures, deduped (empty means none):"
grep -E 'ENGINE REJECTED|OFFERS DIFFER|ANSWERS DIFFER' "$LOG" \
  | sed 's/^ *//' | sort | uniq -c | sort -rn | head -20 | tee -a "$LOG"
