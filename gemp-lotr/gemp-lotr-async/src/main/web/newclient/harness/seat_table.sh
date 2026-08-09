#!/usr/bin/env bash
# Open a table for N seats and fill it. The first named user creates, the rest
# join. Prints the game id and everyone's session cookie.
#
#   ./seat_table.sh 2 asdf qwer
#   ./seat_table.sh 5 asdf qwer Librarian carol dave
set -u
cd "$(dirname "$0")"
source ./gemp_api.sh

SEATS=$1; shift
USERS=("$@")

declare -A COOKIE
for u in "${USERS[@]}"; do
  p=qwer; [ "$u" = "asdf" ] && p=asdf
  c=$(login "$u" "$p")
  [ -z "$c" ] && { echo "LOGIN FAILED: $u"; exit 1; }
  COOKIE[$u]=$c
done

# Abandon any table still being waited at, so seat counts start clean.
for u in "${USERS[@]}"; do
  for t in $(hall "$u" "${COOKIE[$u]}" | grep -o '<table [^>]*status="WAITING"[^>]*>' \
             | grep -o 'id="[0-9]*"' | grep -o '[0-9]*'); do
    curl -s -X POST "$BASE/hall/$t/leave" --cookie "loggedUser=${COOKIE[$u]}" \
      --data-urlencode "participantId=$u" --max-time 20 >/dev/null
  done
done

HOST_USER=${USERS[0]}
createtable "$HOST_USER" "${COOKIE[$HOST_USER]}" "$SEATS" >/dev/null

# The hall does not publish the new table the instant `createtable` returns, so
# a single lookup intermittently finds nothing and the whole run reports "no
# table created" as though the deck or the account were at fault. Retry briefly.
TID=""
for _ in 1 2 3 4 5 6 7 8; do
  TID=$(hall "$HOST_USER" "${COOKIE[$HOST_USER]}" \
        | grep -o "<table [^>]*players=\"[^\"]*$HOST_USER[^\"]*\"[^>]*status=\"WAITING\"[^>]*>" \
        | grep -o 'id="[0-9]*"' | grep -o '[0-9]*' | tail -1)
  [ -n "$TID" ] && break
  sleep 1
done
[ -z "$TID" ] && { echo "no table created"; exit 1; }

for u in "${USERS[@]:1}"; do
  jointable "$u" "${COOKIE[$u]}" "$TID" >/dev/null
done

FINAL=$(hall "$HOST_USER" "${COOKIE[$HOST_USER]}" | grep -o "<table [^>]*id=\"$TID\"[^>]*>")
echo "table=$TID seats=$SEATS"
echo "$FINAL" | grep -o 'gameId="[^"]*"'
echo "$FINAL" | grep -o 'status="[^"]*"'
echo "$FINAL" | grep -o 'players="[^"]*"'
for u in "${USERS[@]}"; do echo "cookie $u ${COOKIE[$u]}"; done
