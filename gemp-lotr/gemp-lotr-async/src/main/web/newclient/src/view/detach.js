/**
 * A seat's board in its own window.
 *
 * The whole reason one-opponent-at-a-time was chosen is screen space. A second
 * monitor removes that constraint: pin two or three opponents and stop cycling.
 *
 * The rule that governs this: **only one window talks to the server.**
 * LotroGameMediator throws SubscriptionConflictException when a second client
 * claims a player's channel, which reaches the browser as HTTP 409 -- the same
 * error this project hit when a browser and a bot shared an account. A detached
 * board is a second VIEW: it subscribes to the same store and opens no
 * connection of its own. That is why the store exists.
 *
 * WHAT A DETACHED BOARD DELIBERATELY IS NOT. It is a read-only card view: no
 * prompt, no decision handling, no flyouts, no selection. Everything that
 * ANSWERS lives in the main window, and that is not a simplification to be
 * removed later -- two windows offering the same decision would let a player
 * answer twice, and the second answer arrives at a decision the engine has
 * already closed. It also means `view/flyout.js`'s page-wide registry stays
 * correct; see the note there.
 *
 * Proven first in harness/multiwindow_spike.html.
 */

import { renderCard } from "./card.js";
import { createZoom } from "./piles.js";
import { minionsOf, handSize, threats, clockOf, formatClock, pileSize }
  from "../state/reduce.js";

/**
 * playerId -> { win, unsubscribe }. Module-level because a seat may only be
 * detached once: `detach` is idempotent per seat, and closing the parent has to
 * be able to find every child to take them with it.
 */
const open = new Map();

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

/**
 * Styles do not follow a node across documents, so the child needs its own copy
 * of every stylesheet -- both <link> and <style>. Miss this and the panel
 * arrives unstyled, which was the first thing the spike got wrong.
 */
function copyStyles(from, to) {
  for (const node of from.querySelectorAll('link[rel="stylesheet"], style')) {
    to.head.appendChild(node.cloneNode(true));
  }
  const theme = from.documentElement.getAttribute("data-theme");
  if (theme) to.documentElement.setAttribute("data-theme", theme);
}

/**
 * The boundary between two of the window's bands drags to resize them --
 * the same feature the main board's rows have, because the ruling was
 * "adjustable at all times", not "adjustable on the big board". Weights
 * live in the per-window map so they survive every repaint; the move/up
 * listeners are on the CHILD window's document and look the bands up fresh
 * per move, since a store event mid-drag rebuilds everything.
 */
function makeDivider(doc, weights, aboveId, belowId) {
  const divider = el(doc, "div", "banddivider");
  divider.title = "Drag to resize the rows above and below";
  divider.addEventListener("pointerdown", (down) => {
    if (down.button !== 0) return;
    down.preventDefault();
    const find = (id) => doc.querySelector(`#seat .band[data-band="${id}"]`);
    const a = find(aboveId), b = find(belowId);
    if (!a || !b) return;
    const hA = a.getBoundingClientRect().height;
    const hB = b.getBoundingClientRect().height;
    const win = doc.defaultView;
    const pair = parseFloat(win.getComputedStyle(a).flexGrow) +
                 parseFloat(win.getComputedStyle(b).flexGrow);
    if (!(pair > 0) || !(hA + hB > 0)) return;
    doc.getElementById("seat")?.classList.add("is-resizing");
    const move = (e) => {
      const frac = Math.min(0.9, Math.max(0.1,
        (hA + e.clientY - down.clientY) / (hA + hB)));
      const wA = pair * frac;
      weights.set(aboveId, wA);
      weights.set(belowId, pair - wA);
      const na = find(aboveId), nb = find(belowId);
      if (na) na.style.flexGrow = String(wA);
      if (nb) nb.style.flexGrow = String(pair - wA);
    };
    const up = () => {
      doc.getElementById("seat")?.classList.remove("is-resizing");
      doc.removeEventListener("pointermove", move);
      doc.removeEventListener("pointerup", up);
    };
    doc.addEventListener("pointermove", move);
    doc.addEventListener("pointerup", up);
  });
  return divider;
}

function paint(win, playerId, state, weights) {
  const doc = win.document;
  const root = doc.getElementById("seat");
  if (!root) return;
  root.textContent = "";

  const isFp = state.currentPlayer === playerId;
  root.appendChild(el(doc, "div", "d-who",
    `${playerId}${isFp ? " — Free Peoples" : ""}${playerId === state.viewerId ? " — you" : ""}`));

  const vitals = el(doc, "div", "d-vitals");
  vitals.append(
    el(doc, "span", null, `hand ${handSize(state, playerId)}`),
    el(doc, "span", null, `threats ${threats(state, playerId)}`),
    el(doc, "span", null, `discard ${pileSize(state, playerId, "DISCARD")}`)
  );
  const secs = clockOf(state, playerId);
  if (secs != null) vitals.appendChild(el(doc, "span", null, formatClock(secs)));
  root.appendChild(vitals);

  const cards = Object.values(state.cards).filter((c) => c.owner === playerId);
  let prev = null;
  const band = (id, title, list, tone, defaultWeight) => {
    if (prev) root.appendChild(makeDivider(doc, weights, prev, id));
    prev = id;
    const box = el(doc, "div", `band band--${tone}`);
    box.dataset.band = id;
    box.style.flexGrow = String(weights.get(id) ?? defaultWeight);
    box.appendChild(el(doc, "span", "band-label", `${title} · ${list.length}`));
    if (!list.length) {
      box.appendChild(el(doc, "span", "band-empty", "Nothing in play."));
    } else {
      const row = el(doc, "div", "row");
      for (const c of list) row.appendChild(renderCard(c, state, { density: "board", doc }));
      box.appendChild(row);
    }
    root.appendChild(box);
  };

  band("free", "Free characters", cards.filter((c) => c.zone === "FREE_CHARACTERS"), "free", 1);
  band("minions", "Minions", minionsOf(state, playerId), "shadow", 1);
  band("support", "Support area", cards.filter((c) => c.zone === "SUPPORT"), "plain", 0.6);
}

/**
 * @returns { detach, isDetached, closeAll } -- `detach` toggles.
 */
export function createDetacher(store, { onNote = () => {} } = {}) {
  function detach(playerId) {
    const existing = open.get(playerId);
    if (existing && !existing.win.closed) {
      existing.win.close();
      return false;
    }

    let win = null;
    try {
      win = window.open("", "gemp-seat-" + playerId, "width=440,height=620");
    } catch (err) {
      onNote(`window.open threw ${err.name} — popups are blocked here.`);
      return false;
    }
    if (!win) {
      onNote("Popup blocked. Allow popups for this page and try again.");
      return false;
    }

    try {
      win.document.head.innerHTML = `<title>${playerId} — board</title>`;
      copyStyles(document, win.document);
      const extra = win.document.createElement("style");
      extra.textContent =
        "body{margin:0;background:var(--ground);color:var(--ink);font-family:var(--body);position:relative}" +
        "#seat{box-sizing:border-box;height:100vh;padding:10px;display:flex;flex-direction:column;gap:8px}" +
        ".d-who{font-family:var(--mono);font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:var(--brass)}" +
        ".d-vitals{display:flex;gap:10px;font-family:var(--mono);font-size:10px;color:var(--ink-3)}" +
        // Bands size by flex weight, set inline per band so a dragged divider
        // sticks; kill the height transition while a drag is live, exactly as
        // the main board does.
        "#seat.is-resizing .band{transition:none}#seat.is-resizing{cursor:row-resize}";
      win.document.head.appendChild(extra);
      win.document.body.innerHTML = '<div id="seat"></div>';
      // The hover preview, same module the main board uses. Hosted on the
      // BODY, not #seat -- every repaint wipes #seat, and the zoom node has
      // to outlive that or the preview dies on the first store event.
      createZoom(win.document.body);
    } catch (err) {
      // A sandboxed popup can be handed back with an opaque origin: the window
      // exists but its document is unreachable. Say so rather than looking dead.
      onNote(`Popup opened but its document is sandboxed (${err.name}).`);
      try { win.close(); } catch (ignored) {}
      return false;
    }

    // Dragged band heights for THIS window, band id -> flex weight. Lives
    // beside the subscription so it survives every repaint and dies with the
    // window.
    const weights = new Map();
    const unsubscribe = store.subscribe((state) => {
      if (win.closed) return;
      try { paint(win, playerId, state, weights); } catch (ignored) { /* window went away */ }
    });

    // Paint NOW rather than on the next store event -- a detach during a
    // quiet moment used to open a blank window until something happened.
    try { paint(win, playerId, store.getState(), weights); } catch (ignored) {}

    win.addEventListener("pagehide", () => {
      unsubscribe();
      open.delete(playerId);
      onNote(`${playerId}'s window closed.`);
    });

    open.set(playerId, { win, unsubscribe });
    onNote(`${playerId} detached · ${open.size} window(s), still 1 connection`);
    return true;
  }

  // A reload would orphan the children, so take them with us.
  window.addEventListener("beforeunload", () => {
    for (const { win } of open.values()) if (!win.closed) win.close();
  });

  return {
    detach,
    isDetached: (playerId) => open.has(playerId) && !open.get(playerId).win.closed,
    closeAll() {
      for (const { win, unsubscribe } of open.values()) {
        unsubscribe();
        if (!win.closed) win.close();
      }
      open.clear();
    }
  };
}
