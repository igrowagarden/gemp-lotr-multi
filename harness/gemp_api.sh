#!/usr/bin/env bash
# Drive GEMP through its HTTP API: log players in, give them a deck, open a
# table, fill it. Beats clicking through the UI five times, and gives us the
# session cookies we need to point a browser at the resulting game.
set -u
BASE="http://localhost:17002/gemp-lotr-server"

# The Aragorn starter, lifted from BotService. Format is
#   ringBearer|ring|9 sites|draw deck
#
# `DECK` is a deck's CONTENTS, and is what `savedeck` uploads. The deck a table
# is created with is chosen by NAME, which is `DECK_NAME`. They are different
# things: conflating them sends a 900-character card list as a deck name, the
# table is never created, and the run reports "no table created" as though the
# account or the deck were at fault.
DECK='1_290|1_2|1_320,1_327,1_340,1_346,1_349,1_351,1_355,1_358,1_361|1_365,1_365,1_92,1_94,1_94,1_97,1_97,1_101,1_104,1_104,1_106,1_107,1_107,1_296,1_296,1_299,1_299,1_51,1_108,1_108,1_110,1_110,1_309,1_311,1_116,1_116,1_116,1_117,1_117,1_117,1_121,1_121,1_121,1_133,1_133,1_141,1_141,1_145,1_150,1_150,1_150,1_150,1_151,1_151,1_151,1_152,1_152,1_152,1_152,1_153,1_153,1_153,1_153,1_154,1_154,1_154,1_157,1_157,1_158,1_158'

login() {  # $1=user $2=pass -> cookie on stdout
  curl -s -i -X POST "$BASE/login" -d "login=$1&password=$2" --max-time 20 \
    | grep -i '^set-cookie' | sed 's/.*loggedUser=//; s/[[:space:];].*//'
}

savedeck() {  # $1=user $2=cookie
  curl -s -X POST "$BASE/deck" --cookie "loggedUser=$2" \
    --data-urlencode "participantId=$1" \
    --data-urlencode "deckName=starter" \
    --data-urlencode "targetFormat=fotr_block" \
    --data-urlencode "notes=" \
    --data-urlencode "deckContents=$DECK" --max-time 25
}

createtable() {  # $1=user $2=cookie $3=seatCount
  curl -s -X POST "$BASE/hall" --cookie "loggedUser=$2" \
    --data-urlencode "participantId=$1" \
    --data-urlencode "format=${FORMAT:-fotr_block}" \
    --data-urlencode "deckName=${DECK_NAME:-starter}" \
    --data-urlencode "timer=default" \
    --data-urlencode "desc=" \
    --data-urlencode "isPrivate=false" \
    --data-urlencode "isInviteOnly=false" \
    --data-urlencode "seatCount=$3" --max-time 25
}

jointable() {  # $1=user $2=cookie $3=tableId
  curl -s -X POST "$BASE/hall/$3" --cookie "loggedUser=$2" \
    --data-urlencode "participantId=$1" \
    --data-urlencode "deckName=${DECK_NAME:-starter}" --max-time 25
}

hall() {  # $1=user $2=cookie
  curl -s --cookie "loggedUser=$2" "$BASE/hall?participantId=$1" --max-time 25
}

register() {  # $1=user $2=pass -- our database only seeds asdf/qwer/Librarian
  curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/register" \
    -d "login=$1&password=$2" --max-time 20
}
