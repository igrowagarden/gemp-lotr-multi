# Handoff — new board client

**Status:** the client runs. It renders a live five-player game from the real
server, answers decisions, detaches boards into their own windows, animates
between zones and draws real card art. It is differentially verified against the
reference client with the engine judging, across **all seven playable formats**,
with working negative controls on both halves. Read `DESIGN.md` next — it is the
design of record and carries the evidence for every claim about GEMP's
behaviour.

**The one open failure is now solved**: every unexplained engine rejection was
the harness answering a `CARD_SELECTION` that asked for `max=0` cards. Fixed and
verified. See "The CARD_SELECTION rejections: SOLVED".

**There is now a git repository here.** Two commits. See "Version control,
finally".

**The newest instrument is `src/dev/decisionfuzz.html`** -- a differential over
DECISION SHAPES rather than over games. It found four real client bugs in an
afternoon, after 400 games overnight found none. Read "The decision-space
differential" before running another soak: the lesson is that volume and
coverage are different things, and this project had been buying the wrong one.
All seven decision types now pass -- 47 cases, offers AND the exact string
sent, with three controls proven to fire. `bash harness/fuzzrun.sh` is the one
command.

**This header used to say "no client code written yet" and was a full session
out of date.** If you are reading this after a crash, trust file mtimes over
prose: `find . -type f -newermt "<when>" -printf "%TH:%TM %p\n" | sort` is how
the state of an unversioned tree gets reconstructed, and it is how this session
started. There is still no git here (see below), so nothing else records it.

---

## Read this first: this project is deliberately detached

This directory is **not part of any repository**. It is plain files, sitting
beside the main project, by choice:

```
Documents/
  gemp_gui/            <- you are here. No git. Not a worktree, not a submodule.
  gemp_multiplayer/    <- the main project. Do not touch.
    vendor/gemp-lotr/  <- the GEMP fork. Do not touch.
```

**The main project is worked separately, in both repos, and may be active while
you are here.** Anything you do in either of those trees lands in the middle of
someone else's work. Sessions for *this* project are sometimes launched from the
`gemp_multiplayer` directory, so check which project you are on before editing:
if the task is the board client, everything you touch is under `gemp_gui/` and
`C:\Users\emers\gemp2`.

This arrangement was chosen after trying and rejecting a git worktree on a
`gui/board-client` branch. It worked, but it still put this project inside the
main repo's history, which is more coupling than wanted for now. Both the branch
and the worktree were removed and `gemp_multiplayer` was restored to exactly the
state it was found in — `main` at `ad12dfe`, one branch, clean tree.

### Version control, finally

**There is a git repository here now**, created after ~50 source files and
thirteen suites had accumulated with no undo. It shares nothing with either GEMP
repo, which is the whole point: the project stays detached while stopping being
unrecoverable.

    4c44d79  Initial commit: five-player GEMP board client, harness and docs
    f2fb788  Instrument the fuzz loop; localise the button-driver hang

One thing it does NOT buy, and the reason it should have existed sooner: **the
baseline captures the state at the end of the session that created it**, broken
driver included. It cannot bisect the regression that prompted it. Half a day
went into chasing a fault with nothing to diff against; the repository prevents
the next one, not that one.

Commit before editing `src/dev/oldharness.html`. It is the shared oracle for
three pages and was edited five times in a single session below.

**We have our own GEMP server.** No coordination needed any more.

| stack | app | db | tree | owner |
|---|---|---|---|---|
| `gemp_1` | 17001 | 35001 | `gemp_multiplayer/vendor/gemp-lotr` | the other agent |
| `gemp_2` | **17002** | 35002 | `C:\Users\emers\gemp2` | **this project** |

The compose file is parameterised by `SERVID`, so most of the isolation existed
already. Two things did not. The JRE debug port is hardcoded at 8052 — and note
`805${SERVID}` is *also* 8052, which is the first thing that bit. And the
`database/`, `logs/`, `replay/` binds are fixed relative paths, so two stacks
would have written a single MySQL data directory.

`C:\Users\emers\gemp2` is a 143 MB copy of the module tree, deliberately outside
OneDrive so it does not sync to the cloud. Its database seeded itself from
`database_script.sql` + `initial_user_setup.sql`, giving `asdf`/`asdf` plus
`qwer` and `Librarian` on `qwer`; `carol` and `dave` were registered afterwards.

```bash
cd /c/Users/emers/gemp2/gemp-lotr/docker
DOCKER=/c/Users/emers/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe
"$DOCKER" compose up -d          # `down` to stop
```

**Never point our stack at their tree.** Their `web.jar` is rebuilt while they
work — it changed at 16:29 mid-session — so a container mounted there reads a
moving target.

### Driving it

`harness/` holds copies of the main project's API scripts, repointed at 17002:

```bash
cd /c/Users/emers/OneDrive/Documents/gemp_gui/harness
bash ./seat_table.sh 5 asdf qwer Librarian carol dave     # -> gameId, cookies
python play_bots.py --players asdf,qwer,Librarian,carol,dave --game 1 --seconds 70
```

`play_bots.py` answers with the same MINIMAL policy the engine harness uses, so
the game advances without anyone trying to win.

### The wire check

`src/dev/wirecheck.html` runs the decoder and reducer over
`src/dev/live_capture.xml` — XML captured from a real five-player game on our own
server, not XML we wrote. Re-capture with:

```bash
curl -s "http://localhost:17002/gemp-lotr-server/game/1?participantId=asdf" \
  --cookie "loggedUser=<cookie>" > src/dev/live_capture.xml
```

Two bugs came from real data, and neither was findable by reading the serialiser:

1. `charStats="356=4|2|RF10"` — a companion's resistance carries its **signet**
   as a letter (`A`/`F`/`G`/`T` for Aragorn, Frodo, Gandalf, Theoden), so `RF10`
   is signet Frodo with resistance 10. Reading it as a plain number gave `NaN`.
2. **`PRE_GAME_SETUP` carries `allParticipantIds`**, and during bidding it
   arrives *before* any `PARTICIPANTS` event. Treating it as presentation-only
   left the seat strip empty for the whole pre-game.

Also worth knowing from the wire: `fellowshipArchery="null"` arrives as the
literal string `"null"`, and `hindered` is a boolean on card events but a comma
list of ids on `GAME_STATS`.

---

## Where the work is

```
gemp_gui/
  HANDOFF.md              this file
  DESIGN.md               the design of record, with file:line evidence
  src/                    THE CLIENT. live.html, replay.html, hall.html + modules
  src/dev/                assertion suites, and BOTH differential harnesses
  harness/                seat tables, run bots, deploy, compare, clean up
  board_prototype.html    the original wireframe — open it directly
  multiwindow_spike.html  proves detachable windows — open it directly
```

```
harness/
  sync.sh          deploy src/ into the container's web dir, cache-busted
  seat_table.sh    open a 5-seat table and fill it        (DECK_NAME=)
  play_bots.py     drive seats with the MINIMAL policy    (--delay, --play)
  randomdeck.py    a fresh legal random deck per seat, any of 7 formats
  diffrun.sh       REPLAY differential over recorded games (+ sabotage control)
  livediffrun.sh   LIVE differential, engine judging  (BOT_DELAY=, FORMATS=,
                   SEED_BASE=)
  soak.sh          hours of unattended games, with outage recovery  (OFFSET=)
  cleanhall.sh     concede abandoned games; see the restart note
  fuzzrun.sh       DECISION-SPACE differential: all 7 types + all 3 controls,
                   no server game needed        (TYPES=, `baseline` for no controls)
```

`src/` is the real thing and is where all current work happens; the two
prototypes below it are the wireframes that preceded it, kept because they still
demonstrate behaviour quickly with no server. Both are standalone: no server, no
build, no dependencies. Open them from disk.

`board_prototype.html` has demo buttons for phase, decision, skirmish, site
move, chat message and elimination, plus a Player/Spectator mode switch. Every
behaviour described in DESIGN.md can be exercised there.

`multiwindow_spike.html` must be opened **outside a sandboxed frame** — its ⧉
buttons call `window.open`, which a sandboxed iframe blocks regardless of
browser popup settings. Its log reports `N window(s) detached, 1 connection`,
which is the property the whole approach rests on.

---

## What is decided

All of it is in `DESIGN.md`. The short version:

- One opponent focused at a time, cycled with edge arrows, ← / →, or a seat chip.
  Full-size cards are the thing this buys.
- Player statistics are a **horizontal strip** across the top, not a sidebar.
- **Minions are one shared band**, every seat's, each tagged with whose. Never
  filtered. Survives a second move.
- Four-way side filter (**Auto · FP · Shadow · All**) over the focused seat's own
  cards.
- Chat and the site path are **floating windows** — draggable, resizable,
  position and size remembered, Escape closes the active one.
- The site path shows **only what has been played**, tagged with whose deck each
  site came from.
- Skirmishes **suspend the focus** and draw every participating seat.
- Spectators get **no adopted seat**; every seat is selectable, the bottom shows
  the contested fellowship, and "Follow the action" tracks the FP player.
- Multi-window: **one transport, many views**. Never a second client.

---

## Next, in order

The prototypes are wireframes, not the client. They pin down *what the board
does*; none of them pin down the state model, and none are structured the way
the real client must be. See the Architecture section of `DESIGN.md` — modularity
is an explicit requirement, not a preference.

1. ~~**`model/zones.js`** — the registry.~~ **Done.** Zone mirror, ordered
   bands, `assignBands`, and a `checkComplete` invariant.
2. ~~**`net/protocol.js`** — the wire format.~~ **Done.** All 27
   `GameEvent.Type` codes, `charStats`, decisions, game stats; unknown codes
   degrade instead of throwing.
3. ~~**`state/reduce.js` + `store.js`**~~ **Done.** Pure `(state, event) ->
   state` plus selectors, and a store whose `dispatchAll` repaints once per
   batch and drops a listener that throws.
4. ~~**The board as views over the store.**~~ **Done.** `src/board.html` renders
   from decoded event XML through the store — no fixture state anywhere, the
   same path a live game takes.
5. ~~**Transport.**~~ **Done.** `src/net/transport.js`, unit-tested against a
   fake `fetch` and **connected to a real five-player game**.
6. ~~**Serving from the GEMP origin.**~~ **Done.** `harness/sync.sh` copies the
   client into our container's bind-mounted web directory:
   `http://localhost:17002/gemp-lotr/newclient/live.html?gameId=2&participantId=asdf`

7. ~~**Answering decisions.**~~ **Done.** The prompt renders controls by shape
   and answers through the transport; proven end to end against a live game.
8. ~~**Floating windows.**~~ **Done.** `view/flyout.js` is the component;
   `view/panels.js` instantiates the site path and chat.

9. ~~**Pile viewer and card zoom.**~~ **Done.** `view/piles.js`.

10. ~~**Card art.**~~ **Done.** `model/images.js` derives the image URL from the
    blueprint id; verified rendering real cards on a live board.

11. ~~**Detachable boards.**~~ **Done.** `view/detach.js`; ⧉ on any seat chip.

12. ~~**Animation between zones.**~~ **Done.** `view/animate.js` — FLIP.

13. ~~**Card-art override tables.**~~ **Done.** `model/card-images.json`.

14. ~~**Seat navigation.**~~ **Done.** Edge arrows, ← / →, seat chips; keys are
    ignored while typing in a field and during a skirmish, when the arrows also
    disable. Covered by `dev/navcheck.html`.

15. ~~**The hall.**~~ **Done, but see the open item below.** `net/hall.js` +
    `hall.html`: lists the tables you can open a board for, so a game is reached
    by clicking rather than by hand-editing a gameId into a URL. Read-only on
    purpose — creating and joining need deck selection, which is the
    deckbuilder's job. `harness/` seats tables meanwhile.

16. ~~**Stat badges on the card's own stat column.**~~ **Done**, and the
    geometry is *measured*; see `DESIGN.md` under "The card", and the traps
    below for the CSS bug it exposed.

**Where it stands.** The client plays a real five-player game end to end. Every
decision type is implemented and verified against the old client with the engine
judging the answers. **Twelve assertion suites, all passing** -- re-run at the
end of this session:

    wirecheck 12   actioncheck 19   assigncheck 6    pickcheck 17
    pathcheck 9    navcheck 9       flipcheck 4      zoomcheck 6
    chatcheck 18   cardstatecheck 18  pregamecheck 14   statcheck (report)

Two things to know before you read a red result. `statcheck` prints MEASUREMENTS
(badge geometry as percentages), not assertions -- it has no RESULT line and
never "passes". And `wirecheck` stalls at "RUNNING" for ever when served over
HTTP, because `sync.sh` deliberately does not deploy `dev/live_capture.xml`; run
it from disk, or copy the capture across for the run. Neither is a failure, and
a naive grep for `FAIL` reports every suite broken -- it matches the page's own
source text.

**Verification status, precisely.** Live differential over random-deck games
across **all seven formats**, one per round:

| run | games | decisions | result |
|---|---|---|---|
| all formats, after the format work | 21 | 943 | 0 mismatches, 0 rejections |
| four formats that had failed | 19 | ~1300 | 0 mismatches, 0 rejections |
| earlier, Fellowship Block only | ~30 | ~2000 | 0 mismatches, 0 rejections |

**Both negative controls fire**, which is what makes those zeros mean anything:

- `sabotage=actionids` -> client divergence, e.g.
  `OFFERS DIFFER CARD_ACTION_CHOICE: old ["0"] vs new ["276"]`
- `sabotage=badcard` -> engine rejection, with the decision parameters printed

**The `CARD_SELECTION` rejections are SOLVED.** They were the harness answering
a decision whose `max` was 0 -- zero cards wanted, cards still listed. Both
clients were refused identically because both were handed the same choice.
Fixed; see "The CARD_SELECTION rejections: SOLVED" for the two wrong turns taken
on the way, both worth not repeating.

**The controls do not cover every decision type.** Both inject into
`CARD_ACTION_CHOICE` offers and `CARD_SELECTION` answers. Nothing tests whether
a divergence in `ASSIGN_MINIONS` encoding, `ARBITRARY_CARDS` or the grouped
assignment format would be *detected* at all. For those types "0 mismatches" is
untested detection, not proven detection. One control per decision type is the
obvious next move.

**Next**, in the order they are worth doing:

0. ~~**The fuzzer's BUTTON driver, the last two answer call sites, and a
   per-type runner.**~~ **All done.** 47/47 across seven types, three controls
   firing in scope, `harness/fuzzrun.sh`. See "The decision-space differential".

0d. **Make rejection counts trustworthy.** Decision ids are not unique -- 22 call
   sites pass `1` -- so "a warning arrived AND the same decision id was asked
   again" counts unrelated warnings as rejections. Match on the decision's
   identity. And **add a negative control per decision type**: today only
   `CARD_ACTION_CHOICE` offers and `CARD_SELECTION` answers have one, so a clean
   `ASSIGN_MINIONS` run means no divergence was found, not that one would be.

1. **Play a hand yourself as a seated player.** Everything is verified by
   machine; nothing has been driven by a human through the real UI. Sit at
   `live.html?...&participantId=asdf` and play a card, use a card with two
   abilities, and reach an assignment.
2. ~~**`git init` here.**~~ **Done.** See "Version control, finally".
3. **Auto-pass** — the reference auto-passes a CARD_ACTION_CHOICE with no
   eligible cards. In a five-player game you pass constantly.
4. **Parallel differential runs.** Games are independent, but all five seats use
   the same five accounts and one session per account means concurrent games
   fight over channels. Needs a second pool of registered accounts; worth ~4x on
   top of the 15x already gained.
5. **Stacked cards** — grouped with attachments in code, never seen in real data.
6. **`GameEvent._side`** — still blocks side-filtering an opponent's support
   area, and needs a server change in `vendor/gemp-lotr`, the OTHER project's
   tree. Coordinate rather than edit.

### The image override table

`model/card-images.json` (84 KB): **1,308 image overrides and 64 errata across
9 sets**, extracted from the reference client's `set40.js`, `hobbit.js` and
`PC_Cards.js`.

Extracted by **evaluating** those files in a browser and serialising the
globals, not by parsing them — `set40.js` builds its URLs by string
concatenation, so the literal text in the file is not the value.

`loadImageTable()` is optional and non-fatal. Until it resolves, and if it never
does, `imageUrl()` falls back to the derivation, which is correct for every card
without an override. Errata resolve to `/gemp-lotr/images/erratas/…` on the GEMP
origin rather than the CDN.

**One dead host, inherited.** The 1,308 entries split:

| entries | host | resolves |
|---|---|---|
| 988 | `i.lotrtcgpc.net` | yes |
| 320 | `lotrtcg2e.club` | **no — DNS failure** |

Every set-40 image points at a domain that no longer resolves, so those cards
render as the tinted placeholder. This is inherited from the reference client,
where the same images are equally broken — not something we introduced. If set
40 matters, those 320 URLs need rehosting; the fix is a data edit, not a code
change, which is the point of keeping the table as data.

### Animation, and the trap in it

The board is a pure function of state and re-renders wholesale, so a card is a
different DOM node every paint and has no identity to animate. FLIP handles
exactly that: measure before the repaint, let it happen, apply the inverse
transform, animate it away. Animation stays a decoration over the renderer
rather than a constraint on it — no keyed reconciliation, no node reuse.

**`getBoundingClientRect()` on an animating element returns its ANIMATED
position, not its settled layout position.** Two updates arriving close together
is enough: the second measures cards mid-flight, every delta picks up the
previous animation's offset, and a board that has not moved animates as though
it had. `captureRects` finishes any in-flight animation before measuring — a new
state supersedes the transition to the old one anyway.

A test caught it (`dev/flipcheck.html`, "a change that moves nothing animates
nothing"), which is worth noting because it is invisible by eye: it looks like a
slightly lively board, not a bug.

### The suites

| page | what it covers |
|---|---|
| `src/tests.html` | 197 unit assertions — zones, protocol, reducer, store, ranks, transport, clocks, images |
| `src/dev/wirecheck.html` | decoder and reducer over XML captured from a real game (12) |
| `src/dev/flipcheck.html` | the animation path, by counting animations rather than looking (4) |
| `src/dev/navcheck.html` | seat navigation: keys, arrows, wrap, typing, skirmish suspension (9) |
| `src/dev/pathcheck.html` | the site path window grows one step per site, and yields to a hand resize (9) |
| `src/dev/assigncheck.html` | an assigned minion lines up under its companion, without overlapping (6) |
| `src/dev/zoomcheck.html` | the hover preview shows a picture, for cards **and** sites (6) |
| `src/dev/chatcheck.html` | the chat box sends, survives a repaint, and separates people from the room (18) |
| `src/dev/pickcheck.html` | choosing among cards the client was never sent — the ARBITRARY_CARDS contract (17) |
| `src/dev/actioncheck.html` | CARD_ACTION_CHOICE answers an action index; ASSIGN_MINIONS answers groups (19) |
| `src/dev/pregamecheck.html` | pre-game panel, and meta-site modifiers drawn as the site they modify (14) |
| `src/dev/cardstatecheck.html` | inactive cards, errata marks, and drag-to-reorder within a row (13) |
| `src/dev/statcheck.html` | stat badge alignment — reports **measured** geometry, not a verdict |
| `src/dev/decisionfuzz.html` | the decision SHAPE space, against the old client — offers *and* answers. Not an assertion suite: it reports DIFF/KNOWN per shape |

All twelve assertion suites run the same way (see "Running the tests"); expect
`ALL PASS`. Grep for `RESULT: ALL PASS \([0-9]+\)` rather than `RESULT:` — the
loose pattern also matches the template literal in each page's own source and
will happily report a pass that is really a line of JavaScript.

`statcheck.html` is different in kind: it renders one real card under a
percentage ruler and prints what the browser actually computed for each badge.
It has to be served **from the GEMP origin**, because `board.css` reaches for
`/gemp-lotr/images/o_icon_*.png`. It exists because reasoning about the
stylesheet got the alignment wrong twice and measuring found the cause at once.

### Detaching a board

⧉ on a seat opens that seat's board in its own window: free characters, minions,
support, vitals and clock, repainting from the same store. Press it again to
close.

**Only one window ever talks to the server.** A detached board subscribes to the
store and opens no connection — `LotroGameMediator` throws
`SubscriptionConflictException` if a second client claims a player's channel,
which arrives as HTTP 409. That constraint is why the store exists at all, and
why detaching is nearly free.

Three things it has to get right, all learned in the spike:

- **Styles do not follow a node across documents.** The child gets a clone of
  every `<link rel="stylesheet">` *and* `<style>`, plus the theme attribute.
  Miss it and the board arrives unstyled.
- A sandboxed popup can be returned with an **opaque origin**, so the window
  exists but `win.document` throws. Every failure path reports rather than
  leaving a dead button.
- `beforeunload` closes the children, or a reload orphans them.

### Card art

`blueprintId` → `LOTR<set:2><card:3>.jpg` on the community CDN:

```
"1_320"  ->  https://i.lotrtcgpc.net/decipher/LOTR01320.jpg
```

Masterworks are the exception: above a per-set threshold the numbering restarts
with an `O0` prefix, so set 12 card 195 is `LOTR12O01`, not `LOTR12195`.

**Deliberately not implemented:** errata substitutions and the per-set override
tables (`set40.js`, `hobbit.js`, `PC_Cards.js` — about 2,100 lines between them
in the reference client). Those are *data*, and belong in a JSON file loaded at
runtime rather than transcribed into a module. `OVERRIDES` in `model/images.js`
is the hook. A card with no entry falls through to the derivation, and one whose
image fails to load removes the `<img>` and shows the tinted placeholder — so a
missing override is a blank card, never a broken board. The same fallback covers
being offline, which matters because the art is on an external host.

### The modules

```
net/protocol    284   the wire format, and nothing else
net/transport   242   the one connection: long poll, answers, 409/410
net/hall        133   tables and queues -- a different format on the same shape
net/chat        122   the table chat room: its own endpoint, its own format
state/reduce    414   (state, event) -> state, plus selectors
state/store      86   subscribe / emit / snapshot
model/zones     259   the registry: zones, bands, completeness
model/images    104   blueprint id -> art URL, overrides, errata/meta-site ranges
model/actions    61   CARD_ACTION_CHOICE unpacked: action ids, virtual actions
model/assign     49   ASSIGN_MINIONS unpacked: freeCharacters/minions, grouping
layout/rank      59   when a row needs a second rank
layout/bands    120   how bands are presented, as data
view/board      370   the board, a pure function of state
view/card       116   one card at three densities
view/flyout     236   a floating window; chat and the path are instances
view/panels     134   the site path and chat
view/piles      147   the pile viewer and card zoom
view/picker     181   cards a decision describes, that state never holds
view/actionmenu  58   choosing between several abilities on one card; shared
view/effects     82   the three transient events: flash, affected-by, on-screen
view/alerts     105   sound + tab title when a decision waits and the tab is hidden
view/cardinfo    68   right-click a card for what is modifying it
view/pregame    115   summary, notes and the table, before the first card
view/detach     157   a seat's board in its own window, off the same store
view/animate     83   FLIP between zones
dev/fixture     140   a five-player game as event XML
dev/probe       121   what the NEW client offers, for the differential
```

Nothing imports downward across those layers: `view/*` never touches the
network, `state/*` never touches the DOM, and only `net/protocol` has heard of
`PCIP` or `charStats`.

### What the live server has corrected so far

Five things, none findable by reading the serialiser:

1. `charStats="356=4|2|RF10"` — a companion's resistance carries its **signet**
   as a letter (`A`/`F`/`G`/`T`). Reading it as a number gave `NaN`.
2. **`PRE_GAME_SETUP` carries `allParticipantIds`** and arrives before any
   `PARTICIPANTS` event, so the seat strip was empty for the whole pre-game.
3. The clocks block includes a **`decisionClock`** entry that is not a player;
   left in, it appears as a sixth seat.
4. **`PLAYER_POSITION.index` is a SITE NUMBER, not a seat index.** The reference
   client feeds it to `advPathGroup.setPositions()` and matches it against each
   site's `siteNumber`. I had used it to sort the seat strip, which would have
   re-ordered the table by how far along the path people were — and a unit test
   encoded that assumption and passed. Seat order comes from `PARTICIPANTS`.
5. A client only ever receives **its own** decisions (`decisionRequired` appends
   only `if (playerId.equals(_self))`), so "who is the server waiting on" is not
   derivable from events. It comes from the **running clock**: whichever seat's
   clock went down since the last poll.

### Keeping a game alive for someone to watch

The working loop is: seat a table, run bots, hand over a URL, fix what they see.

```bash
cd /c/Users/emers/OneDrive/Documents/gemp_gui/harness
bash ./seat_table.sh 5 asdf qwer Librarian carol dave    # -> table + gameId
python play_bots.py --players asdf,qwer,Librarian,carol,dave \
       --game <id> --play --delay 1.5 --seconds 900
bash ./sync.sh
```

`--play` makes the bots actually play cards instead of passing everything, and
`--delay` paces them so a human can follow. Without both, a watcher sees a game
that technically advances and visibly does nothing.

**Games do not outlive their bots by much.** A finished game is dropped from the
hall, and its `gameId` then 404s — a link handed over ten minutes ago may
already be dead. Check before blaming the client:

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  "http://localhost:17002/gemp-lotr-server/game/<id>?participantId=watcher" --cookie "$C"
```

**Kill orphaned bot runs before starting a new one.** A `play_bots.py` left over
from a previous session — especially one started with a long `--seconds` — is
still logged in as the same five accounts, and two runners on one account steal
each other's channels. The symptom is a game that advances in fits or not at
all, which reads as a broken client. This cost real time once: a bot from a
crashed context was still running `--game 11 --seconds 3600` against a game that
no longer existed.

```powershell
Get-CimInstance Win32_Process -Filter "Name='python.exe'" |
  Where-Object { $_.CommandLine -like '*play_bots*' } |
  Select-Object ProcessId, CreationDate, CommandLine | Format-List
```

**Do not pipe a background bot run through `tail`.** It buffers until exit, so
the output file stays empty for the whole run and there is no way to see
progress. Let it write straight to the file.

To tell "the game is frozen" from "the client is not updating", poll the channel
the way a client does — a GET for `cn`, then POSTs:

```bash
CN=$(curl -s ".../game/<id>?participantId=watcher" --cookie "$C" | grep -o 'cn="[0-9]*"' | grep -o '[0-9]*')
curl -s -m 40 -X POST ".../game/<id>" --cookie "$C" -d "participantId=watcher&channelNumber=$CN"
```

Events coming back means the server and transport are fine.

### Verifying against the live server

```bash
cd /c/Users/emers/OneDrive/Documents/gemp_gui/harness
bash ./seat_table.sh 5 asdf qwer Librarian carol dave
python play_bots.py --players asdf,qwer,Librarian,carol,dave --game 2 --seconds 60
bash ./sync.sh
```

Then open the URL above in a browser. For a **headless** check, add
`&login=asdf&password=asdf&handshake=1`:

- `login`/`password` post the normal login form so the run gets a cookie.
- `handshake=1` stops the transport once the opening state arrives.

Both are dev-only. `handshake=1` exists because **a long poll never resolves by
design**, so `--dump-dom` waits forever and `--virtual-time-budget` cannot help:
virtual time is paused while a request is pending, so timers never fire either.
That cost twenty minutes; do not try to out-wait it.

A consequence worth knowing: `handshake=1` gives you the **opening state only**,
so it cannot show whether a board keeps updating. To test that, either load the
page twice a few seconds apart and compare the DOM, or poll the channel with
curl (see "Keeping a game alive").

**`&for=N` cannot rescue a headless run against a LIVE game**, and this is the
same trap wearing a different hat. The page stops itself after N seconds — but
that is a `setTimeout`, and Chrome's virtual clock is frozen while any request
is pending, so with a long poll parked on the server the timer never fires and
`--dump-dom` hangs until killed. It appears to work against a **finished** game
only because the transport 404s, lets go, and lets the clock run. Use
`handshake=1`, which joins, renders and stops without parking anything —
including chat, which joins once and does not poll under handshake for exactly
this reason.

### Traps that came out of auditing the old client

- **Spreading a flyout loses its getters.** `createFlyout` exposes `isOpen` as a
  getter; `{...panel, extra}` copies its VALUE at spread time, which is `false`
  for ever. Three wrappers did it (picker, piles, pregame) and every caller
  asking "is this window open?" got the answer it had before it opened. Re-expose
  getters explicitly in any wrapper.
- **Decision ids repeat, so never key state on them.** See the decision table.
  It bit twice: the board's per-decision reset, and the picker refusing to show a
  second decision because it "already had" id 1.
- **`metaSites` is nested inside `preGame`,** not a top-level attribute.
  `event.metaSites` is `undefined` and leaves every modifier drawn as itself.
- **`setPointerCapture` throws for a pointer id the browser does not know**, and
  an unguarded call takes the whole `pointerdown` handler down with it -- the
  move and up listeners are never attached, so dragging silently does nothing.
  Guarded; dragging works without capture, just less smoothly.
- **The replay endpoint does not URL-decode.** `ReplayRequestHandler` checks
  `replayId.contains("$")` on the raw URI, so `encodeURIComponent` turns the
  separator into `%24` and the result is a 404 indistinguishable from a missing
  recording. The chat handler DOES decode; they are not consistent.

### Attachments were computed and never drawn

`assignBands` has always returned three lists -- `byBand`, `unclaimed` and
**`onParent`** -- and `paint` used only the first. So every card with
`zone="ATTACHED"` was silently dropped: the One Ring on the Ring-bearer, every
possession, every mount. `renderCard` had a "fan" of spines meant to hint at
them, driven by `card.attachments`, a field **the reducer never set** -- so it
drew nothing for the whole life of the client. The fan is gone; attachments are
grouped with their host and drawn as the cards they are, which says WHICH card
is attached rather than merely how many.

One CSS trap in doing it: the overlap cannot be a percentage. A percentage
margin on a flex item resolves against its container's inline size, and the
group is auto-width -- sized by the very children the margin pulls together --
so the group landed half off the band. Cards are `height: 100%` with an aspect
ratio, so the ROW's height is the one definite dimension: `.row` is a size
container and the overlap is `-48cqh`, which is ~67% of a card's width at 5:7
and stays right at any band height.

### The differential harness: the old client as oracle

`dev/diff.html` feeds ONE recorded game to both clients, event by event, stops
at every decision, and compares what each offers. `harness/diffrun.sh` samples
random recordings and aggregates. The old client has had thousands of real games
played through it, so a disagreement is a bug HERE until shown otherwise.

```bash
cd harness
bash diffrun.sh 14 40            # 14 random games, 40 decisions each
bash diffrun.sh 4 30 actionids   # NEGATIVE CONTROL -- must report mismatches
```

**Always run the control.** "Agree" means nothing until the comparison has been
seen to disagree; `sabotage=actionids` re-introduces the real bug this client
shipped with (answering CARD_ACTION_CHOICE with card ids) and a run with it must
fail. It found 8-13 mismatches per game when checked.

How the can opener works (`dev/oldharness.html`), because none of it is obvious:

- Every answer the old client sends leaves through ONE door,
  `decisionFunction` -> `communication.gameDecisionMade` (gameUi.js:1272).
  Stub that and its answers become observable.
- Events enter through `processGameEvent(ge, animate)`. **Always animate=false**:
  the animated path defers onto jQuery's `#main` queue, and in a headless run
  with no frames painted that queue never drains.
- **`cleanupDecision()` must run before each event**, exactly as
  `playNextReplayEvent` queues it. Without it the per-card `data("action")`
  arrays are never cleared and every decision inherits the previous one's
  options -- which showed up as two different cards both offering "actionId 0".
- A decision only survives while it is the LAST event fed, so the driver stops
  ON decisions. Feeding a round number of events lands between them, with
  nothing on offer.
- Card ids live on `data("card").cardId`, which is what the client's own
  `:cardId()` selector reads (gameUi.js:138) -- not on a `cardId` data key.
- `chatBox` is a Proxy returning a no-op for every property. The UI calls a
  scattering of methods on it during layout, and a hand-written stub list has to
  be kept in step with 3000 lines we are deliberately not reading in full.

**Two real bugs found on the first runs**, neither reachable from the engine's
own contract -- both were "the answer is right but the player cannot get to it":

1. **Actions on ATTACHED cards were unreachable.** Attachments are drawn on
   their host, and were rendered without the eligibility flag or click handler,
   so "Transfer Hobbit Sword" could not be taken. Card rendering and wiring is
   now one `drawCard` helper used for hosts and riders alike.
2. **Actions on SITES were unreachable.** Sites are drawn in the path window
   rather than a band, and nothing there lit them or answered. Sanctuary
   abilities and "Use Rivendell Terrace" were simply unavailable. The action
   menu now lives in `view/actionmenu.js` and is shared by both surfaces.

### Closing the loop: the engine as judge

`diff.html` replays a finished game and compares what each client OFFERS. It
cannot say whether an answer would be ACCEPTED -- a recording's later events
already assume the answer that was really given, so nothing can be sent.

`dev/livediff.html` + `harness/livediffrun.sh` fix that. A fresh table is
seated, bots take four seats, and the differential client holds the fifth and
answers for real. The engine then judges, and it has a precise way of saying
"wrong": an invalid answer is **not** an HTTP error, it comes back as a
SEND_WARNING plus **the same decision asked again**
(LotroGameMediator.java:434-437), which is directly observable.

```bash
cd harness
bash livediffrun.sh 4 70            # 4 random live games, 70 decisions each
bash livediffrun.sh 21 60           # 3 passes over all 7 formats
FORMATS=king_block bash livediffrun.sh 5 60    # pin one format to chase a bug
```

Each round plays a **different format** with freshly generated decks, so a run
covers every block rather than replaying Fellowship over and over. See "Random
decks across every block and format".

**Report the COUNTERS and a few deduped SAMPLES, not one or the other.** An
earlier version kept only the first three problem lines per game, so a run with
128 problems showed 24 lines and "zero rejections" could not be proved. Keeping
only the counters then made the opposite mistake: a run reporting "13 engine
rejections" with no detail could not be diagnosed at all, and a rejection is
**not reproducible from the seed** -- the bots and the engine's shuffles differ
every game, so there is nothing to go back to.

`mode=alternate` (default) sends the old client's answer on even decisions and
the new client's on odd ones, so the engine validates BOTH. `mode=new` makes the
new client answer everything -- the strongest single test, because any illegal
offer it invents is refused by the engine within one decision.

**Standing results.** Across live games covering all six decision types
(INTEGER, MULTIPLE_CHOICE, ARBITRARY_CARDS, CARD_ACTION_CHOICE, CARD_SELECTION,
ASSIGN_MINIONS) and all seven formats: **zero answer mismatches and zero engine
rejections**, in `alternate` and in `mode=new`. The exact counts are in
"Verification status, precisely" near the top.

### Two negative controls, and what they do NOT cover

A differential that cannot fail is worthless, so both halves have a control that
injects a known-wrong answer. Run one after ANY change to the comparison:

```bash
# client divergence -- the real card-ids-for-action-index bug this client shipped
livediff.html?...&sabotage=actionids
# engine rejection -- a card id that was never offered
livediff.html?...&sabotage=badcard
```

Both fire reliably. That pairing -- zero normally, non-zero with the control --
is the only form of "the clients agree" worth writing down.

**A control that cannot fire is worse than none**, because its silence reads as
success. One was built here (`sabotage=undercount`, sending fewer cards than
`min`) that could never fire: measuring the bounds showed `max=1` on every
`CARD_SELECTION` the game asks, so one card is always the whole answer. Its two
clean runs were briefly mistaken for confirmation of a fix that did nothing.
**Before trusting a control, prove it fires.**

**Coverage gap.** Both controls inject into `CARD_ACTION_CHOICE` offers and
`CARD_SELECTION` answers only. `ASSIGN_MINIONS`, `ARBITRARY_CARDS`,
`MULTIPLE_CHOICE` and `INTEGER` have no control, so a clean run says the harness
found no divergence there -- not that it would have.

### Debugging a failure you cannot reproduce

**A live rejection cannot be re-run.** The engine shuffles both decks
server-side and the bots play their own hands, so the same seed gives a
different game. Five re-runs of the format that had just failed came back
clean, which proves nothing either way and cost twenty minutes.

Reproducing the GAME is the wrong goal. A **decision** is self-contained --
its type, its full parameter map, and the board the events built -- so that is
what gets captured:

1. **The failure carries its own evidence.** On a rejection `livediff.html`
   prints the engine's warning text, the decision id and type, and the complete
   parameter map, next to the answer that was refused. That is the entire input
   to the answer, so the bug is diagnosable from the log alone. This is how the
   `CARD_SELECTION` bug below was found: it named `min`, and the answer had one
   card in it.
2. **The game is on disk.** The engine records every game -- 1430 recordings
   and counting. A problem game prints
   `dev/diff.html?replayId=asdf$<id>&login=asdf&password=asdf`, which feeds the
   exact event stream back into BOTH clients deterministically, for as long as
   the recording exists. It cannot test whether an answer would be accepted --
   the recording's later events already assume the answer really given -- but it
   reproduces the board and the decision perfectly, which is what you need to
   see WHY an answer was wrong.
3. **Identical failure on both clients means the harness is at fault.** A real
   divergence refuses one client and accepts the other. When both are refused
   with the same answer, stop looking at the clients.

Seeding the bots would not fix this. The engine's shuffle is server-side and
not exposed, so the deal differs regardless.

### The CARD_SELECTION rejections: SOLVED -- `max=0` means zero cards

Every unexplained engine rejection in this project came from one line. The
engine asks:

    decision 1 CARD_SELECTION params {"min":["0"],"max":["0"],"cardId":["250"]}

**`max=0` -- zero cards -- while STILL LISTING card 250 as offered.** A discard
whose count resolved to nothing. The harness read `cardId`, ignored `max`,
picked a card, and the only legal answer was the empty string. Both clients were
refused because both were handed the same choice by the harness, which is why it
never implicated either GUI.

**AMENDED.** That was right about the harness and wrong about the conclusion
drawn from it. Because the harness handed both clients the same answer, the
question "what would each GUI do if it reached this decision ITSELF?" was never
asked. `decisionfuzz.html` asks it, and the answer is that **the reference
client picks a card and gets refused**: at `min=max=0` it sends a card id where
`""` is the only legal answer. This client sends `""`. So the bug does implicate
a GUI -- the old one -- and it is presumably still rejecting real players'
answers whenever a "discard one for each X" resolves to zero. Marked
`oracleWrong` in the catalogue, the only case carrying that verdict. Worth
raising in `vendor/gemp-lotr`, which is the other project's tree -- coordinate
rather than edit.

Fixed in `livediff.html`: `if (!ids.length || max === 0) return pass;` before
anything else.

**Confirmed against the engine's own validator**, not just the observed
rejection -- `CardsSelectionDecision.getSelectedCardsByResponse`
(`gemp-lotr-logic/.../logic/decisions/CardsSelectionDecision.java:40-49`):

```java
if (response.equals("")) {
    if (_minimum == 0) return Collections.emptySet();   // empty is legal
    else throw new DecisionResultInvalidException();
}
String[] cardIds = response.split(",");
if (cardIds.length < _minimum || cardIds.length > _maximum)
    throw new DecisionResultInvalidException();         // 1 > 0 -> invalid
```

At `min=0, max=0` the empty string is the ONLY legal answer. Four more facts
fall out of the same method, all now first-hand rather than inferred from the
reference client:

- the wire format really is comma-separated (`response.split(",")`);
- `min` AND `max` are both enforced, so neither is a hint;
- **duplicate ids are rejected** (`if (result.contains(card)) throw`) -- the
  harness picks with `splice`, so it cannot repeat one, but a naive random
  picker would fail here;
- an unoffered id throws in `getSelectedCardById`, which is exactly what makes
  `sabotage=badcard` a valid control.

**Where `max=0` comes from.** `DiscardFromHand` resolves its count through
`ValueResolver.resolveEvaluator(effectObject.get("count"), ...)`
(`cards/build/field/effect/appender/DiscardFromHand.java:33`) and hands it to
`CardResolver.resolveCardsInHand(... "Choose cards from hand to discard" ...)`.
The count is an EVALUATOR, not a constant, so a card that discards "one for
each X" produces `min=max=0` whenever X is zero -- and the decision is still
asked, with the whole hand listed as selectable.

That makes it **data-dependent**: it needs a particular card played in a
particular board state, which is why it clustered in `rotk_sta`, `ttt_block`,
`ts_reflections` and `king_block` and never appeared in Fellowship Block.

Reconcile is NOT a source of it, though it looks like one:
`PlayerReconcilesAction:101` builds `CardsSelectionDecision` with
`min = max = cardsInHand.size() - handSize`, which would be 0 -- but the call is
guarded by `if (cardsInHand.size() > handSize)`, so it never fires at zero.
Checked, because it was the first plausible-looking candidate.

**Why it took all session.** The gate passes on `min===0` only 40% of the time,
so the same decision is answered CORRECTLY six times in ten. Combined with the
data-dependence above, that produced 13-36 rejections in one game and none in
the next twenty -- a signature that reads like a race, and sent the
investigation toward concurrency and stale decisions.

**Two wrong turns worth not repeating:**

1. **Fixed from the symptom.** The first fix assumed too FEW cards for `min` and
   sent `min` cards comma-joined. The truth was the opposite end of the same
   parameter -- too MANY, where the limit is zero. The fix was inert and its
   negative control could not fire, and two clean runs were briefly read as
   confirmation.
2. **Generalised from four samples.** Measuring `min`/`max` across ONE game gave
   `max=1` every time, and that became "max is always 1". It is not; `max=0`
   exists and is the whole bug. Four decisions is not a sample.

The lesson that actually generalises: **when both clients fail identically, the
harness is wrong -- and print the decision's full parameters before theorising.**
The parameter map named the bug the first time it was captured, after three
theories had already failed on it.

**Still worth fixing: decision ids are not unique.** 22 call sites pass `1`, and
a live rejection prints `decision 1`. Rejection detection is "a warning arrived
AND a decision with the same id was asked again", so an unrelated warning beside
any decision counts as a rejection. That did not cause this bug, but it means
rejection COUNTS are not trustworthy. Match on the decision's identity instead.

### Warnings are not in the recordings

Worth knowing before trusting `diff.html` to re-examine a rejection: a
per-player warning is not written to the shared recording. Scanning 140 games
found `type="W"` in exactly ONE, while five known-bad games had 13-36 rejections
each. The one that was recorded said `Something went wrong` and came from a
genuine server-side race, `ConcurrentModificationException` in
`GameState.terminateOldListener` (GameState.java:287) reached via
`signupUserForGame` -- six occurrences in the whole log, unrelated to the
CARD_SELECTION failures. It is a real SERVER defect -- a write performed under a
read lock -- and is written up for fixing in the engine project at
`gemp_multiplayer/docs/GEMP_SERVER_RACES.md`, with the stack, the two suggested
fixes, and a second bug in the same three lines (`_channelNextIndex++` is not
atomic, so two concurrent sign-ups can be handed the same channel number).

Note the engine answers `Something went wrong` for BOTH an internal exception
and a rejected decision answer, so that string alone never tells you which you
are looking at.

Recordings also use SHORT type codes (`W`, `D`, `M`, `GS`), mapped in
`net/protocol.js`. Grepping one for `SEND_WARNING` finds nothing and looks like
a clean game. They are **zlib**, not gzip, despite the `.xml.gz` name -- `zcat`
fails on them; fetch through `/gemp-lotr-server/replay/<player>$<id>` instead
and let the server decompress.

So a replay reproduces the board and the decision perfectly, but cannot show you
the rejection. Only the live diagnostics can.

**The offer discrepancy was the COMPARISON, not either client.**

The old client appeared to offer nothing on some CARD_ACTION_CHOICE decisions.
It was not failing. The sequence:

1. A batch carries the decision; the old client renders it and lights the cards.
2. A LATER batch arrives carrying no decision -- card movement, clocks, log
   lines.
3. `feedBatch` runs `cleanupDecision()` before each event, exactly as the real
   client does, which correctly clears the finished decision. There is no
   decision in this batch to re-render.
4. The NEW client keeps `state.decision` until another replaces it.

So the harness compared a decision the new client still held against an old
client that had correctly cleared it. Both clients were right. The fix is to
**compare only on a batch that actually carried a decision**
(`events.some(e => e.decision)`).

That accounts for every property that misled three earlier theories: the
intermittency (does a follow-up batch arrive between decisions?), the cards
being present and findable, the answers never differing, and the engine never
rejecting anything.

The decisive evidence was a self-contradiction in the probe --
`attachedAfterRerender: 2` together with `reRendered: false`, which cannot come
from the same call. **When two counters disagree about the same moment, the
frame of reference is wrong, not the subject.**

Two real harness defects were found on the way and are worth keeping:

- **Decisions must be fed with `animate=true`.** `processDecision` ends with
  `if (!animate) that.game.layoutUI(false)` (gameAnimations.js:1233), which
  re-lays out the board and discards the `data("action")` and `actionableCard`
  class the handler attached a moment earlier. Everything else stays
  `animate=false`, which is what keeps the animation queue from stalling.
- **Never drain the queue AFTER re-rendering a decision.** Draining runs stale
  queued work -- a `layoutUI` from an earlier event -- on top of the decision
  just rendered. Flush before, not after.

**Always run the negative control after changing the comparison.** Gating
comparisons on "did this batch carry a decision" could just as easily have
silenced the check entirely, and a silenced check reports zero for ever.
`livediff.html?...&sabotage=actionids` re-introduces the real card-ids-for-
action-index bug and still fires. See "Two negative controls, and what they do
NOT cover" for both controls, the gap in what they test, and the one that was
built here unable to fire at all.

`diagnose()` keeps the probes that found this: `attachedDuringCall` (measured
inside the handler), `attachedAfterRerender`, `offersAtEndOfFeed`, `reRendered`.
Reach for them before theorising -- three of the four theories tried here were
wrong, and each cost a full round of games.

### Running the differential fast, and keeping the server clean

Two things dominate a batch's wall clock, and neither is the game:

**The bots' delay.** They pace themselves so a human can watch. Headless nobody
is watching, and four seats pausing before every answer is almost the entire
cost: **168s per game at `--delay 0.4`, 11s at 0** -- a 15x difference, measured
back to back. `livediffrun.sh` now defaults to `BOT_DELAY=0`; set it only when a
person is actually watching the board move.

**The hall grows without bound.** Every run abandons a five-player game whose
table sits at PLAYING for ever. After a day of testing that was 198 tables and
64 KB per fetch, and `seat_table.sh` fetches the hall about ten times per game.

```bash
bash cleanhall.sh              # concede every live game, leave WAITING tables
docker restart gemp_app_2      # ~36s; what ACTUALLY empties the listing
```

Conceding genuinely ends the games (195 tables went PLAYING -> FINISHED in one
pass) but GEMP keeps finished tables listed, so the listing does not shrink.
The hall and live games are in memory; **decks are in gemp_db_2 and replays are
on disk, so both survive a restart** -- verified after one: decks intact, 1326
replays. Concede first anyway; it ends games properly instead of killing them
mid-flight, and it is the only option if the server must stay up.

Result: 198 tables/64 KB -> 0 tables/5.6 KB, and ~12s per game end to end.

### Soaking for hours: `soak.sh`

```bash
cd harness
bash soak.sh 2000 60              # 2000 DECKS = 400 games (five seats each)
OFFSET=200 bash soak.sh 1800 60   # resume without repeating decks
LOG=/tmp/mine.log bash soak.sh 500
```

A game costs ~21s, not the 60-90s first guessed -- most end well before the
decision cap -- so 2000 decks is about 2.5 hours, not eight.

`livediffrun.sh` alone is not safe to leave running that long. Four things go
wrong, and **every one of them corrupts the RESULTS rather than stopping the
run**, which is the dangerous kind:

| | what happens | what it looks like |
|---|---|---|
| the hall | a table per game; `seat_table.sh` fetches the hall ~10x per game | later games mysteriously slower |
| chrome dirs | one `--user-data-dir` per round; 255 were already in Temp | disk fills |
| seeds | a batch restarting at round 1 rebuilds the SAME decks | a long soak is one short run repeated |
| **the server** | see below | hundreds of "could not seat a table" |

**The server going away is the one that actually bit.** Docker Desktop died
mid-soak -- the whole daemon, taking both stacks with it. A game against a dead
server fails to seat in MILLISECONDS instead of 21s, so three batches, 75 games,
were consumed in 40 SECONDS and logged as "no result". The entire 400-game
budget would have gone in about four minutes, and the log would have read like a
harness fault rather than an outage.

So `soak.sh` probes the server before every batch, restarts Docker Desktop and
the containers if the daemon is gone, waits up to 20 minutes, and **stops rather
than logging fake results** if it cannot recover. It also treats any batch that
finishes implausibly fast with zero clean games as an outage rather than
counting it -- a fast failure and a fast success are indistinguishable in the
tally, so the elapsed time has to be checked separately.

Recovery survives the restart: decks are in `gemp_db_2`, replays are on disk.
The hall is **in memory**, so game ids reset to 1 -- do not read that as data
loss.

**`pgrep` does not exist in this shell.** `pkill -f soak.sh` followed by
`pgrep ... | wc -l` reports `0` because the check itself failed, not because
anything died. A soak was reported stopped on that basis and kept running for
another twenty minutes. Kill through the process table instead:

```powershell
Get-CimInstance Win32_Process -Filter "Name='bash.exe'" |
  Where-Object { $_.CommandLine -match 'soak.sh|livediffrun.sh' } |
  Stop-Process -Force
```

### Random decks across every block and format

`randomdeck.py` gives every seat a fresh deck per game, built from the **card
definitions** in `gemp-lotr-cards/.../cards/official`, for any of seven formats.

An earlier version sampled from the seeded `starter` deck's own 28 distinct
cards and kept its ring-bearer, ring and nine sites FIXED. Every "random" deck
was the same cards in different proportions on the same adventure path. It
varied which actions became playable and when, which is worth something, but it
never saw a different site, a different ring-bearer, an errata card or anything
else in the block. Reading the real definitions gives 274f/290s and 43 sites for
King Block alone.

**Every legality rule is checked at TABLE CREATION, never on save.** The deck
saves with a 200 and the failure surfaces one call later as "could not seat a
table" -- which reads like an infrastructure problem, and cost real time twice.
The rules, and what each one answers when broken:

| rule | server's answer |
|---|---|
| equal Shadow and Free Peoples cards | deck size / side imbalance |
| exactly nine sites, one per number 1-9 | adventure path incomplete |
| ring-bearer must be a **Companion** with `canStartWithRing` | `Card assigned as Ring-bearer cannot bear the ring` |
| no format-banned card | `Card is X-listed: <name>` |
| at most 4 copies of anything, 1 of a restricted card | copy limit |

Two traps worth naming. `canStartWithRing: true` appears on some **allies** too
(Farmer Maggot, Filibert Bolger) and the server still refuses them, so the type
check is not redundant. And the field must be matched at the card's OWN
indentation -- two tabs -- because matching it anywhere in the card's text also
catches nested effects that merely mention the ring.

Formats differ in exactly two ways, both read from `lotrFormats.hjson` rather
than hardcoded: which **sets** are legal, and which site **block** the adventure
path comes from (`sites: FELLOWSHIP` maps to `block: Fellowship` on a site card).
`banned` and `restricted` lists come from the same entry.

The seven formats the hall accepts a generated deck for on every attempt:

    fotr_block  pc_fotr_block  ttt_block  towers_standard  ts_reflections
    king_block  rotk_sta

`--formats` marks about 30 as buildable, but most are refused, in two different
ways worth telling apart. Most answer **"This format is not supported: X"** --
the hall simply does not offer them, and nothing about the deck is at fault. The
`movie*` family is different: it is offered, and it *usually* works, but on some
seeds it still answers `Card assigned as Ring-bearer cannot bear the ring`. Its
larger set pool yields 16 ring-bearer candidates against three for a block
format, and something beyond `type: Companion` + `canStartWithRing` disqualifies
some of them. Left out of the rotation rather than diagnosed.

`livediffrun.sh` rotates through them one per round. `FORMATS=king_block bash
livediffrun.sh 5 60` pins one to chase a failure -- necessary because a
rejection is **not reproducible from the seed**: the bots and the engine's
shuffles differ every game.

SHADOWS and MULTIPATH formats are skipped, not broken: their sites carry no
`site:` number at all, so the nine-numbered-site path this builds does not apply.
Modelling their path is a separate job. `python randomdeck.py --formats` lists
what is buildable.

- **`DECK` is a deck's CONTENTS; `DECK_NAME` is which saved deck to play.**
  They are different things in `gemp_api.sh`, and conflating them sends a
  900-character card list as a deck name and silently creates no table.

### Blueprint id ranges carry meaning

Three behaviours key off the SET number in a blueprint id, and all three are
plain range checks lifted from the reference client's `Card`:

| sets | meaning | what the client does |
|---|---|---|
| 50-89 | errata | a dashed stripe down the card's edge |
| 90-93 | meta-site modifier | drawn as the site it modifies, own art on the bottom 27% |

`TURN_CHANGE.otherCardIds` is the third piece of card state that was tracked and
never drawn: cards the engine has marked **inactive for the turn**. They are
dimmed and their stat badges go grey, the way the reference greys them. A card
that cannot be used this turn no longer looks identical to one that can.

### Watching a finished game

`src/replay.html?replayId=<player>$<recordingId>` — recordings live at
`/replay/{player}${id}` and are just the same `<ge>` events wrapped in
`<gameReplay>` with an `<info>` header, so the decoder, reducer and board work
unchanged. A replay is the live client with the transport replaced by an array
and a cursor.

Seeking rebuilds from event 0 every time. That sounds wasteful and is not: the
reducer is pure and a whole game is ~1700 events, measured at **under 1ms** for
a full rebuild, and it makes stepping BACKWARD free. Inverting events instead
would mean an undo for each of the 27, and one subtly wrong would corrupt the
board with nothing to catch it.

Find recordings under `/etc/gemp-lotr/replay/<year>/<month>/<player>/` in the
container.

### Cards the client was never sent: the ARBITRARY_CARDS contract

An opponent's hand, a draw deck, a discard pile, a reveal — the client has none
of those cards and never will, so the engine **describes them in the decision**.
Read from `ArbitraryCardsSelectionDecision.java` and
`ChooseArbitraryCardsEffect.java`, because every part of this is easy to get
wrong by assumption:

- **`cardId` is already `temp0`, `temp1`, …** — not real card ids. The answer is
  those same strings, comma-joined, and the engine reads the index back out with
  `Integer.parseInt(cardId.substring(4))`. There is no lookup at either end.
  Treating them as ids (`Number("temp0")` → `NaN`) is what made this silently
  dead: the eligible set matched nothing, so it looked like a decision with no
  eligible cards rather than one drawn in the wrong place.
- **`blueprintId` is a parallel array**, and is the only way to draw the cards.
- **`selectable` is a parallel `"true"`/`"false"` array. Shown is not
  selectable.** "Look at an opponent's hand and discard a Shadow card" shows the
  whole hand and lets you pick part of it; the engine rejects a response naming
  an unselectable card. **It is never absent.** This file previously claimed that
  an absent `selectable` means everything shown is selectable, "the two-argument
  constructor passing the same collection twice". The constructor does pass the
  collection twice -- but it delegates, `this(id, text, physicalCards,
  physicalCards, min, max)`, to the six-argument form, and ALL THREE
  constructors call `setParam("selectable", ...)`. The parameter is always on
  the wire; the two-argument case arrives as an all-`"true"` array. The old
  client requires the literal string (`if (selectable[i] == "true")`) and so
  selects nothing when the array is missing -- correct behaviour for input it
  cannot receive. Checked in
  `ArbitraryCardsSelectionDecision.java:17-45` after `decisionfuzz.html`
  reported a difference on a shape that turned out to be unreachable.
- **`min`/`max` bound the count**, and outside them the engine throws
  `DecisionResultInvalidException`. An empty answer is legal **only** when min is
  0, so "Pass" must not be offered above it.

Two real shapes, both of which must work:

| | shown | selectable | min/max |
|---|---|---|---|
| look at an opponent's hand | the whole hand | **empty** | 0 / 0 |
| discard from it | the hand, or matching only | the filtered cards | from the card |

The first is informational and **still has to be answered with `""`**, or the
game waits for ever on a player who thinks they were only shown something.

A decision only arrives when there is a real choice: the engine auto-resolves
when `max` is 0 or when exactly `minimum` cards match, so the picker never sees
"one candidate, nothing to decide" — the same behaviour as a scoped selection
with a single candidate resolving without asking.

`view/picker.js` owns this, and owns its own buttons; the prompt strip
deliberately shows only a note for ARBITRARY_CARDS, because two sets of controls
for one decision is how a player sends the wrong answer.

### Every decision type, and what it actually answers with

Three of the seven answer with something other than "the card ids I picked", and
all three were wrong or missing until they were read from the engine. Shape
alone is not enough to drive a decision — the type decides the answer.

| type | shown by | answers with |
|---|---|---|
| `INTEGER` | a number box | the number |
| `MULTIPLE_CHOICE`, `ACTION_CHOICE` | buttons | the option's **index** |
| `CARD_SELECTION` | cards on the board | the chosen **card ids**, comma-joined |
| `CARD_ACTION_CHOICE` | cards on the board + prompt buttons | a single **action index** |
| `ARBITRARY_CARDS` | the picker window | the given **`temp<i>` ids**, comma-joined |
| `ASSIGN_MINIONS` | pairing on the board | **groups**: `fp minion minion,fp minion` |

**`CARD_ACTION_CHOICE` is the commonest decision in the game** — every "Play
Shadow action or Pass" — and it answers with an index into the engine's action
list (`Integer.parseInt(result)`), not with card ids. Sending a card id there is
not simply rejected: **a card id lower than the number of actions parses fine
and performs a different action.** Three consequences:

- `actionId`/`cardId`/`blueprintId`/`actionText` are parallel, and **several
  actions can share one card**. Clicking a card is not always choosing an
  action, so a card with two abilities opens a menu.
- `blueprintId` is the literal string `"inPlay"` when the source is on the
  board. Anything else is a **virtual card action** — the source is in a discard
  pile or draw deck (`ActivatePhaseActionsFromDiscardRule`,
  `ActivatePhaseActionsFromDrawDeckRule`) or is a trigger added by another card
  (`AddTrigger`). Those have no board node and are offered in the prompt strip,
  or they are unreachable.
- `""` passes.

**`ASSIGN_MINIONS` carries `freeCharacters` and `minions`, and NO `cardId`.**
A client that builds its eligible set from `cardId` finds nothing and silently
offers no way to assign, which is exactly what ours did. The answer groups a
companion with the minions assigned to it.

**Decision ids repeat.** They are hardcoded at the call sites — 22 places pass
`1`. Anything that resets per decision must key on the decision **object**, not
its id, or a half-made pairing leaks into the next question. The reducer builds a
new object per DECISION event, so identity is the right key.

### The transport contract, as read from the server

```
GET  /gemp-lotr-server/game/{id}?participantId=X
       -> full state; the channel number is the root element's `cn`
POST /gemp-lotr-server/game/{id}
       participantId, channelNumber [, decisionId, decisionValue]
       -> long-polls until there are events
```

Answering a decision is **not** a separate call — it rides on the same POST that
polls, so an answer has to abort the poll in flight rather than wait for the
server's timeout.

Status codes are not interchangeable:

| code | meaning | what the transport does |
|---|---|---|
| 409 | `SubscriptionConflictException` — another client took this seat | **stops.** Retrying would fight that client for the channel, which is how this project produced stuck bots |
| 410 | `SubscriptionExpiredException` | re-handshakes for a fresh channel |
| 403 | private information refused | stops |
| 404 | no such game | stops |
| other | network or server error | exponential backoff, and a pending answer is kept for the retry |

**404 is worth taking literally.** A game that has finished is gone, and its id
404s for everyone. When a bot run reports `answered=0  HTTP Error 404` on every
seat, the game ended — it is not an auth or a client problem.

### Seeing the board

```bash
cd /c/Users/emers/OneDrive/Documents/gemp_gui/src
(python -m http.server 8761 >/dev/null 2>&1 &)
# then open http://localhost:8761/board.html
```

The dev bar drives it: shadow phase, decision, skirmish, regroup, spectate.
`?feed=shadowPhase,skirmish` replays a sequence without clicking, which is how it
gets checked headlessly:

```bash
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless --disable-gpu \
  --user-data-dir="C:\\Users\\emers\\AppData\\Local\\Temp\\cr" --dump-dom \
  --virtual-time-budget=5000 "http://localhost:8761/board.html?feed=shadowPhase,skirmish"
```

`src/dev/fixture.js` is a five-player game **as event XML**, not as mock state,
so the decoder and reducer are exercised for real.

**Keep the fixture's ids real.** Its sites carried `blueprintId="site_1"`, which
no image derivation can resolve, so fixture sites drew no art — and a check that
sites preview a picture could not have caught the bug it was written for. They
are now real ids from a captured game (`1_320`, `1_327`, `1_340`, `1_346`). A
fixture with invented ids tests the shape and silently skips everything
downstream of them. When the transport lands it is
the only file that gets replaced.

### Running the tests

`src/tests.html` — 195 assertions. There is no node or deno on this machine, so
headless Chrome is the runner, which is the method the project's own harness
already uses.

**Run it from bash, not PowerShell.** This is HANDOFF trap #2 of the main
project, hit again here: PowerShell mangles a native process's output, and
`chrome --dump-dom` returns *nothing at all* through it. The same command from
bash works.

```bash
cd /c/Users/emers/OneDrive/Documents/gemp_gui/src
(python -m http.server 8751 >/dev/null 2>&1 &) ; sleep 2
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless --disable-gpu \
  --user-data-dir="C:\\Users\\emers\\AppData\\Local\\Temp\\cr_test" \
  --dump-dom --virtual-time-budget=6000 "http://localhost:8751/tests.html" \
  2>/dev/null | grep -o 'RESULT:[^<]*'
```

Expect `RESULT: ALL PASS`. It must be served over http — ES modules do not load
from `file://`.

---

## Traps, all paid for

1. **Wire a listener next to its element, not next to its function.** Registering
   `wireA.addEventListener(...)` hundreds of lines above `var wireA = ...` threw
   `TypeError` on a hoisted-but-unassigned var and killed the entire script — no
   rendering, no handlers. It read as "the whole GUI broke at once".
2. **Guards written for one case rot silently.** The zoom handler matched
   `.slot` and missed `.site` when sites arrived; the window-drag handler
   excluded `.flyout-close` and swallowed the transpose button added later. Both
   were correct when written. Keep the zone list a **single registry** that
   layout, filtering and zoom all read from — the current client's unclaimed
   `SHADOW_CHARACTERS` zone is this same failure, larger.

   **It happened a third time, to the same handler.** The preview reuses the
   `<img>` already on whatever is hovered, and matched `.c-art` only; the moment
   sites got art as `.s-art`, every site previewed a bare label. Written
   correctly, rotted on contact with a new element. `dev/zoomcheck.html` now
   covers both, and the preview falls back to deriving from
   `data-blueprint-id`, so a card whose own image was dropped can still show
   one.
3. **Do not measure an element mid-transition.** Opening a window and immediately
   reading its position captured the *docked* geometry because the size
   transition was still at its start value; the window then grew off-screen and
   could not be dragged back. Removing the size transition removed the whole bug
   class.
4. **Sandboxed frames block popups** whatever the browser's popup setting says.
   `window.open` can also hand back a window whose document is opaque-origin, so
   `win.document` throws *after* a null check passes. Surface every failure path
   or a dead button looks like a dead feature.
5. **Verify board semantics against the engine, not against what looks
   plausible.** Two rules corrections came from the user and were confirmed in
   source: minions are discarded only on the *stay* branch of regroup, so they
   survive a second move; and the adventure path is revealed a site at a time out
   of different players' decks. Both would have shipped as wrong behaviour.
6. **A blanket rule that sets `position` will silently unposition every
   overlay.** `board.css` carried
   `.card > :not(.c-art) { position: relative; z-index: 1; }` to lift overlays
   above the art. At specificity **0,2,0** it beat each overlay's own
   `position: absolute` (0,1,0) regardless of source order, so every badge was
   laid out in normal flow and its `top`/`left` became a *flow offset*. The
   damage compounds down the column: the first badge landed near-right by luck,
   the second inherited the first's 115px of height and fell 16% too low, the
   third went off the card and was clipped by `overflow: hidden`. It reads as
   "the numbers are slightly wrong", which sends you to tune numbers. The rule
   only ever needed the `z-index` half. **Suspect a `position` override before
   re-deriving coordinates**, and check with `getBoundingClientRect` rather than
   by eye — `statcheck.html` prints exactly that.
7. **Do not transcribe the reference client's positions and assume they are
   right.** They are the reference's own approximation, ~1–1.5% of card height
   off what the cards print. The card art is the authority. Same for fonts: the
   reference sets none for stat numerals and inherits Verdana.
8. **Measure card art by its white numerals, not by luminance.** The first
   attempt profiled mean luminance across the stat column and mis-detected on
   every card, because the vitality ball is *dark* red and reads as frame. The
   numerals are white, high-luminance and near-zero saturation, and they are
   also the thing a badge has to line up with. `scratchpad/measure2.py`.

---

## Open

- ~~**The hall lists zero tables while a game is plainly playable.**~~
  **RETRACTED — the hall was never broken.** The diagnostic was:

      curl .../hall?participantId=X | tr '<' '\n<' | grep -ci "^<table"

  and `tr` maps character to character: it **cannot** expand one character into
  two. `tr '<' '\n<'` therefore turns every `<` into a newline and drops it, so
  no line ever begins with `<` and the `^<table` anchor can never match. It
  reported 0 against a response containing four `<table>` elements, and "0 for a
  seated player too" made it look like a server state rather than a broken pipe.
  `hall.html` renders its rows correctly against the live server. Use
  `grep -o '<table '` to count them. Left visible rather than deleted, because
  the failure was in the measurement and that is worth remembering: **a
  diagnostic is code, and an unverified one is not evidence.**
- **The site path (adventure path) draws no card images.** Reported from a live
  board; not yet investigated. Note `imageUrl()` derives from the blueprint id
  and sites are landscape — the reference client rotates them
  (`CardGroup.layoutCard` special-cases `effectivelyHorizontal()`), and a real
  site image measured 496x357 against 357x497 for a minion. Suspect the
  derivation, the aspect, or `ADVENTURE_PATH` cards not carrying a blueprint id
  through the reducer — check which before changing anything.
- **Chat runs on its own connection, and that is not optional.** `GameEvent`
  declares `CHAT_MESSAGE("CM")` and **nothing in the server ever emits it** —
  the same dead-enum situation as `_side` below. A client reading only the game
  channel shows an empty chat for ever. `net/chat.js` talks to `/chat/Game{id}`
  instead: GET joins and returns history, POST with a message sends, POST
  without one long-polls. Messages come back as rendered markdown (`hi` arrives
  as `<p>hi</p><br/>`) and are flattened to text rather than trusted to
  innerHTML. Server lines are `from="System"`, which is the only thing telling a
  person talking from the room narrating itself.

  Both feeds land in **one** `state.log`, in arrival order, with a `kind` of
  `game` or `chat` and the `gameLog` / `chatLog` / `spokenTo` selectors over it.
  They were two lists drawn one after the other, which put every chat line after
  every game line however long ago it was said. Arrival order rather than
  timestamps because **the server does not date its game log at all** — only
  chat messages carry a `date` — so interleaving is exact for anything said
  while connected and approximate only for the backlog handed over at join.

  The game log also embeds card references as markup
  (`<div class='cardHint' value='1_340'>Rivendell Terrace</div>`).
  `renderMessage` parses those in a detached document and copies across only
  text and hints, so unknown markup can make a line look plain but never make it
  dangerous. Hints carry `data-blueprint-id`, which is what makes them hover to
  a preview through the zoom's existing fallback.
- **`GameEvent._side` is declared but never populated or serialised**
  (`GameEvent.java:44`; `grep -rn "\.side("` finds nothing). This is the only
  thing blocking side-filtering of an opponent's **support area** — characters
  filter fine, because `FREE_CHARACTERS` and `SHADOW_CHARACTERS` are separate
  zones. Small, self-contained, and plausibly upstream-shaped. Needs the other
  agent's tree, so coordinate.
- **Site owner may not reach the client either.** Worth checking together with
  the above rather than twice.
- **Pile access for spectators** should follow the `discardPublic` flag on the
  `PARTICIPANTS` event, not be hidden wholesale — in formats with public
  discards a spectator may browse them.

---

## The decision-space differential

`src/dev/decisionfuzz.html` + `src/dev/decisionshapes.js`. **No server, no bots,
no game.** ~15s per decision type, deterministic, from disk state only.

    bash harness/sync.sh
    # NOTE the CDN block -- see the traps below. Without it the run never ends.
    chrome --headless --disable-gpu \
      --host-resolver-rules="MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost" \
      --user-data-dir=SOMEWHERE --dump-dom --virtual-time-budget=25000 \
      "http://localhost:17002/gemp-lotr/newclient/dev/decisionfuzz.html?only=CARD_SELECTION"

`?only=<TYPE>` runs one type -- **use it**, the full 47-case run no longer fits
one browser pass. `?sabotage=dropoffer` and `?sabotage=phantom` are the negative
controls and must report DIFFs (they fired at 23 and 32 against a baseline of 2).

### Why it exists

The overnight soak was 400 games and 2000 decks across all seven formats and came
back completely clean -- while the `max=0` bug it was meant to confirm was **never
once exercised**. Measuring afterwards found 59 `CARD_SELECTION` decisions across
six formats and not one with `max=0`.

That is structural, not bad luck. The GAME state space is astronomical; the
DECISION SHAPE space is tiny -- eleven parameter names across eight engine
decision classes. Sampling games to reach shapes buys volume where coverage was
needed. **Stop sampling game space; enumerate decision space.**

### Where the shapes come from

Not invented. The `setParam` calls in `gemp-lotr-logic/.../logic/decisions/` give
the complete parameter alphabet -- `actionId actionText blueprintId cardId
defaultValue freeCharacters max min minions results selectable`, eleven names --
and the conditionals inside each validator give the boundary cases. `max=0` is
one of those conditionals; it was in the source the whole time.

**The second axis is PLACEMENT**, and it is what makes this a GUI test rather
than a protocol test. Actions on ATTACHED cards and actions on SITES were both
unreachable once, and both were IDENTICAL decision shapes on the wire -- what
differed was where the card was drawn. `WHERE` in `decisionshapes.js` gives one
card per placement class: in play, support, minion, attached-to-host, hand, site,
and one id present nowhere.

### What it found, in an afternoon

Four real client bugs, all fixed and verified end to end:

| bug | evidence |
|---|---|
| `defaultValue` ignored on INTEGER | the reference pre-fills from it (`gameUi.js:2079-2081`); we opened on `min`. Invisible to the live differential, whose INTEGER answers are self-compared |
| Sites never lit for `CARD_SELECTION` | `panels.js` gated eligibility on `isActionChoice(d)`. "action on a SITE" passed while "selects a SITE" did not |
| Empty `MULTIPLE_CHOICE` offered a dead button | it sent index `"0"` into an empty list -- a control whose only outcome is a rejection |
| **`min`/`max` unenforced when sending** | Confirm sent `chosen.join(",")` with no bounds check and Pass sent `""` unconditionally, so a `max=0` decision could be answered with a card id and a `min=1` decision with nothing. The engine throws on both (`CardsSelectionDecision:40-49`) |

The last one is the important one. It survived 400 games because an OFFER
comparison structurally cannot see it: both clients light the card, because the
engine still lists it. Only driving the answer exposes it.

### Offers are not enough: drive the handlers

The old client's whole answer surface is nine `decisionFunction` call sites across
seven handlers. Comparing what each client OFFERS catches "the player cannot reach
the answer"; it does not catch "the player reaches it and we encode it wrong", and
three of the seven types answer with something other than the card ids picked.

**Seven of nine call sites are now confirmed reached by observed sends.**
Remaining: `cardActionChoiceDecision` (2523) and the bare-pass path (2478). The
lead on the first is that it calls `attachSelectionFunctions(cardIds, false)`
where `cardSelectionDecision` passes `true`.

Two rules the drivers had to learn, both of which manufactured false findings
first:

- **Point both clients at the same card ID.** Taking "the first eligible card" on
  each side picks a DIFFERENT card, because the two lay the board out
  differently. That alone produced a column of "ANSWERS DIFFER".
- **A design difference must never excuse an ENCODING difference.** The
  assignment cases were marked `expect: "design"` for how they are drawn, and
  that marking was quietly covering a different string going on the wire.

### `expect` vs `equivalent`

`expect` means "these differ by intent" -- presentation only. `equivalent` means
"these send different strings and the ENGINE parses both to the same thing", and
it requires a source citation. The report refuses to mark an `ANSWERS DIFFER`
finding as known unless the case carries `equivalent`.

The one case carrying it: assignment answers, where the reference sends
`"120 110,101"` (a group per companion, assigned or not) and we send `"120 110"`.
`PlayerAssignMinionsDecision:36-53` splits on `,` then ` ` and gives a lone
companion id an empty minion set, so both build the same assignment. Not a bug --
but only checking said so.

### It presented as a hang; it was an exception

`MULTIPLE_CHOICE` and `ACTION_CHOICE` did not complete for a while, and this
file previously blamed `clickAny` and the touch debounce. **Both were innocent.**
The button branch still called `oldClicks`/`newClicks`, helpers replaced by the
DRIVE table and left behind; `ReferenceError` on the first case killed the loop.
Because the report only renders once the loop finishes, the page came out
carrying a passing preflight and nothing else -- which is what a hang looks like
too.

Two things came out of it, both kept:

- **The loop catches per case.** A case that throws is recorded as `ERR` and the
  run continues. Losing forty-six results to one bad case is how a suite becomes
  something people stop running.
- **A progress line prints BEFORE each case.** It is what finally separated
  "stopped" from "slow", and it survives in the dumped DOM.

The generalisable bit: **an uncaught error and a hang are indistinguishable from
outside a headless run.** Three rounds went into reverting the wrong suspects
because the symptom had already been named "hang" and the name was never
questioned.

Fixing it exposed a real oracle defect underneath. `multipleChoiceDecision` has
TWO renderings -- a `<select>` above two options, one `<button>` per option at
two or fewer (`gameUi.js:2121`). `offers()` found the select by the explicit id
it carries, but looked for the buttons via `#smallDialog` and `:visible`, and
neither holds headlessly: the buttons are appended straight into `smallDialog`,
whose node has no such id and is never visible. So a ten-way choice was read
correctly while **yes/no reported zero options** -- the commonest decision shape
in the game, measured through the broken path. It now asks `ui.smallDialog` for
its own buttons.

### Per-type status: 47/47

    CARD_SELECTION     13/13 (2 reference-wrong)   CARD_ACTION_CHOICE  11/11
    ARBITRARY_CARDS     7/7                        ASSIGN_MINIONS       5/5
    INTEGER             5/5                        MULTIPLE_CHOICE      5/5
    ACTION_CHOICE       1/1

Every type compares OFFERS and the exact string SENT, and all nine of the
reference's `decisionFunction` call sites are reached.

`bash harness/fuzzrun.sh` runs all of it plus every control and exits non-zero
on a failure. **Use it rather than driving the page by hand.**

### Three root causes accounted for most of the failures

Almost nothing that looked like a client difference was one. Three harness
faults, each of which faked a whole category of disagreement:

1. **`ui.hand` is null.** `initializeGameUI` builds the hand group as part of
   seating a real player and the can opener never gets that far, but several
   paths call `this.hand.layoutCards()` unguarded. For CARD_ACTION_CHOICE the
   throw lands AFTER `finishChoice` has torn the decision down and BEFORE
   `decisionFunction` reaches `gameDecisionMade` -- so the decision vanished and
   no answer was ever sent. It read as "the reference silently refuses every card
   action" for three rounds. Stubbed with the `chatBox` Proxy trick.
2. **This page's own click wrappers swallowed exceptions.** `catch { return
   false }` turns "the handler exploded" into "there was nothing to click".
   `window.onerror` never sees a synchronous throw from `.trigger("click")`, so
   the two opposite diagnoses were indistinguishable and the wrong one was acted
   on repeatedly. They now record what they caught.
3. **`PlaySound` throws on a missing `<audio>`.** It fires after the answer is
   sent, so it is harmless -- but it propagates back through `.trigger("click")`
   into the driver, which reported three correctly answered ASSIGN_MINIONS cases
   as errors. It was also the "2 benign errors" every case carried all session.

The pattern worth carrying: **when a whole category fails identically, suspect
the instrument.** Every one of these presented as the reference client being
broken, and none of them were.

### Three markers, and why they are not interchangeable

A difference can be acceptable for three genuinely different reasons, and
collapsing them is how a real bug becomes a design note:

| marker | claim | evidence needed |
|---|---|---|
| `expect` | differs by intent -- PRESENTATION only | the design decision |
| `equivalent` | different strings, engine parses both the same | a source citation |
| `unreachable` | the engine cannot emit this shape at all | the constructor |
| `oracleWrong` | **the reference is wrong and this client is right** | strongest available |

`expect` can never excuse an ANSWERS DIFFER -- only `equivalent` or
`unreachable` can, because the first two are claims about *presentation* and an
answer is not presentation.

**A near-miss worth remembering.** Two virtual-action cases were about to be
marked `expect` as a presentation difference. They were not one: the answers
already matched and the mechanism counts already agreed. Both findings were the
harness reporting the reference's fabricated `extra<id>` DIALOG card as a BOARD
card. Marking would have made two harness artifacts permanent and stamped them
"differs by intent". **Verify the answers match before reaching for a marker; if
they already match, the difference is probably in the measurement.**

### A control that cannot fire, again

After the 47/47 result both controls were re-run across all seven types.
**Neither fires on INTEGER, MULTIPLE_CHOICE or ACTION_CHOICE** -- they mutate
card-id lists, and those three compare option counts and the driven answer. Three
of seven types had been reporting a pass that proved nothing.

This is the same trap already documented above from an earlier session, and it
recurred for a specific reason: the controls were written when the page compared
OFFERS only, and teaching it to drive ANSWERS changed what needed covering
without changing them. **Re-running a control is not enough -- its SCOPE has to
be re-derived whenever the comparison changes.**

`sabotage=answer` perturbs the answer itself and reaches all seven.
`fuzzrun.sh` now records which control is expected to reach which type and fails
the run if one is silent where it should fire, so this cannot recur quietly.

### Five edits to the shared oracle, and why each was needed

`oldharness.html` is the oracle for `diff.html`, `livediff.html` AND
`decisionfuzz.html`. **Re-run `diffrun.sh`'s negative control before trusting a live
run against it again** -- five edits is enough to want the control seen firing. All five are evidenced fixes; the two that were briefly suspected of
causing the "hang" above were not responsible for it.

1. **`offers()` ignored the client's own click gate.** It read `data("action")`;
   `gameUi.js:832` acts on a click only when the card carries `selectableCard` or
   `actionableCard`. `clearSelection` nulls the data only on elements still
   carrying one of those classes, so a host card with an attachment kept a stale
   action array for ever and the oracle INVENTED offers. Live runs rarely hit it,
   because board events between decisions recreate the elements.
2. **`offers()` could not see the dropdown.** `multipleChoiceDecision` switches to
   a `<select>` above two options (`gameUi.js:2121`), so counting buttons reported
   "1 option" for a ten-way choice.
3. **Native modals were an uncut wire.** `assignMinionsDecision`'s Done calls
   `confirm(...)` when a minion is unassigned (`gameUi.js:2892`). Headless nobody
   answers and the page stops dead -- `--dump-dom` returns ZERO BYTES, which reads
   as a crash rather than as a question.
4. **`clickAny`**, an unfiltered click. jQuery calls an element visible only when
   it has layout, and cards inside `cardActionDialog` have none headlessly, so
   every `CARD_ACTION_CHOICE` and `ARBITRARY_CARDS` card was refused.
5. **`selectionDebounceMs = 0`.** `gameUi.js:834-840` ignores a repeat click on
   the same card id within a window, to stop a touch device double-firing. Cases
   run milliseconds apart reusing the same card, so it swallowed most clicks and
   the run read as "the old client sent nothing".

### Traps this page paid for

- **Block the card-art CDN or the run never ends.** Chrome's virtual clock is
  paused while any request is pending, so unresolved image loads freeze it -- the
  same trap as the long poll and `handshake=1`, in a new place.
- **But do NOT use that flag for `cardstatecheck.html`.** Blocked images change
  card geometry and that suite measures drag distances in pixels; it fails 2 of 18
  under the flag and passes without it. **The flag is per-suite.** Two failures
  were briefly blamed on a client change that had nothing to do with them.
- **"Reset choice" sits BEFORE "Done".** `processButtons` (`gameUi.js:2815`)
  appends them in that order, so clicking `alertButtons` by index CLEARS the
  selection instead of submitting it. Target `#Done` by id.
- **`boot()` is not idempotent.** It resets by assigning `#main`'s innerHTML to
  itself, which after the first boot re-parses the GENERATED UI. Booting per case
  gave 2 errors on the first and 22 on every one after, with the oracle offering
  nothing from case two onward -- every "DIFF" in that run was the harness
  breaking its own oracle. Boot once; `feedBatch` already runs `cleanupDecision`
  before every event.
- **`targetType` is not optional in a fixture.** `EventSerializer` writes it in
  the same branch as `targetCardId` (`:37-40`) and the reducer sets `attachedTo`
  only on `type === "attached"`. Written without it the attached card had no host
  and the suite reported "the new client never lights an attached card" for three
  cases. That was the fixture. Same lesson as the sites that carried
  `blueprintId="site_1"`.
- **PREFLIGHT, ALWAYS.** The page refuses to report anything unless the oracle
  demonstrably holds the board, because "old offers nothing, new offers something"
  reads exactly like the new client inventing options. The first run of this page
  produced ~40 confident findings that were all the harness breaking its oracle.

### The honest measure

`decisionfuzz` covers decision SHAPES. It does not cover:

- whether an answer would be **accepted** -- no engine in the loop. That stays
  `livediffrun.sh`'s job. Breadth here, verdict there; neither replaces the other.
- branches INSIDE the seven handlers.
- everything that is not a decision -- piles, zoom, card info, chat, the game log,
  replay controls, drag-to-reorder, concede, spectating, detached boards.

And the number that tracks convergence is not the diff count, it is the
**discovery rate**. 400 games found nothing; the shape catalogue found three bugs
in an hour; driving the handlers found a fourth immediately. Every new instrument
has found something within hours of existing. Convergence looks like a new
instrument coming back empty, and that has not happened yet.

---

## What this project has left in the other trees

Nothing. That is the intended state and worth re-checking if anything here starts
depending on those trees.

`gemp_multiplayer` was restored to how it was found. The two branches this
project created (`gui/board-client`, and a stale `worktree-gui-board` from a
first attempt) were deleted, the worktree removed, and a stray copy of the spike
left at `harness/web/multiwindow_spike.html` was cleaned up. That repo has moved
on under its own project since, so do not expect any particular commit there —
the claim being made is only that none of the commits are ours.

`vendor/gemp-lotr` was never touched at all. Every claim in `DESIGN.md` about
GEMP's behaviour was arrived at by **reading** a GEMP tree, never by editing
one — and the reference-client reading (`gameUi.js`, `CardGroup.js`,
`game.css`) is done against **our own** copy at `C:\Users\emers\gemp2`, which
needs no coordination at all. Prefer that copy.

Nothing here is pushed anywhere, because nothing here is in a repository.

---

## OPEN AND IMPORTANT: today's oracle edits broke the REPLAY differential

`diffrun.sh` reports **0 of 3 games agreeing**, every mismatch a
`CARD_ACTION_CHOICE`. It was clean before today.

Bisected with git rather than guessed at, after two theories had already been
wrong:

| tree | result |
|---|---|
| everything at the initial commit | **3 of 3 AGREE** |
| today's CLIENT + baseline ORACLE | **3 of 3 AGREE** |
| today's client + today's oracle | 0 of 3 |

So **the client changes are not implicated at all** -- the fault is in
`src/dev/oldharness.html`. Two candidates were tested individually and cleared:
the `actionableCard` class gate on `offers()` (since reverted, with the reasoning
kept in place) and the `ui.hand` Proxy stub. Neither fixed it.

Remaining suspects, all added today, in rough order of how much they could touch
a replay's offers:

1. the `<select>` reader branch in `offers()`
2. `PlaySound` stubbed to a no-op
3. `window.confirm`/`alert`/`prompt` stubs
4. `selectionDebounceMs = 0`
5. `clickAny` (additive; least likely)

Bisecting the rest is now cheap and should be done ONE AT A TIME with a
`diffrun.sh 3 25` between each:

    git checkout 4c44d79 -- src/dev/oldharness.html   # known-good oracle
    # re-apply one edit, bash harness/sync.sh, bash harness/diffrun.sh 3 25

**Until this is closed:** `decisionfuzz.html` (47/47) is unaffected and can be
trusted -- it was validated against this same oracle all day and its controls
fire. `diff.html` and `livediff.html` cannot be, because they consume the same
`offers()` and are exactly what is failing. Do not read a clean `livediffrun.sh`
as evidence while this is open.

The lesson, and it is the same one twice in one day: **a shared file with three
consumers cannot be edited to suit one of them.** Every edit here was made to
make `decisionfuzz.html` work, none was re-checked against the two older pages
until the end, and the one that broke them is still unidentified.

