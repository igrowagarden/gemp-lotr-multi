/**
 * One answer per decision, enforced at the single place answers leave the
 * client.
 *
 * Why this must exist, measured in the first human five-player playtest
 * (game 1's recording, three `W "Something went wrong"` events): GEMP
 * hardcodes decision ids -- nearly every decision arrives as id=1 -- and the
 * mediator accepts any answer whose id matches the CURRENT decision
 * (LotroGameMediator:423). So a duplicate send (double-click, a held Enter
 * key) is not rejected as stale: it lands on the NEXT decision, where it is
 * either refused ("Something went wrong", the same question asked again) or,
 * worse, silently ACCEPTED -- a duplicate "2" from a burden bid answered the
 * follow-up "choose position" decision as "go third", a prompt the player
 * never saw.
 *
 * The gate keys on the decision OBJECT, not its id, for the same reason
 * view/board.js keys its per-decision reset on the object: ids repeat, but
 * the reducer builds a fresh object per DECISION event -- including for the
 * re-ask after a refusal, so a genuinely refused answer can be retried.
 */
export function createAnswerGate(getDecision, send) {
  let answered = null;
  return (decisionId, value) => {
    const d = getDecision();
    if (!d || d === answered) return false;
    answered = d;
    send(decisionId, value);
    return true;
  };
}
