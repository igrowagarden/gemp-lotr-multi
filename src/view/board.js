/**
 * The board: a pure function of state, rendered into a container.
 *
 * It reads the zone registry for what goes where and the band table for how that
 * is presented, so adding a zone is one registry entry rather than four edits in
 * four files. It owns no state -- pass it a store and it subscribes; pass a
 * different window's document and it renders there instead, which is all a
 * detached board is.
 */

import { assignBands, checkComplete, contestedId, focusableSeats } from "../model/zones.js";
import { displayFor, undrawn } from "../layout/bands.js";
import { allCards, fpId, seats, handSize, threats, minionsOf, bandContext,
         clockOf, isWaitingOn, formatClock }
  from "../state/reduce.js";
import { cardActions, isActionChoice } from "../model/actions.js";
import { assignable, isAssignment, encodeAssignments } from "../model/assign.js";
import { openActionMenu, closeActionMenu, wireActions } from "./actionmenu.js";
import { renderCard } from "./card.js";
import { captureRects, playFlip } from "./animate.js";

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

export function createBoard(root, store, options = {}) {
  const doc = root.ownerDocument;
  let focusId = options.focusId ?? null;
  let filter = "auto";
  let following = true;
  let onDefect = options.onDefect ?? (() => {});
  const onAnswer = options.onAnswer ?? null;
  const onPiles = options.onPiles ?? null;
  const onDetach = options.onDetach ?? null;
  // Cards picked for a card-shaped decision, held here rather than in the
  // store: an unconfirmed selection is not game state.
  const selected = new Set();
  // Assignment in progress: companion cardId -> Set(minion cardId), plus the
  // minion waiting for a companion. Also not game state until confirmed -- the
  // server sends ADD_ASSIGNMENT only once the answer is accepted.
  const pairs = new Map();
  let heldMinion = null;
  let lastDecision = null;

  /** Answer and clear, so a stale selection cannot leak into the next decision. */
  function answer(decisionId, value) {
    selected.clear();
    pairs.clear();
    heldMinion = null;
    closeActionMenu(root);
    onAnswer?.(decisionId, value);
  }

  /** Which companion, if any, this minion is currently promised to. */
  function pairedTo(minionId) {
    for (const [fp, minions] of pairs) if (minions.has(minionId)) return fp;
    return null;
  }

  function assignClick(cardId, kind) {
    if (kind === "minion") {
      const already = pairedTo(cardId);
      if (already != null) {
        // Clicking an assigned minion takes it back, which is the only way to
        // correct a mistake before confirming.
        pairs.get(already).delete(cardId);
        if (!pairs.get(already).size) pairs.delete(already);
        heldMinion = null;
      } else {
        heldMinion = heldMinion === cardId ? null : cardId;
      }
    } else if (heldMinion != null) {
      if (!pairs.has(cardId)) pairs.set(cardId, new Set());
      pairs.get(cardId).add(heldMinion);
      heldMinion = null;
    }
    paint(store.getState());
  }

  /**
   * Hand-chosen order within a band, band id -> [cardId, ...].
   *
   * The reference client lets you drag cards sideways within your characters,
   * your support area and your hand, and people use it to group what they are
   * about to play. It is presentation only, so it lives here and never reaches
   * the store: nobody else's client should see how you have arranged yours.
   */
  const orders = new Map();

  /** Cards in the player's order, with anything new left in the server's. */
  function ordered(bandId, cards) {
    const order = orders.get(bandId);
    if (!order) return cards;
    const rank = new Map(order.map((id, i) => [id, i]));
    return [...cards].sort((a, b) =>
      (rank.get(a.cardId) ?? Number.MAX_SAFE_INTEGER) -
      (rank.get(b.cardId) ?? Number.MAX_SAFE_INTEGER));
  }

  /**
   * Dragging a card sideways to reorder it. Deliberately NOT HTML5 drag and
   * drop: that fires no events over a card that is a drop target of its own,
   * and it cannot be cancelled below a threshold -- which is what keeps a
   * click on a card during a decision from becoming a two-pixel reorder.
   */
  const DRAG_THRESHOLD = 6;
  function makeDraggable(node, bandId, row) {
    node.addEventListener("pointerdown", (down) => {
      if (down.button !== 0) return;
      const startX = down.clientX;
      let dragging = false;

      const move = (e) => {
        if (!dragging && Math.abs(e.clientX - startX) < DRAG_THRESHOLD) return;
        if (!dragging) { dragging = true; node.classList.add("is-dragging"); }
        // Where it would land: the first sibling whose midpoint is past us.
        const others = [...row.children].filter((n) => n !== node);
        const after = others.find((n) => {
          const r = n.getBoundingClientRect();
          return e.clientX < r.left + r.width / 2;
        });
        row.insertBefore(node, after ?? null);
      };

      const up = () => {
        node.releasePointerCapture?.(down.pointerId);
        node.removeEventListener("pointermove", move);
        node.removeEventListener("pointerup", up);
        if (!dragging) return;
        node.classList.remove("is-dragging");
        orders.set(bandId, [...row.children].map((n) => Number(n.dataset.cardId)));
        // Swallow the click this drag ends with, or releasing over a card
        // during a decision would also answer it.
        node.addEventListener("click", (e) => e.stopImmediatePropagation(),
                              { capture: true, once: true });
      };

      // Capture keeps the pointer's events coming to this node even when it
      // slides under a sibling. It THROWS for a pointer id the browser does not
      // know, so it must not be allowed to take the listeners down with it --
      // dragging still works without capture, just less smoothly.
      try { node.setPointerCapture?.(down.pointerId); } catch { /* not fatal */ }
      node.addEventListener("pointermove", move);
      node.addEventListener("pointerup", up);
    });
  }

  root.classList.add("board");
  // The board renders into an inner stack, never into `root` itself. Floating
  // panels are hosted on `root`, and a repaint that cleared it would delete
  // them -- which is exactly what happened the first time.
  const stack = el(doc, "div", "board-stack");
  root.appendChild(stack);

  /**
   * Seats the carousel can land on: everyone except the contested seat, which is
   * permanently on screen, and except your own, whose board is already the
   * bottom of the screen. A spectator has no seat of their own, so for them only
   * the contested one is excluded.
   */
  function focusable(state) {
    return focusableSeats(seats(state), bandContext(state, focusId))
      .filter((id) => state.spectating || id !== state.viewerId);
  }

  function ensureFocus(state) {
    const options_ = focusable(state);
    if (!options_.length) { focusId = null; return; }
    // Following the action keeps the selection on the seat about to act; without
    // it a spectator has to chase the turn by hand.
    if (focusId == null || !options_.includes(focusId)) focusId = options_[0];
  }

  /** Walk the carousel. Deliberate movement takes over from auto-follow. */
  function step(delta) {
    const state = store.getState();
    const seatsList = focusable(state);
    if (!seatsList.length) return;
    const at = seatsList.indexOf(focusId);
    focusId = seatsList[((at < 0 ? 0 : at) + delta + seatsList.length) % seatsList.length];
    following = false;
    paint(state);
  }

  function renderReadout(state) {
    const bar = el(doc, "div", "readout");
    const s = state.stats ?? {};
    const twilight = el(doc, "span", "twilight");
    twilight.appendChild(doc.createTextNode("Twilight "));
    twilight.appendChild(el(doc, "b", null, String(state.twilight)));
    bar.appendChild(twilight);
    const add = (text) => {
      bar.appendChild(el(doc, "span", "sep"));
      bar.appendChild(el(doc, "span", null, text));
    };
    if (state.phase) add(state.phase);
    if (s.initiative) add(`Initiative: ${s.initiative}`);
    if (s.moveLimit != null) add(`Move ${s.moveCount ?? 0} / ${s.moveLimit}`);
    if (s.shadowArchery != null) add(`Shadow archery ${s.shadowArchery}`);

    const seg = el(doc, "div", "seg");
    for (const mode of ["auto", "fp", "shadow", "all"]) {
      const b = el(doc, "button", "seg-btn" + (filter === mode ? " on" : ""),
                   mode === "fp" ? "FP" : mode[0].toUpperCase() + mode.slice(1));
      b.type = "button";
      b.addEventListener("click", () => { filter = mode; paint(store.getState()); });
      seg.appendChild(b);
    }
    seg.style.marginLeft = "auto";
    bar.appendChild(seg);
    return bar;
  }

  function renderSeats(state) {
    const strip = el(doc, "div", "seatstrip");
    for (const id of seats(state)) {
      const isFp = id === contestedId(bandContext(state, focusId));
      const chip = el(doc, "div", "seat " + (isFp ? "fp" : "sh"));
      if (id === state.viewerId) chip.classList.add("mine");
      if (id === focusId) chip.classList.add("on");
      if (isWaitingOn(state, id)) chip.classList.add("deciding");
      chip.appendChild(el(doc, "span", "nm",
        id + (id === state.viewerId ? " — you" : isFp ? " — Free Peoples" : "")));
      const vitals = el(doc, "span", "vitals");
      vitals.appendChild(el(doc, "i", null, `minions ${minionsOf(state, id).length}`));
      vitals.appendChild(el(doc, "i", null, `hand ${handSize(state, id)}`));
      vitals.appendChild(el(doc, "i", null, `thr ${threats(state, id)}`));
      const secs = clockOf(state, id);
      if (secs != null) vitals.appendChild(el(doc, "i", "clk", formatClock(secs)));
      chip.appendChild(vitals);
      if (onPiles || onDetach) {
        const btns = el(doc, "span", "seatbtns");
        if (onDetach) {
          const pop = el(doc, "button", "pilebtn", "⧉");
          pop.type = "button";
          pop.title = `Open ${id}'s board in its own window`;
          pop.addEventListener("click", (e) => { e.stopPropagation(); onDetach(id); });
          btns.appendChild(pop);
        }
        if (onPiles) {
          const piles = el(doc, "button", "pilebtn", "⊞");
          piles.type = "button";
          piles.title = `${id}'s piles`;
          piles.addEventListener("click", (e) => { e.stopPropagation(); onPiles(id); });
          btns.appendChild(piles);
        }
        chip.appendChild(btns);
      }
      chip.addEventListener("click", () => {
        if (isFp) return;             // the contested seat is always on screen
        focusId = id; following = false; paint(store.getState());
      });
      strip.appendChild(chip);
    }
    return strip;
  }

  function renderBand(spec, cards, state, ctx, attachedBy) {
    const band = el(doc, "div", `band band--${spec.tone ?? "plain"}`);
    band.dataset.band = spec.id;
    const empty = cards.length === 0;
    band.style.flexGrow = empty ? "0" : String(spec.weight);
    band.style.flexBasis = empty ? `${spec.collapsed}px` : "0";
    if (empty) band.classList.add("is-empty");

    // Only dim a band that actually has something to hide; "0 hidden" over an
    // empty band tells you nothing.
    const dim = !empty && spec.dimWhen ? spec.dimWhen({ ...ctx, filter }) : false;
    if (dim) band.classList.add("is-dim");

    const seatCount = new Set(cards.map((c) => c.owner)).size;
    band.appendChild(el(doc, "span", "band-label",
      spec.label({ ...ctx, filter, filtered: filter !== "all" }, cards.length, seatCount)));

    if (empty && spec.empty) {
      band.appendChild(el(doc, "span", "band-empty", spec.empty));
      return band;
    }
    if (dim) {
      band.appendChild(el(doc, "span", "band-empty", `${cards.length} hidden`));
      return band;
    }

    const row = el(doc, "div", "row");
    const registry = ctx.registry;
    const d = state.decision;
    const mine = !!d && !state.spectating && d.forPlayer === state.viewerId;
    // Board selection is for decisions that name REAL card ids. ARBITRARY_CARDS
    // names "temp0", "temp1", ... for cards that are not on the board at all,
    // so Number() gave a set of NaN that matched nothing -- it looked like a
    // decision with no eligible cards rather than a decision drawn in the wrong
    // place. Those go to the picker instead.
    //
    // CARD_ACTION_CHOICE does name real card ids, but choosing a card is not
    // choosing an answer: the answer is an action index, and one card may carry
    // two actions. It gets its own click behaviour below.
    const actions = mine && isActionChoice(d) ? cardActions(d) : null;
    // ASSIGN_MINIONS carries `freeCharacters` and `minions`, never `cardId` --
    // reading `cardId` here is what left assignment with nothing to click.
    const assign = mine && isAssignment(d) ? assignable(d) : null;
    const onBoard = mine && d.shape === "cards" &&
                    d.decisionType !== "ARBITRARY_CARDS" && !actions && !assign;
    const eligible = actions ? new Set(actions.byCard.keys())
      : assign ? new Set([...assign.freeCharacters, ...assign.minions])
      : new Set(onBoard ? (d.parameters?.cardId ?? []).map(Number) : []);
    /**
     * Draw one card and wire whatever the current decision makes it able to do.
     *
     * Shared by cards standing in the row AND by cards attached to them. An
     * attachment is a perfectly ordinary target -- "Transfer Hobbit Sword" is
     * an action on a possession -- and rendering it without this wiring made
     * every such action unreachable while the answer looked correct. Found by
     * the differential harness against the old client, not by reading this.
     */
    const drawCard = (card) => {
      const node = renderCard(card, state, {
        density: "board",
        ownerTag: registry.ownerTag,
        eligible: eligible.has(card.cardId),
        doc
      });
      // A pairing made but not yet confirmed reuses the same tag and the same
      // alignment as a committed one, so the board looks the way it will look.
      if (assign) {
        const to = pairedTo(card.cardId);
        if (to != null) { node.dataset.assignedTo = String(to); node.classList.add("is-assigned"); }
        if (heldMinion === card.cardId) node.classList.add("is-chosen");
      }

      if (eligible.has(card.cardId)) {
        if (assign) {
          const kind = assign.minions.includes(card.cardId) ? "minion" : "free";
          node.addEventListener("click", () => assignClick(card.cardId, kind));
        } else if (actions) {
          // One action resolves on the click. Several means the card offers a
          // choice of abilities, and picking the card has not yet picked one --
          // that is what the menu is for.
          wireActions(node, actions.byCard.get(card.cardId) ?? [], root,
                      (actionId) => answer(d.id, actionId));
        } else {
          if (selected.has(card.cardId)) node.classList.add("is-chosen");
          // Selecting is deliberately UNBOUNDED here; `min`/`max` are enforced
          // where the answer is actually SENT, on the Confirm button below.
          // Bounding the send is what keeps an illegal answer off the wire, and
          // it is the whole fix; capping the click as well would only make a
          // card silently refuse to highlight, with drag-to-reorder riding the
          // same handler. The engine judges the answer, not the highlight.
          node.addEventListener("click", () => {
            selected.has(card.cardId) ? selected.delete(card.cardId)
                                      : selected.add(card.cardId);
            paint(store.getState());
          });
        }
      }
      return node;
    };

    for (const card of ordered(spec.id, cards)) {
      const node = drawCard(card);
      makeDraggable(node, spec.id, row);

      // Attachments ride on their host rather than standing in the row: a
      // possession is only meaningful as "this, on that". Drawn BEFORE the host
      // and overlapped, so the host paints on top and its attachments peek out
      // to the left -- and each is a real card node, so it zooms and can be
      // right-clicked for its modifiers like any other.
      const riders = attachedBy?.get(card.cardId);
      if (riders?.length) {
        const group = el(doc, "div", "cardgroup");
        for (const rider of riders) {
          group.appendChild(drawCard(rider));
        }
        group.appendChild(node);
        row.appendChild(group);
      } else {
        row.appendChild(node);
      }
    }
    band.appendChild(row);
    return band;
  }

  /**
   * The seven decision types collapse to three interaction shapes, so the strip
   * renders the shape and never switches on the type. Answer formats are the
   * ones the server expects, taken from the reference client's policy:
   *
   *   button (MULTIPLE_CHOICE, ACTION_CHOICE)  -> the option's INDEX
   *   number (INTEGER)                         -> the number
   *   cards  (CARD_SELECTION, CARD_ACTION_...) -> comma-joined card ids,
   *                                               or "" to pass/decline
   *   ARBITRARY_CARDS is the exception: it answers with temp<index>, not ids.
   */
  /**
   * Line each assigned minion up under the companion it will fight.
   *
   * Assignment happens BEFORE the skirmish starts, so the `skirmish` band --
   * which does gather both sides once one is running -- has not claimed anything
   * yet: the minions are still in the shared band and the companions in the
   * contested one. Two bands of different heights hold cards of different
   * widths, so nothing lines up by index and the pairing has to be measured.
   *
   * Two deliberate choices:
   *
   * - The row is REORDERED first, so a minion assigned to the leftmost companion
   *   is not stuck behind one assigned to the rightmost and unable to reach it.
   * - The offset is `left`, not `transform`. FLIP animates transform to "none",
   *   so a transform here would be undone for the length of every animation and
   *   snap back at the end. `.card` is already `position: relative`, so `left`
   *   moves the card, is included in the rect FLIP measures, and composes.
   *
   * Offsets are clamped against the previous card so aligning never stacks two
   * minions on top of each other; a pairing that cannot be reached exactly gets
   * as close as it can, which still reads as "these two are paired".
   */
  function alignAssignments(state) {
    const row = stack.querySelector('[data-band="minions"] .row');
    if (!row) return;
    for (const node of row.children) node.style.left = "";
    // Local pairings count too: while the decision is open the server has sent
    // no ADD_ASSIGNMENT yet, and the whole point is to see the pairing form.
    if (!Object.keys(state.assignments ?? {}).length && !pairs.size) return;

    // Companion centres, from every band except the one we are aligning.
    const centres = new Map();
    for (const node of stack.querySelectorAll(".card[data-card-id]")) {
      if (row.contains(node)) continue;
      const r = node.getBoundingClientRect();
      if (r.width) centres.set(node.dataset.cardId, r.left + r.width / 2);
    }

    const nodes = [...row.children];
    const target = (n) => centres.get(n.dataset.assignedTo ?? "");
    const assigned = nodes.filter((n) => target(n) != null)
                          .sort((a, b) => target(a) - target(b));
    if (!assigned.length) return;
    const rest = nodes.filter((n) => target(n) == null);
    for (const n of [...assigned, ...rest]) row.appendChild(n);

    let prevRight = -Infinity;
    for (const node of assigned) {
      node.classList.add("is-assigned");
      const r = node.getBoundingClientRect();
      const wanted = target(node) - (r.left + r.width / 2);
      const dx = Math.max(wanted, prevRight + 4 - r.left);
      node.style.left = dx.toFixed(1) + "px";
      prevRight = r.left + dx + r.width;
    }
  }

  function renderPrompt(state) {
    const strip = el(doc, "div", "prompt");
    const d = state.decision;
    const yours = !!d && !state.spectating && d.forPlayer === state.viewerId;
    if (yours) strip.classList.add("yours");
    strip.appendChild(el(doc, "span", "pdot"));

    const waiting = state.waitingOn.filter((p) => p !== state.viewerId);
    strip.appendChild(el(doc, "span", "ptext",
      yours ? `You: ${d.text ?? d.decisionType}`
            : waiting.length ? `Waiting for ${waiting.join(", ")}`
            : "Waiting…"));

    if (!yours || !onAnswer) return strip;

    const actions = el(doc, "div", "pactions");
    const send = (value) => onAnswer(d.id, value);
    const p = d.parameters ?? {};

    if (d.shape === "button") {
      const options = p.results ?? p.actionText ?? p.actionId ?? [];
      options.forEach((label, i) => {
        const b = el(doc, "button", "pbtn" + (i === 0 ? " primary" : ""), String(label).slice(0, 28));
        b.type = "button";
        b.addEventListener("click", () => send(String(i)));
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
      input.value = String(start);
      actions.appendChild(input);
      const b = el(doc, "button", "pbtn primary", "Accept");
      b.type = "button";
      b.addEventListener("click", () => send(input.value));
      actions.appendChild(b);
    } else if (isAssignment(d)) {
      const assigned = [...pairs.values()].reduce((n, s) => n + s.size, 0);
      actions.appendChild(el(doc, "span", "pnote",
        heldMinion != null ? "Now choose who it fights"
                           : assigned ? `${assigned} assigned — choose a minion, then a companion`
                                      : "Choose a minion, then the companion it fights"));
      const confirm = el(doc, "button", "pbtn primary",
        assigned ? `Confirm ${assigned}` : "Confirm");
      confirm.type = "button";
      confirm.addEventListener("click", () => send(encodeAssignments(pairs)));
      actions.appendChild(confirm);

      const none = el(doc, "button", "pbtn", "Assign none");
      none.type = "button";
      none.addEventListener("click", () => { pairs.clear(); heldMinion = null; send(""); });
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
        b.addEventListener("click", () => send(action.actionId));
        actions.appendChild(b);
      }
      if (acts.byCard.size) {
        actions.appendChild(el(doc, "span", "pnote",
          `${acts.byCard.size} card${acts.byCard.size === 1 ? "" : "s"} to choose from`));
      }
      const pass = el(doc, "button", "pbtn", "Pass");
      pass.type = "button";
      pass.addEventListener("click", () => { selected.clear(); send(""); });
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
      const chosen = [...selected];
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
        send(chosen.join(","));
        selected.clear();
      });
      actions.appendChild(confirm);

      // Passing is answering with "", which is legal only at `min` 0. Offering
      // it above that is offering a button whose only outcome is a rejection.
      if (lo === 0) {
        const pass = el(doc, "button", "pbtn", "Pass");
        pass.type = "button";
        pass.addEventListener("click", () => { selected.clear(); send(""); });
        actions.appendChild(pass);
      }
    }

    strip.appendChild(actions);
    const seconds = state.clocks[state.viewerId];
    if (seconds != null) strip.appendChild(el(doc, "span", "ptimer", formatClock(seconds)));
    return strip;
  }

  function paint(state) {
    ensureFocus(state);
    // A new decision must not inherit the previous one's half-made pairing.
    //
    // Keyed on the decision OBJECT, not its id: the engine hardcodes decision
    // ids at the call sites -- 22 of them pass 1 -- so consecutive decisions
    // routinely share one, and an id check would carry a stale pairing or
    // selection straight into the next question. The reducer builds a new
    // object per DECISION event, so identity changes exactly when it should.
    if (state.decision !== lastDecision) {
      lastDecision = state.decision ?? null;
      pairs.clear();
      heldMinion = null;
      selected.clear();
    }
    const ctx = { ...bandContext(state, focusId), viewerId: state.viewerId, fpId: fpId(state) };
    const split = assignBands(allCards(state), ctx);

    // `onParent` was computed from the start and never used, so every attached
    // card -- the One Ring included -- was simply not drawn. Group them by host
    // once, rather than scanning per card.
    const attachedBy = new Map();
    for (const rider of split.onParent) {
      const host = rider.attachedTo ?? rider.stackedOn;
      if (host == null) continue;
      if (!attachedBy.has(host)) attachedBy.set(host, []);
      attachedBy.get(host).push(rider);
    }

    // FLIP: measure before the DOM is replaced, animate after. The renderer
    // stays a pure function of state and knows nothing about this.
    const before = captureRects(stack);

    for (const old of root.querySelectorAll(":scope > .navarrow, :scope > .poschip")) old.remove();
    stack.textContent = "";
    stack.appendChild(renderReadout(state));
    stack.appendChild(renderSeats(state));

    for (const spec of displayFor(ctx)) {
      const cards = split.byBand.get(spec.id) ?? [];
      // `collapsed: 0` means "this band has nothing to say when it is empty".
      // Rendering it anyway still costs a label, padding and a border, which is
      // how a spectator ended up with three dead strips at the bottom of the
      // screen for zones they can never have.
      if (!cards.length && !spec.collapsed) continue;
      stack.appendChild(renderBand(spec, cards, state, { ...ctx, registry: spec }, attachedBy));
    }

    stack.appendChild(renderPrompt(state));

    // Edge arrows. Suspended during a skirmish, which draws every seat -- they
    // go visibly dead rather than silently doing nothing.
    const skirmishing = state.skirmish != null;
    for (const [cls, delta, glyph] of [["prev", -1, "‹"], ["next", 1, "›"]]) {
      const arrow = el(doc, "button", "navarrow " + cls, glyph);
      arrow.type = "button";
      arrow.disabled = skirmishing || focusable(state).length < 2;
      arrow.setAttribute("aria-label", delta < 0 ? "Previous seat" : "Next seat");
      arrow.addEventListener("click", () => step(delta));
      root.appendChild(arrow);
    }
    const live = focusable(state);
    const chip = el(doc, "div", "poschip", skirmishing
      ? "skirmish · every seat shown"
      : `${live.indexOf(focusId) + 1} / ${live.length}${state.spectating ? " · spectating" : ""}`);
    root.appendChild(chip);

    // Before FLIP, so the offsets are part of the position it measures.
    alignAssignments(state);

    playFlip(stack, before);

    // Two invariants the old client never had. A card claimed by no band would
    // render at the container's full width; a card claimed by a band this mode
    // does not draw would vanish with nothing to report it.
    const complete = checkComplete(allCards(state), ctx);
    const dropped = undrawn(split.byBand, ctx);
    if (!complete.ok || dropped.length) {
      onDefect(complete.missing, dropped);
    }
  }

  // Left/right walk the seats. Scoped so typing a number into the prompt does
  // not move the board, and skipped while a skirmish suspends the focus.
  function onKey(e) {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
    // `matches` exists on Element, not on Document -- and a keydown with nothing
    // focused can target the document. Calling it unguarded threw here, which
    // killed the handler before it ever stepped, so the keys did nothing while
    // the on-screen arrows worked fine.
    if (typeof e.target?.closest === "function" &&
        e.target.closest("input, textarea, select")) return;
    if (store.getState().skirmish) return;
    e.preventDefault();
    step(e.key === "ArrowRight" ? 1 : -1);
  }
  if (options.keyboard !== false) doc.addEventListener("keydown", onKey);

  const unsubscribe = store.subscribe(paint);

  return {
    focus: (id) => { focusId = id; following = false; paint(store.getState()); },
    next: () => step(1),
    prev: () => step(-1),
    setFilter: (mode) => { filter = mode; paint(store.getState()); },
    get focusId() { return focusId; },
    get following() { return following; },
    /**
     * The current card selection, so surfaces OUTSIDE the bands can join it.
     *
     * Sites are the reason. They are drawn in the path flyout rather than in a
     * band, so a CARD_SELECTION that names a site had nothing to click: the
     * panel lit sites only for CARD_ACTION_CHOICE and a selection decision fell
     * straight through. That is the same shape of bug as attachments and site
     * ACTIONS before it -- a guard written for the one case that existed when it
     * was written -- and the fix is to share the selection rather than to grow a
     * third copy of it.
     */
    selection: {
      has: (cardId) => selected.has(cardId),
      toggle: (cardId) => {
        selected.has(cardId) ? selected.delete(cardId) : selected.add(cardId);
        paint(store.getState());
      }
    },
    repaint: () => paint(store.getState()),
    destroy: () => { doc.removeEventListener("keydown", onKey); unsubscribe(); }
  };
}
