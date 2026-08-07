/**
 * The zoom space: what hovering something previews, and when it previews
 * nothing.
 *
 * Fifth application of the method. The rule is `AutoZoom.handleMouseOver`
 * (autoZoomHandler.js:283-329) plus the two triggers it dispatches to, and it
 * has more refusals than a hover preview looks like it should:
 *
 *     if (isTouchDevice || !showPreviewImage || (!tarIsHint && !tarIsCard)
 *         || isDragging || infoDialogOpen)          -> no preview
 *
 *     triggerHover:                                  // .actionArea in a .card
 *         card = refCard.data("card"); if (!card) return;
 *         if (!$(base).hasClass('card-animating'))
 *             if (bp !== "-1_1" && bp !== "-1_2")    // NOT the card backs
 *                 displayPreviewImage(card, ...)
 *
 *     triggerHintHover:                              // .cardHint in the log
 *         displayPreviewImage(new Card(target.attr("value"), ...))
 *
 * The one worth pulling out: **a face-down card previews NOTHING.** `-1_1` and
 * `-1_2` are the Free and Shadow card backs, and blowing one up to full size
 * shows a large picture of a card back -- which is not information, and worse,
 * looks briefly like the client leaking a card it should not show.
 *
 * `data("card")` returning null also yields no preview: an element that looks
 * like a card but carries no card is not previewable, and the reference checks
 * rather than assuming.
 */

export const VIEWER = "asdf";
export const PLAYERS = ["asdf", "qwer", "Librarian"];

/** Blueprint ids used below. `-1_1` / `-1_2` are the two card backs. */
export const FREE_BACK = "-1_1";
export const SHADOW_BACK = "-1_2";

/**
 * `expectBlueprint` is what a preview should show, or null for "no preview".
 * An INDEPENDENT transcription of the reference above, not a restatement of
 * this client's rule -- which is checked against it.
 */
export const CASES = [
  { name: "an ordinary card in play", target: "card",
    cardId: 42, blueprintId: "1_42", expectBlueprint: "1_42" },

  { name: "another card, to prove it is not one fixed answer", target: "card",
    cardId: 90, blueprintId: "1_90", expectBlueprint: "1_90" },

  // The finding. A card whose art IS the back must not be blown up.
  { name: "a face-down card, Free Peoples back", target: "card",
    cardId: 7, blueprintId: FREE_BACK, expectBlueprint: null },

  { name: "a face-down card, Shadow back", target: "card",
    cardId: 8, blueprintId: SHADOW_BACK, expectBlueprint: null },

  // A card named in the game log. Identified by blueprint, previewed with no
  // hover delay at all -- triggerHintHover aborts the timer.
  { name: "a card named in the game log", target: "hint",
    blueprintId: "1_340", expectBlueprint: "1_340" },

  { name: "something that is not a card at all", target: "other",
    expectBlueprint: null }
];

/**
 * The suppression states `handleMouseOver` takes as arguments. Each must
 * silence every case above, including the ones that would otherwise preview.
 */
export const STATES = [
  { name: "hovering normally", dragging: false, infoOpen: false, suppressed: false },
  { name: "while click-dragging a card", dragging: true, infoOpen: false, suppressed: true },
  { name: "while the card info dialog is open", dragging: false, infoOpen: true, suppressed: true }
];

export function baseBoard() {
  const seats = PLAYERS.map((p, i) =>
    `<ge type="PP" participantId="${p}" index="${i}"/>`).join("");
  const participants =
    `<ge type="P" participantId="${VIEWER}" ` +
    `allParticipantIds="${PLAYERS.join(",")}" discardPublic="false"/>`;
  const cards = CASES.filter((c) => c.target === "card").map((c) =>
    `<ge type="PCIP" cardId="${c.cardId}" blueprintId="${c.blueprintId}" ` +
    `zone="FREE_CHARACTERS" participantId="${VIEWER}"/>`).join("");
  return `<gameState>${participants}${seats}${cards}</gameState>`;
}
