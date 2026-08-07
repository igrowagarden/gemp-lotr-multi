#!/usr/bin/env bash
# Run the old-vs-new differential over a sample of recorded games.
#
# The old client is the oracle: it is what thousands of real games were played
# through, so a disagreement is a bug in the new client until shown otherwise.
# `dev/diff.html` does one game; this drives many and aggregates.
#
#   bash diffrun.sh [count] [decisions-per-game] [sabotage]
#
# Pass `actionids` as the third argument for the NEGATIVE CONTROL: it
# re-introduces a bug the new client really shipped with, and the run must then
# report mismatches. A comparison that has never been seen to fail is not
# evidence of agreement.
set -u

COUNT="${1:-8}"
MAX="${2:-30}"
SABOTAGE="${3:-}"

CHROME="/c/Program Files/Google/Chrome/Application/chrome.exe"
DOCKER=/c/Users/emers/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe
BASE="http://localhost:17002/gemp-lotr/newclient/dev/diff.html"
LIST=/tmp/replays.txt

# Recording ids are <player>$<id>, and the path encodes the player.
"$DOCKER" exec gemp_app_2 sh -c 'find /etc/gemp-lotr/replay -type f -name "*.xml.gz"' 2>/dev/null \
  | sed 's|.*/replay/[0-9]*/[0-9]*/||; s|\.xml\.gz$||; s|/|$|' | sort -u > "$LIST"

total=$(wc -l < "$LIST")
echo "recordings available: $total, sampling $COUNT (max $MAX decisions each)${SABOTAGE:+ [SABOTAGE=$SABOTAGE]}"

# Shuffled, so a run is a random sample rather than always the same games.
#
# FIX THE SAMPLE WHEN BISECTING. A random sample is right for "do the clients
# agree in general" and useless for "did this change break something": two runs
# draw different games, so 0-of-3 against 1-of-3 compares nothing at all. A whole
# bisect was run on that comparison before the flaw was noticed. `IDS=` pins the
# games; `SEED=` pins the shuffle.
agree=0; mismatch=0; errored=0
if [ -n "${IDS:-}" ]; then
  SAMPLE="$IDS"
elif [ -n "${SEED:-}" ]; then
  SAMPLE="$(shuf -n "$COUNT" --random-source=<(yes "$SEED") "$LIST")"
else
  SAMPLE="$(shuf -n "$COUNT" "$LIST")"
fi
for id in $SAMPLE; do
  player="${id%%\$*}"
  url="$BASE?replayId=$id&max=$MAX&login=$player&password="
  # Every seeded account uses one of two passwords.
  case "$player" in
    asdf) url="$url"asdf ;;
    *)    url="$url"qwer ;;
  esac
  [ -n "$SABOTAGE" ] && url="$url&sabotage=$SABOTAGE"

  res=$(timeout 200 "$CHROME" --headless --disable-gpu \
        --user-data-dir="C:\\Users\\emers\\AppData\\Local\\Temp\\cr_diff" \
        --window-size=1500,950 --dump-dom --virtual-time-budget=45000 "$url" 2>/dev/null \
        | python -c "
import sys,re,html
d=sys.stdin.read()
m=re.search(r'<pre id=\"out\">(.*?)</pre>', d, re.S)
t=html.unescape(re.sub(r'<[^>]+>','',m.group(1))) if m else 'NO OUTPUT'
res=[l for l in t.splitlines() if l.startswith('RESULT:')]
det=[l for l in t.splitlines() if l.strip().startswith(('#','     '))][:6]
print((res[0] if res else 'RESULT: NONE') + ('|' + ' // '.join(x.strip() for x in det) if det else ''))
")
  verdict="${res%%|*}"
  detail="${res#*|}"
  printf "  %-34s %s\n" "$id" "$verdict"
  [ "$verdict" != "$detail" ] && [ -n "$detail" ] && echo "      $detail"

  case "$verdict" in
    *AGREE*)    agree=$((agree+1)) ;;
    *MISMATCH*) mismatch=$((mismatch+1)) ;;
    *)          errored=$((errored+1)) ;;
  esac
done

echo
echo "games agreeing: $agree   disagreeing: $mismatch   errored: $errored"
