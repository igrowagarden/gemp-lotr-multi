/**
 * ASSIGN_MINIONS, unpacked.
 *
 * The assignment decision does not look like the other card decisions and does
 * not answer like them either (PlayerAssignMinionsDecision):
 *
 *  - Its parameters are `freeCharacters` and `minions`. There is **no `cardId`
 *    parameter at all**, which is why a client that builds its eligible set
 *    from `cardId` finds nothing and silently offers no way to assign.
 *  - The answer is GROUPED, not a flat list:
 *
 *        "<fpId> <minionId> <minionId>,<fpId> <minionId>"
 *
 *    comma between groups, space within a group, the first id in each group
 *    being the companion or ally. `""` assigns nothing.
 *  - The engine rejects a companion appearing twice, a minion assigned twice,
 *    and any id not in the list it offered.
 *
 * Every id is a real card id, so both sides are already on the board -- this is
 * the one decision that is genuinely about pairing what you can see.
 */

export const isAssignment = (decision) =>
  decision?.decisionType === "ASSIGN_MINIONS";

export function assignable(decision) {
  const p = decision?.parameters ?? {};
  return {
    freeCharacters: (p.freeCharacters ?? []).map(Number),
    minions: (p.minions ?? []).map(Number)
  };
}

/**
 * @param pairs Map<fpCardId, Set<minionCardId>> | iterable of [fp, minions]
 * @returns the engine's wire format
 */
export function encodeAssignments(pairs) {
  const groups = [];
  for (const [fp, minions] of pairs) {
    const list = [...minions];
    // A companion with nothing assigned is not an assignment. Sending it would
    // be a group of one, which the engine reads as "this companion, no
    // minions" -- harmless but meaningless, and it muddies the answer.
    if (!list.length) continue;
    groups.push([fp, ...list].join(" "));
  }
  return groups.join(",");
}
