#!/usr/bin/env bash
# Play random live games, comparing both GUIs with the ENGINE as judge.
#
# Each round seats a fresh five-player table, puts bots on four seats, and sits
# the differential client in the fifth. Every decision is checked three ways:
#
#   offers   do both clients light up the same options?
#   answers  for the same chosen option, do they produce the same string?
#   engine   is the string actually accepted, or refused with a warning and the
#            same decision asked again (LotroGameMediator:434)?
#
# `mode=alternate` sends the old client's answer on even decisions and the new
# client's on odd ones, so the engine validates BOTH -- sending only the oracle's
# answers would keep the game legal while never testing the new client at all.
#
#   bash livediffrun.sh [games] [decisions] [mode]
set -u

GAMES="${1:-3}"
MAX="${2:-60}"
MODE="${3:-alternate}"

# Batches of this script are run back to back by `soak.sh`. Without an offset
# every batch would restart at round 1 and rebuild the SAME decks from the same
# seeds -- a long soak would then be one short run repeated, which is exactly
# the weakness the deck generator was rewritten to fix.
SEED_BASE="${SEED_BASE:-0}"

# Formats the hall accepts a generated deck for, one per round.
#
# `FORMATS=king_block bash livediffrun.sh 6 60` pins one to hunt a failure that
# only that format produces. Hunting is the right word: a rejection is NOT
# reproducible from the seed, because the engine shuffles both decks server-side
# and the bots play their own hands, so the same seed gives a different game.
# That is why a problem game prints its decision parameters and its replay id
# rather than expecting anyone to re-run it.
FORMATS=(${FORMATS:-fotr_block pc_fotr_block ttt_block towers_standard
         ts_reflections king_block rotk_sta})

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
DOCKER=/c/Users/emers/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe
HERE="$(cd "$(dirname "$0")" && pwd)"
BASE="http://localhost:17002/gemp-lotr/newclient/dev/livediff.html"

clean=0; problems=0; failed=0
for round in $(seq 1 "$GAMES"); do
  # A fresh random deck for every seat, every game. Sampled from a pool the
  # server has already accepted, so legality is guaranteed -- an illegal deck
  # does not fail loudly, it fails later as "no table created", and the whole
  # run then looks like an infrastructure problem rather than a bad deck.
  # Rotate through every format the hall offers AND randomdeck can build a legal
  # deck for. Different formats mean different card pools, different adventure
  # paths and different site abilities -- a differential that only ever plays
  # Fellowship Block never sees most of the game.
  n=$((round + SEED_BASE))
  FMT=${FORMATS[$(( (n - 1) % ${#FORMATS[@]} ))]}

  if python "$HERE/randomdeck.py" rnd "$n" "$FMT" asdf qwer Librarian carol dave >/dev/null 2>&1; then
    DECKNAME=rnd
  else
    DECKNAME=starter; FMT=fotr_block
  fi

  gid=$(DECK_NAME="$DECKNAME" FORMAT="$FMT" bash "$HERE/seat_table.sh" 5 asdf qwer Librarian carol dave 2>/dev/null \
        | grep -oE 'gameId="[0-9]+"' | grep -oE '[0-9]+')
  if [ -z "${gid:-}" ]; then echo "  round $round: could not seat a table"; failed=$((failed+1)); continue; fi

  # Bots take the other four seats. `asdf` is left alone -- that is our seat.
  python "$HERE/play_bots.py" --players qwer,Librarian,carol,dave --game "$gid" \
         --play --delay "${BOT_DELAY:-0}" --seconds 300 > /tmp/livebots_$gid.log 2>&1 &
  bots=$!
  sleep 2

  # A different seed per round, so the choices differ game to game but any one
  # round can be replayed exactly.
  seed=$((n * 7 + 3))
  res=$(timeout 220 "$CHROME" --headless --disable-gpu \
        --user-data-dir="C:\\Users\\emers\\AppData\\Local\\Temp\\cr_live$round" \
        --window-size=1500,950 --dump-dom --virtual-time-budget=170000 \
        "$BASE?gameId=$gid&participantId=asdf&login=asdf&password=asdf&mode=$MODE&seed=$seed&max=$MAX&for=100" \
        2>/dev/null | python -c "
import sys,re,html
d=sys.stdin.read()
m=re.search(r'<pre id=\"out\">(.*?)</pre>', d, re.S)
t=html.unescape(re.sub(r'<[^>]+>','',m.group(1))) if m else ''
ls=[l for l in t.splitlines() if l.strip()]
res=[l for l in ls if l.startswith('RESULT:')]
det=[l for l in ls if l.startswith('by type')]
# The COUNTERS, not a sample of the lines. Keeping the first three problem
# lines per game made 'zero engine rejections' unprovable: 128 problems were
# reported and 24 lines survived, so anything past the third was invisible.
cnt=[l for l in ls if l.startswith(('offer mismatches','engine rejections'))]
# Counters AND a few deduped samples. Counters alone prove HOW MANY went wrong
# but say nothing about WHY, and a rejection cannot be reproduced from the seed,
# so a run that reports '13 rejections' and no detail cannot be diagnosed at all.
seen, why = set(), []
for l in ls:
    if any(k in l for k in ('REJECTED','MISMATCH','engine said','  decision ')):
        k = l[:90]
        if k not in seen: seen.add(k); why.append(l[:220])
print((res[-1] if res else 'RESULT: NONE') + '|' + (det[-1] if det else '') + '|'
      + ' ; '.join(cnt) + '|' + (chr(10) + '      ').join(why[:12]))
")
  kill "$bots" 2>/dev/null; wait "$bots" 2>/dev/null

  verdict="${res%%|*}"; rest="${res#*|}"; types="${rest%%|*}"
rest="${rest#*|}"; bad="${rest%%|*}"; why="${rest#*|}"
  printf "  game %-4s %-16s seed %-3s %s\n" "$gid" "$FMT" "$seed" "$verdict"
  [ -n "$types" ] && echo "      $types"
  [ -n "$bad" ] && echo "      $bad"
  [ -n "$why" ] && echo "      $why"

  # A problem game's RECORDING, so the failure outlives the run. The engine
  # records every game, and `diff.html?replayId=<id>` feeds it back to both
  # clients deterministically -- which is the only way to re-examine a rejection,
  # since re-running the format just plays a different game.
  case "$verdict" in *PROBLEM*)
    rid=$("$DOCKER" exec gemp_app_2 sh -c           'ls -t /etc/gemp-lotr/replay/*/*/asdf/*.xml.gz 2>/dev/null | head -1' 2>/dev/null           | sed 's|.*/||; s|\.xml\.gz||')
    [ -n "$rid" ] && echo "      replay: dev/diff.html?replayId=asdf\$$rid&login=asdf&password=asdf"
  ;; esac

  case "$verdict" in
    *CLEAN*)   clean=$((clean+1)) ;;
    *PROBLEM*) problems=$((problems+1)) ;;
    *)         failed=$((failed+1)) ;;
  esac
done

echo
echo "clean games: $clean   with problems: $problems   no result: $failed"
