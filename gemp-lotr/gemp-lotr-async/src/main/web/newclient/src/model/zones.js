/**
 * The zone registry.
 *
 * This is the single place that knows where a card belongs. Layout, filtering,
 * zoom, hit-testing and detaching all read from here. The old client kept four
 * independent lists of card groups -- initializeGameUI, getReorganizableCard-
 * GroupForCardData, layoutGroupWithCardOnly and getBoardCardGroups -- and they
 * drifted, which is why no group there claims the viewer's own SHADOW_CHARACTERS
 * and your own minions are invisible until they reach a skirmish.
 *
 * Two exports matter:
 *   ZONES  - a mirror of the engine's Zone enum, so client code can reason about
 *            visibility without guessing.
 *   BANDS  - the ordered board regions. First match wins, so a card lands in
 *            exactly one band by construction rather than by discipline.
 */

export const Side = Object.freeze({
  FREE_PEOPLES: "fp",
  SHADOW: "shadow"
});

function zone(name, opts) {
  return Object.freeze({ name, ...opts });
}

/**
 * Mirrors com.gempukku.lotro.common.Zone, in declaration order and with the same
 * three flags, so this table can be diffed against the enum by eye.
 *
 * `side` is ours, not the engine's. SUPPORT is deliberately null: it holds both
 * sides' conditions in one zone and the wire carries no side attribute, which is
 * the single thing blocking side-filtering of an opponent's support area.
 * GameEvent declares a _side field, but nothing populates or serialises it.
 */
export const ZONES = Object.freeze({
  FREE_CHARACTERS:   zone("FREE_CHARACTERS",   { inPlay: true,  isPublic: true,  visibleByOwner: true,  side: Side.FREE_PEOPLES }),
  SHADOW_CHARACTERS: zone("SHADOW_CHARACTERS", { inPlay: true,  isPublic: true,  visibleByOwner: true,  side: Side.SHADOW }),
  SUPPORT:           zone("SUPPORT",           { inPlay: true,  isPublic: true,  visibleByOwner: true,  side: null }),
  ADVENTURE_PATH:    zone("ADVENTURE_PATH",    { inPlay: true,  isPublic: true,  visibleByOwner: true,  side: null }),
  ATTACHED:          zone("ATTACHED",          { inPlay: true,  isPublic: true,  visibleByOwner: true,  side: null }),
  STACKED:           zone("STACKED",           { inPlay: false, isPublic: true,  visibleByOwner: true,  side: null }),
  DEAD:              zone("DEAD",              { inPlay: false, isPublic: true,  visibleByOwner: true,  side: null }),
  REMOVED:           zone("REMOVED",           { inPlay: false, isPublic: true,  visibleByOwner: true,  side: null }),
  HAND:              zone("HAND",              { inPlay: false, isPublic: false, visibleByOwner: true,  side: null }),
  ADVENTURE_DECK:    zone("ADVENTURE_DECK",    { inPlay: false, isPublic: false, visibleByOwner: true,  side: null }),
  DISCARD:           zone("DISCARD",           { inPlay: false, isPublic: false, visibleByOwner: true,  side: null }),
  VOID:              zone("VOID",              { inPlay: false, isPublic: false, visibleByOwner: false, side: null }),
  VOID_FROM_HAND:    zone("VOID_FROM_HAND",    { inPlay: false, isPublic: false, visibleByOwner: false, side: null }),
  DECK:              zone("DECK",              { inPlay: false, isPublic: false, visibleByOwner: false, side: null })
});

/**
 * Mirrors GameCommunicationChannel.cardCreated. The server has already applied
 * this before anything reaches us -- a spectator is never sent a hand card at
 * all -- but having it here lets the client assert rather than assume, and
 * explains why a zone renders empty.
 */
export function visibleTo(card, viewerId, { discardPublic = false } = {}) {
  const z = ZONES[card.zone];
  if (!z) return false;
  if (z.isPublic) return true;
  if (card.zone === "DISCARD" && discardPublic) return true;
  return z.visibleByOwner && card.owner === viewerId;
}

/**
 * The fellowship every minion on the table is attacking: the Free Peoples
 * player's. It no longer has a band of its own -- the playtest ruled the
 * "fellowship under attack" row out of the layout -- but the id still marks
 * the seat chips and drives the default focus, which FOLLOWS the Free
 * Peoples seat so the active fellowship is the opponent view unless the
 * player deliberately looks elsewhere.
 */
export function contestedId(ctx) {
  return ctx.fpId;
}

/** Seats the carousel can focus: every seat at the table. The contested seat
 *  used to be excluded because its own band drew it permanently; with that
 *  band gone it is focusable like any other. */
export function focusableSeats(seatIds, ctx) {
  return seatIds;
}

/**
 * The board regions that hold cards, in precedence order.
 *
 * `holds` is a predicate over (card, ctx) where ctx is
 *   { viewerId, focusId, fpId, spectating, skirmishing, filter }
 *
 * Ordering does real work. `selfFree` precedes `focusFree`, so the viewer's
 * fellowship NEVER leaves their own row ("my side of the board is sacred" --
 * the playtest's ruling), even when they focus themselves. There is no
 * contested band: the playtest removed the "fellowship under attack" row, so
 * the Free Peoples player's cards render in the focus rows when focused (the
 * default focus follows them) and are deliberately absent otherwise, like any
 * other unfocused opponent.
 */
export const BANDS = Object.freeze([
  {
    id: "skirmish",
    density: "board",
    shared: true,
    ownerTag: true,
    // A skirmish involves several seats at once, so it outranks every
    // owner-based band for as long as it is running.
    holds: (card, ctx) => ctx.skirmishing && (card.inSkirmish === true || card.assignedTo != null)
  },
  {
    // A CONFIRMED assignment: the companion and the minions committed against
    // it merge into one boxed fight, out of their home rows -- the skirmish
    // view forming as assignments land (playtest ruling). Pre-confirm picks
    // stay in their rows, and the skirmish band outranks this while one
    // actually resolves. `fightComps` is the set of companion ids with
    // assignments, provided by the board from state.assignments.
    id: "fights",
    density: "board",
    shared: true,
    ownerTag: true,
    filterable: false,
    holds: (card, ctx) => !ctx.skirmishing &&
      (card.assignedTo != null ||
       (ctx.fightComps != null && ctx.fightComps.has(card.cardId)))
  },
  {
    id: "selfFree",
    density: "board",
    shared: false,
    ownerTag: false,
    filterable: false,   // your own board is never filtered
    holds: (card, ctx) => card.zone === "FREE_CHARACTERS" && card.owner === ctx.viewerId
  },
  {
    id: "selfSupport",
    density: "board",
    shared: false,
    ownerTag: false,
    filterable: false,
    holds: (card, ctx) => card.zone === "SUPPORT" && card.owner === ctx.viewerId
  },
  {
    id: "focusFree",
    density: "board",
    shared: false,
    ownerTag: false,
    filterable: true,
    holds: (card, ctx) => card.zone === "FREE_CHARACTERS" && card.owner === ctx.focusId
  },
  {
    id: "focusSupport",
    density: "board",
    shared: false,
    ownerTag: false,
    filterable: true,
    holds: (card, ctx) => card.zone === "SUPPORT" && card.owner === ctx.focusId
  },
  {
    id: "minions",
    density: "board",
    shared: true,
    ownerTag: true,
    filterable: false,   // never filtered: this is the state everyone acts on
    holds: (card) => card.zone === "SHADOW_CHARACTERS"
  },
  {
    id: "hand",
    density: "board",
    shared: false,
    ownerTag: false,
    filterable: false,
    // A spectator has no hand, and the server never sends them one.
    holds: (card, ctx) => !ctx.spectating && card.zone === "HAND" && card.owner === ctx.viewerId
  },
  {
    id: "path",
    density: "board",
    shared: true,
    ownerTag: true,      // sites come from different players' adventure decks
    filterable: false,
    holds: (card) => card.zone === "ADVENTURE_PATH"
  }
]);

const BAND_BY_ID = Object.freeze(
  Object.fromEntries(BANDS.map((b) => [b.id, b]))
);

export function band(id) {
  return BAND_BY_ID[id];
}

/**
 * Cards that render on another card rather than in a band of their own.
 * Attachments fan behind their host; stacked cards sit under it.
 */
export function ridesOnParent(card) {
  return card.zone === "ATTACHED" || card.zone === "STACKED";
}

/**
 * Assign every card to exactly one band.
 *
 * Returns { byBand, unclaimed, onParent }. `unclaimed` is the important one: a
 * card that no band claims is the bug that put a full-width ring-bearer across
 * the five-player board, because an unpositioned card keeps position:static and
 * falls into normal flow. Callers should treat a non-empty `unclaimed` as a
 * defect, not as cards to skip.
 */
export function assignBands(cards, ctx) {
  const byBand = new Map(BANDS.map((b) => [b.id, []]));
  const unclaimed = [];
  const onParent = [];

  for (const card of cards) {
    if (ridesOnParent(card)) {
      onParent.push(card);
      continue;
    }
    const hit = BANDS.find((b) => b.holds(card, ctx));
    if (hit) byBand.get(hit.id).push(card);
    else unclaimed.push(card);
  }
  return { byBand, unclaimed, onParent };
}

/** Zones that live off the board entirely -- piles, decks, the void. */
export const OFF_BOARD = Object.freeze([
  "DECK", "DISCARD", "DEAD", "REMOVED", "ADVENTURE_DECK", "VOID", "VOID_FROM_HAND"
]);

/**
 * Whether a card is *expected* on screen right now.
 *
 * Not every undrawn card is a defect: an unfocused opponent's fellowship is
 * deliberately absent, which is the whole point of showing one opponent at a
 * time. Only these must appear, and a card that qualifies but lands in no band
 * is a real bug.
 */
export function shouldDraw(card, ctx) {
  if (OFF_BOARD.includes(card.zone)) return false;
  if (ridesOnParent(card)) return false;
  // Shared state: everyone sees it whatever the focus says.
  if (card.zone === "SHADOW_CHARACTERS" || card.zone === "ADVENTURE_PATH") return true;
  if (card.zone === "HAND") return !ctx.spectating && card.owner === ctx.viewerId;
  // The Free Peoples player's cards are NOT special here any more: with the
  // contested band removed, an unfocused Free Peoples fellowship is
  // deliberately absent like any other unfocused opponent's.
  return card.owner === ctx.viewerId
      || card.owner === ctx.focusId;
}

/**
 * Everything that ought to be on the board is on the board.
 *
 * Cheap enough to run in development on every state change, and it is the
 * invariant the old client never had: an unclaimed card there keeps
 * position:static, falls into normal flow and renders at the container's full
 * width -- which is how a ring-bearer ended up covering the five-player screen.
 */
export function checkComplete(cards, ctx) {
  const { unclaimed } = assignBands(cards, ctx);
  const missing = unclaimed.filter((c) => shouldDraw(c, ctx));
  return { ok: missing.length === 0, missing };
}
