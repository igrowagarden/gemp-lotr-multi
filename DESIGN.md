# A new board client for five-player GEMP

Design of record for the rebuilt game board. Nothing here is speculative — every
claim about GEMP's behaviour was checked against the source, and the file:line
references are the evidence.

Paths like `gameUi.js:336` are relative to the GEMP fork, which lives outside
this directory at `../gemp_multiplayer/vendor/gemp-lotr/`. That tree is
read-only for this project — see `HANDOFF.md`.

**Prototypes in this directory**

| file | what it is |
|---|---|
| `board_prototype.html` | the board, interactive. Open it directly. |
| `multiwindow_spike.html` | proves detachable windows. Open it directly. |

---

## What was wrong with the old board

At five seats the existing client shows **one opponent at a time**, chosen from a
`Viewing:` dropdown, and removes the other three from the DOM entirely. Roughly
60% of the board area is unused black, and the whole player roster, twilight
pool, phase, initiative and the seat selector share one fixed 150px column.

## The shape we built

Rows, top to bottom:

1. **Readout** — twilight pool (the one big number), phase, initiative, move
   count, rule of 4, side filter, clock.
2. **Seats** — every player, horizontally. Vitals, pile access, detach, and the
   marker for whoever is currently deciding. Horizontal because a sidebar cost
   168px of board width.
3. **Focused seat** — support area and free characters, cycled with the edge
   arrows, ← / →, or a seat chip.
4. **Minions in play** — *shared*, every seat's. See below.
5. **The contested fellowship** — *always drawn*. See below.
6. **Your board** — free characters, support area.
7. **Prompt** — always present, never moves.
8. **Your hand.**

The shadow pool sits directly above the fellowship it is attacking, and your own
board sits below that.

### The contested fellowship is always on screen

There is exactly one Free Peoples player per turn, and their fellowship is what
every minion on the table is assigned against. It gets a permanent band, for
players and spectators alike.

This was **missed in the wireframes and caught by the zone registry**: with a
seat carousel and a "your board" band, a shadow player focused on another shadow
player saw no fellowship at all — the single most important object in the game
was visible only if you happened to focus that seat. The completeness check
reported carol's fellowship as claimed by no band, which is exactly the class of
defect the registry exists to catch.

Nothing moves house as the turn passes. When you hold the Free Peoples role your
fellowship is drawn by the *same band* it always was, so the layout is stable —
this is what the rejected lane design could not offer.

Consequence: **the carousel is always one seat short of the table.** The
contested seat is permanently on screen, so it is not among the seats you cycle.
At five players you cycle four boards and the fifth is always visible.

### One opponent at a time, but nothing hidden

Full-size cards are the thing an all-seats view cannot give you. The dropdown's
real faults were that switching cost a menu interaction and that unfocused seats
were *invisible*. Arrows fix the first; the seat strip fixes the second by
carrying every player's vitals whether or not they are focused.

### Minions are one shared band

There is one fellowship under attack and one pool of minions attacking it, so
every minion from every seat lives in a single band, each tagged with **whose it
is**. At two players ownership was implied by position; above two it has to be
said.

This also closes a real gap. No main-board group in the current client claims the
viewer's own `SHADOW_CHARACTERS` — the only group touching that zone
(`gameUi.js:336`) filters through `isFocusedOpponent`, which returns false for
`bottomPlayerId` (`gameUi.js:1559`). Your own minions are invisible until they
reach a skirmish. Pooling the band fixes that without a special case.

The band never filters, and it **does not reset between moves**: minions are
discarded only on the *stay* branch of regroup
(`DefaultAdventure.getPlayerStaysGameProcess` → `DiscardAllMinionsGameProcess`).
Choosing to move again loops back into another shadow phase with every survivor
still on the table. It matters more at five seats than two, because the move
limit derives from seat count above four players (`4910ab56f`).

It keeps its position always but not its height — through the fellowship phase it
collapses to a labelled strip, since no minion can exist before the shadow phase.

### Filtering opponents by side

Four modes — **Auto · FP · Shadow · All** — governing the focused seat's own
cards. Auto shows a seat's fellowship only while that player holds the Free
Peoples role. A filtered-out row collapses to a count chip, never to nothing.

**Blocked on a protocol gap.** `FREE_CHARACTERS` and `SHADOW_CHARACTERS` are
separate zones, so filtering characters is free. `SUPPORT` holds both sides in
one zone, and a shadow condition in an opponent's support area is exactly what a
Free Peoples player needs to see. `GameEvent` already declares a `_side` field
with a getter and fluent setter (`GameEvent.java:44`), but `grep -rn "\.side("`
returns nothing and `EventSerializer` never writes it. Populating and serialising
it is small and self-contained. Until then, opponents' support areas render
unfiltered and say so.

### Skirmishes suspend the focus

A skirmish involves minions from several seats at once, so focus is suspended and
every participating seat is drawn regardless of the filter. Assignment pairs are
columns — minions above, companion below, each labelled with its owner and the
strength totals. The arrows go dead rather than silently doing nothing.

### The prompt

Seven decision types (`AwaitingDecisionType`) collapse into three interaction
shapes:

| shape | types | how it answers |
|---|---|---|
| button | `MULTIPLE_CHOICE`, `ACTION_CHOICE` | choices in the strip |
| number | `INTEGER` | stepper in the strip |
| touch cards | `ARBITRARY_CARDS`, `CARD_ACTION_CHOICE`, `CARD_SELECTION`, `ASSIGN_MINIONS` | eligible cards ring in gold |

**`CARD_SELECTION` answers with a COMMA-SEPARATED LIST of card ids**, even when
only one card is wanted. The reference client builds a JS array and submits
`"" + selectedCardIds`, which stringifies comma-joined (`gameUi.js:2805`), and
only enables its Done button once `selectedCardIds.length >= min` (`:2823`).
So `min` is a real constraint, not a hint.

In practice **every `CARD_SELECTION` observed so far arrives `max=1`** --
measured across live games in four formats, never a single instance above one.
The list format is still what the wire expects; do not "simplify" it to a bare
id on the strength of that observation, because `min`/`max` are decided per
card ability and nothing in the engine caps them.

An id the decision did not offer is refused. The engine's reply is
`Something went wrong` -- the same generic string it returns for an internal
exception -- so a rejection cannot be told from a server fault by its text.

One strip above the hand, always present, showing either your decision or **who
you are waiting on**. Today the only waiting signal is `gameUi.js:1536` flashing
the browser tab title, which says a decision exists but not whose — inferable at
two seats, guesswork at five.

### Elimination

An eliminated player leaves the rotation and the arrows skip them, but they stay
in the strip, struck through, because their cards are still on the table. The
engine has done this since `b47df48dd`; the client has never shown it.

### Spectators

**No adopted seat.** Today a spectator must sit in someone's chair
(`setSpectatorSeat`, `gameUi.js:1599`), which forces a second selector that
invalidates the first — the comment there describes rebuilding the options rather
than merely reselecting.

Once the contested fellowship has its own permanent band, spectating stops being
a mode at all. A spectator is simply **a viewer with no seat and no hand**:

- The contested fellowship and the minion band are shared, so they render
  identically for players and spectators — same bands, same code.
- The "your board" bands match on `viewerId`, which matches no player, so they
  are empty. Nothing special-cases them.
- No hand band, and the prompt reports rather than asks.
- **Follow the action** tracks whoever holds the Free Peoples role.

The hand is not a styling choice — it is impossible. `GameCommunicationChannel`
only emits a card when `zone.isPublic()`, or the discard is public in this
format, or the card's owner equals `_self`. `Zone.HAND` is not public, and a
spectator's `_self` matches no player, so no hand card ever reaches their
channel. Hand *size* still does, via `GameStats` zone sizes.

Note `publicDiscard = zone == DISCARD && _format.discardPileIsPublic()`: in
formats with public discards a spectator **can** browse them, so pile access
should follow the `discardPublic` flag on the `PARTICIPANTS` event rather than
being hidden wholesale.

### Chat and the site path are windows

Drag by the title bar, resize from the corner, ✕ or Escape to dock. Position and
size both persist across a close. Escape closes **one** window — the active one,
then the next down; the front window's title bar goes gold so you can see which.

They go semi-transparent **only while actually covering something that wants
attention** — the prompt, a playable card, a running skirmish, another window's
unread flag. Parked in an empty corner they stay solid.

Because they only ever overlay, **the layout reserves no column for either**.

### The site path

Sites are turned on their side and overlapped, each covering the one before, so
the exposed edge carries the number; the current site is never covered. `⇄`
transposes the path to run left-to-right — same card orientation, different
overlap axis.

**Only the revealed part of the path exists.** Site 1 is pre-played
(`FirstPlayerPlaysSiteGameProcess`); every other site is played on demand by
whoever sits two seats around from the current player, in the direction the
*current* site sets:

```java
Direction dir = gameState.getCurrentSite().getBlueprint().getSiteDirection();
PlayOrder order = (dir == LEFT) ? getClockwisePlayOrder(...) : getCounterClockwisePlayOrder(...);
order.getNextPlayer();                     // skip one
playerToPlaySite = order.getNextPlayer();
```

So the path is revealed as you go, out of different players' decks. Each site is
tagged with whose it is; the pending card names who will play it. The path opens
itself when the fellowship moves, marks the new site, then drops back on its own
unless you are pointing at it.

### The card

One component at three densities, and what survives each step down is a
decision rather than a scale factor.

| density | where | carries |
|---|---|---|
| board | in-play zones, ~180–280px | art, strength/vitality, wound pips, token count, attachment fan |
| compact | pile grids, ~57px | art only — nothing else is legible |
| zoom | hover preview | everything, full text |

What a card in play has to be able to show, from the source:

- **Strength / vitality**, plus site number or resistance — packed into
  `GameStats.charStats` as `cardId=str|vit|site` (or `|R<resistance>`).
- **Wounds and burdens**, plus twenty culture tokens (`Token`), via
  `ADD_TOKENS` / `REMOVE_TOKENS`.
- **Attachments** are drawn as the cards themselves, grouped with their host and
  overlapped so the host paints on top. They arrive as `zone="ATTACHED"` with a
  `targetCardId`, and `assignBands` separates them as `onParent`. An earlier
  design fanned card *spines* behind the host instead; that showed how many were
  attached but never which, and the count it read was never populated, so it
  drew nothing at all for the whole life of the client.
- **State flags** — flipped, inverted, sideways, hindered.
- **Transient highlights** — `CARD_AFFECTED_BY_CARD`, `FLASH_CARD_IN_PLAY`,
  `SHOW_CARD_ON_SCREEN`.
- **Decision states** — eligible, selected.

Two calls worth keeping:

- **Wounds get the loudest treatment on the card.** A wounded character is the
  thing you most need to notice, and wounds must not blend into culture tokens,
  which are bookkeeping. Wounds are red pips along the top edge; tokens are a
  single muted count in the corner.
- **Stats sit on the card's own printed stat column** — three icon badges down
  the left edge, over the shield, the vitality ball and the compass, rather than
  in a "4/3 R10" strip. A strip reads as a label stuck on top of the art; these
  read as part of the card, and a player who knows the cards already looks
  there. It costs an alignment problem, which is why the geometry is measured
  rather than chosen — see below.

  **The placement is measured off real card art, not off the reference
  client.** The reference (`gameUi.js:2211`) draws a separate icon box and
  number box per stat, and its number sits a consistent **1 to 1.5% of card
  height below** where the cards actually print theirs — visible on a real
  board. Our badges take the reference's *sizes* (they drive the same PNGs) and
  their own *positions*, from the white numerals printed on four different
  minions, which agree to within 0.1%:

  | | printed centre | reference client |
  |---|---|---|
  | strength | 67.27% of height | 68.8% |
  | vitality | 79.07% | 80.0% |
  | site / resistance | 89.66% | 90.5% |
  | column | 12.15% of width | 12.6% |

  Strength is **not square** — 11.6 × 16.5 — and squaring it reads as a
  misalignment rather than as a wrong size. Each badge is placed by its centre,
  because the centre is what is being matched. `scratchpad/measure2.py` did the
  measuring; `src/dev/statcheck.html` checks it.

  The numerals are set in a **humanist serif with lining figures**, matching the
  card's own. The reference client is no guide here either: it declares only
  `text-align`, `font-weight` and `color`, and inherits Verdana. Georgia and
  Garamond are kept out of the stack because both default to *oldstyle* figures,
  which would drop the 4 below the baseline and lift the 6 above it inside a
  badge sized for one uniform digit.

**Only shared zones carry an owner tag** — the minion band and the skirmish.
Everywhere else the zone already says whose the card is, so a tag would be
noise. This is why the minion band's tags matter and the focused seat's cards
have none.

### Rows, and when to add a rank

A zone stays a single row of full-height cards until they would be squeezed below
half the band height. Rank *k* earns its place at **N > k(k−1)·M**, where M is how
many full-height cards one row holds. At 16:9 the threshold is
resolution-independent and lands near 19 cards in the tall band — board zones
never reach it.

The existing client already implements exactly this test
(`CardGroup.js:347`): it takes the one-row height only
`if (oneRowHeight * 2 + padding > this.height)`, otherwise it adds a row and
retries. Worth carrying over rather than reinventing.

**Piles are the exception.** A discard runs past 40 cards against an M near 3, so
the pile viewer is a wrapped grid — the one place two dimensions win on geometry
rather than meaning.

### Piles and zoom

Piles are reached from the seat that owns them — five seats × four piles is
twenty, far too many for their own controls. Zoom anchors to the far side of the
board from whatever you are pointing at, so it never sits under the cursor. Sites
preview on their side; an unplayed site previews nothing.

---

## Multi-window

**Validated** by `multiwindow_spike.html`. Press ⧉ on a seat to detach that
board, then step the phase and watch every window repaint from one store.

The rule that governs it: **only one window may talk to the server.**
`LotroGameMediator.playerAnswered` throws `SubscriptionConflictException` when a
second client claims a player's channel, which reaches the browser as HTTP 409 —
the same error this project already hit when a browser and a bot shared an
account. A detached panel is a **second view, never a second client**: the main
window keeps the sole transport, decoder and store, and children are view layers
reading it directly.

Gotchas the spike had to handle:

- Styles do not follow a node across documents — the child needs its own copy of
  the stylesheet.
- Moving DOM across documents needs `adoptNode`, not `append`.
- Popup blockers require detaching to happen in a direct click handler.
- A reload orphans children unless they are closed on `beforeunload`.

Baseline `window.open` works everywhere. Document Picture-in-Picture (Chromium
116+) suits a single pinned panel; the Window Management API (Chromium 100+,
`window-management` permission) lets panels open on a chosen monitor rather than
being dragged there.

The most valuable thing to detach is **opponent boards** — one-at-a-time was
chosen because of screen space, and a second monitor lifts that constraint.

---

## Palette

Basalt and ash neutrals, warm-biased, lit by firelight from below the board.

| token | means |
|---|---|
| gold | you, and whatever has focus |
| ember | the shadow side |
| jade | Free Peoples — deliberately the only hue that is not fire |
| ash blue | shared things, the road |
| signal red | unread — a solid pill against 12% washes, separated by form not hue |

---

## Architecture

The engine and protocol contract is fixed and we follow it exactly. Everything
above that line is ours to build properly, and the goal is a client that is
**modular, adaptable and pleasant to change** — which the current one is not.

### What we are avoiding, specifically

The existing client is one 3,011-line object literal. The failures worth naming,
because each one has a structural fix:

| problem | where | fix |
|---|---|---|
| Four places independently enumerate the card groups | `initializeGameUI`, `getReorganizableCardGroupForCardData`, `layoutGroupWithCardOnly`, `getBoardCardGroups` | one zone registry |
| The DOM *is* the state — `$(".card:cardId(N)").data("card")` | throughout | a store; views never read state back out of the DOM |
| Seven bespoke decision methods building UI ad hoc | `integerDecision` … `assignMinionsDecision` | one decision descriptor with a `shape` |
| Layout is imperative arithmetic over magic index arrays | `layoutUI`, `heightScales`, `yScales` | bands declared as data |
| Transport, state and rendering interleaved | `communication.js` ↔ `gameUi.js` | strict layering |

The drift between those four group lists **is** the bug where no group claims the
viewer's own `SHADOW_CHARACTERS`. It is not an oversight to be patched; it is
what four hand-maintained lists always eventually do.

### Layering

```
net/transport   one connection. retry, cancellation, 409.
net/protocol    XML -> plain event objects. THE ONLY place that knows the wire.
state/reduce    (state, event) -> state. pure, no DOM.
state/store     subscribe / emit / snapshot.
state/selectors derived reads: seats, minionsInPlay, focusedSeat, activeSide.
model/zones     the registry. one declaration per zone.
model/rules     small pure helpers: fpPlayer, siteOwner, activeSideOf.
view/*          subscribers. render from state, own no state.
layout/*        rank packing, band allocation. pure geometry.
detach/*        a child window is another subscriber.
```

Each arrow is one-directional. A view never reaches past the store; the store
never touches the DOM; `net/protocol` is the only module that has ever heard of
`PCIP` or `charStats`.

### The commitments that make it adaptable

1. **One zone registry.** Every zone declared once — id, side, owner, shared or
   not, which band, which density, whether it filters. Layout, filtering, zoom,
   hit-testing and detaching all read from it. Adding a zone is one entry, not
   four edits in four files.
2. **The store is the state.** Views are pure functions of it. This is what
   makes multi-window free rather than a feature — already proven by
   `multiwindow_spike.html`.
3. **Reducers are pure and headless.** `(state, event) -> state` runs with no
   browser, so recorded event streams can be replayed and diffed — the same
   record/replay/compare discipline the project's harness already uses.
4. **Decisions are data.** One descriptor carrying `shape` (button / number /
   cards), the prompt text, the options and the eligible card ids. The seven
   `AwaitingDecisionType` values map onto three shapes; the view renders the
   shape and never switches on the type.
5. **Layout is declarative.** Bands are data — id, fr weight, what it holds,
   how it responds to the filter and the phase. The grid is generated. No
   `setBounds` arithmetic.
6. **Native ES modules, no build step**, so the bind-mounted web dir keeps
   edit-and-reload (HANDOFF trap #8).

### Two invariants, checked on every paint

Both catch the drift the old client shipped, and the second only exists because
the first missed a case in practice.

1. **Nothing is claimed by no band.** `checkComplete` flags any card that ought
   to be on screen and matched no band. An unclaimed card keeps `position:static`
   and renders at the container's full width — this is the five-player
   ring-bearer bug.
2. **Nothing is claimed by a band this mode does not draw.** During a skirmish
   the display list changes; the first version of it replaced the whole board, so
   the minion band and most of the fellowship silently disappeared. Every card
   was still *claimed*, so check 1 passed. `undrawn()` compares the bands holding
   cards against the bands actually displayed.

The lesson generalises: a registry stops the *model* drifting, but the
presentation layer can still drop what the model correctly assigned. Check both
ends.

### Testability, honestly

Pure reducers and a zone registry make far more of this testable headlessly than
the old client — but the M5 lesson stands: 19 headless assertions passed against
a rendering bug the whole time, because they tested the filter predicate and the
defect was in what happened to the cards it rejected. **Reducers get unit tests;
layout gets pixels.** Do not let the first tempt you into skipping the second.

### Where it ships

A **new route alongside `game.html`**, which keeps the 0-pixel two-player
regression and the six upstream-clean commits intact.

---

## Open

- `GameEvent.side` unpopulated and unserialised — blocks filtering opponents'
  support areas.
- Site owner may not reach the client either; worth checking with the above
  rather than twice.
