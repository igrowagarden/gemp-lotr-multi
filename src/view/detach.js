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
 * Proven first in harness/multiwindow_spike.html.
 */

import { renderCard } from "./card.js";
import { minionsOf, handSize, threats, clockOf, formatClock, pileSize }
  from "../state/reduce.js";

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

function paint(win, playerId, state) {
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
  const band = (title, list, tone) => {
    const box = el(doc, "div", `band band--${tone}`);
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

  band("Free characters", cards.filter((c) => c.zone === "FREE_CHARACTERS"), "free");
  band("Minions", minionsOf(state, playerId), "shadow");
  band("Support area", cards.filter((c) => c.zone === "SUPPORT"), "plain");
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
        "body{margin:0;background:var(--ground);color:var(--ink);font-family:var(--body)}" +
        "#seat{box-sizing:border-box;height:100vh;padding:10px;display:flex;flex-direction:column;gap:8px}" +
        ".d-who{font-family:var(--mono);font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:var(--brass)}" +
        ".d-vitals{display:flex;gap:10px;font-family:var(--mono);font-size:10px;color:var(--ink-3)}" +
        "#seat .band{flex:1}#seat .band:last-child{flex:.6}";
      win.document.head.appendChild(extra);
      win.document.body.innerHTML = '<div id="seat"></div>';
    } catch (err) {
      // A sandboxed popup can be handed back with an opaque origin: the window
      // exists but its document is unreachable. Say so rather than looking dead.
      onNote(`Popup opened but its document is sandboxed (${err.name}).`);
      try { win.close(); } catch (ignored) {}
      return false;
    }

    const unsubscribe = store.subscribe((state) => {
      if (win.closed) return;
      try { paint(win, playerId, state); } catch (ignored) { /* window went away */ }
    });

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
