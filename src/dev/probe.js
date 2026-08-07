/**
 * What the NEW client offers for a decision, in the same shape the old client's
 * harness reports.
 *
 * Two levels, and both matter:
 *
 *   contract    what the decision's own parameters say is answerable. Catches
 *               "we send the wrong kind of value" -- the CARD_ACTION_CHOICE and
 *               ASSIGN_MINIONS class of bug.
 *   reachable   what a player could actually click on the rendered board.
 *               Catches "the answer is right but the card is in no band", which
 *               a contract-only check cannot see.
 *
 * The old client is the oracle for both: it is what the games were played
 * through. Where they disagree, this client is wrong until shown otherwise.
 */

import { cardActions, isActionChoice } from "../model/actions.js";
import { assignable, isAssignment } from "../model/assign.js";
import { pickable, boundsOf } from "../view/picker.js";

/** Answers the decision itself says are possible, sorted for comparison. */
export function contractOffers(decision) {
  if (!decision) return { shape: null, answers: [] };
  const p = decision.parameters ?? {};

  if (isActionChoice(decision)) {
    const acts = cardActions(decision);
    const all = [...acts.byCard.values()].flat().concat(acts.virtual);
    return {
      shape: "cardAction",
      answers: all.map((a) => String(a.actionId)).sort(),
      detail: all.map((a) => ({ cardId: String(a.cardId), actionId: String(a.actionId),
                                text: a.text, virtual: a.blueprintId !== "inPlay" }))
    };
  }

  if (isAssignment(decision)) {
    const a = assignable(decision);
    return {
      shape: "assign",
      // Not an answer list: assignment answers are combinations. What is
      // comparable is which cards each client believes are in play for it.
      answers: [],
      detail: { freeCharacters: a.freeCharacters.map(String).sort(),
                minions: a.minions.map(String).sort() }
    };
  }

  if (decision.decisionType === "ARBITRARY_CARDS") {
    const cards = pickable(decision);
    return {
      shape: "arbitrary",
      answers: cards.filter((c) => c.selectable).map((c) => c.id).sort(),
      detail: { shown: cards.length, bounds: boundsOf(decision) }
    };
  }

  if (decision.decisionType === "CARD_SELECTION") {
    return { shape: "cardSelection", answers: (p.cardId ?? []).map(String).sort() };
  }

  // INTEGER / MULTIPLE_CHOICE / ACTION_CHOICE: the answer is an index.
  const options = p.results ?? p.actionText ?? p.text ?? [];
  return {
    shape: "button",
    answers: options.map((_, i) => String(i)),
    detail: options.slice()
  };
}

/**
 * What is actually clickable on the rendered board, read off the DOM exactly as
 * the old harness reads its own. A card that is eligible but drawn in no band is
 * not an option, however correct the contract is.
 */
export function reachableOffers(root, decision) {
  const out = { shape: null, ids: [] };
  if (!decision) return out;

  // Sites are `.site`, not `.card`, and live in the path window -- but a site
  // with an ability is an ordinary card-action target, so it counts as reachable.
  const lit = [
    ...root.querySelectorAll(".card.is-eligible[data-card-id]"),
    ...root.querySelectorAll(".site.is-eligible[data-card-id]")
  ].map((n) => n.dataset.cardId);

  if (isActionChoice(decision)) {
    out.shape = "cardAction";
    out.ids = lit.sort();
    // Virtual actions have no card; they are offered as prompt buttons.
    out.virtual = [...root.querySelectorAll(".prompt .pbtn")]
      .map((b) => b.textContent)
      .filter((t) => t !== "Pass" && !t.startsWith("Confirm"));
    return out;
  }

  if (isAssignment(decision)) {
    out.shape = "assign";
    out.ids = lit.sort();
    return out;
  }

  if (decision.decisionType === "ARBITRARY_CARDS") {
    out.shape = "arbitrary";
    // The picker is a separate window, not the board.
    out.ids = [...root.querySelectorAll(".pickgrid .card.is-pickable")]
      .map((n) => n.dataset.cardId).sort();
    return out;
  }

  if (decision.decisionType === "CARD_SELECTION") {
    out.shape = "cardSelection";
    out.ids = lit.sort();
    return out;
  }

  out.shape = "button";
  out.ids = [...root.querySelectorAll(".prompt .pbtn")].map((b) => b.textContent);
  return out;
}
