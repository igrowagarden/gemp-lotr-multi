/**
 * The card-info space: which cards can be inspected, and which are worth
 * asking the server about.
 *
 * Fourth application of the method, after decisionshapes, pileshapes and
 * logshapes. The space is tiny and completely closed, which is the reason to
 * enumerate it rather than sample: the reference's whole rule is four lines
 * (gameUi.js:956-968) and every branch of it is reachable from a card id.
 *
 *     showModifiers = !replayMode
 *                     && cardId != "hint"
 *                     && (cardId.length < 4 || cardId.substring(0,4) != "temp")
 *
 * TWO decisions come out of it and they are independent, which is the part this
 * client had wrong -- it fused them and refused to open at all for anything
 * non-numeric:
 *
 *   show       is the dialog opened?   ALWAYS, for every id
 *   modifiers  is the server asked?    only when there is an instance to ask
 *                                      about
 *
 * The `length < 4` clause is not a special case for short ids: it is a guard so
 * `substring(0,4)` cannot mis-read a two-character id. `"abc"` gets modifiers,
 * and the cases below pin that down so a "simplification" to `startsWith`
 * without it stays honest.
 */

export const VIEWER = "asdf";
export const PLAYERS = ["asdf", "qwer", "Librarian"];

/**
 * Every id kind that reaches `displayCardInfo`, with what each represents.
 * `expectShow` / `expectModifiers` are an INDEPENDENT transcription of the rule
 * above -- not a restatement of `model/cardinfo.js`, which is checked against
 * it. Comparing this client's rule with itself is how the INTEGER differential
 * passed for a session while measuring a value against a copy.
 */
export const CASES = [
  { name: "an ordinary card in play", id: "42",
    expectShow: true, expectModifiers: true },

  { name: "a card id with more than one digit", id: "1307",
    expectShow: true, expectModifiers: true },

  // The picker describes its cards in the decision rather than holding them in
  // state, and the engine rejects these ids. Asking anyway returns a refusal
  // that reads exactly like "no modifiers", which is a different answer.
  { name: "a picker card", id: "temp0",
    expectShow: true, expectModifiers: false },

  { name: "a picker card past the first", id: "temp12",
    expectShow: true, expectModifiers: false },

  // A card NAMED IN THE GAME LOG. Identified by blueprint, with no instance in
  // play, so there is nothing for modifiers to act on.
  { name: "a card named in the game log", id: "hint",
    expectShow: true, expectModifiers: false },

  // The `length < 4` guard. "tem" is not a temp id and must still be asked
  // about; a naive startsWith("temp") agrees here, but a substring(0,4) with no
  // length guard would throw or mis-read on some engines.
  { name: "a short id that merely starts like temp", id: "tem",
    expectShow: true, expectModifiers: true },

  // Longer than four and starting with temp, but not one of the picker's --
  // the reference cannot tell them apart and neither should we.
  { name: "an id beginning with temp but longer", id: "temporary",
    expectShow: true, expectModifiers: false }
];

/** The same list under replay, where nothing may be asked of a finished game. */
export const REPLAY_EXPECT = CASES.map((c) => ({
  ...c, expectModifiers: false
}));

export function baseBoard() {
  const seats = PLAYERS.map((p, i) =>
    `<ge type="PP" participantId="${p}" index="${i}"/>`).join("");
  const participants =
    `<ge type="P" participantId="${VIEWER}" ` +
    `allParticipantIds="${PLAYERS.join(",")}" discardPublic="false"/>`;
  const cards = [42, 1307].map((id) =>
    `<ge type="PCIP" cardId="${id}" blueprintId="1_${id}" zone="FREE_CHARACTERS" ` +
    `participantId="${VIEWER}"/>`).join("");
  return `<gameState>${participants}${seats}${cards}</gameState>`;
}
