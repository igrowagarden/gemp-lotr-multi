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
import { canReorder } from "../model/reorder.js";
import { displayFor, undrawn } from "../layout/bands.js";
import { allCards, fpId, seats, handSize, threats, minionsOf, bandContext,
         clockOf, isWaitingOn, formatClock }
  from "../state/reduce.js";
import { cardActions, isActionChoice } from "../model/actions.js";
import { assignable, isAssignment } from "../model/assign.js";
import { createSelection } from "../model/selection.js";
import { openActionMenu, closeActionMenu, wireActions } from "./actionmenu.js";
import { renderCard } from "./card.js";
import { renderPrompt } from "./prompt.js";
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
  // A host-owned node adopted into the readout row, left of the filters --
  // the connection status used to float over the bottom of the board, and
  // the playtest wanted it in the top bar. Re-appending the SAME node each
  // paint keeps its buttons and their listeners alive.
  const statusNode = options.statusNode ?? null;
  // What the player has picked but not yet sent -- cards and the assignment
  // being built. A small state machine with real rules, so it lives in
  // model/selection.js where it can be tested without a board to click.
  const picked = createSelection();
  // Whether the player collapsed the centered prompt popup. Reset per
  // decision, like `picked`: a collapse is an answer to THIS question's
  // presentation, not a setting.
  const popup = { hidden: false };
  // What the player has typed into the number input, per decision. A repaint
  // rebuilds the input, and repaints arrive constantly in a five-player game;
  // without the draft the typed bid was wiped to the default mid-typing.
  const draft = {};
  // The rendered prompt, kept ACROSS paints while the same decision stands --
  // rebuilding it per store event detaches live controls, and a click whose
  // mousedown/mouseup straddles a repaint never fires ("Accept doesn't work").
  let promptNode = null, promptFor = null, promptWarning = null;
  let lastDecision = null;

  /** Answer and clear, so a stale selection cannot leak into the next decision. */
  function answer(decisionId, value) {
    picked.clear();
    closeActionMenu(root);
    onAnswer?.(decisionId, value);
  }

  function assignClick(cardId, kind) {
    kind === "minion" ? picked.clickMinion(cardId) : picked.clickCompanion(cardId);
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
    // The default focus FOLLOWS the seat under attack -- the Free Peoples
    // player -- so the opponent rows show the active fellowship (the
    // playtest's ruling: "focus the player under attack; that makes more
    // sense"). A deliberate click or arrow clears `following` and the view
    // then stays where the player put it. When the VIEWER holds the Free
    // Peoples role their fellowship is in their own sacred rows, so the
    // focus simply stays wherever it last was.
    if (following) {
      const fp = fpId(state);
      if (fp && fp !== state.viewerId && options_.includes(fp)) {
        focusId = fp;
        return;
      }
    }
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
    if (statusNode) {
      statusNode.style.marginLeft = "auto";
      bar.appendChild(statusNode);
      seg.style.marginLeft = "12px";
    } else {
      seg.style.marginLeft = "auto";
    }
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
        // Your own board is the sacred bottom rows; focusing it would empty
        // the opponent view for nothing.
        if (id === state.viewerId && !state.spectating) return;
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
    // A filtered band hides its cards -- but NOT one carrying an attachment
    // that belongs to somebody else.
    //
    // The filter's job is to put away the side of the FOCUSED SEAT'S OWN cards
    // you are not thinking about. A Shadow condition on an enemy companion is
    // not that seat's card at all; it is yours, sitting on theirs. Hiding the
    // companion took your own card off the screen at exactly the moment you
    // asked to see the shadow side, which is backwards.
    //
    // It cannot be shown on its own either -- a possession means "this, on
    // that", and a condition floating without the companion it afflicts is
    // worse than hidden. So the HOST is kept, and the rest of the band is put
    // away as before.
    let hidden = [];
    if (dim) {
      const carriesForeign = (c) =>
        (attachedBy?.get(c.cardId) ?? []).some((r) => r.owner !== c.owner);
      hidden = cards.filter((c) => !carriesForeign(c));
      cards = cards.filter(carriesForeign);
      if (!cards.length) {
        band.appendChild(el(doc, "span", "band-empty", `${hidden.length} hidden`));
        return band;
      }
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
        const to = picked.pairedTo(card.cardId);
        if (to != null) { node.dataset.assignedTo = String(to); node.classList.add("is-assigned"); }
        if (picked.held === card.cardId) node.classList.add("is-chosen");
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
          if (picked.has(card.cardId)) node.classList.add("is-chosen");
          // Selecting is deliberately UNBOUNDED here; `min`/`max` are enforced
          // where the answer is actually SENT, on the Confirm button below.
          // Bounding the send is what keeps an illegal answer off the wire, and
          // it is the whole fix; capping the click as well would only make a
          // card silently refuse to highlight, with drag-to-reorder riding the
          // same handler. The engine judges the answer, not the highlight.
          node.addEventListener("click", () => {
            picked.toggle(card.cardId);
            paint(store.getState());
          });
        }
      }
      return node;
    };

    for (const card of ordered(spec.id, cards)) {
      const node = drawCard(card);
      // Only where the reference allows it (model/reorder.js). Every card in
      // every band used to get a drag handler, which put one on the adventure
      // path and on attachments -- rows whose order MEANS something and is not
      // the player's to rearrange.
      if (canReorder(card.zone, { spectating: state.spectating,
                                  own: card.owner === state.viewerId })) {
        makeDraggable(node, spec.id, row);
      }

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
    // Say what is still put away, or a band showing one companion under a
    // filter reads as a band with one companion in it.
    if (hidden.length) {
      band.appendChild(el(doc, "span", "band-empty",
        `${hidden.length} hidden — showing what carries another player's card`));
    }
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
    if (!Object.keys(state.assignments ?? {}).length && !picked.pairs.size) return;

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
      picked.clear();
      popup.hidden = false;
      delete draft.value;
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

    // The prompt SURVIVES the wipe while the same decision stands (same
    // decision object, same warning) and its shape is answered from the strip
    // alone. Rebuilding it per store event -- and a five-player game emits
    // events constantly -- detaches live controls: a click whose mousedown and
    // mouseup straddle a repaint never fires, and the number input loses its
    // caret. Selection shapes rebuild every paint because their Confirm label
    // counts `picked`. The kept node is never moved, so focus is undisturbed;
    // only its timer text is refreshed in place.
    const keepPrompt = promptNode != null &&
      promptFor === state.decision &&
      promptWarning === state.warning &&
      (state.decision?.shape === "button" || state.decision?.shape === "number");
    for (const child of [...stack.children]) {
      if (!(keepPrompt && child === promptNode)) child.remove();
    }
    const put = (node) => stack.insertBefore(node, keepPrompt ? promptNode : null);
    put(renderReadout(state));
    put(renderSeats(state));

    for (const spec of displayFor(ctx)) {
      const cards = split.byBand.get(spec.id) ?? [];
      // `collapsed: 0` means "this band has nothing to say when it is empty".
      // Rendering it anyway still costs a label, padding and a border, which is
      // how a spectator ended up with three dead strips at the bottom of the
      // screen for zones they can never have.
      if (!cards.length && !spec.collapsed) continue;
      put(renderBand(spec, cards, state, { ...ctx, registry: spec }, attachedBy));
    }

    if (keepPrompt) {
      const t = promptNode.querySelector(".ptimer");
      const secs = state.clocks[state.viewerId];
      if (t && secs != null) t.textContent = formatClock(secs);
    } else {
      promptNode = renderPrompt(doc, state, picked, onAnswer, { popup, draft });
      promptFor = state.decision ?? null;
      promptWarning = state.warning ?? null;
      stack.appendChild(promptNode);
    }

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
    // The top-centre position chip ("1 / 4") is GONE by playtest ruling; the
    // seat strip already shows which opponent is focused. The skirmish state
    // keeps its one useful message, folded into the poschip slot only then.
    if (skirmishing) {
      root.appendChild(el(doc, "div", "poschip", "skirmish · every seat shown"));
    }

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
      has: (cardId) => picked.has(cardId),
      toggle: (cardId) => {
        picked.toggle(cardId);
        paint(store.getState());
      }
    },
    repaint: () => paint(store.getState()),
    destroy: () => { doc.removeEventListener("keydown", onKey); unsubscribe(); }
  };
}
