#!/usr/bin/env bash
# Clear abandoned games out of the hall.
#
# Every differential run leaves a five-player game behind: the bots stop, nobody
# answers, and the table sits at PLAYING for ever. They are never collected, so
# the hall grows without bound -- 191 tables and 64 KB per fetch by the end of a
# day's testing, and `seat_table.sh` fetches it about ten times per game.
#
# A game ends when its players concede, so that is what this does: concede every
# live game as each of the five accounts, then leave anything still WAITING.
# Conceding is per-player, and a table only disappears once the game is actually
# over, so all five are asked.
#
# It only touches the five test accounts' games. Nothing else is on this server,
# but that is the boundary if it ever is.
#
# WHAT THIS DOES AND DOES NOT DO. Conceding genuinely ends the games -- 195
# tables went PLAYING -> FINISHED in one pass -- but GEMP keeps finished tables
# in the hall listing, so the listing does not shrink and neither does the 64 KB
# every `hall` fetch pays. To actually empty it, restart the app container:
#
#   docker restart gemp_app_2      # ~36s to answer again
#
# The hall and live games are in memory; decks are in gemp_db_2 and replays are
# on disk, so both survive a restart (verified: decks intact, 1326 replays).
# Conceding first is still worth it -- it ends games properly rather than
# killing them mid-flight, and it is the only option if the server must stay up.
set -u
cd "$(dirname "$0")"
source ./gemp_api.sh

USERS=(asdf qwer Librarian carol dave)

declare -A COOKIE
for u in "${USERS[@]}"; do
  p=qwer; [ "$u" = "asdf" ] && p=asdf
  COOKIE[$u]=$(login "$u" "$p")
done

before=$(hall asdf "${COOKIE[asdf]}" | grep -o '<table ' | wc -l)
echo "tables before: $before"

# Live games, by game id.
games=$(hall asdf "${COOKIE[asdf]}" \
        | grep -o '<table [^>]*gameId="[0-9]*"[^>]*>' \
        | grep -o 'gameId="[0-9]*"' | grep -o '[0-9]*' | sort -un)

n=0
for g in $games; do
  for u in "${USERS[@]}"; do
    curl -s -o /dev/null -X POST "$BASE/game/$g/concede" \
      --cookie "loggedUser=${COOKIE[$u]}" \
      --data-urlencode "participantId=$u" --max-time 10
  done
  n=$((n+1))
  [ $((n % 25)) -eq 0 ] && echo "  conceded $n games…"
done
echo "conceded: $n games"

# Anything still merely waiting to fill.
for u in "${USERS[@]}"; do
  for t in $(hall "$u" "${COOKIE[$u]}" | grep -o '<table [^>]*status="WAITING"[^>]*>' \
             | grep -o 'id="[0-9]*"' | grep -o '[0-9]*'); do
    curl -s -o /dev/null -X POST "$BASE/hall/$t/leave" --cookie "loggedUser=${COOKIE[$u]}" \
      --data-urlencode "participantId=$u" --max-time 10
  done
done

sleep 2
after=$(hall asdf "${COOKIE[asdf]}" | grep -o '<table ' | wc -l)
echo "tables after: $after  (removed $((before - after)))"
