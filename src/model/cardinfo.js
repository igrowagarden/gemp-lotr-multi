/**
 * When card info is shown, and when the server is asked why.
 *
 * "Why is this card strength 8?" is a question only the server can answer --
 * `GET /game/{id}/cardInfo` returns the modifiers currently acting on a card,
 * and a printed 6 that is currently 8 looks exactly like a printed 8. But not
 * every card CAN be asked about, and the reference is precise about which:
 *
 *     displayCardInfo: function (card) {            // gameUi.js:956-968
 *         var showModifiers = false;
 *         var cardId = card.cardId;
 *         if (!this.replayMode && cardId != "hint"
 *             && (cardId.length < 4 || cardId.substring(0, 4) != "temp"))
 *             showModifiers = true;
 *         this.cardInfoDialog.showCard(card, showModifiers ? "..." : null);
 *         if (showModifiers) this.getCardModifiersFunction(cardId, ...);
 *     }
 *
 * Two separate decisions, and this client had them fused into one:
 *
 * 1. **The card is ALWAYS shown.** There is no id for which the reference
 *    declines to open the dialog. This client refused any non-numeric id
 *    outright, so right-clicking a card in the picker did nothing at all --
 *    not "no modifiers available", nothing. The card's own text is worth
 *    reading even when nothing is modifying it, which is most of the time.
 *
 * 2. **Modifiers are fetched only when there is a server card to ask about.**
 *    Three exclusions, each for its own reason:
 *      - `replay`  a recording has no live game to query.
 *      - `"hint"`  a card named in the game log is identified by BLUEPRINT, not
 *                  by a card in play; there is no instance to have modifiers.
 *      - `temp*`   the picker's cards are described by a decision rather than
 *                  held in state, and the server rejects those ids outright.
 *
 * Asking anyway is not harmless: the engine refuses the id, and a refusal comes
 * back looking like a card with no modifiers, which is a different and wrong
 * answer.
 */

/**
 * @param cardId  the id as the DOM carries it -- a string, possibly "hint" or
 *   "temp3". Numbers are accepted and stringified rather than rejected, because
 *   a caller reading `dataset.cardId` and one reading a decision parameter
 *   disagree about the type and neither is wrong.
 * @returns {{ show: boolean, modifiers: boolean }}
 */
export function cardInfoFor(cardId, { replay = false } = {}) {
  const id = cardId == null ? "" : String(cardId);
  return {
    // Matches the reference: there is no card it declines to show.
    show: id !== "",
    modifiers: !replay && id !== "hint" && !id.startsWith("temp")
  };
}

/** Convenience for the common question, kept so callers do not re-derive it. */
export const wantsModifiers = (cardId, opts) => cardInfoFor(cardId, opts).modifiers;
