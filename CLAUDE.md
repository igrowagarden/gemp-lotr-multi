# gemp_gui — a new board client for five-player GEMP

A rebuilt game board for GEMP LotR, replacing a client that shows one opponent at
a time. Native ES modules, no build step. `src/` is the client; `src/dev/` is the
test and differential harnesses; `harness/` drives the server.

## Read first

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

## Do not touch these trees

```
gemp_gui/            <- this project
gemp_multiplayer/    <- ANOTHER project, worked separately. Read only.
  vendor/gemp-lotr/  <- the GEMP fork. Read only.
C:\Users\emers\gemp2 <- OUR server + our copy of the reference client. Read
                        freely; this is the one to read the old client from.
```

`gemp_multiplayer/docs/GEMP_CARD_SELECTION_MAX0.md` is a bug report this project
left there, deliberately **untracked**. Do not commit it or anything else in
that repo.

## Running things

Deploy before testing anything in a browser — the pages are served from the GEMP
origin because the reference client loads from `/gemp-lotr/js/`:

```bash
bash harness/sync.sh
```

The differentials, all green and all proven capable of failing:

```bash
bash harness/fuzzrun.sh            # 48 decision shapes x 7 types + 3 controls
bash harness/pilerun.sh            # 4 viewer configs x 5 piles + 3 controls
bash harness/logrun.sh             # game log + chat message shapes + 4 controls
bash harness/inforun.sh            # card info: 7 id kinds x live/replay + 3 controls
bash harness/zoomrun.sh            # zoom: 6 hover targets x 3 states + 4 controls
bash harness/diffrun.sh 12 40      # replay differential over recorded games
bash harness/livediffrun.sh 4 70   # live, with the ENGINE judging
```

The server:

```bash
cd /c/Users/emers/gemp2/gemp-lotr/docker
DOCKER=/c/Users/emers/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe
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
- **`src/dev/oldharness.html` is shared by FIVE pages** — `decisionfuzz`,
  `pilefuzz`, `logfuzz`, `diff` and `livediff`. An edit that suits one broke another for
  hours. Gate anything page-specific behind an opt-in flag, as
  `setReadStaleDialogButtons` and `setRealChatBox` are.
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
- The reference client is the oracle but is **not** infallible — three of its
  bugs are recorded here, all measured, two of them reported upstream. A
  disagreement is still this client's bug until shown otherwise.
