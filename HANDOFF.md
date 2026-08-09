# Handoff — new board client

**Status:** the client runs. It renders a live five-player game from the real
server, answers decisions, detaches boards into their own windows, animates
between zones and draws real card art. It is differentially verified against the
reference client with the engine judging, across **all seven playable formats**,
with working negative controls on both halves. Read `DESIGN.md` next — it is the
design of record and carries the evidence for every claim about GEMP's
behaviour.

## Start here

### The selection-extraction regression is RESOLVED (a79b6a0)

**It was one leftover line.** The extraction (43aa51e) deleted the `selected`
Set but the decision strip's CARD_SELECTION branch still read
`const chosen = [...selected];` — so every render of that branch threw a
ReferenceError. The line was never *substituted*, which is why reading the
diff found only equivalent substitutions: the broken line was not in the diff.

Cause isolated by measurement in both directions on the pinned game
(`IDS='dave$oizsis9e43f2gbfx'`): extraction + one-line fix
(`const chosen = picked.cards;`) → AGREE on all 30 (x3); bad line deliberately
put back → the original 3 MISMATCH of 30, decisions #19–#21, all
`CARD_ACTION_CHOICE`; fix restored → AGREE again.

**Why the mismatches were displaced.** `renderPrompt` runs at the END of
`paint`, after the bands are lit — the decision that threw still looked
rendered, and the damage surfaced on LATER decisions as offer mismatches.
Chasing the decision type named in the mismatch led away from the cause.

**The lesson, now part of the extraction recipe: after extracting code out of
a file, grep that file for EVERY identifier the extraction deleted** before
running anything. One second of grep against a day of differential bisection.
Green suites cannot see an untested branch — this sat in exactly the
`renderPrompt` gap this file already documents.

`model/selection.js` and `dev/selectioncheck.html` are back in with the fix
(a79b6a0), verified: pinned game x3, diffrun 12/12, fuzzrun CLEAN with all
controls firing, tests.html 197, all sixteen reporting suites green.

### Two process failures this cost, both worth more than the bug

1. **A false claim was committed.** `43aa51e`'s message said "diffrun SEED=7
   2-of-2 agreeing". The run had already printed `1 disagreeing` on screen. The
   fourteen green suites were read, the differential's last line was not. **Run
   the differential FIRST for anything touching `view/` or `state/`**, and read
   its final line before writing anything down.

2. **`SEED=` does not pin a comparison.** It pins the shuffle, not the CORPUS --
   and this session's live games added recordings, so a "before" and an "after"
   run at the same seed sampled DIFFERENT games. Only `IDS=` pins a game. This
   file already said so about bisecting and it still caught somebody out.


**There is a git repository now.** `git log` is the real record; the commit
messages carry the reasoning, not just the change. That is new as of the last
session and it replaces the old advice about reconstructing state from file
mtimes.

**Ten harnesses, all green and all PROVEN CAPABLE OF FAILING.** The three
originals plus seven surface differentials added since:

    bash harness/fuzzrun.sh      48 decision shapes x 7 types + 3 controls  ~20 min
    bash harness/pilerun.sh      4 viewer configs x 5 piles + 3 controls
    bash harness/logrun.sh       game log + chat, 14 message shapes + 4 controls
    bash harness/inforun.sh      card info, 7 id kinds x live/replay + 3 controls
    bash harness/zoomrun.sh      zoom, 6 hover targets x 3 states + 4 controls
    bash harness/replayrun.sh    replay speed + play/pause + 3 controls
    bash harness/optsrun.sh      concede + cancel x player/spectator + 4 controls
    bash harness/reorderrun.sh   which zones drag + 3 controls
    bash harness/diffrun.sh 12 40    replay differential over recorded games
    bash harness/livediffrun.sh 4 70 live, with the ENGINE judging

Plus `harness/autopassmeasure.py`, which proves the auto-pass cookie changes
what the ENGINE asks rather than merely what it parses.

**The standalone suite: `bash harness/fastrun.sh`** — every assertion page
plus `shapecheck`, with no Docker and no reference client, in about a minute.
`shapecheck` (52) drives all 48 catalogue decision shapes through a real
paint and asserts the client's own two levels against each other (contract
vs reachable, via `dev/probe.js`), plus the engine's bounds on the strip and
that NO SHAPE THROWS — reintroducing the a79b6a0 ReferenceError turns it
43-red. Three controls: `nolight`, `phantomlight`, and `mute` (which proves
the runner reports a dead page instead of skipping it). It is the fast inner
loop; it does not replace the differential for `view/`/`state/` changes.

**EIGHTEEN assertion suites that print a RESULT line** (counted by running
them, not from memory): `shapecheck` (52) is new with the standalone
runner; `selectioncheck` (28) is back
with the fixed extraction and `promptcheck` (39) is new with the renderPrompt
extraction -- both cover code that used to be reachable only by painting a
board. `wirecheck` needs `live_capture.xml` copied into the deployed dev/
directory for the run (sync.sh excludes it deliberately; remove it after).
`statcheck` prints no RESULT on purpose -- it is a measuring ruler for badge
placement, not a suite; do not count its silence as a failure.

The original three, for reference:

    bash harness/fuzzrun.sh      48 cases x 7 decision types + 3 controls   ~6 min
    bash harness/diffrun.sh 12 40    replay differential over recorded games
    bash harness/livediffrun.sh 4 70 live, with the ENGINE judging answers

    SEED= or IDS= pin diffrun's sample -- REQUIRED when bisecting, see below
    SABOTAGE=badcard bash harness/livediffrun.sh 2 60    its negative control

Last full verification: fuzzrun 48/48 with all three controls firing in scope
(exit 0); diffrun 12 of 12 agreeing with `actionids` firing; livediff 4 clean
games over ~260 decisions across all six decision types with `badcard` firing.

**All seven decision types are done.** Every type compares what each client
OFFERS and the exact string each SENDS, and all nine of the reference's
`decisionFunction` call sites are reached. The decision space is finished work;
do not re-open it without a reason.

**Untested surfaces: PILES, the LOG, CARD INFO, ZOOM, REPLAY CONTROLS and
CONCEDE/CANCEL are done.** Detached boards have NO reference counterpart --
`window.open` appears nowhere in `gameUi.js` -- so there is nothing to compare;
drag-to-reorder DOES exist here and always has (`view/board.js`,
`makeDraggable`, a 6px threshold and a per-band `orders` map) -- an earlier
queue entry claiming otherwise was simply wrong.

Seven surface differentials now, all with proven controls. Twelve client gaps have
come out of them, none of which the client itself reported: it rendered
everything without error every time, and only the oracle said what was missing.

**`src/dev/oldharness.html` now has NINE consumers.** Anything added to it must
be opt-in and default off; there are three such flags now
(`setReadStaleDialogButtons`, `setRealChatBox`, `setReplayMode`) and every one
of them exists because the default is right for the other pages.

**The probe's dropdown read is gated on `calls.lastType` (0cbe40a).** The
reference's `<select id='multipleChoiceDecision'>` persists in the closed
dialog until the NEXT smallDialog decision replaces it, and a card decision
never does — so a >2-option MULTIPLE_CHOICE followed by a pass-only
CARD_ACTION_CHOICE made `offers()` hand back the dead dropdown as that
decision's offer. It surfaced as a livediff "1 PROBLEM" that looked exactly
like a client divergence; the measurement chain is in the commit. If a
livediff or diffrun mismatch ever shows an offer of PLAYER NAMES on a card
decision, it is this class of bug: suspect the probe before either client. `decisionfuzz.html`
is the template and `pilefuzz.html` is the worked second example: enumerate the
space from the source, drive both clients, prove the control fires.

    bash harness/pilerun.sh    4 viewer configs x 5 piles x 2 seats + 3 controls
    bash harness/logrun.sh     14 message shapes + ordering, 4 controls
    bash harness/inforun.sh    7 card-id kinds x live/replay, 3 controls
    bash harness/zoomrun.sh    6 hover targets x 3 states, 4 controls
    bash harness/replayrun.sh  replay speed + play/pause, 3 controls
    bash harness/optsrun.sh    concede + cancel x player/spectator, 4 controls

Two client bugs came out of the first enumeration, both in one afternoon and
neither visible without the oracle: **no draw-deck pile at all**, and **every
pile offered for every seat** including an opponent's adventure deck, which
could only ever say "face down to you". See "The pile differential".

### Five client bugs found and fixed by the shape catalogue

All in one afternoon, after an overnight run of 400 games and 2000 decks found
nothing:

| bug | why the old harness could not see it |
|---|---|
| `defaultValue` ignored on INTEGER | the live differential's INTEGER answers were self-compared -- a value against itself |
| sites never lit for `CARD_SELECTION` | offers-only comparison; a guard written for `isActionChoice` alone |
| empty `MULTIPLE_CHOICE` offered a dead button | sends index "0" into an empty list; a control whose only outcome is a rejection |
| **`min`/`max` unenforced when sending** | both clients LIGHT the card, because the engine still lists it. Only driving the answer exposes it. |
| the picker's Confirm/Pass bounds | same defect, same blind spot |

### Auto-pass: done, and it was not what it looked like

**Auto-pass is a server feature.** The engine skips the decision entirely
(`playableActions.isEmpty() && game.shouldAutoPass(...)`, three call sites) and a
client cannot participate by answering faster — there is nothing to answer. All
a client chooses is the phase set, and it says so in a **cookie** the server
re-reads on every game request.

Three things that reading the server settled, none of them guessable:

1. **This client has always auto-passed.** No cookie means `_autoPassDefault` =
   FELLOWSHIP, MANEUVER, ARCHERY, ASSIGNMENT, REGROUP. The gap was never the
   behaviour, it was the control over it, plus SHADOW and SKIRMISH.
2. **An empty `autoPassPhases` cookie breaks every game request** —
   `Phase.valueOf("")` throws. "Pass nothing" is `autoPass=false` with the
   phases cookie removed.
3. **The cookie must be written at `Path=/`.** At the page's own path it never
   reaches `/gemp-lotr-server`. **Measured** — see below.

`model/autopass.js` + `view/settings.js` + the "Auto-pass" button on the status
bar; `dev/autopasscheck.html` is the suite, **51 assertions** (47 without a
`gameId`, and it says SKIP rather than counting the measurement as passed).

The client-side arm — the reference's `gameUi.js:2477` — is implemented and
**off by default**, because in the reference it is dead code (`settingsAutoPass`
is `false` at `gameUi.js:84` and assigned nowhere). Default-on would answer
decisions the reference sits on, and the differential would correctly call that
a divergence.

### THREE reference bugs now, and the third is the auto-pass UI itself

The reference's seven auto-pass checkboxes are **inert**. `$.cookie` is called
with no `path`, so the browser defaults it to the setting page's directory
(`/gemp-lotr`), and the API at `/gemp-lotr-server` does not path-match it. Every
reference game runs on `_autoPassDefault` regardless of what the boxes say.

Measured against the live endpoint, not reasoned. The trick is that a cookie
value which is not a `Phase` makes the handler throw, so arrival is a status
code:

| cookie path | clean | poisoned |
|---|---|---|
| `/`                      | 200 | **500** — arrives |
| `/gemp-lotr`             | 200 | 200 — never arrives |
| the page's own directory | 200 | 200 — never arrives |

**The `/` row is the control.** Without it, two 200s are indistinguishable from
a probe that does nothing — which is this project's most-repeated failure, and
the reason the measurement was built this way rather than as two assertions.

#### And it demonstrably changes what the engine ASKS

The cookie table above proves the cookie *arrives and is parsed*. It does not
prove a valid set changes anything. `harness/autopassmeasure.py` does, by
counting the one decision auto-pass suppresses — a `CARD_ACTION_CHOICE`
offering **no cards at all**, which is exactly what `playableActions.isEmpty()`
produces at the three call sites.

Three fresh 3-player games, MINIMAL policy, 100s each:

| run | cookie | no-action `CARD_ACTION_CHOICE` by phase |
|---|---|---|
| A | `autoPass=false` | FELLOWSHIP 7, REGROUP 21, SHADOW 4 — **32** |
| C | *none* (`_autoPassDefault`) | SHADOW 4 — **4** |
| B | all seven phases | **none** |

**A is the control**; without it a zero in B would be indistinguishable from a
measurement that cannot see these decisions. C is the sharpest and is what every
client gets today: the default suppresses Fellowship and Regroup and leaves
Shadow asking, matching `GameRequestHandler.java:50-54` exactly. C against B is
the user-facing claim — tick Shadow and the Shadow prompts stop.

All three games ran to identical phase counts (`FELLOWSHIP` 22, `SHADOW` 44,
`REGROUP` 22) with identical `CARD_SELECTION` (26), `ARBITRARY_CARDS` (1) and
`INTEGER` (1) counts, because the MINIMAL policy plays nothing and the turn
structure is therefore fixed. The only counter that moved is the one under test.
`CARD_ACTION_CHOICE` totals were 43 / 22 / 9: the drop is the no-action ones
plus a couple that differ because the shuffles differ, so do **not** read those
totals as exact arithmetic — the by-phase no-action column is the measurement.

    bash harness/seat_table.sh 3 asdf qwer Librarian     # note the gameId
    cd harness && python autopassmeasure.py --game NNN --subject asdf \
        --others qwer,Librarian --seconds 100 --only C

Each run needs its **own fresh game** — running two halves against one game
compares different portions of it, which is the same trap as an unpinned
`diffrun` sample.

#### The two bugs mask each other, and fixing one alone makes it WORSE

Unticking all seven boxes in the reference writes `autoPassPhases=` — an empty
value (`gameUi.js:763-765` leaves it `""`). Measured at `path=/`:

    empty value : clean 200 -> 500

So the empty value survives the browser write *and* Netty's STRICT decoder, and
`Phase.valueOf("")` throws exactly as the source predicts. It is fatal, not
ignored: every game request 500s.

Today nobody hits it, because the *path* bug means the cookie never arrives. The
two defects cancel. **A fix that only adds `path: "/"` would take the reference
from "the checkboxes do nothing" to "unticking them all breaks the game."** The
upstream fix has to be both at once:

    $.cookie("autoPassPhases", v, {expires: 365, path: "/"})   // and
    // for the empty set: remove autoPassPhases, write autoPass=false instead

which is what `cookieWrites()` here does, and what `autopasscheck` asserts.

### Two REFERENCE bugs found, and written up for the engine project

The reference client sends an answer the engine refuses whenever a selection
has `max=0` -- one card where only `""` is legal. Both submit paths have it:
`cardSelectionDecision` and `arbitraryCardsDecision` each test `< min` and never
`max`, while the engine throws on either bound. **Both are measured, not
inferred.** Written up in `gemp_multiplayer/docs/GEMP_CARD_SELECTION_MAX0.md`,
left UNTRACKED in that repo for its owners to triage. Do not edit that tree.

Three catalogue cases carry the `oracleWrong` marker for this. It is the only
marker that says "the reference is wrong and this client is right", and it
requires a measurement.

### The lessons that cost the most, in one place

1. **Volume is not coverage.** 400 games found nothing; enumerating the decision
   SHAPE space found five bugs in an afternoon. The game state space is
   astronomical, the shape space is eleven parameter names.
2. **Instrument the instrument.** Nearly every "client difference" this session
   was the harness measuring wrong. When a whole CATEGORY fails identically,
   suspect the tool.
3. **Count, do not infer.** "The reference did nothing" has three distinct
   causes -- the click never arrived, it arrived and the selection declined, or
   the selection was made and never submitted. They are indistinguishable from
   outside. Two counters (`selectionFunction`, `decisionFunction`) settled in one
   run what six rounds of theorising could not.
4. **A random sample cannot bisect.** `diffrun.sh` drew different games each run,
   so "0 of 3" against "1 of 3" compared nothing. Use `SEED=`/`IDS=`.
5. **A shared file with three consumers cannot be edited to suit one.** Every
   edit to `oldharness.html` was made for `decisionfuzz.html`; one of them broke
   the replay differential and went unnoticed for hours. Gate them opt-in.
6. **A control that cannot fire, or cannot be INVOKED, reads as success.** Two
   controls silently covered only four of seven types; `livediffrun.sh` ignored
   `SABOTAGE=` entirely and printed CLEAN. Both looked exactly like passing.

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
  sync.sh          deploy src/ into the container's web dir, cache-busted.
                   REFUSES while a harness holds the lock  (SYNC_FORCE=1)
  harnesslock.sh   the shared lock that makes that refusal possible
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
  pilerun.sh       PILE differential: 4 viewer configs + 3 controls, no server
                   game needed                  (CONFIGS=, `baseline`)
  logrun.sh        LOG differential: game log + chat, 4 controls (`baseline`)
  inforun.sh       CARD INFO differential: live + replay, 3 controls (MODES=)
  zoomrun.sh       ZOOM differential: 6 targets x 3 states, 4 controls
  replayrun.sh     REPLAY CONTROLS differential: speed + play/pause, 3 controls
  optsrun.sh       GAME OPTIONS differential: concede + cancel, 4 controls (ROLES=)
  reorderrun.sh    REORDER differential: which zones drag, 3 controls
  autopassmeasure.py  proves the auto-pass cookie changes what the ENGINE asks,
                   by counting no-action CARD_ACTION_CHOICEs  (--only A|B|C)
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
judging the answers. **Sixteen assertion suites, all passing** -- re-run at the
end of this session, plus `tests.html` at 197:

    sessioncheck 21   replaycheck 27   attachcheck 19   <- new this session


    wirecheck 12   actioncheck 19   assigncheck 6    pickcheck 17
    pathcheck 9    navcheck 9       flipcheck 4      zoomcheck 6
    chatcheck 18   cardstatecheck 18  pregamecheck 14   statcheck (report)
    autopasscheck 51  <- 47 of them without a ?gameId=, and it SKIPs the rest
                         rather than passing them. Needs a live game to measure
                         the cookie path; see "Auto-pass" above for the URL.

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

**One control per decision type: DONE.** `SABOTAGE=perturb` spoils the new
client's answer for EVERY type in a single run and tallies perturbed-vs-caught
per type, so a type whose divergence would go unnoticed is NAMED rather than
assumed. Measured over one game:

    CONTROL perturbed: {INTEGER:1, MULTIPLE_CHOICE:5, ARBITRARY_CARDS:2,
                        CARD_ACTION_CHOICE:12, CARD_SELECTION:4, ASSIGN_MINIONS:3}
    CONTROL caught:    (identical)
    CONTROL COVERAGE: every perturbed type was caught
    CONTROL UNSEEN — this game asked none, so detection is still UNPROVEN
                     for: ACTION_CHOICE

So detection is proven in the LIVE differential for six of the seven types.

**`ACTION_CHOICE` is the seventh, and "run more games" is not a plan with a
bound.** It has exactly TWO call sites in the whole engine, both in
`TurnProcedure.java`, and both need more than one required trigger at the same
moment:

    :225  required "is about to" responses   raised only when
                                             requiredBeforeTriggers.size() > 1
    :367  "Required responses"               raised only when _actions.size() > 1
                                             AND !areAllActionsTheSame() --
                                             identical triggers auto-resolve
                                             without asking anybody

So it needs **two or more DISTINCT required triggers firing on one effect**.
That is rare by construction, not by sampling accident, and random decks cannot
be made to produce it on demand -- the engine's own `TimingAtTest` builds a
specific board (Gimli + Dwarven Heart + Elrond) to get one.

The resolution is therefore not "keep running games": `fuzzrun` covers this type
**deterministically** -- its `answer` control reaches all seven, every run --
while the live differential covers it **opportunistically**. What matters is
that a live run says which it did not prove, and `CONTROL UNSEEN` does exactly
that. Treat it as covered, not as an open gap.

**Searched for anyway, and it did not appear.** Six games across six formats
(fotr_block, pc_fotr_block, ttt_block, towers_standard, ts_reflections,
king_block), ~230 perturbed decisions: `CONTROL UNSEEN ... ACTION_CHOICE` on
every one. That is the expected result given the two call sites, and it is
recorded so nobody runs the same six games again to find out.

### What that hunt DID find, which was worth more

Two harness defects, neither of them about ACTION_CHOICE:

1. **The old client was sending the literal string `"undefined"`.** `choose()`
   picked from `oldOffer.options` and used `String(pick.actionId)`; an option
   without an `actionId` -- a button rather than a card action -- made that
   `"undefined"`, which the engine refused:

       ENGINE REJECTED the old client's answer "undefined" to
       CARD_ACTION_CHOICE — Choose action to play or Pass

   on a decision whose cards were `[]`. **That is the harness sending nonsense,
   not a client disagreeing**, and it has been inflating the rejection count --
   the harness's strongest signal -- for as long as it has existed. Options are
   now filtered to those carrying an actionId.

   It was pre-existing and only fired on the half of decisions the old client
   happened to answer. `sabotage=perturb` forces every answer to come from the
   old client, which is what finally made it fire every time and be noticed. A
   control built to prove detection ended up finding a bug in the measurer.

2. **A backtick inside `python -c "..."`.** The block is a double-quoted SHELL
   string, so a backtick is command substitution: a comment mentioning the
   control by name made the shell try to run `perturb` once per game and print
   an error into the middle of the results. One game came back `RESULT: NONE`
   because of it -- a run that produced no result, which this project treats as
   a failure and not a pass. Fixed, and the fix's own comment reintroduced the
   bug once before it was written without backticks.

Two things make the control honest. The spoiled answer is **never sent** --
`from` is forced to `old` -- so the engine always receives a legal answer and
the game keeps going; otherwise the control would produce engine rejections
instead of client divergences and stall a few decisions in. And the unseen list
is computed against the CANONICAL SEVEN, not against the types this game
happened to ask, so a run that saw six of seven cannot report full coverage.

Before this, the two older controls injected into `CARD_ACTION_CHOICE` offers
and `CARD_SELECTION` answers only, and "0 mismatches" on the other five meant
untested detection rather than proven detection.

**Next**, in the order they are worth doing:

0. ~~**The fuzzer's BUTTON driver, the last two answer call sites, and a
   per-type runner.**~~ **All done.** 47/47 across seven types, three controls
   firing in scope, `harness/fuzzrun.sh`. See "The decision-space differential".

0. **The untested surfaces.** Piles, zoom, card info, chat, the game log,
   replay controls, drag-to-reorder, concede, spectating, detached boards.
   Nothing compares any of it against the reference, and it is the larger half
   of "does the new client match the old". `decisionfuzz.html` is the template.

0b. ~~**Auto-pass.**~~ **Done**, and the premise in this queue was wrong: the
   reference does NOT auto-pass, the SERVER does, and this client had it all
   along via `_autoPassDefault`. See "Auto-pass: done" above.

0c. **A nightly.** All three harnesses are controlled now, so an unattended run
   accumulates evidence rather than unverified green. `fuzzrun.sh` is ~6 min and
   exits non-zero on failure.

0d. ~~**Make rejection counts trustworthy.**~~ **Done, both halves.**

   Rejections now match on the decision's **identity** -- id, type, text and the
   full parameter map -- not on its id. Ids are not unique (22 call sites pass
   `1`), so an unrelated decision arriving as id 1 looked exactly like the one
   just answered being re-asked, and paired with any warning in the same batch
   that counted as a rejection. Rejections are the harness's strongest signal:
   a run reporting them is a run someone spends a day on.

   **Measured, and the honest result is that no phantom was ever observed.**
   Three clean games across three formats (139 decisions) and a `badcard` run:
   `phantom rejections avoided: 0` every time. So this is a correctness
   hardening against a real hole, not a fix for observed noise -- and the
   counter stays in so a future occurrence is visible rather than silent.

   **The stricter rule does not blind the harness**, which was the risk worth
   checking: `SABOTAGE=badcard` still reports 26 old + 26 new rejections.

   And the phantom counter is **proven capable of firing**, because a counter
   that has never moved is indistinguishable from a dead one. `FPCLASH=1`
   spoils the fingerprint so nothing can match it; run with `badcard`, whose
   rejections are real, and the two categories swap:

   | run | rejections | phantoms |
   |---|---|---|
   | `SABOTAGE=badcard` | 26 old + 26 new | 0 |
   | `SABOTAGE=badcard FPCLASH=1` | 0 | **34** |

   `FPCLASH=1` is a diagnostic only. It makes a `badcard` run print CLEAN --
   correctly, since a phantom is not a problem -- so never leave it set.

1. ~~**Play a hand yourself as a seated player.**~~ **BACKLOGGED** -- see the
   Open section, which carries the list of what to click and why the machine
   cannot do it.
2. ~~**`git init` here.**~~ **Done.** See "Version control, finally".
3. ~~**Auto-pass**~~ — **Done.** See above; the entry's premise was wrong.
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

`autopasscheck` is the one suite that wants the DEPLOYED origin and a live game,
because half of it measures whether a cookie reaches `/gemp-lotr-server` at all.
Without `?gameId=` it still runs its 39 pure assertions and reports the rest as
SKIP:

```bash
bash harness/sync.sh
GID=$(cd harness && source ./gemp_api.sh && C=$(login watcher qwer) \
      && hall watcher "$C" | grep -o 'gameId="[0-9]*"' | head -1 | grep -o '[0-9]*')
"/c/Program Files/Google/Chrome/Application/chrome.exe" --headless --disable-gpu \
  --user-data-dir="$(mktemp -d)/cr" --dump-dom --virtual-time-budget=25000 \
  "http://localhost:17002/gemp-lotr/newclient/dev/autopasscheck.html?gameId=$GID&participantId=watcher&login=watcher&password=qwer" \
  2>/dev/null | sed -e 's/<[^>]*>/\n/g' | grep -E "^(FAIL|SKIP|RESULT)"
```

Expect `RESULT: ALL PASS (51)`. A `gameId` that is not actually playing makes
the clean probe return something other than 200, and the suite SKIPs rather than
inventing a verdict — read the SKIP line, it names which.

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
9. **`sync.sh` and a running harness are mutually exclusive.** `sync.sh` opens
   with `rm -rf "${DST:?}"/*`, so a `fuzzrun`/`diffrun` in flight resolves its
   ES module imports against a directory that is being emptied and refilled.
   The pages die, emit no `RESULT` line, and `fuzzrun.sh` reports
   `<-- CONTROL DID NOT FIRE; this type's pass proves nothing` — which is
   indistinguishable, at a glance, from the real gap that message was written
   for. **Tell them apart by the empty `RESULT` field before the arrow:** a
   control that genuinely did not fire still prints `RESULT: ALL PASS (n)`,
   whereas a killed page prints nothing at all. Cost a 15-minute run and a
   false regression report.

   **Now enforced rather than remembered.** `harness/harnesslock.sh` is a
   SHARED lock — any number of harnesses may hold it, since only `sync.sh` is
   dangerous — with one file per holder named by pid, so a dead holder reaps
   itself and no counter can drift. `fuzzrun`, `diffrun`, `livediffrun` and
   `soak` take it; `sync.sh` refuses while any holder is alive and names it.
   `SYNC_FORCE=1` overrides.

   Five cases were driven before this was believed: no lock syncs, a live
   holder refuses, a released holder syncs again, a **SIGKILLed** holder leaves
   a stale file that the next reader reaps rather than blocking for ever, and
   `SYNC_FORCE=1` overrides a live one while an unforced run still refuses.

   The integration test earned its keep immediately: `fuzzrun.sh` already does
   `cd "$(dirname "$0")"`, so sourcing the lock by `$(dirname "$0")` *after*
   that resolved to `harness/harness` and the script ran **unlocked** while
   reporting a clean baseline. Nothing about the run looked wrong. Only
   checking that the lock file actually existed caught it — `source
   ./harnesslock.sh` is what it needs, and that is why the line carries a
   comment saying so.
10. **Do not pipe a harness through `tail`.** It buffers until EOF, so there is
   no progress while it runs and the baseline section — the half that actually
   catches regressions — is discarded. Worse, `$?` afterwards is the pipe's
   status, not the script's, so a non-zero exit is silently swallowed.
   Redirect and stamp instead:
   `bash harness/fuzzrun.sh > out.txt 2>&1; echo "EXIT=$?" >> out.txt`.
11. **The same concept can arrive in two alphabets, and comparing them fails
   silently.** A phase is `REGROUP` in the auto-pass cookie the server parses
   and `Regroup` on the wire — `GAME_PHASE_CHANGE` carries
   `Phase.getHumanReadable()`, which `live_capture.xml` confirms. Compared raw,
   the client-side auto-pass arm could never fire *once*, in any phase, and
   from outside that is indistinguishable from "there was nothing to pass".
   Caught only by writing an assertion in the wire's spelling. The engine's own
   normaliser is `Phase.findPhase` (Phase.java:37-39) and `phaseName()` mirrors
   it. **When a value crosses a boundary, check the spelling on BOTH sides
   against real captured data, not against the enum you happen to be holding.**

---

## Where the production code stands

Read this before proposing a refactor; two of the obvious ones are already done
and one is a trap.

**The layering is sound and should not be changed.** Every import points one
way -- `view -> model` (15), `view -> state` (5), `state -> model` (1),
`layout -> model` (1) -- with no upward dependencies, and `net/` is entirely
self-contained. Measured, not assumed.

**Entry points are composition again.** `live.html` 409 -> 348 and
`replay.html` 193 -> 161. Both used to carry real behaviour in a file nothing
imports, which put it outside every suite:

  * `view/session.js` (169 lines) -- the eight reactions: when the picker
    opens, the auto-pass arm and its double-answer guard, who may concede, what
    counts as unread, when a decision alerts, when the path opens itself, what
    flyouts yield to. Covered by `sessioncheck` (21).
  * `state/replayer.js` (107 lines) -- position, pacing, and the state at any
    point. Timers are INJECTED, so `replaycheck` (27) drives playback with a
    fake clock instead of sleeping.

**`view/board.js` is DOWN TO ~366 code lines** (from 494) and its two planned
cuts are done, each with the suite the code never had:

  * `model/selection.js` (a79b6a0) -- what is picked but not yet sent, the
    small state machine with the take-back rule. `selectioncheck` (28). The
    first attempt shipped a regression that was ONE LEFTOVER LINE reading the
    deleted `selected` Set; the resolution note at the top of this file is the
    extraction recipe now.
  * `view/prompt.js` (9a1d13d) -- the decision strip, a pure function of
    (doc, state, picked, onAnswer). `promptcheck` (39) asserts the rules that
    were previously unreachable: min AND max on Confirm, Pass only at min 0,
    the engine's suggested default, the inverted-range case, virtual actions.
    Note `onAnswer` is deliberately the RAW callback, not board.js's answer()
    wrapper -- the module header says why.

What remains in board.js is the board: bands, focus and filter, drag order,
assignment alignment, and wiring cards to decisions. That is cohesive; there
is no third cut waiting. Further splitting would be trading one import for
one indirection.

**Comment density is NOT a useful signal here.** A measurement of comment lines
over total lines named `protocol.js`, `transport.js`, `hall.js` and `detach.js`
as under-documented. Reading them showed the opposite -- the metric penalises a
file that is mostly a flat code table or one carrying a single dense header. The
real gaps were narrow, were about design decisions rather than behaviour, and
are fixed. Do not re-run that measurement and act on it.

---

## Open

- **BACKLOGGED: animate the `.cardgroup`, not each card in it.** Optional, and
  probably not worth it -- recorded so the reasoning is not re-derived.

  `playFlip` animates every `.card[data-card-id]` independently
  (`view/animate.js:36,54`), so a host and the cards riding it each compute
  their own delta. In a shared `.cardgroup` those deltas agree, and `flipcheck`
  measures how well:

      host  {dx:-0.5, dy:-17.890625}
      rider {dx: 0,   dy:-17.890625}

  Identical vertically, half a pixel apart horizontally -- sub-pixel rounding of
  two nodes at different x-offsets. Below sight, and below `playFlip`'s own 2px
  churn guard, so it cannot grow into a visible separation.

  Animating the WRAPPER instead would make separation structurally impossible
  rather than arithmetically small. **Not done** because it is a real refactor
  of the animation path for a sub-pixel gain, and the failure it would prevent
  has never been observed. What guards against it meanwhile is `flipcheck`,
  which prints the measured gap on every run and asserts it under 1px -- so
  drift toward the limit surfaces rather than hiding behind a pass, and that is
  the signal that would justify taking this up.

  The failure mode that WOULD be visible -- one card animating while its rider
  snaps, because the 2px guard is applied per card -- is separately asserted and
  has never fired.


- ~~**A Shadow condition on a companion VANISHES under the Shadow filter.**~~
  **DECIDED AND FIXED** — a filtered band now keeps a card carrying somebody
  else's attachment; see `DESIGN.md`, "Filtering opponents by side". The
  original finding is kept below because the reasoning is the useful part.

  **A Shadow condition on a companion used to VANISH under the Shadow filter.**
  Found by `src/dev/attachcheck.html`, which is the first thing in this project
  ever to exercise a cross-side attachment.

  The four-way filter does not dim, it HIDES: a filtered band renders
  `N hidden` and returns without its cards (`view/board.js:283-286`). Riders are
  drawn inside their host's band and have no band of their own, so an
  opponent-owned Shadow condition attached to a Free Peoples companion is hidden
  along with the companion. Measured:

      filter -> the cross-side rider:
        {"auto":"not drawn","fp":"lit","shadow":"not drawn","all":"lit"}

  For that card it is backwards: a Shadow player filtering to **Shadow** to see
  the shadow-side picture loses sight of their OWN condition on an enemy
  companion.

  **The rule chosen:** the filter puts away the focused seat's OWN cards, and a
  foreign rider is not one of them. The host is kept with it -- a condition
  shown without the companion it afflicts is worse than hidden -- and the rest
  of the band is still put away, with a count.

  Deliberately narrow: a rider owned by the host's own player changes nothing,
  so The One Ring on its bearer filters exactly as before. And it needs NO side
  information, which is why it could be done at all -- `ATTACHED` carries
  `side: null` and a per-card side filter would have been blocked behind the
  same `GameEvent._side` gap. Owner inequality is the whole test.

  Measured after: `{"auto":"dimmed","fp":"lit","shadow":"dimmed","all":"lit"}`
  -- drawn under every setting, with `attachcheck` also asserting the band is
  still hiding everything else, so the rule cannot quietly become "never hide".


- **BACKLOGGED: nobody has ever played this client by hand.** Everything is
  verified by machine. Every differential drives both clients through their own
  functions; no human has moved a mouse through the real UI, and a whole class
  of fault lives where the machine does not look -- a control that is present
  and correct but unreachable, unreadable, or two pixels under something else.

      bash harness/sync.sh
      bash harness/seat_table.sh 3 asdf qwer Librarian     # note the gameId
      # then, in a browser:
      http://localhost:17002/gemp-lotr/newclient/live.html?gameId=NN&participantId=asdf&login=asdf&password=asdf
      # and drive the other two seats so the game moves:
      cd harness && python play_bots.py --players qwer,Librarian --game NN --seconds 600 --play

  What to exercise, chosen because each is either load-bearing or has never
  been touched by a person:

  - play a card; use a card with **two abilities** (the action menu, which only
    appears when one card offers more than one action);
  - reach an **assignment** and assign a minion by dragging;
  - open the **piles** for your own seat and for an opponent's -- the tab set
    should differ, and your own draw deck should be there;
  - **right click** a card for its modifiers, and **left click** a card name in
    the game log, which should open the same panel without querying;
  - the **Auto-pass** panel: tick Shadow, confirm the Shadow prompts stop;
  - **Cancel game** and **Concede** (concede last, it ends the game);
  - a **detached board**, which has no reference counterpart and so has no
    differential at all -- this is the only way it is ever checked;
  - in a replay: the **speed buttons**, the scrubber, and step-back.

  Not automatable in any honest way: the point is the parts a headless run
  cannot see. Needs a person at a browser, which is why it is parked rather
  than half-done.

- **BACKLOGGED: a nightly over the whole harness set.** Everything is controlled
  now and the lock makes concurrent runs safe, so an unattended run would
  accumulate evidence instead of one-off green. Not started; this is the recipe
  so picking it up is assembly rather than rediscovery.

  Deploy once, then run in this order — cheap and deterministic first, so a
  break is reported in two minutes rather than forty:

      bash harness/sync.sh          # ONCE, before anything. Never during.

      # ~seconds each, no server game needed
      tests.html + the twelve *check.html suites   (see "Running the tests")
      bash harness/pilerun.sh
      bash harness/logrun.sh
      bash harness/inforun.sh
      bash harness/zoomrun.sh
      bash harness/replayrun.sh
      bash harness/optsrun.sh

      # ~20 min, the long pole
      bash harness/fuzzrun.sh

      # need the server, bots and recordings
      bash harness/diffrun.sh 12 40
      bash harness/livediffrun.sh 4 70
      SABOTAGE=perturb bash harness/livediffrun.sh 1 60
      SABOTAGE=badcard bash harness/livediffrun.sh 1 60
      cd harness && python autopassmeasure.py --game NNN --only C ...

  Every one of those exits non-zero on failure **except** `autopasscheck` and
  `autopassmeasure`, which need arguments a nightly has to supply:

  - `autopasscheck` needs `?gameId=` and a login or it SKIPs its measured half
    and still says ALL PASS. Take a live gameId from the hall first.
  - `autopassmeasure` needs its own fresh game per run (`seat_table.sh 3 asdf
    qwer Librarian`), because two halves against one game compare different
    parts of it.
  - `wirecheck` stalls for ever over HTTP; run it from disk or copy
    `dev/live_capture.xml` across for the run.
  - `cardstatecheck` must NOT get the CDN block; every other page must.

  Three things the nightly should assert rather than eyeball, because each has
  already been mistaken for success in this project:

  1. `RESULT: ALL PASS \([0-9]+\)` — a bare grep for `FAIL` or `RESULT:`
     matches the pages' own source.
  2. Controls FIRED. `fuzzrun`, `pilerun`, `inforun` and `optsrun` check their
     own scope tables; `livediffrun` needs the `CONTROL` lines read — watch for
     `CONTROL HOLES` and for `CONTROL UNSEEN`, which names types this run did
     not prove.
  3. No run produced NO RESULT. A dead page is not a pass, and it is what
     `sync.sh` running concurrently used to cause.

  Housekeeping it should also do: `harness/cleanhall.sh`, or the hall grows and
  silently adds minutes per game to `seat_table.sh`.

- **BACKLOGGED: the reference executes `<script>` in a log message.**
  `REFERENCE_SCRIPT_EXECUTION.md` is the write-up, and it is written
  upstream-ready so filing it later is a copy rather than a rewrite.
  `src/dev/scriptprobe.html` re-runs every measurement in about ten seconds.

  The short version: `ChatBoxUI.appendMessage` interpolates the message into
  markup and inserts it with jQuery 1.6.2, which evaluates scripts on insertion.
  **The sink is unsafe — proven, four ways, three of them without this
  project's harness. That an attacker can reach it — NOT shown**, and the two
  player-controlled routes are closed (chat escaped by
  `MarkdownParser.java:33-43`; logins alphanumeric per
  `DbPlayerDAO.java:15,389-396`).

  Deferred deliberately. It moves if a reachable route turns up — audit the
  `SEND_MESSAGE` producers for unescaped free text, deck names and table
  descriptions first — or if someone decides defence-in-depth is worth
  reporting anyway. Not urgent while both routes stay shut.

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
- ~~**Pile access for spectators** should follow the `discardPublic` flag.~~
  **CLOSED, and it is now differentially verified.** A spectator gets Dead and
  Removed for every seat always, and every seat's Discard when `discardPublic`
  is set. See "The pile differential" below — the rule is `model/piles.js`,
  transcribed from `gameUi.js:1747-1789` and checked against the oracle in all
  four viewer configurations.

---

## The log differential

`src/dev/logfuzz.html` + `src/dev/logshapes.js`, driven by `harness/logrun.sh`.
No server, no bots, no game.

    bash harness/logrun.sh             baseline + 4 controls
    bash harness/logrun.sh baseline

### One transcript, and that is the reference's doing

Game messages, warnings and chat all land in ONE list in arrival order. That was
not a choice made here: `gameAnimations.message` and `.warning` both call
`chatBox.appendMessage` (gameAnimations.js:1175-1197), the same method the chat
poll calls, into the same `chatMessagesDiv`.

### Four fields per line, not the rendered text

The reference composes a chat line as
`<div class='msg-identifier'><b>from: </b></div><div class='msg-content'>text</div>`
(chat.js:418-428), so its `.text()` runs the speaker and the words together.
Comparing raw text would measure two styling choices and bury a real difference
in the WORDS among cosmetic ones. Each line is reduced to **kind, from, body,
hints** and each is compared separately, so a failure names which moved.

### Three client bugs

1. **The log cap was 200; the reference's is 500** (`ChatBoxUI.maxMessageCount`,
   chat.js:16). Both drop the oldest, so this silently threw away 300 lines of
   history the reference still shows -- invisible in a short game and only ever
   noticed by someone scrolling back. `MAX_LOG` now matches and says why.
2. **`<script>` in a log message rendered its SOURCE as prose.** `renderMessage`
   kept the words of any element it did not understand, which is right for `<b>`
   and wrong for a script: `before<script>window.x=1;</script>after` displayed as
   `beforewindow.x=1;after`. It never executed -- a rendering bug, not a hole --
   but the log is where a player reads what happened and code is not what
   happened. SCRIPT/STYLE/TEMPLATE/NOSCRIPT now contribute nothing.
3. **Rendered lines carried no record of where they came from.** The reference
   stamps every line `gameMessage` / `warningMessage` / `chatMessage` /
   `systemMessage` and uses it to filter (its "toggle system messages" button).
   Ours had only a warning colour, so the log could not be filtered and System
   lines lost their attribution entirely -- the room's narration read like
   something a person said. One class per kind now, and `System` is labelled the
   way the reference labels it.

### A REFERENCE hazard, measured, and NOT a demonstrated hole

`appendMessage` builds its line with `$("<div class='message ...'>" + message +
"</div>")`, and jQuery EXECUTES `<script>` in parsed HTML. Measured: the script
case sets `window.__pwned` in the oracle's frame and not in ours. The page
reports it as `ORACLE` and does **not** count it as a DIFF -- it is not this
client's defect and would otherwise make every run red for someone else's bug.

**Confirmed four ways**, because "the harness made it happen" is the commonest
way this project has been wrong, and the first claim rested on a chat-box SINK
this project built. `src/dev/scriptprobe.html` re-runs all four:

| probe | result |
|---|---|
| jQuery 1.6.2 parse only, never inserted | inert |
| jQuery parsed then `.append()`ed | **EXECUTED** |
| `ChatBoxUI.prototype.appendMessage` on a bare object | **EXECUTED** |
| an `M` game event through the whole feed path | **EXECUTED** |

Three of the four never touch the sink. The mechanism is jQuery's `.append()`
evaluating scripts in an interpolated string, and parse-only being inert says it
is INSERTION that does it -- which is what an upstream fix would have to
address. So the defect belongs to the reference, not to this project's harness.

**It is not an exploitable XSS by any route checked**, and that qualification is
the point rather than a hedge:

- chat is escaped server-side before it is ever sent -- `MarkdownParser` builds
  its renderer with `escapeHtml(true)` and `sanitizeUrls(true)`
  (MarkdownParser.java:33-43), so a script typed into chat arrives as text;
- player names cannot carry `<` -- `validLoginChars` is alphanumerics plus `-_`
  (DbPlayerDAO.java:15,389-396).

So the sink is unsafe by construction and is held safe by input handling
elsewhere. **The sink is unsafe -- proven; an attacker can reach it -- NOT
shown.**

**Written up and BACKLOGGED in `REFERENCE_SCRIPT_EXECUTION.md`**, which is the
one home for it: the mechanism, all four measurements, why it is not filed as a
vulnerability, what would settle the reachability question, and the fix if it is
ever taken up. Nothing is filed upstream. That is worth telling the engine project as defence-in-depth, but it
is NOT worth reporting as a vulnerability, and no upstream document has been
written for it -- see the queue.

### Verified

    baseline    ALL PASS (20)
    drophint    4 DIFF   -- exactly the four lines carrying card references
    raw         18 DIFF
    misclass    3 DIFF   -- exactly the three warnings
    reorder     18 DIFF

One control per compared field, so a field that silently stopped being compared
shows up as a control that stopped firing.

### It needed an opt-in on the shared oracle

`oldharness.html` stubs `ui.chatBox` with a no-op proxy, because the real one
opens its own long poll. That swallows every message, and a silent sink reads
exactly like a client that renders nothing -- the log surface could not be
compared at all. `OLD.setRealChatBox(true)` installs a sink that borrows
`ChatBoxUI.prototype.appendMessage` itself, so the reference's real behaviour is
what is measured rather than a reimplementation of it. **Opt-in, default off,
and this is the only page that opts in** -- that file has four consumers now.

Its methods are bound to the PROXY, not to the bare object: `appendMessage`
calls `this.checkForEnd` (chat.js:382) and other siblings the sink does not
provide, and bound to the object those are TypeErrors that kill the page. Bound
to the proxy they fall through to the no-op, so the reference's own method runs
without anyone having to enumerate what it happens to touch.

---

## The game-options differential: concede and cancel

`src/dev/optsfuzz.html`, driven by `harness/optsrun.sh`. No server, no game.

    bash harness/optsrun.sh             player + spectator, 4 controls
    bash harness/optsrun.sh baseline

Two things per control -- whether it is OFFERED and what it SENDS. Offers alone
would miss a client that shows the right buttons and posts to the wrong
endpoint, which is the CARD_SELECTION blind spot in another costume.

### Two client gaps

1. **Concede was offered to spectators.** The button was hardcoded into the
   status bar. It is now gated on `canConcede(state)` and hidden until
   PARTICIPANTS says which seat, if any, is ours.
2. **There was no "request cancel" at all.** The reference offers it beside
   concede (gameUi.js:797-801) and the endpoint has always existed
   (`POST /game/{id}/cancel`, GameRequestHandler.java:63-64), so the only way
   out of a game that had gone wrong was to LOSE it. `transport.cancel()` and a
   button, same shape as concede.

### A FOURTH reference bug, and its own guard proves it

The reference's intent is unambiguous: `gameUi.js:608` carries the comment
`//No Options box` for spectators, and `gameUi.js:791` guards both buttons on
`!spectatorMode && !replayMode`. **Its behaviour does not match.**

`addBottomLeftTabPane` runs from `init` at CONSTRUCTION (gameUi.js:213), long
before `participant()` assigns `spectatorMode` -- so both guards read a field
that has no value yet, and the Options box is built for everyone. Measured: the
page records whether the buttons exist BEFORE feeding the PARTICIPANTS event,
and they do, in both roles.

    offered before any role was known: {"concede":true,"cancel":true}

So a spectator in the reference gets a Concede button for a game they are not
playing. Recorded as `ORACLE`, not counted as a DIFF -- the same treatment the
`max=0` cases get, and for the same reason: counting somebody else's bug makes
every run red and teaches a reader to ignore the colour. The comparison then
runs against the reference's own INTENT, which is what this client implements.

### The confirm is a kept divergence, not a gap

The reference concedes on ONE click with no confirmation. This client asks
first, because conceding is irreversible and ends the game for everyone at the
table. Deliberate, defended in DESIGN.md, and deliberately NOT compared -- a
differential that flagged it would be reporting a design decision as a defect.

### Verified

    baseline     player ALL PASS (6)   spectator ALL PASS (6)
    offerall     fires on SPECTATOR only   -- it forces both ON, so it can only
                                             differ where the truth is OFF
    offernone    fires on PLAYER only      -- the mirror
    wrongverb    fires on both             -- what a control sends does not
    wrongpath    fires on both                depend on who is looking

The two offer controls being one-sided is their SCOPE, recorded in
`CONTROL_SCOPE`; without that, each one's passing role reads as coverage.

### Detached boards: there is nothing to compare, and that is the finding

`window.open` appears nowhere in `gameUi.js` -- only in the deckbuilder and hall
(`deckBuildingUi.js`, `hallUi.js`). **The reference has no detachable game board
at all**, so `view/detach.js` has no counterpart and no differential is
possible. That is not a gap in the testing; it is the absence of an oracle, and
it should be recorded rather than papered over with a comparison against
nothing. `multiwindow_spike.html` remains the evidence that the approach works.

---

## The replay-controls differential

`src/dev/replayfuzz.html`, driven by `harness/replayrun.sh`. No server, no
recording needed -- the controls are tested, not the playback.

    bash harness/replayrun.sh             baseline + 3 controls
    bash harness/replayrun.sh baseline

### What is comparable, and what is deliberately not

The reference's replay panel is THREE buttons (gameUi.js:153-180): slower,
faster, play/pause. **No scrubber, no step-back, no seek** --
`playNextReplayEvent` walks `replayGameEventNextIndex` forward and only forward.
This client has all three extras. Those are a SUPERSET, not a disagreement, and
a differential flagging them would be reporting a design decision as a defect.

So two things are compared, both of which each client has: the speed model, and
the play/pause contract.

### The gap: no speed control at all

The one control the reference has and this client lacked. A replay ran at a
fixed 220ms per event with no way to slow it down or speed it up. Added, with
the reference's bounds.

### The speed model is INVERTED, and that is what the control catches

    slower:  replaySpeed = Math.min(16,     replaySpeed * 2)
    faster:  replaySpeed = Math.max(0.0625, replaySpeed / 2)

`replaySpeed` is a duration **multiplier** -- `gameAnimations.js:17` returns
`origValue * this.replaySpeed` -- so the number going UP means the replay goes
SLOWER, and "faster" is the one that divides. Built from the button labels
alone it comes out backwards, **and a replay with its speed buttons swapped
still looks like it works**. The `inverted` control exists for exactly that.

The two clients pace different things: the reference scales animation
DURATIONS, this client advances one event per interval and scales the INTERVAL.
Same control, same bounds, same inversion, applied to what each actually paces
on -- so the multiplier SEQUENCE is compared, not milliseconds.

### Verified

    baseline   ALL PASS (8)
    noclamp    4 DIFF   -- both six-press sequences and both named limits
    inverted   3 DIFF   -- both sequences and the inversion assertion
    autoplay   3 DIFF   -- all three play/pause checks

### It needed the second gated opt-in on the shared oracle

`oldharness.html` constructed the reference with `replayMode` hardcoded
**false** -- right for the other pages, and it meant the replay panel was never
built, so the surface was UNMEASURABLE rather than merely untested.
`OLD.setReplayMode(true)` is opt-in, default off, and must precede `boot()`
because `init` reads the flag at construction. The preflight checks the three
buttons exist for that reason: forgetting it would report the reference offering
no replay controls, which is the opposite of the truth.

Note also that `#replayButton`'s click handler is bound inside
`processXmlReplay`, not at init, so a `<gameReplay>` document has to arrive
before the play button does anything at all.

---

## The zoom differential

`src/dev/zoomfuzz.html` + `src/dev/zoomshapes.js`, driven by
`harness/zoomrun.sh`. No server, no bots, no game.

    bash harness/zoomrun.sh             baseline + 4 controls
    bash harness/zoomrun.sh baseline

Six hover targets across three suppression states = 18 cells.

### The client bug: a face-down card was previewed

`AutoZoom.triggerHover` refuses the two card BACKS outright:

    if (bp !== "-1_1" && bp !== "-1_2") displayPreviewImage(card, ...)

`-1_1` and `-1_2` are the Free and Shadow backs. This client copied the art off
the hovered card, so hovering a face-down card blew the BACK up to full size --
not information, and for a moment it reads as the client leaking a card it
should not be showing. `view/piles.js` now refuses both.

### And two suppression states it had none of

`handleMouseOver` also silences the preview **while a card is being
click-dragged** and **while the card-info dialog is open**
(autoZoomHandler.js:292). In both cases a full-size card lands on top of the
thing the player is working with -- and the second is the sharper one: you open
card info to READ it, and the preview covered it. `createZoom` now takes a
`suppressed` predicate; `live.html` and `replay.html` pass "the card-info window
is open".

Dragging is not wired, because this client has no drag-to-reorder yet. When it
arrives, the predicate is where it goes.

### previewImageBPID is DEAD STATE -- do not read it

`AutoZoom.previewImageBPID` is declared at autoZoomHandler.js:3 and **assigned
nowhere**, exactly like the reference's `settingsAutoPass`. An earlier note in
this file said it was the observable; that was wrong. Reading it would have
reported "no preview" for all 18 cells, which is indistinguishable from a client
that previews nothing -- a clean-looking sheet of agreement over a measurement
that never measured. The real observable is the reference's own
`displayPreviewImage(card, div)`, wrapped per case.

### The 500ms hover delay is bypassed on purpose

`hoverValid` is set so `handleMouseOver` takes its immediate branch into exactly
the same `triggerHover` / `triggerHintHover` the timer would have called. The
delay is PACING, not content, and this page is about content -- the same
argument the oracle's queue patch makes. Waiting on real timers against a
virtual clock would test the clock.

### Verified

    baseline      ALL PASS (18)
    showback      2 DIFF   -- the two backs, unsuppressed
    blank         3 DIFF   -- the three cells that DO preview
    wrongcard     3 DIFF   -- the same three
    ignorestate   6 DIFF   -- three previewing cells x two suppressed states

Every count is arithmetic. `ignorestate` sabotages the CLIENT rather than the
reading, so it exercises the real guard.

No oracle edit: everything hangs off `ui.autoZoom`, which the reference builds
itself at gameUi.js:209.

---

## The card-info differential

`src/dev/infofuzz.html` + `src/dev/infoshapes.js`, driven by
`harness/inforun.sh`. No server, no bots, no game.

    bash harness/inforun.sh              live + replay, 3 controls
    bash harness/inforun.sh baseline

### Two decisions, not one -- which is the bug it found

`displayCardInfo` (gameUi.js:956-968) makes two INDEPENDENT decisions:

    showModifiers = !replayMode && cardId != "hint"
                    && (cardId.length < 4 || cardId.substring(0,4) != "temp")
    cardInfoDialog.showCard(card, showModifiers ? "..." : null);   // ALWAYS
    if (showModifiers) getCardModifiersFunction(cardId, ...);      // guarded

**The card is always shown; only the QUERY is guarded.** This client had the two
fused -- a `/^\d+$/` test on the id that declined to open at all for anything
non-numeric. So right-clicking a card in the picker did nothing whatsoever: not
"no modifiers available", nothing. A card's own text is worth reading even when
nothing is modifying it, which is most of the time.

Three exclusions from the query, each for its own reason: `replay` has no live
game to ask, `"hint"` is a card named in the LOG and identified by blueprint
with no instance in play, and `temp*` ids belong to the picker and are rejected
by the engine -- a refusal that comes back looking exactly like "no modifiers",
which is a different and wrong answer.

The `length < 4` clause is a guard so `substring(0,4)` cannot mis-read a short
id, not a special case: `"tem"` still gets modifiers. `infoshapes.js` pins that
down so a later "simplification" to `startsWith` stays honest.

### Two more gaps it exposed

- **A card named in the game log could not be opened.** The reference opens card
  info when a `cardHint` is clicked (gameUi.js:808-814), building a Card from
  the blueprint with cardId `"hint"`. This client drew the hints and previewed
  them on hover with no way to inspect them. Now a left click opens them --
  left, not right, because a hint is not a decision target and the objection
  that made right-click the choice for board cards does not apply.
- **`replay.html` had no card info at all.** The reference shows it in a replay
  and skips only the fetch. Wired, with `replay: true`.

### Verified

    baseline    live ALL PASS (7)    replay ALL PASS (7)
    alwaysask   live 4 DIFF   replay 7 DIFF
    neverask    live 3 DIFF   replay ALL PASS   <- its scope, see below
    neveropen   live 7 DIFF   replay 7 DIFF

`neverask` **cannot** fire in replay: it suppresses the query, and in a replay
the reference queries nothing anyway. That is its scope, `CONTROL_SCOPE` records
it, and without recording it a clean replay run reads as coverage it is not.

Every count is arithmetic: 4 ids the reference declines to ask about live, 3 it
does, 7 it always shows.

### No oracle edit was needed

Both observables are hooks on the reference's own objects --
`ui.cardInfoDialog.showCard` and `ui.getCardModifiersFunction`, wrapped per case
and restored after. `oldharness.html` has five consumers and every edit to it
has cost this project time, so not touching it is worth a little more work in
the page. `replayMode` is set directly on the ui rather than by booting a
recording: it is a plain flag `displayCardInfo` reads, and driving a whole
replay to flip one boolean would be testing the replay harness instead.

---

## The pile differential

`src/dev/pilefuzz.html` + `src/dev/pileshapes.js`, driven by
`harness/pilerun.sh`. **No server, no bots, no game** -- the same shape as the
decision-space differential, applied to the first of the untested surfaces.

    bash harness/pilerun.sh              4 configs + 3 controls
    bash harness/pilerun.sh baseline     baseline only
    CONFIGS=spectator-public bash harness/pilerun.sh

### The space, and why it is four page loads

Read out of `participant()` (`gameUi.js:1747-1789`), which is the ONLY place the
reference decides a pile dialog exists:

    role           player | spectator     getPlayerIndex(bottomPlayerId) == -1
    discardPublic  true | false           an attribute on PARTICIPANTS
    pile           5 kinds                one createPile call each
    target seat    own | another          the loop runs over allPlayerIds

Role and `discardPublic` are fixed at PARTICIPANTS time and every dialog is
built once from them, so they cannot be varied inside a page -- and `boot()` may
only be called once, which decisionfuzz.html measured the hard way. They are
therefore CONFIGURATIONS, one per page load, and `pilerun.sh` runs all four.

The rule, transcribed:

| pile | offered for seat T when |
|---|---|
| Dead, Removed | T is any player — spectators included |
| Discard | `discardPublic` ? any player : your own, and only if seated |
| Adventure Deck, **Draw Deck** | your own, and only if seated |

`model/piles.js` is that rule; `pileshapes.js` carries an INDEPENDENT
transcription of it and the page checks the oracle against that first. If the
two disagree the page reports `HARNESS`, not a client difference -- comparing
the new client's rule against itself is exactly how the INTEGER differential
passed for a session while measuring a value against a copy of itself.

### Two client bugs, found immediately

1. **No draw-deck pile at all.** The reference gives your own deck a dialog,
   filed under `miscPileDialogs`, which is not a name anyone would guess. This
   client had four pile kinds and the deck was not one of them, so a player
   could not look at their own deck.
2. **Every pile offered for every seat.** An opponent's adventure deck and
   an opponent's private discard both had tabs, and both could only ever say
   "face down to you" -- the server never sends those cards. A control that
   opens onto a dead end is worse than no control.

Both were invisible from inside: the client rendered them without error and the
counts were even correct. Only the oracle says which ones should exist.

### Verified

    baseline    player-private 9   player-public 10
                spectator-private 6   spectator-public 8      all ALL PASS
    dropdeck    fires on the two PLAYER configs                 (its scope)
    extrapile   fires on all four
    contents    fires on all four, every reachable pile

`dropdeck` **cannot** fire on a spectator configuration, because the draw deck
is never offered to a spectator in the first place. That is its scope, not a
gap, and `CONTROL_SCOPE` in `pilerun.sh` records it -- otherwise two clean
spectator runs read as coverage they are not.

### The harness fault it paid for, again

The first run reported `old []` for all nine content comparisons at once. That
is the standing signal: **a whole category failing identically is the harness.**
It was. `NormalCardGroup` holds no card array -- `getCardElems` re-queries
`$(".card", this.container)` and reads each element's `.data("card")`
(`CardGroup.js:32-41`) -- so `group.cards` was `undefined` everywhere. Reading
the dialog's DOM instead turned nine DIFFs into nine passes without a line of
client code changing.

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

### Per-type status: 48/48

    CARD_SELECTION     13/13 (2 reference-wrong)   CARD_ACTION_CHOICE  11/11
    ARBITRARY_CARDS     8/8  (1 reference-wrong)   ASSIGN_MINIONS       5/5
    INTEGER             5/5                        MULTIPLE_CHOICE      5/5
    ACTION_CHOICE       1/1

**Three cases are `oracleWrong`** -- the reference sends an answer the engine
refuses and this client does not. All three are the same defect: a selection
whose `max` is 0, submitted with a card in it. `cardSelectionDecision` and
`arbitraryCardsDecision` both test `< min` and never `max`, while the engine
throws on either bound. Written up in
`gemp_multiplayer/docs/GEMP_CARD_SELECTION_MAX0.md` (untracked, for that project
to triage).

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

**Two untracked bug reports in `gemp_multiplayer/docs/`, and nothing else.**
Both are findings this project measured and has no business fixing, left for
that project to triage:

    GEMP_CARD_SELECTION_MAX0.md   the reference sends an illegal answer at max=0
    GEMP_AUTOPASS_COOKIE.md       the reference's auto-pass settings are inert,
                                  AND the obvious one-line fix breaks games

Neither is committed, neither is staged, and nothing else in that repository has
been touched. Check with `git status --short` there before believing it — the
claim is falsifiable and should be re-checked rather than trusted.

`gemp_multiplayer` was otherwise restored to how it was found. The two branches this
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

Nothing here is pushed anywhere. There *is* a repository here now (see "Version
control, finally"), but it has no remote and shares no history with either GEMP
repo, which is the whole point.

---

## CLOSED: the oracle edit that broke the replay differential

`diffrun.sh` went from clean to **0 of 3 games agreeing**, every mismatch a
`CARD_ACTION_CHOICE`. Cause, found by bisect: the `ui.smallDialog.find("button")`
fallback added to `offers()`. That line alone takes a pinned sample from 3-of-3
agreeing to 0-of-3.

`smallDialog` is a PERSISTENT jQuery UI element that keeps the buttons of earlier
decisions. Read unconditionally it reports stale options: on a pass-only
`CARD_ACTION_CHOICE` the reference correctly offers nothing and the fallback
handed back the previous decision's buttons.

`decisionfuzz.html` genuinely needs it -- it feeds decisions with no board events
between them and jQuery calls the real dialog invisible headlessly, so yes/no
reported zero options without it. So it is **opt-in**:
`OLD.setReadStaleDialogButtons(true)`, set by that page and by nothing else.

### Two methodology failures on the way, both worth more than the bug

**A random sample cannot bisect.** `diffrun.sh` drew three RANDOM recordings per
run, so "0 of 3" against "1 of 3" compared different games and meant nothing. A
whole bisect was run on that comparison, and two edits were wrongly cleared by
it. `diffrun.sh` now takes `SEED=` to pin the shuffle and `IDS=` to name the
games; **use one of them whenever the question is "did this change break
something"** rather than "do the clients agree in general".

**The suspect list was written before the file was re-read.** Five candidates
were listed and ranked; the actual cause was a sixth edit that was simply
forgotten, and it was the only one that touched what `diff.html` actually
consumes. Two theories were acted on -- reverting the `actionableCard` class gate
and disabling the `ui.hand` stub -- before either was tested, and neither was the
cause. Enumerate from the diff, not from memory.

### Verified after the fix

    fuzzrun.sh (full)         47/47 baseline, all three controls fired in scope
    diffrun.sh SEED=7 3 25    3 of 3 agreeing
    diffrun.sh SEED=7 + actionids   0 of 3  <- the control, on the SAME games
    diffrun.sh 12 40          12 of 12 agreeing, 0 disagreeing

The control line is the one that makes the others mean anything: same three
games, clean without sabotage and broken with it.

### A control you cannot INVOKE reads like a control that found nothing

`livediffrun.sh` had no `SABOTAGE` plumbing at all. Running
`SABOTAGE=badcard bash livediffrun.sh` therefore printed an ordinary CLEAN
result and applied no control whatsoever -- and it was nearly read as
confirmation twice before the missing grep hit was noticed.

This is the same failure as a control that cannot FIRE, one level up: the
runner silently dropped the flag instead of refusing an option it did not
understand. **A runner should fail on an option it does not implement, not
ignore it.** `SABOTAGE=` is now plumbed through to `livediff.html`:

    SABOTAGE=badcard    the engine must REJECT (a card id never offered)
    SABOTAGE=actionids  the clients must DIVERGE (card ids for an action index)

Verified firing: `ENGINE REJECTED the old client's answer "999999" to
CARD_SELECTION`, with the decision's full parameter map beside it.

### Live differential, verified

    livediffrun.sh 4 70              4 clean games, ~260 decisions, all six
                                     decision types, 0 mismatches 0 rejections
    SABOTAGE=badcard ... 2 60        2 of 2 games report engine rejections

### The rule this cost twice in one day

**A shared file with three consumers cannot be edited to suit one of them.**
Every edit to `oldharness.html` was made to make `decisionfuzz.html` work, and
none was re-checked against the two older pages until the end. When one is
needed, gate it opt-in.
