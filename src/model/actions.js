/**
 * CARD_ACTION_CHOICE, unpacked.
 *
 * This is the commonest decision in the game -- every "Play Shadow action or
 * Pass" -- and its contract is not the one a card decision looks like:
 *
 *  - The answer is a single **actionId**, and the engine reads it with
 *    `Integer.parseInt(result)` as an index into its own action list
 *    (CardActionSelectionDecision.getSelectedAction). Answering with card ids
 *    is not merely rejected: a card id smaller than the number of actions
 *    parses fine and silently performs a DIFFERENT action.
 *  - `""` passes.
 *  - `actionId`, `cardId`, `blueprintId` and `actionText` are parallel arrays,
 *    and **several actions can share one cardId** -- one card offering two
 *    abilities. Picking a card is therefore not always picking an action.
 *  - `blueprintId` is the string `"inPlay"` when the action's source is a card
 *    on the board. Anything else is a **virtual card action**: the source is in
 *    a discard pile or a draw deck (ActivatePhaseActionsFromDiscardRule,
 *    ActivatePhaseActionsFromDrawDeckRule) or is a trigger added by another
 *    card (AddTrigger). Those have no board node at all and need their own way
 *    in, or they are simply unreachable.
 */

export const IN_PLAY = "inPlay";

/**
 * @returns {{ byCard: Map<number, Array>, virtual: Array, total: number }}
 *   byCard  - board cards, keyed by numeric cardId, each with its actions
 *   virtual - actions whose source is not on the board, in decision order
 */
export function cardActions(decision) {
  const p = decision?.parameters ?? {};
  const ids = p.actionId ?? [];
  const cards = p.cardId ?? [];
  const blueprints = p.blueprintId ?? [];
  const texts = p.actionText ?? [];

  const byCard = new Map();
  const virtual = [];

  for (let i = 0; i < ids.length; i++) {
    const entry = {
      actionId: ids[i],
      text: texts[i] ?? "Action",
      cardId: cards[i],
      blueprintId: blueprints[i]
    };
    if (blueprints[i] === IN_PLAY || blueprints[i] == null) {
      const key = Number(cards[i]);
      if (!byCard.has(key)) byCard.set(key, []);
      byCard.get(key).push(entry);
    } else {
      virtual.push(entry);
    }
  }
  return { byCard, virtual, total: ids.length };
}

/** Is this a decision whose answer is an action index rather than card ids? */
export const isActionChoice = (decision) =>
  decision?.decisionType === "CARD_ACTION_CHOICE";
