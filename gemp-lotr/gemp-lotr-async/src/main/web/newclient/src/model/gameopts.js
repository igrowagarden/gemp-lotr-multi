/**
 * Leaving a game: concede, and asking for a cancel.
 *
 * Both are built in one place in the reference, behind one condition
 * (gameUi.js:791-801):
 *
 *     if (!this.spectatorMode && !this.replayMode) {
 *         $("#gameOptionsBox").append("<button id='concedeGame'>...");
 *         $("#concedeGame").click(() => that.communication.concede());
 *         $("#gameOptionsBox").append("<button id='cancelGame'>...");
 *         $("#cancelGame").click(() => that.communication.cancel());
 *     }
 *
 * So they appear together or not at all, and only for someone actually SEATED
 * in a live game. This client showed Concede unconditionally -- a spectator got
 * a button to concede a game they are not playing, which the server can only
 * refuse. It also had no equivalent of the cancel request at all, though the
 * endpoint has always been there (`POST /game/{id}/cancel`,
 * GameRequestHandler.java:63-64).
 *
 * The difference this client KEEPS, deliberately: the reference concedes on a
 * single click with no confirmation. Conceding is irreversible and ends the
 * game for everyone at the table, and a misclick on a button that sits beside
 * ordinary controls is not a thing to be relaxed about. See DESIGN.md. That is
 * a divergence recorded and defended, not an oversight -- `dev/optsfuzz.html`
 * compares WHETHER each control is offered and WHAT it sends, not how many
 * clicks it takes to get there.
 */

/**
 * @param state  the store's state; `spectating` is set by the reducer from the
 *   PARTICIPANTS event, the same source the reference reads.
 * @param replay a recording has no live game to leave.
 */
export function canConcede(state, { replay = false } = {}) {
  return !replay && !state?.spectating;
}

/** Same condition, same reason: the reference builds both in one branch. */
export const canCancel = canConcede;

/**
 * The two requests, described rather than performed, so the shape can be
 * asserted without a server. Both are POSTs carrying only `participantId`,
 * exactly as `GempLotrCommunication.concede` / `.cancel` build them
 * (communication.js:379-399).
 */
export function concedeRequest(gameId) {
  return { method: "POST", path: `/game/${gameId}/concede`, body: ["participantId"] };
}

export function cancelRequest(gameId) {
  return { method: "POST", path: `/game/${gameId}/cancel`, body: ["participantId"] };
}
