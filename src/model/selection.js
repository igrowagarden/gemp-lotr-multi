/**
 * What the player has picked but not yet sent.
 *
 * Two things, kept together because they are the same idea and are cleared by
 * the same event: the CARDS chosen for a card-shaped decision, and the
 * ASSIGNMENT being built minion by minion.
 *
 * WHY THIS IS NOT IN THE STORE. An unconfirmed selection is not game state.
 * Nobody else's client should see it, it never reaches the server until the
 * answer is sent, and the server itself only emits ADD_ASSIGNMENT once an
 * answer has been ACCEPTED. Putting it in the store would make every other view
 * -- a detached board, a spectator's -- redraw somebody's half-made choice.
 *
 * WHY IT IS NOT IN THE VIEW EITHER, which is where it used to live. It is a
 * small state machine with real rules: clicking an assigned minion takes it
 * back, clicking a held minion twice puts it down, a companion only accepts a
 * minion while one is held. Those are testable without a DOM, and inside
 * `view/board.js` they were reachable only by rendering a board and clicking
 * it.
 */

import { encodeAssignments } from "./assign.js";

export function createSelection() {
  /** Cards chosen for CARD_SELECTION / CARD_ACTION_CHOICE / ARBITRARY_CARDS. */
  const cards = new Set();

  /** companion cardId -> Set(minion cardId), for ASSIGN_MINIONS. */
  const pairs = new Map();

  /** The minion picked up and waiting for a companion, or null. */
  let held = null;

  return {
    // ---------------------------------------------------------------- cards
    has: (cardId) => cards.has(cardId),
    get size() { return cards.size; },
    get cards() { return [...cards]; },
    toggle(cardId) {
      cards.has(cardId) ? cards.delete(cardId) : cards.add(cardId);
      return cards.has(cardId);
    },

    // ----------------------------------------------------------- assignment
    get held() { return held; },
    get pairs() { return pairs; },

    /** Which companion this minion is currently promised to, or null. */
    pairedTo(minionId) {
      for (const [companion, minions] of pairs) {
        if (minions.has(minionId)) return companion;
      }
      return null;
    },

    /**
     * Clicking a MINION. Three cases, and the first is the only way to correct
     * a mistake before confirming:
     *
     *   already assigned -> take it back off its companion
     *   already held     -> put it down
     *   otherwise        -> pick it up
     */
    clickMinion(cardId) {
      const already = this.pairedTo(cardId);
      if (already != null) {
        pairs.get(already).delete(cardId);
        if (!pairs.get(already).size) pairs.delete(already);
        held = null;
        return "unassigned";
      }
      held = held === cardId ? null : cardId;
      return held === cardId ? "held" : "released";
    },

    /**
     * Clicking a COMPANION. Only means anything while a minion is held --
     * otherwise a stray click on a companion would do something invisible.
     */
    clickCompanion(cardId) {
      if (held == null) return "ignored";
      if (!pairs.has(cardId)) pairs.set(cardId, new Set());
      pairs.get(cardId).add(held);
      held = null;
      return "assigned";
    },

    /** How many minions are promised, across every companion. */
    get assignedCount() {
      let n = 0;
      for (const minions of pairs.values()) n += minions.size;
      return n;
    },

    /** The ASSIGN_MINIONS answer, in the engine's own encoding. */
    encode: () => encodeAssignments(pairs),

    /**
     * Everything, at once. Called when an answer is sent and when the decision
     * changes -- a selection left behind leaks into the next decision, which is
     * how a card picked for one question becomes part of the answer to another.
     */
    clear() {
      cards.clear();
      pairs.clear();
      held = null;
    }
  };
}
