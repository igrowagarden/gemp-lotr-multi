# Architecture — how the GUI files work together

This is the orientation document: what each directory is for, how a server
event becomes pixels, and how a click becomes an answer. For evidence behind
any claim about GEMP's behaviour, see `DESIGN.md`; for current project state,
`HANDOFF.md`.

## The whole idea in one paragraph

The server speaks XML game events over a long poll. `net/` turns that XML into
plain JavaScript event objects and nothing else. `state/` folds those events
into a single state object — pure functions, no DOM, no network. Every view
subscribes to that one state and redraws from it; views never talk to the
server and never hold game state of their own. When the player answers a
decision, the answer callback travels back up to the page, which hands it to
`net/` to send. That one-way loop is the entire client:

```
        server XML                    render
  ┌──── long poll ────┐        ┌────────────────┐
  ▼                   │        ▼                │
net/  ──decode──▶  state/  ──notify──▶  view/ ──┘
  ▲                (store)              │
  │                                     │ click
  └────────── answer(decisionId, value) ┘
```

Because every window is just another subscriber to the same store, a detached
board, a spectator view and the picker cost nothing extra — they are readers,
not clients.

## The layering rule

Imports point one way, and keeping it that way is what keeps the client
testable:

    view  ->  model, state (read-only), layout
    state ->  model
    net   ->  nothing (self-contained)
    model ->  nothing
    layout -> nothing

`model/` and `layout/` are leaf code: pure functions and data tables with no
DOM and no network, which is why most of the test suites need no browser
mechanics at all. Nothing imports an entry page, ever — behaviour that lives
only in an HTML file is unreachable by every suite, and both times that
happened it hid real gaps (`view/session.js` and `state/replayer.js` exist
because of it).

## One decision, end to end

The concrete walk-through, because the abstract loop hides the interesting
part — where each responsibility lives:

1. The long poll in `net/transport.js` returns a batch of `<ge>` elements.
2. `net/protocol.js` decodes each into `{ type, ...fields }`. A decision is
   detected by `hasAttribute("decisionType")` — the server attaches decisions
   to whatever event it is flushing — and gets a `shape` from
   `DECISION_SHAPES` so the view can render unknown types as their options.
3. The page feeds the batch to the store: `store.dispatchAll(events)` runs
   `state/reduce.js` over each, producing a new state with `state.decision`
   set, and notifies once at the end of the batch.
4. The store notifies subscribers. `view/board.js` repaints: bands and cards
   from the zone registry, eligibility highlights from `model/actions.js`,
   and the decision strip from `view/prompt.js`. `view/session.js` reacts too
   — alert sound, unread counts, opening the picker for ARBITRARY_CARDS.
5. The player clicks. Card clicks toggle `model/selection.js` (what is picked
   but not yet sent — deliberately NOT in the store, because an unconfirmed
   selection is not game state and other windows must not see it). Strip
   buttons enforce the engine's own rules (min AND max, Pass only at min 0).
6. Confirm calls `onAnswer(decisionId, value)`, which the page wired to
   `transport.answer(...)`. The selection is cleared so nothing leaks into
   the next decision, and the loop goes round again.

## The four pages

The `.html` files are composition roots — wiring, not behaviour. Each creates
the store, the net client it needs, and the views, then connects callbacks:

- **`live.html`** — the real client. Transport + chat + board + panels +
  picker + alerts + session reactions + settings. Must be served from the
  GEMP origin (the session cookie is bound to it).
- **`replay.html`** — a finished game. Same store and views, no transport:
  `state/replayer.js` owns position, seek and pacing with injected timers,
  and answering is disabled (`onAnswer` null — the strip renders read-only).
- **`hall.html`** — the table list, on `net/hall.js`'s own long poll. Gets
  you to a game by clicking instead of hand-editing a gameId.
- **`board.html`** — a bare board fed events directly; used by the dev pages
  and as the document a detached window loads.

## Directory tour

### `net/` — the server's language

- `transport.js` — the one game connection: long poll, channel numbers, the
  409/410 meanings, and `answer()` going back.
- `protocol.js` — XML `<ge>` → event objects. Field-by-field decoding, and
  the place that knows decisions ride on arbitrary event types.
- `hall.js` — the hall's own long poll (tables, seats, joining).
- `chat.js` — table chat; a separate connection because the game channel
  never carries chat.

### `state/` — the one truth

- `store.js` — subscribe / dispatch / snapshot. Small on purpose.
- `reduce.js` — `(state, event) -> state`, plus the derived readers the views
  use (`allCards`, `seats`, `clockOf`, `formatClock`, …).
- `replayer.js` — replay position and pacing over a fixed event list.

### `model/` — the rules, with no DOM

- `zones.js` — THE zone registry: where every card belongs. Layout,
  filtering, zoom, piles and detach all read this one table.
- `actions.js` — CARD_ACTION_CHOICE unpacked: which actions belong to board
  cards, which are "virtual" (discard pile / draw deck sources).
- `assign.js` — ASSIGN_MINIONS: what may be assigned, and the encoding.
- `selection.js` — what is picked but not yet sent; the take-back state
  machine for assignments.
- `autopass.js` — the auto-pass cookie (a server feature; the client only
  chooses the phase set).
- `piles.js` — which piles a viewer may open, and whose.
- `images.js` — blueprintId → card image URL.
- `cardinfo.js`, `gameopts.js`, `reorder.js`, `replayctl.js` — one rule each:
  when to ask the server "why is this 8", who may concede/cancel, which zones
  drag, replay speed bounds.

### `layout/` — presentation as data

- `bands.js` — the band table: which rows exist per viewer mode and what
  goes in them. Adding a band is a table row, not arithmetic.
- `rank.js` — how many ranks a row needs so cards stay readable.

### `view/` — pure readers of state

- `board.js` — the board: bands, cards, focus and filter, drag order,
  assignment alignment, and wiring clicks to decisions. The largest view,
  and after the selection/prompt extractions, cohesively the board.
- `prompt.js` — the decision strip: what the decision asks and the controls
  that answer it from the strip. Enforces the engine's answer bounds.
- `card.js` — one card: art, stat badges, wounds, tokens.
- `session.js` — the reactions: picker opening, the (default-off) client
  auto-pass arm and its double-answer guard, unread counting, alerts.
- `picker.js` — the ARBITRARY_CARDS window; owns its own buttons so one
  decision never has two sets of controls.
- `piles.js` — pile viewers and card zoom.
- `panels.js` + `flyout.js` — the adventure-path and chat panels, and the
  shared flyout mechanics (page-wide: Escape closes topmost, one active).
- `detach.js` — a board in its own window: read-only on purpose, because two
  windows offering one decision would let a player answer twice.
- `actionmenu.js` — the per-card menu when one card offers several actions.
- `animate.js` — FLIP animation; measures before, animates after, so the
  renderer stays a pure function of state.
- `alerts.js`, `effects.js`, `cardinfo.js`, `settings.js` —
  tab-title/sound alerts, the three feedback-only events, the modifier
  panel, and the settings flyout. (There was a `pregame.js` seat display;
  the playtest ruled the panel out and it was deleted whole — the
  PRE_GAME_SETUP decoding survives in `net/protocol.js` for metaSites.)

## The rules that keep it working

- **Views never write state.** They read the store and call callbacks the
  page gave them. If a view needs to remember something, ask whether it is
  game state (store), a rule (model), or truly view-local (keep it there,
  like an unconfirmed selection).
- **The zone registry is the single source of where cards belong.** Four
  independent copies of that knowledge is what made the old client
  unmaintainable.
- **Unconfirmed input is not game state.** It never enters the store, so no
  other window can ever render somebody's half-made choice.
- **Enforce what the engine enforces, where the answer is sent.** Highlights
  stay permissive; the Confirm button is the gate. The engine judges answers,
  not highlights.
- **Nothing imports an entry page.** Behaviour belongs in a module a suite
  can import; pages are wiring.

## Testing (the short version)

`src/dev/` holds per-module assertion suites (`*check.html`), surface
differentials against the reference client (`*fuzz.html` driven by
`harness/*run.sh`), and the replay/live differentials (`diff.html`,
`livediff.html`). The reference client is the oracle; `dev/oldharness.html`
is the shared probe that reads what it offers. The differential comes first
for anything touching `view/` or `state/` — see `CLAUDE.md` for the run
commands and the traps.
