/**
 * The pile space, enumerated from the reference's source rather than sampled.
 *
 * Same method as `decisionshapes.js`, applied to the first of the untested
 * surfaces. The space is small and closed, which is the whole argument for
 * enumerating it: four viewer configurations times five pile kinds times two
 * target seats is forty cells, and forty cells can be checked exhaustively
 * where a game state space cannot be sampled meaningfully at all.
 *
 * The axes come from `gameUi.js:1747-1789` (`participant()`), which is the only
 * place the reference decides what a pile dialog exists for:
 *
 *   role           player | spectator     `getPlayerIndex(bottomPlayerId) == -1`
 *   discardPublic  true | false           an attribute on the PARTICIPANTS event
 *   pile           5 kinds                one createPile call each
 *   target seat    own | another          the loop runs over allPlayerIds
 *
 * Role and `discardPublic` are fixed at PARTICIPANTS time and the reference
 * builds every dialog once from them, so they cannot be varied within a page:
 * `boot()` may only be called once (see decisionfuzz.html for what happens
 * otherwise). They are therefore CONFIGURATIONS, selected by `?config=`, and
 * `harness/pilerun.sh` runs all four.
 */

export const PLAYERS = ["asdf", "qwer", "Librarian"];
export const VIEWER = "asdf";           // seat 0, when the viewer is a player
export const OTHER = "qwer";            // seat 1, always someone else
export const SPECTATOR = "watcher";     // deliberately not in PLAYERS

/**
 * The five pile kinds, each with the reference's dialog map that holds it.
 *
 * The map name IS the observable: `createPile(playerId, name, dialogsName, ...)`
 * writes `this[dialogsName][playerId]`, so "does the reference offer this pile
 * for this seat" is exactly "is that key present". Reading the map beats
 * hunting for a clickable element, because the click handlers are wired by
 * index in `initializeGameUI` and a missing dialog and an unwired handler are
 * different failures that should not be conflated.
 *
 * `DECK` is the wire's name for the draw deck; the reference files its dialog
 * under `miscPileDialogs`, which is not a name anybody would guess.
 */
export const PILES = [
  { zone: "DISCARD",        oldMap: "discardPileDialogs" },
  { zone: "DEAD",           oldMap: "deadPileDialogs" },
  { zone: "REMOVED",        oldMap: "removedPileDialogs" },
  { zone: "ADVENTURE_DECK", oldMap: "adventureDeckDialogs" },
  { zone: "DECK",           oldMap: "miscPileDialogs" }
];

export const CONFIGS = {
  "player-private":    { viewer: VIEWER,    discardPublic: false },
  "player-public":     { viewer: VIEWER,    discardPublic: true },
  "spectator-private": { viewer: SPECTATOR, discardPublic: false },
  "spectator-public":  { viewer: SPECTATOR, discardPublic: true }
};

/**
 * What the REFERENCE does, derived by reading `participant()` line by line.
 *
 * This is not the new client's rule restated -- that would make the comparison
 * check a thing against itself, which is how the INTEGER differential passed
 * for a session while comparing a value to a copy of itself. It is an
 * independent transcription of the oracle, and `model/piles.js` is checked
 * AGAINST it.
 */
export function referenceOffers(config, target) {
  const { viewer, discardPublic } = config;
  const spectating = !PLAYERS.includes(viewer);
  const seated = PLAYERS.includes(target);
  const own = !spectating && target === viewer;

  return PILES.filter(({ zone }) => {
    switch (zone) {
      // The loop over allPlayerIds is outside the player/spectator branch.
      case "DEAD":
      case "REMOVED":
        return seated;
      // Inside the loop when public; inside the `else` and own-only when not.
      case "DISCARD":
        return discardPublic ? seated : own;
      // Only in the `else`, and only for `participantId` itself.
      case "ADVENTURE_DECK":
      case "DECK":
        return own;
      default:
        return false;
    }
  }).map((p) => p.zone);
}

/** Seats a case asks about: the viewer's own if they have one, and another. */
export function targetsFor(config) {
  const seats = [config.viewer, OTHER].filter((t, i, a) => a.indexOf(t) === i);
  // A spectator has no seat of their own, so ask about both real seats instead
  // -- otherwise the spectator configs would only ever test one column.
  return PLAYERS.includes(config.viewer) ? seats : [PLAYERS[0], OTHER];
}

const put = (cardId, zone, owner) =>
  `<ge type="PCIP" cardId="${cardId}" blueprintId="1_${cardId}" zone="${zone}" participantId="${owner}"/>`;

/**
 * A board with something in every pile of every seat.
 *
 * Contents matter as much as reachability: a client that offers the right pile
 * and lists the wrong cards is wrong in a way a reachability-only comparison
 * cannot see. That was the exact gap that let `CARD_SELECTION` pass while the
 * sites were never lit -- offers-only comparison.
 */
export function baseBoard(config) {
  const seats = PLAYERS.map((p, i) =>
    `<ge type="PP" participantId="${p}" index="${i}"/>`).join("");

  const participants =
    `<ge type="P" participantId="${config.viewer}" ` +
    `allParticipantIds="${PLAYERS.join(",")}" ` +
    `discardPublic="${config.discardPublic}"/>`;

  // Two cards per pile per seat, with ids encoding where they belong so a
  // mismatch names itself instead of being a bare number.
  let id = 100;
  const cards = [];
  for (const p of PLAYERS) {
    for (const { zone } of PILES) {
      for (let n = 0; n < 2; n++) cards.push(put(id++, zone, p));
    }
    // A companion apiece, so the board is not otherwise empty and the old
    // client has a real game to lay out rather than a bare table.
    cards.push(put(id++, "FREE_CHARACTERS", p));
  }

  return `<gameState>${participants}${seats}${cards.join("")}</gameState>`;
}
