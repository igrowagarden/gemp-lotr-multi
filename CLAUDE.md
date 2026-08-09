# gemp-lotr-multi — the five-player GEMP fork

One repo, two worlds, two methods. Know which one you are in.

## The engine (Java)

`gemp-lotr/` is the Maven tree. The multiplayer rework — seats 2–5, turn
rotation, elimination, the rulings — is documented in the wrapping research
project: **`../../HANDOFF.md` and `../../PLAN.md`** (the `gemp_multiplayer`
project two levels up). Its method is measured rulings and `mvn` tests;
read its HANDOFF box before touching engine code. Beware:
`mvn -B test` silently SKIPS gemp-lotr-async — use
`mvn -B test "-Dmaven.test.failure.ignore=true"`.

## The clients (web)

**To launch a game in either client, see `LAUNCHING.md`** — server start,
the URLs for both GUIs, and the same game open in both at once.

Both are served from `gemp-lotr/gemp-lotr-async/src/main/web/`:

- **The OLD client** — `game.html`, `hall.html`, `js/gemp-022/gameUi.js` at
  the web root. Shows one opponent at a time. It is the *oracle* for the new
  client's differential tests: measured, trusted, but not infallible (its
  known bugs are documented in the new client's docs).
- **The NEW client** — `web/newclient/`, a complete project with its own
  `CLAUDE.md`, `HANDOFF.md`, `ARCHITECTURE.md`, test suites and harnesses.
  Imported from the standalone `gemp_gui` repo with full history
  (`git log -- gemp-lotr/gemp-lotr-async/src/main/web/newclient`). **Read
  `newclient/CLAUDE.md` before touching anything in there.**

## Testing the new client

- `bash newclient/harness/fastrun.sh` — 18 assertion suites + the 48-shape
  decision catalogue, no Docker, no reference client, ~1 minute. The inner
  loop.
- The differentials (`newclient/harness/*run.sh`) drive BOTH clients against
  the running server and compare; `diffrun.sh` replays recorded games,
  `livediffrun.sh` plays live ones with the engine judging. These are the
  verification of record for client changes.

## The runtime

The server actually runs from **`%USERPROFILE%\gemp2`** — a copy OUTSIDE
OneDrive holding the built jar, the MySQL data dir and the recording corpus
(4,600+ games). `newclient/harness/sync.sh` deploys the client there;
`gemp2/gemp-lotr/docker` runs it (port 17002). Do not run a server off THIS
tree's `database/` — MySQL under OneDrive sync is a corruption hazard. After
engine changes: rebuild (`mvn -B -q -DskipTests install`), copy or rebuild in
gemp2, restart the app container. The engine at gemp2 was last synced to this
repo's `5e2a2a273` and verified by the full client battery (2026-08-09).
