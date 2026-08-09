/**
 * Auto-pass: which phases the SERVER skips for you when you have nothing to do.
 *
 * The important thing, and it is not what the name suggests: **auto-pass is a
 * server feature, not a client one.** The engine tests it at three call sites
 * and never sends the decision at all:
 *
 *   PlayerPlaysPhaseActionsUntilPassesGameProcess.java:34
 *   PlayersPlayPhaseActionsInOrderGameProcess.java:56
 *   SkirmishActionProcedureAction.java:70          (SKIRMISH only)
 *
 * each of them `playableActions.isEmpty() && game.shouldAutoPass(playerId,
 * phase)`. So a client cannot "implement auto-pass" by answering faster; the
 * decision it would be answering was never sent. What a client can do is say
 * WHICH phases, and it says it in a **cookie**:
 *
 *   GameRequestHandler.java:243  getAutoPassPhases(request)
 *     - cookie `autoPassPhases`, phase names joined by "0"  -> exactly that set
 *     - else cookie `autoPass=false`                        -> the empty set
 *     - else                                                -> _autoPassDefault
 *
 * and it is re-read on EVERY game request, both the GET handshake
 * (GameRequestHandler.java:225) and every POST poll/answer (:91). There is no
 * separate "save settings" call: changing the cookie takes effect on the next
 * poll.
 *
 * Three things follow, and each of them cost a reading of the server:
 *
 * 1. **We already had auto-pass and did not know it.** With no cookie the
 *    server applies `_autoPassDefault` = FELLOWSHIP, MANEUVER, ARCHERY,
 *    ASSIGNMENT, REGROUP (GameRequestHandler.java:50-54). Every game this
 *    client has ever played ran with those five auto-passing. What was missing
 *    was never the behaviour -- it was the control over it, plus SHADOW and
 *    SKIRMISH, which are not in the default set.
 *
 * 2. **The empty set is a trap.** `""` is a legal cookie value and the server
 *    does `cookie.value().split("0")` then `Phase.valueOf(phase)` on each
 *    (:250-253). `"".split("0")` is `[""]` in Java, and `Phase.valueOf("")`
 *    throws -- so an empty `autoPassPhases` cookie does not mean "pass
 *    nothing", it means every game request fails. "Nothing" has to be said the
 *    other way, with `autoPass=false` and NO `autoPassPhases` cookie at all,
 *    because the phases cookie is checked first and wins.
 *
 * 3. **The path matters and the reference gets it wrong.** `$.cookie` with no
 *    `path` option leaves the browser to default it to the directory of the
 *    page that set it (jquery.cookie.js:30,73). The reference sets it from
 *    `/gemp-lotr/game.html`, so it is stored at `Path=/gemp-lotr` -- and the
 *    API lives at `/gemp-lotr-server/game/{id}`, which does not path-match
 *    (RFC 6265 5.1.4: the prefix has to end at a `/` boundary and "-server"
 *    does not). We write `Path=/` deliberately. This is a divergence from the
 *    oracle ON PURPOSE; see dev/autopasscheck.html, which measures it against
 *    the live endpoint rather than asserting it.
 *
 * The client-side arm -- answering a zero-action CARD_ACTION_CHOICE ourselves,
 * gameUi.js:2477 -- is implemented here too, and is OFF by default. It is off
 * because in the reference it is dead code: `settingsAutoPass` is declared
 * `false` at gameUi.js:84 and assigned nowhere in the tree, so that branch can
 * never be taken. Turning it on by default would make this client answer
 * decisions the reference sits on, which the differential would correctly
 * report as a divergence. It earns its place only for SHADOW and SKIRMISH,
 * where the server will not act for you.
 */

/** The seven the reference offers, in its own order (gameUi.js:729-735). */
export const PHASES = Object.freeze([
  "FELLOWSHIP", "SHADOW", "MANEUVER", "ARCHERY", "ASSIGNMENT", "SKIRMISH", "REGROUP"
]);

/** What the server applies when no cookie is sent (GameRequestHandler.java:50-54). */
export const SERVER_DEFAULT = Object.freeze([
  "FELLOWSHIP", "MANEUVER", "ARCHERY", "ASSIGNMENT", "REGROUP"
]);

/**
 * Not a typo: the phase names are joined by the character "0", not a comma.
 * `Phase` has no name containing a digit, so it is unambiguous.
 */
export const SEPARATOR = "0";

const COOKIE_PHASES = "autoPassPhases";
const COOKIE_ENABLED = "autoPass";
const YEAR_DAYS = 365;

/** Sort into PHASES order and drop anything unknown or repeated. */
function normalise(phases) {
  const wanted = new Set(phases ?? []);
  return PHASES.filter((p) => wanted.has(p));
}

/**
 * Phase names joined the server's way. Order-normalised so the same set always
 * produces the same cookie -- otherwise "did the setting change" is a question
 * about click order.
 */
export function encodePhases(phases) {
  return normalise(phases).join(SEPARATOR);
}

/**
 * The inverse, and deliberately lenient: a cookie can be hand-edited or left
 * over from another client, and an unknown name is dropped rather than thrown
 * on. The SERVER is not lenient about this -- `Phase.valueOf` throws -- which
 * is exactly why we never write a name we did not generate.
 */
export function decodePhases(value) {
  if (value == null || value === "") return [];
  return normalise(String(value).split(SEPARATOR));
}

/**
 * The wire does NOT carry the enum name. `GAME_PHASE_CHANGE` carries
 * `Phase.getHumanReadable()` -- a real capture has `phase="Regroup"`, not
 * `phase="REGROUP"` -- while the cookie the server parses wants `REGROUP`. So
 * state.phase and PHASES are in two different alphabets and comparing them
 * directly always fails.
 *
 * That is the whole bug this function exists to prevent, and it is the kind
 * that reads as working: the client-side arm would simply never fire, which
 * from outside is indistinguishable from "there was nothing to pass".
 *
 * The mapping is the engine's own (`Phase.findPhase`, Phase.java:37-39):
 * upper-case, trim, and turn spaces and hyphens into underscores.
 */
export function phaseName(wirePhase) {
  if (wirePhase == null) return null;
  return String(wirePhase).toUpperCase().trim().replace(/[ -]/g, "_");
}

/** One cookie's raw value out of a `document.cookie` string, or null. */
export function readCookie(name, cookieString) {
  for (const part of String(cookieString ?? "").split(";")) {
    const eq = part.indexOf("=");
    if (eq < 0) continue;
    if (part.slice(0, eq).trim() !== name) continue;
    return decodeURIComponent(part.slice(eq + 1).trim());
  }
  return null;
}

/**
 * What the server will actually apply, given a cookie string. A mirror of
 * `GameRequestHandler.getAutoPassPhases`, including the precedence: the phases
 * cookie is consulted first and wins outright, so `autoPass=false` alongside a
 * phases cookie means the phases, not nothing.
 *
 * Having this as a function rather than as UI state is the point. The settings
 * panel shows what the SERVER will do, not what we last clicked, so a cookie
 * that failed to stick shows up as the panel disagreeing with the click.
 */
export function effectivePhases(cookieString) {
  const raw = readCookie(COOKIE_PHASES, cookieString);
  if (raw != null) return decodePhases(raw);
  if (readCookie(COOKIE_ENABLED, cookieString) === "false") return [];
  return [...SERVER_DEFAULT];
}

/**
 * The `document.cookie` assignments that put `phases` into effect, in order.
 *
 * Returned rather than written so this stays testable without a document and
 * so the caller can see what is about to be set. Always two writes: one clears
 * the representation we are not using, because a stale `autoPassPhases` would
 * outrank a fresh `autoPass=false` at the server.
 *
 * `path` defaults to "/" on purpose -- see the note at the top of this file.
 */
export function cookieWrites(phases, { path = "/", days = YEAR_DAYS, now = null } = {}) {
  const wanted = normalise(phases);
  const expires = new Date((now ?? Date.now()) + days * 86400000).toUTCString();
  const dead = "Thu, 01 Jan 1970 00:00:00 GMT";
  const tail = `; path=${path}`;

  if (wanted.length === 0) {
    // NOT `autoPassPhases=`: an empty value makes Phase.valueOf("") throw on
    // the server. Say "none" with the flag and remove the phases cookie.
    return [
      `${COOKIE_PHASES}=; expires=${dead}${tail}`,
      `${COOKIE_ENABLED}=false; expires=${expires}${tail}`
    ];
  }
  return [
    `${COOKIE_PHASES}=${encodeURIComponent(encodePhases(wanted))}; expires=${expires}${tail}`,
    `${COOKIE_ENABLED}=; expires=${dead}${tail}`
  ];
}

/**
 * Write the cookies and hand back what the server will now apply, read back
 * out of `document.cookie` rather than assumed. A cookie write can silently do
 * nothing -- wrong path, disabled cookies, a `Secure` mismatch -- and reading
 * back is the difference between "we set it" and "it is set".
 */
export function applyPhases(phases, doc = document, options = {}) {
  for (const write of cookieWrites(phases, options)) doc.cookie = write;
  return effectivePhases(doc.cookie);
}

/**
 * The client-side arm, matching gameUi.js:2477 exactly: a CARD_ACTION_CHOICE
 * offering no cards at all, which can only be passed. The reference tests the
 * `cardId` array, so this does too -- `actionId` is parallel to it in every
 * decision the server builds, but the oracle names `cardId` and a reproduction
 * of a behaviour should not quietly pick the other array.
 *
 * `phases` gates it so the client never acts in a phase the user did not ask
 * for, and `enabled` is the reference's own `settingsAutoPass`, off by default.
 * Replay is excluded for the reference's reason: a replay is a recording, and
 * answering during one would be inventing an input that was never made.
 *
 * `phase` is taken as it comes off the wire -- "Regroup" -- and normalised
 * here. See `phaseName`.
 */
export function shouldClientAutoPass(decision, phase, {
  enabled = false, phases = SERVER_DEFAULT, replay = false
} = {}) {
  if (!enabled || replay) return false;
  if (decision?.decisionType !== "CARD_ACTION_CHOICE") return false;
  const cardIds = decision?.parameters?.cardId ?? [];
  if (cardIds.length !== 0) return false;
  return normalise(phases).includes(phaseName(phase));
}

/** The answer that passes a CARD_ACTION_CHOICE (actions.js: `""` passes). */
export const PASS = "";
