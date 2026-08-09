# newclient — the new board client for five-player GEMP

**This is the canonical home (since 2026-08-09).** The project began life as
the standalone `gemp_gui` repository and was imported here with full history
by `git subtree` — `git log -- .` from this directory has every commit, and
the messages carry the reasoning. The old directory
(`Documents/gemp_gui`, tag `pre-merge-into-engine`) is a retired snapshot;
never commit there. The OLD client this one replaces lives two levels up, at
the web root (`../../game.html`, `../../js/gemp-022/gameUi.js`).

A rebuilt game board for GEMP LotR, replacing a client that shows one opponent at
a time. Native ES modules, no build step. `src/` is the client; `src/dev/` is the
test and differential harnesses; `harness/` drives the server.

## Read first

- **`ARCHITECTURE.md`** — how the files work together: the event loop, the
  layering, and a one-line tour of every module. Read it before touching
  anything if you are new to the tree.
- **`HANDOFF.md`, the "Start here" block** — current state, the three harness
  commands with their last verified results, what is done, what is not, and the
  lessons that cost the most. Long, but the top ~90 lines are the orientation.
- **`DESIGN.md`** — the design of record. Every claim about GEMP's behaviour
  carries a `file:line` citation into the engine. If you are about to assert how
  GEMP works, check there first.
- **`git log`** — the commit messages carry the *reasoning*, not just the change.
  Several record why an approach was abandoned; that is often what you need.
- **`REFERENCE_SCRIPT_EXECUTION.md`** — a measured defect in the REFERENCE
  client, backlogged here rather than filed upstream. Read it before concluding
  anything about script handling in the log, in either client.

`src/` layers one way — `view -> model -> nothing`, `state -> model`, `net`
self-contained. Keep it that way. `view/board.js` is down to ~366 code lines
after the selection and prompt extractions; what remains is cohesively the
board, and no further split is planned.

## The geography

```
vendor/gemp-lotr/                    <- THIS repo: the engine fork
  gemp-lotr/.../src/main/web/        <- the OLD client (game.html, js/)
  gemp-lotr/.../web/newclient/       <- THIS project
%USERPROFILE%\gemp2                 <- the RUNTIME: server jar + MySQL data +
                                        the recording corpus. Deliberately
                                        OUTSIDE OneDrive; harness/sync.sh
                                        deploys there and docker runs there.
                                        Never run a server off THIS tree's
                                        database/ dir -- MySQL under OneDrive
                                        sync is a corruption hazard.
Documents/gemp_gui                   <- retired pre-merge snapshot. Read only.
Documents/gemp_multiplayer           <- the engine research project wrapping
                                        this repo. Its docs and harness are
                                        its own; coordinate, don't clobber.
```

Engine (Java) changes carry the engine project's own method — measured
rulings, mvn tests (see gemp_multiplayer/HANDOFF.md). Client changes carry
this project's method — the differentials below. The win of sharing one repo
is that a change to both lands as ONE commit.

`gemp_multiplayer/docs/GEMP_CARD_SELECTION_MAX0.md` is a bug report this project
left there, deliberately **untracked**. Do not commit it or anything else in
that repo.

## Running things

Deploy before testing anything in a browser — the pages are served from the GEMP
origin because the reference client loads from `/gemp-lotr/js/`:

```bash
bash harness/sync.sh
```

The standalone suite — no Docker, no reference client, ~1 minute. This is the
inner loop while working; it proves internal consistency, NOT equivalence:

```bash
bash harness/fastrun.sh            # 18 assertion suites + tests.html + shapecheck's
                                   # 48-shape catalogue drive + 3 controls
ONLY=shapecheck bash harness/fastrun.sh    # one page, for iterating
```

It serves `src/` directly via `harness/devserver.py`, so no `sync.sh` and no
server. **It does not replace the differential**: anything touching `view/` or
`state/` still runs `diffrun.sh` FIRST before being called verified.

The differentials, all green and all proven capable of failing:

```bash
bash harness/fuzzrun.sh            # 48 decision shapes x 7 types + 3 controls (~20 min)
bash harness/pilerun.sh            # 4 viewer configs x 5 piles + 3 controls
bash harness/logrun.sh             # game log + chat message shapes + 4 controls
bash harness/inforun.sh            # card info: 7 id kinds x live/replay + 3 controls
bash harness/zoomrun.sh            # zoom: 6 hover targets x 3 states + 4 controls
bash harness/replayrun.sh          # replay speed + play/pause + 3 controls
bash harness/optsrun.sh            # concede + cancel x player/spectator + 4 controls
bash harness/reorderrun.sh         # which zones drag + 3 controls
bash harness/diffrun.sh 12 40      # replay differential over recorded games
bash harness/livediffrun.sh 4 70   # live, with the ENGINE judging
```

**Run the DIFFERENTIAL FIRST and read its last line**, for anything touching
`view/` or `state/`. Sixteen green assertion suites did not catch a real
`CARD_ACTION_CHOICE` regression that `diffrun` caught immediately — and a commit
went in claiming the differential was clean when its final line already said
otherwise. See the resolved selection-extraction regression at the top of
`HANDOFF.md` — the cause was a single leftover reference to a deleted variable.

**After extracting code out of a file, grep that file for every identifier the
extraction deleted** before running anything. A deleted declaration whose
identifier is still in use does not appear in the diff, throws only on the
branch that reads it, and cost a day twice (zoomfuzz's `frame`, board.js's
`selected`).

**`SEED=` does NOT pin a before/after comparison.** It pins the shuffle, not the
corpus, and live runs add recordings — so two runs at the same seed can compare
different games. Only `IDS=` pins a game.

The server:

```bash
cd ~/gemp2/gemp-lotr/docker
DOCKER="$HOME/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe"
"$DOCKER" compose up -d
```

## Traps that will cost you an hour each

- **Run headless Chrome from bash, not PowerShell.** PowerShell returns nothing
  at all from `chrome --dump-dom`.
- **Block the card-art CDN for `decisionfuzz`, and never for `cardstatecheck`.**
  Chrome's virtual clock is paused while any request is pending, so unresolved
  images freeze it — but blocked images also change card geometry, and
  `cardstatecheck` measures drag distances in pixels. The flag is per-suite.
  `--host-resolver-rules="MAP i.lotrtcgpc.net 127.0.0.1, MAP lotrtcg2e.club 127.0.0.1, EXCLUDE localhost"`
- **`SEED=` or `IDS=` whenever you bisect with `diffrun.sh`.** It samples random
  recordings, so two unpinned runs compare different games and prove nothing.
- **`src/dev/oldharness.html` is shared by NINE pages** — `decisionfuzz`,
  `pilefuzz`, `logfuzz`, `infofuzz`, `zoomfuzz`, `replayfuzz`, `scriptprobe`,
  `diff` and `livediff`. An edit that suits one broke another for hours. Gate
  anything page-specific behind an opt-in flag defaulting OFF, as
  `setReadStaleDialogButtons`, `setRealChatBox` and `setReplayMode` are. After
  editing it, re-run EVERY consumer, not just the one you were working on.
- **A grep for `FAIL` or `RESULT:` matches the pages' own source.** Use
  `RESULT: ALL PASS \([0-9]+\)`.
- **`sync.sh` now REFUSES while a harness is running** — it does `rm -rf
  "$DST"/*` before copying, so a run in flight fetched ES modules from a
  directory being emptied, and the dead pages printed as `CONTROL DID NOT FIRE`:
  an exact impersonation of a real regression. `harness/harnesslock.sh` is the
  shared lock; `SYNC_FORCE=1` overrides. If you ever see that message with no
  lock held, tell a dead page from a live one by the empty `RESULT` field
  before the arrow.
- **Never pipe a harness through `tail`.** It buffers to EOF, so nothing is
  visible while it runs *and* the baseline section is lost. Redirect to a file
  and append the exit code: `bash harness/fuzzrun.sh > out.txt 2>&1; echo
  "EXIT=$?" >> out.txt` — through a pipe, `$?` is the pipe's, not the script's.

## Responding

**End every response with an explicit `## Next steps` list** — numbered, ordered,
concrete, visually separated. Not woven into a closing paragraph, and not omitted
because the answer was short or the task finished. When something is done, the
list says what verification or follow-up remains.

Each item says what to DO, not just what is broken. If an item is blocked, it
says what would unblock it.

This project runs across many sessions and the queue has to be readable at a
glance without re-reading the whole answer to reconstruct it.

Always continue working on item #1 without human prompting. Document and flag issues needed human guidance and intervention. When an item is complete remove it from the next steps list.

## Working rules

- Prove a control fires before trusting a clean run. A control that cannot fire,
  or cannot be invoked, is indistinguishable from one that found nothing — this
  project has shipped both.
- When a whole category of comparisons fails identically, suspect the harness
  before either client. That was true nearly every time this session.
- Count rather than infer. "The client did nothing" has several distinct causes
  and they look identical from outside; instrument and count entries.
- The reference client is the oracle but is **not** infallible — four of its
  bugs are recorded here, all measured, two of them reported upstream. A
  disagreement is still this client's bug until shown otherwise, and "shown"
  means a measurement, not an argument.
