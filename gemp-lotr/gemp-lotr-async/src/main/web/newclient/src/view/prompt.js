/**
 * The decision strip: what the current decision asks, and the controls that
 * answer it from the strip rather than from a card on the board.
 *
 * Extracted from view/board.js, where it was ~157 lines reachable only by
 * painting a whole board -- which is how a ReferenceError in the
 * CARD_SELECTION branch survived sixteen green suites and had to be found by
 * the replay differential (see a79b6a0). The branches encode real rules --
 * which bounds the engine enforces, when Pass is a legal offer, where the
 * answer lives for each shape -- and rules deserve assertions of their own.
 *
 * A pure function: given a document, the state, the in-progress selection and
 * the answer callback, it returns the strip element. It holds nothing between
 * calls; `picked` is owned by the board, because a selection outlives any one
 * paint.
 *
 * `onAnswer` here is the RAW callback, not the board's `answer()` wrapper:
 * strip answers do not close an open action menu, and each branch clears
 * `picked` exactly where the original did. Changing that is changing
 * behaviour, not moving it.
 *
 * THE STRIP IS ALSO THE POPUP. The two shapes answered entirely from the
 * strip -- "button" (mulligan? which response?) and "number" (bid how many
 * burdens?) -- are easy to miss at 24px tall, so when the caller owns a
 * popup state (`opts.popup`, reset per decision like `picked`) the SAME
 * element takes a `pmodal` class and renders front and center. One element,
 * one set of controls: a separate modal would be a second answer surface,
 * which is exactly the ARBITRARY_CARDS mistake this file already refuses.
 * Card-shaped decisions stay on the board, where their answers live.
 */

import { cardActions, isActionChoice } from "../model/actions.js";
import { isAssignment } from "../model/assign.js";
import { formatClock } from "../state/reduce.js";

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

/**
 * Engine wording that names a mechanism instead of an action, retold as what
 * the player can actually DO. "Optional responses" is the engine's term for
 * "a card of yours may trigger now" -- the playtest read it and asked what it
 * meant. Presentation only: the ANSWER format is untouched, and any text not
 * in this table passes through verbatim.
 */
const RETOLD = new Map([
  ["Optional responses",
   "A card of yours may respond — click it to trigger it, or Pass"]
]);

export function renderPrompt(doc, state, picked, onAnswer, opts = {}) {
  const strip = el(doc, "div", "prompt");
  const d = state.decision;
  const yours = !!d && !state.spectating && d.forPlayer === state.viewerId;
  if (yours) strip.classList.add("yours");
  strip.appendChild(el(doc, "span", "pdot"));

  const waiting = state.waitingOn.filter((p) => p !== state.viewerId);
  strip.appendChild(el(doc, "span", "ptext",
    yours ? `You: ${RETOLD.get(d.text) ?? d.text ?? d.decisionType}`
          : waiting.length ? `Waiting for ${waiting.join(", ")}`
          : "Waiting…"));

  if (!yours || !onAnswer) return strip;

  // The engine REFUSED the previous answer and is asking again. Without this
  // line the re-asked decision renders identically to the first ask, and a
  // player who already clicked reads the unchanged board as a frozen game --
  // which is how the first five-player playtest ended in a timeout
  // elimination. The reducer keeps the warning exactly as long as the re-ask
  // it belongs to (see the DECISION case).
  if (state.warning) {
    strip.appendChild(el(doc, "span", "pwarn",
      `⚠ ${state.warning.text} — the answer was refused, please answer again`));
  }

  const actions = el(doc, "div", "pactions");
  /**
   * Send, then say so. `onAnswer` returning exactly `true` means the answer
   * actually left (live.html's gate returns false for a duplicate; test
   * stubs return undefined and are unaffected). The strip is then marked
   * ANSWERED in place -- the node is kept until the next decision arrives,
   * and without this the popup sat fully interactive through the server
   * round-trip: the playtest read that as "not responding", clicked more
   * options (all silently gated), and could not tell which click had won.
   * The chosen control keeps its identity via `pchosen`.
   */
  const send = (value, source) => {
    if (onAnswer(d.id, value) !== true) return;
    strip.classList.add("answered");
    for (const c of strip.querySelectorAll("button, input")) {
      if (!c.classList.contains("pmin")) c.disabled = true;
    }
    source?.classList.add("pchosen");
    actions.appendChild(el(doc, "span", "pnote", "answered — waiting…"));
  };
  const p = d.parameters ?? {};
  // Set by the branches whose answer lives wholly in the strip; drives pmodal.
  let center = false;

  if (d.shape === "button") {
    const options = p.results ?? p.actionText ?? p.actionId ?? [];
    center = options.length > 0;
    options.forEach((label, i) => {
      const b = el(doc, "button", "pbtn" + (i === 0 ? " primary" : ""), String(label).slice(0, 28));
      b.type = "button";
      b.addEventListener("click", () => send(String(i), b));
      actions.appendChild(b);
    });
    if (!options.length) {
      // NO OPTIONS MEANS NO LEGAL ANSWER, so offer no button.
      //
      // This used to render an "OK" that sent "0" -- an index into an empty
      // list, which the engine refuses. A control whose only outcome is a
      // rejection is worse than no control: the player presses it, the answer
      // bounces, and the decision is asked again looking identical. The
      // reference renders nothing here (its loop over `results` simply does
      // not run), so a note is the honest thing to show.
      actions.appendChild(el(doc, "span", "pnote", "No options offered"));
    }
  } else if (d.shape === "number") {
    center = true;
    // `IntegerAwaitingDecision` has THREE constructors, so both `max` and
    // `defaultValue` are optional and each has to be handled as absent.
    const lo = parseInt(p.min?.[0] ?? "0", 10);
    const hasMax = p.max?.[0] != null;
    const hi = hasMax ? parseInt(p.max[0], 10) : lo + 10;
    // START ON THE ENGINE'S SUGGESTION when it gives one. The reference does
    // this (gameUi.js:2079-2081) and we did not, so every decision carrying a
    // default opened on the minimum instead -- "discard how many?" answering 0
    // where the engine proposed 7. The live differential could not see it:
    // its INTEGER answers are computed once and assigned to both sides, so it
    // was comparing a value with itself. `dev/decisionfuzz.html` found it by
    // asking the shape directly.
    const suggested = parseInt(p.defaultValue?.[0] ?? "", 10);
    let start = Number.isNaN(suggested) ? lo : Math.max(suggested, lo);
    // Clamp DOWN to max only when the range is the right way round. On an
    // inverted `min>max` the reference shows `min` and so must we -- clamping
    // regardless turned a 5 into a 2 and invented a disagreement where the
    // first version of this fix had none. The engine should never emit an
    // inverted range; behaving like the oracle when it does is free.
    if (hasMax && hi >= lo) start = Math.min(start, hi);
    const input = doc.createElement("input");
    input.type = "number";
    input.className = "pnum";
    input.min = String(lo);
    input.max = String(hi);
    // WHAT THE PLAYER TYPED OUTLIVES THE PAINT. The strip is rebuilt on every
    // store event -- and a five-player game emits them constantly (each bot
    // message, every clock tick) -- so an input holding only its own value is
    // wiped back to the default mid-typing. That shipped: a player's burden
    // bids "just went to zero" as bots chattered. The draft lives in
    // `opts.draft`, owned by the board and reset per decision like `picked`.
    input.value = opts.draft?.value ?? String(start);
    // A bid that would corrupt the ring-bearer WARNS, live, but stays legal:
    // the first playtest fix was a hard cap, and Galadriel (resistance 3,
    // +1 per spotted Elven companion) refuted it -- over-bidding is a real
    // archetype. The engine sends the printed resistance on the decision.
    const resistance = parseInt(p.ringBearerResistance?.[0] ?? "", 10);
    const corruptWarn = Number.isNaN(resistance) ? null
      : el(doc, "span", "pwarn bidwarn", "");
    const updateCorruptWarn = () => {
      if (!corruptWarn) return;
      const v = parseInt(input.value, 10);
      const bad = !Number.isNaN(v) && v >= resistance;
      corruptWarn.textContent = bad
        ? `⚠ ${v} burdens would CORRUPT your ring-bearer (resistance ${resistance}) — only card effects could save them`
        : "";
      corruptWarn.style.display = bad ? "" : "none";
    };
    input.addEventListener("input", () => {
      if (opts.draft) opts.draft.value = input.value;
      updateCorruptWarn();
    });
    // `b` is declared below; the listener only runs after render completes.
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") send(input.value, b);
    });
    actions.appendChild(input);
    const b = el(doc, "button", "pbtn primary", "Accept");
    b.type = "button";
    b.addEventListener("click", () => send(input.value, b));
    actions.appendChild(b);
    if (corruptWarn) {
      actions.appendChild(corruptWarn);
      updateCorruptWarn();
    }
  } else if (isAssignment(d)) {
    const assigned = picked.assignedCount;
    actions.appendChild(el(doc, "span", "pnote",
      picked.held != null ? "Now choose who it fights"
                         : assigned ? `${assigned} assigned — choose a minion, then a companion`
                                    : "Choose a minion, then the companion it fights"));
    const confirm = el(doc, "button", "pbtn primary",
      assigned ? `Confirm ${assigned}` : "Confirm");
    confirm.type = "button";
    confirm.addEventListener("click", () => send(picked.encode(), confirm));
    actions.appendChild(confirm);

    const none = el(doc, "button", "pbtn", "Assign none");
    none.type = "button";
    none.addEventListener("click", () => { picked.clear(); send("", none); });
    actions.appendChild(none);
  } else if (isActionChoice(d)) {
    // The answer is an action, not a selection, so there is nothing to
    // confirm: a click on the board resolves it. What the strip must carry is
    // Pass, and the actions that have no card on the board to click.
    const acts = cardActions(d);
    for (const action of acts.virtual) {
      const b = el(doc, "button", "pbtn", action.text);
      b.type = "button";
      b.title = "From your discard pile or draw deck";
      b.addEventListener("click", () => send(action.actionId, b));
      actions.appendChild(b);
    }
    // SITE actions get strip buttons too. Sites are drawn in the path window,
    // and auto-opening it whenever a site text lit up was ruled OUT ("the
    // site path popped up when someone else moved... that is a no no") right
    // after the offer's visibility was ruled IN -- a button carries the offer
    // without commandeering a window. Clicking the site in the path works too.
    let boardCount = 0;
    for (const [cid, cardActs] of acts.byCard) {
      if (state.cards?.[cid]?.zone === "ADVENTURE_PATH") {
        for (const action of cardActs) {
          const b = el(doc, "button", "pbtn", action.text);
          b.type = "button";
          b.title = "A site's text";
          b.addEventListener("click", () => send(action.actionId, b));
          actions.appendChild(b);
        }
      } else {
        boardCount++;
      }
    }
    if (boardCount) {
      actions.appendChild(el(doc, "span", "pnote",
        `${boardCount} card${boardCount === 1 ? "" : "s"} to choose from`));
    }
    const pass = el(doc, "button", "pbtn", "Pass");
    pass.type = "button";
    pass.addEventListener("click", () => { picked.clear(); send("", pass); });
    actions.appendChild(pass);
  } else if (d.decisionType === "ARBITRARY_CARDS") {
    // Answered in the picker window, which owns its own buttons -- these
    // cards are not on the board to click. Two sets of controls for one
    // decision is how a player ends up sending the wrong answer.
    actions.appendChild(el(doc, "span", "pnote", "Choose in the card window"));
  } else {
    // Cards are chosen on the board; the strip carries confirm and pass.
    //
    // `min` AND `max` ARE BOTH ENFORCED BY THE ENGINE, so both are enforced
    // here. `CardsSelectionDecision.getSelectedCardsByResponse:40-49` throws
    // `DecisionResultInvalidException` when the count falls outside either
    // bound, and answers the empty string ONLY when `min` is 0. This strip
    // used to ignore both: Confirm sent whatever was selected and Pass sent ""
    // unconditionally, so a `max=0` decision -- zero cards wanted, cards still
    // listed -- could be answered with a card id, and a `min=1` decision could
    // be answered with nothing. Both are rejections the player cannot see the
    // cause of, because a refused answer comes back as the same decision asked
    // again. The reference gets this right by construction: `processButtons`
    // only draws Done at `length >= min` (gameUi.js:2823).
    const chosen = picked.cards;
    const lo = parseInt(p.min?.[0] ?? "0", 10);
    const hi = p.max?.[0] != null ? parseInt(p.max[0], 10) : Infinity;
    const legal = chosen.length >= lo && chosen.length <= hi;

    const confirm = el(doc, "button", "pbtn primary",
      chosen.length ? `Confirm ${chosen.length}` : "Confirm");
    confirm.type = "button";
    confirm.disabled = !legal;
    if (!legal) confirm.title = `Choose ${lo === hi ? lo : `${lo} to ${hi}`}`;
    confirm.addEventListener("click", () => {
      if (!legal) return;
      send(chosen.join(","), confirm);
      picked.clear();
    });
    actions.appendChild(confirm);

    // Passing is answering with "", which is legal only at `min` 0. Offering
    // it above that is offering a button whose only outcome is a rejection.
    if (lo === 0) {
      const pass = el(doc, "button", "pbtn", "Pass");
      pass.type = "button";
      pass.addEventListener("click", () => { picked.clear(); send("", pass); });
      actions.appendChild(pass);
    }
  }

  strip.appendChild(actions);

  // The popup form. `opts.popup` is owned by the caller, like `picked`,
  // because whether the player collapsed it must outlive any one paint --
  // the toggle flips the class in place AND records the choice, so the next
  // paint of the same decision agrees with what is on screen.
  if (center && opts.popup) {
    if (!opts.popup.hidden) strip.classList.add("pmodal");
    const toggle = el(doc, "button", "pmin", opts.popup.hidden ? "expand" : "minimize");
    toggle.type = "button";
    toggle.title = opts.popup.hidden ? "Restore the centered prompt" : "Collapse to the strip";
    toggle.addEventListener("click", () => {
      opts.popup.hidden = !opts.popup.hidden;
      strip.classList.toggle("pmodal", !opts.popup.hidden);
      toggle.textContent = opts.popup.hidden ? "expand" : "minimize";
      toggle.title = opts.popup.hidden ? "Restore the centered prompt" : "Collapse to the strip";
    });
    strip.appendChild(toggle);
  }

  const seconds = state.clocks[state.viewerId];
  if (seconds != null) {
    const timer = el(doc, "span", "ptimer", formatClock(seconds));
    strip.appendChild(timer);
    // While a decision waits on the viewer the transport HOLDS: no events, no
    // repaints -- so the clock froze at exactly the moment it started to
    // matter. Tick it down locally. The countdown state lives in
    // `data-left`, which the board's keepPrompt refresh OVERWRITES whenever
    // real events deliver the server's clock, so the local tick only ever
    // covers the gap and server truth re-syncs it. The interval clears
    // itself when the node leaves the document (next decision, next paint
    // shape) rather than trusting anyone to remember it.
    if (state.decision && !state.spectating &&
        state.decision.forPlayer === state.viewerId) {
      timer.dataset.left = String(seconds);
      const every = opts.setInterval ?? ((fn, ms) => setInterval(fn, ms));
      const stop = opts.clearInterval ?? ((id) => clearInterval(id));
      const id = every(() => {
        if (!timer.isConnected) { stop(id); return; }
        const left = Math.max(0, (parseInt(timer.dataset.left, 10) || 0) - 1);
        timer.dataset.left = String(left);
        timer.textContent = formatClock(left);
      }, 1000);
    }
  }
  return strip;
}
