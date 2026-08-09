/**
 * What the client DOES as state arrives — the reactions, in one place.
 *
 * This lived in `live.html`'s `store.subscribe` body, which is how it came to be
 * the least-tested code in the project: nothing imports an HTML file, so none of
 * the seven differentials or thirteen suites could reach it. It is also the code
 * that decides what a player actually sees, which is the worst combination there
 * is. Extracting it is not tidying; it moves eight behavioural rules inside the
 * safety net.
 *
 * The rules, each a named predicate below so it can be asserted on its own:
 *
 *   1  the picker opens for ARBITRARY_CARDS addressed to us, and only then
 *   2  the client auto-pass arm fires, at most once per decision
 *   3  concede and cancel are offered only to someone seated
 *   4  unread counts PEOPLE talking, never the game log
 *   5  a decision raises an alert only if it is ours
 *   6  the site path opens itself when the fellowship reaches a new site
 *   7  flyouts yield only to what actually wants attention
 *   8  the pre-game panel shows until the first card is played
 *
 * WHAT THIS MODULE IS NOT. It owns no game state — the store does. It owns the
 * small amount of SESSION state that is not game state and never reaches the
 * server: which decision the arm has already answered, how many chat lines have
 * been seen, which site the path last opened for. Those belong to this browser
 * tab and to nothing else, which is exactly why they are not in the store.
 */

import { spokenTo, currentSite } from "../state/reduce.js";
import { shouldClientAutoPass, effectivePhases, PASS } from "../model/autopass.js";
import { canConcede, canCancel } from "../model/gameopts.js";
import { allFlyouts } from "./flyout.js";

/**
 * Is this decision ours to answer?
 *
 * Three conditions that are always asked together, and were written out three
 * times in the original. A spectator is never addressed, but the server has
 * already decided that by not sending them one -- this agrees with the server
 * rather than second-guessing it.
 */
export const isMine = (state) =>
  !!state.decision && !state.spectating && state.decision.forPlayer === state.viewerId;

/**
 * Cards the picker draws are described BY THE DECISION, not held in state --
 * they are named "temp0", "temp1" and have no board node. That is the whole
 * reason the picker exists, and why only ARBITRARY_CARDS opens it.
 */
export const wantsPicker = (state) =>
  isMine(state) && state.decision.decisionType === "ARBITRARY_CARDS";

/** The decision an alert should fire for, or null. */
export const alertFor = (state) => (isMine(state) ? state.decision : null);

/**
 * @param store       the game store; this subscribes to it.
 * @param transport   only for the auto-pass arm's answer. Nothing else here
 *                    talks to the server.
 * @param boardEl     read for the rectangles flyouts must yield to.
 * @param panels      { path, chat, piles, picker, alerts, pregame }
 * @param settings    the auto-pass panel, read for `clientArm`.
 * @param buttons     { concede, cancel } — hidden unless seated.
 * @param onNote      a line for the status bar. Optional.
 * @param cookies     injectable for tests; defaults to the live document's.
 */
export function createSession({
  store, transport, boardEl, panels, settings,
  buttons = {}, onNote = () => {}, cookies = () => document.cookie
}) {
  const { path, chat, piles, picker, alerts, pregame } = panels;

  // Session state: this tab's, never the server's. See the note at the top.
  let autoPassed = null;   // the decision OBJECT the arm answered, not its id
  let lastChat = 0;
  let lastSite = null;
  let seenFirstSite = false;

  /**
   * The arm, off unless armed. Guarded by the decision OBJECT rather than its
   * id: ids are not unique -- 22 engine call sites pass `1` -- so remembering
   * "we answered id 1" would sit out the next unrelated decision that also
   * arrived as 1. The reducer builds a fresh object per DECISION event and
   * every other event spreads the old state, so identity means exactly "this
   * decision, still the one we answered".
   */
  function maybeAutoPass(s) {
    if (!settings?.clientArm || !isMine(s) || s.decision === autoPassed) return false;
    if (!shouldClientAutoPass(s.decision, s.phase,
                              { enabled: true, phases: effectivePhases(cookies()) })) {
      return false;
    }
    autoPassed = s.decision;
    transport.answer(s.decision.id, PASS);
    onNote(`auto-passed ${s.decision.id} in ${s.phase}`);
    return true;
  }

  /**
   * The path opens itself when the fellowship reaches a NEW site -- but not for
   * the first one, which arrives with the opening state and would pop a window
   * over the board before the game has begun.
   */
  function maybeOpenPath(s) {
    const here = s.stats ? currentSite(s) : null;
    if (!here || here.siteNumber === lastSite) return false;
    lastSite = here.siteNumber;
    const opened = lastSite != null && !path.isOpen && seenFirstSite;
    if (opened) path.open();
    seenFirstSite = true;
    return opened;
  }

  /**
   * Unread counts PEOPLE talking. The game log is a running commentary on every
   * action, so counting it left the badge permanently lit and telling you
   * nothing -- a real message could not stand out.
   */
  function countUnread(s) {
    const said = spokenTo(s).length;
    if (said <= lastChat) return 0;
    const fresh = said - lastChat;
    lastChat = said;
    chat.notify(fresh);
    return fresh;
  }

  /**
   * A window fades only while it actually covers something wanting attention --
   * your own prompt, or a card on the board. Never merely because the pointer
   * left it.
   */
  function yieldFlyouts() {
    const hot = [];
    const prompt = boardEl.querySelector(".prompt.yours");
    if (prompt) hot.push(prompt.getBoundingClientRect());
    for (const c of boardEl.querySelectorAll(".band .card")) {
      hot.push(c.getBoundingClientRect());
    }
    for (const f of allFlyouts()) f.yieldTo(hot);
  }

  function react(s) {
    path.paint(s);
    chat.paint(s);
    piles.paint(s);

    wantsPicker(s) ? picker.show(s.decision) : picker.hide();
    maybeAutoPass(s);

    if (buttons.concede) buttons.concede.hidden = !canConcede(s);
    if (buttons.cancel) buttons.cancel.hidden = !canCancel(s);

    countUnread(s);
    alerts.update(alertFor(s));
    pregame.update(s);
    maybeOpenPath(s);
    yieldFlyouts();
  }

  const unsubscribe = store.subscribe(react);

  // `react` is returned so a test can drive one state through without a store,
  // and the internals are exposed for the same reason -- each is a rule with
  // its own failure mode, and asserting them only through `react` would make a
  // failure say "something in the session is wrong".
  return { react, unsubscribe, maybeAutoPass, maybeOpenPath, countUnread,
           get autoPassed() { return autoPassed; } };
}
