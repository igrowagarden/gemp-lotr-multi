# Launching a game — old GUI or new GUI

Both clients are served by the same server from the same origin, so any game
can be opened in either. The only difference is the URL.

## 0. Start the server (once)

```bash
cd ~/gemp2/gemp-lotr/docker
DOCKER="$HOME/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe"
"$DOCKER" compose up -d          # serves on http://localhost:17002
```

If you changed the NEW client's source, deploy it first — the pages are
served from the runtime copy, not from this repo:

```bash
bash gemp-lotr/gemp-lotr-async/src/main/web/newclient/harness/sync.sh
```

(It refuses while a test harness is running; that is deliberate.)

## 1. The old GUI

Log in once so the session cookie exists, then the hall does the rest:

    http://localhost:17002/gemp-lotr/                <- login page
    http://localhost:17002/gemp-lotr/hall.html       <- create or join a table

When a table you are seated at starts, the hall opens the board itself. To
open a running game directly:

    http://localhost:17002/gemp-lotr/game.html?gameId=<N>

## 2. The new GUI

The new client's pages take login credentials as URL parameters, so a single
URL is a complete launch (example users from the dev database: `asdf/asdf`,
`watcher/qwer`):

    hall (create/join/open by click):
    http://localhost:17002/gemp-lotr/newclient/hall.html?participantId=asdf&login=asdf&password=asdf

    a live game, as a player or spectator:
    http://localhost:17002/gemp-lotr/newclient/live.html?gameId=<N>&participantId=asdf&login=asdf&password=asdf

    a finished game, from the recording corpus:
    http://localhost:17002/gemp-lotr/newclient/replay.html?replayId=<player>$<recordingId>&login=asdf&password=asdf

## 3. The same game in both

Nothing special: open `game.html?gameId=N` in one tab and
`newclient/live.html?gameId=N&participantId=...` in another. Same server,
same session, same game. The live differential does exactly this on every
run — both clients driven against one game with the engine judging — so the
property is continuously verified, not assumed.

## 4. Getting a game to launch INTO

Fastest way to a running multi-seat game without clicking through the hall:

```bash
bash gemp-lotr/gemp-lotr-async/src/main/web/newclient/harness/seat_table.sh 3 asdf qwer Librarian
# prints the gameId; open it in either client with the URLs above
```
