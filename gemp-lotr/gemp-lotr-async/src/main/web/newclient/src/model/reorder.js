/**
 * Which cards a player may drag into their own order.
 *
 * This client HAS had drag-to-reorder since the board was built --
 * `view/board.js`, `makeDraggable` with a 6px threshold and an `orders` map per
 * band. What it did not have was any RULE about where it applies: every card in
 * every band got a drag handler. The reference is specific, and its list is a
 * closed one (`getReorganizableCardGroupForCardData`, gameUi.js:216-247),
 * checked in order:
 *
 *     charactersOpponent   FREE_CHARACTERS, focused opponent
 *     charactersPlayer     FREE_CHARACTERS, own
 *     supportOpponent      SUPPORT,         focused opponent
 *     supportPlayer        SUPPORT,         own
 *     hand                 HAND (own) or EXTRA -- and only when NOT a spectator
 *     shadow               SHADOW_CHARACTERS, focused opponent
 *     skirmish groups      whatever is in the current skirmish
 *     shadowAssignGroups   minions assigned to a companion
 *
 * Everything else is left alone: the adventure path, the piles, the draw deck.
 *
 * TWO THINGS WORTH KNOWING BEFORE COPYING IT.
 *
 * 1. **The reference's reorder is purely local and does not survive.** The drag
 *    ends in `cardGroup.layoutCards()` and nothing else -- no request, no
 *    stored order. The next relayout of that group puts the cards back in the
 *    server's order. This client keeps the order in `orders` per band, so it
 *    survives repaints; that is a deliberate improvement, not a divergence to
 *    correct, and it is why our version is worth having at all.
 *
 * 2. **"Focused opponent" does not map onto this client.** The reference shows
 *    one opponent at a time and reorders only that one's cards. This board
 *    draws minions as ONE SHARED BAND carrying every seat's (DESIGN.md), so
 *    there is no single focused owner to gate on. The rule below is therefore
 *    by ZONE, which is the part that genuinely transfers.
 */

/**
 * Zones whose cards may be dragged into a player's own order, taken from the
 * group predicates at gameUi.js:329-348.
 *
 * `EXTRA` is in the reference's `hand` group alongside HAND -- cards with a
 * negative id there belong to special skirmishes and are excluded, but this
 * client has no EXTRA band, so it does not arise.
 */
export const REORDERABLE_ZONES = Object.freeze([
  "FREE_CHARACTERS",
  "SHADOW_CHARACTERS",
  "SUPPORT",
  "HAND"
]);

/**
 * Zones this client draws that must NOT be reorderable. Listed explicitly
 * rather than left as "everything else", because the interesting question a
 * differential asks is whether a drag handler reached somewhere it should not.
 */
export const FIXED_ZONES = Object.freeze([
  "ADVENTURE_PATH",   // the path is the road walked, in the order it was walked
  "DISCARD", "DEAD", "REMOVED", "ADVENTURE_DECK", "DECK",
  "ATTACHED",         // an attachment rides its host; it has no row of its own
  "STACKED"
]);

/**
 * @param zone       the card's zone
 * @param spectating a spectator has no hand of their own, and the reference
 *   does not even build its `hand` group for one (gameUi.js:344).
 * @param own        is this card the VIEWER'S? Only HAND cares, and it cares
 *   absolutely: the reference's hand group is `card.owner == bottomPlayerId`
 *   (gameUi.js:346), your own hand and nobody else's. An owner-blind rule said
 *   an opponent's hand card was draggable. It never arises today -- those cards
 *   are not sent to us -- but a latent wrong rule is exactly what a
 *   differential is for, and this one was caught by asking the oracle the
 *   opponent column of a question it had only been asked about the viewer.
 */
export function canReorder(zone, { spectating = false, own = true } = {}) {
  if (zone === "HAND") return own && !spectating;
  return REORDERABLE_ZONES.includes(zone);
}
