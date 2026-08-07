/**
 * Which piles a viewer may open, and whose.
 *
 * This lived in `view/piles.js` as a flat list of four zones offered for every
 * seat. That is not what the reference does, and the rule is not a matter of
 * taste -- the server decides what it will even send you, so offering a control
 * that can only ever say "face down to you" is offering a dead end.
 *
 * The reference builds its pile dialogs once, in `participant()`
 * (gameUi.js:1747-1789), and the shape of that function IS the rule:
 *
 *     var index = this.getPlayerIndex(this.bottomPlayerId);
 *     if (index == -1) {                      // not a player: a spectator
 *         this.spectatorMode = true;
 *     } else {
 *         if (!discardPublic)
 *             this.createPile(participantId, "Discard Pile", ...);   // OWN only
 *         this.createPile(participantId, "Adventure Deck", ...);     // OWN only
 *         this.createPile(participantId, "Draw Deck", ...);          // OWN only
 *     }
 *     for (var i = 0; i < this.allPlayerIds.length; i++) {
 *         this.createPile(allPlayerIds[i], "Dead Pile", ...);        // EVERYONE
 *         this.createPile(allPlayerIds[i], "'Removed From Game' Pile", ...);
 *         if (discardPublic)
 *             this.createPile(allPlayerIds[i], "Discard Pile", ...); // EVERYONE
 *     }
 *
 * Three things fall out of it that a flat list gets wrong:
 *
 * 1. **`discardPublic` moves the discard from private to universal**, not from
 *    hidden to shown. When it is set, EVERY player's discard is browsable --
 *    including by a spectator, since the loop is outside the `else`. That is
 *    the open question recorded in HANDOFF ("pile access for spectators should
 *    follow the `discardPublic` flag, not be hidden wholesale"), answered.
 *
 * 2. **Dead and Removed are public for everyone, always**, spectators included.
 *    They are the two zones `zones.js` already marks `isPublic: true`.
 *
 * 3. **The draw deck is a pile.** The reference gives it a dialog ("Draw Deck",
 *    kept in `miscPileDialogs`); this client had no such pile at all, so a
 *    player could not look at their own deck. It is your own only -- nobody
 *    else's deck is ever browsable, and it is not offered to a spectator.
 *
 * A spectator gets Dead and Removed for every seat, and discards too when the
 * format makes them public. Nothing else.
 */

/** Every pile the client knows how to draw, in the order the tabs show them. */
export const PILE_KINDS = Object.freeze([
  { zone: "DISCARD",        label: "Discard" },
  { zone: "DEAD",           label: "Dead" },
  { zone: "REMOVED",        label: "Removed" },
  { zone: "ADVENTURE_DECK", label: "Adventure" },
  { zone: "DECK",           label: "Draw deck" }
]);

/**
 * Is `zone` of `playerId` browsable by this viewer?
 *
 * Written as one predicate per zone rather than as a table, because each maps
 * to a distinct line of `participant()` and a reader checking this against the
 * reference should be able to do it line by line.
 */
export function canOpenPile(state, playerId, zone) {
  const players = state?.players ?? [];
  // The reference's `getPlayerIndex(bottomPlayerId) == -1`. `state.spectating`
  // is already computed that way in reduce.js, from the same PARTICIPANTS
  // event, so this agrees with it by construction rather than by coincidence.
  const spectating = !!state?.spectating;
  const seated = players.includes(playerId);
  const own = !spectating && playerId === state?.viewerId;

  switch (zone) {
    case "DEAD":
    case "REMOVED":
      return seated;
    case "DISCARD":
      return state?.discardPublic ? seated : own;
    case "ADVENTURE_DECK":
    case "DECK":
      return own;
    default:
      return false;
  }
}

/** The piles to draw tabs for, for one seat. Empty means offer no viewer. */
export function pilesFor(state, playerId) {
  return PILE_KINDS.filter((p) => canOpenPile(state, playerId, p.zone));
}

/**
 * Does this seat have anything to open at all? The seat chip's ⊞ should not be
 * offered otherwise -- a button that opens an empty window is the same dead end
 * one tab further out.
 */
export const hasAnyPile = (state, playerId) => pilesFor(state, playerId).length > 0;
