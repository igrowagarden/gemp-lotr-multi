/**
 * "Why is this card strength 8?"
 *
 * The server answers that, and nothing was asking. `GET /game/{id}/cardInfo`
 * returns the modifiers currently acting on a card -- the one thing a board
 * cannot show, because a printed 6 that is currently 8 looks exactly like a
 * printed 8. The transport already had `cardInfo()`; it had no caller.
 *
 * The response is HTML, and it is rendered through the same `renderMessage`
 * used for the game log: parsed in a detached document, with only text and card
 * hints copied across. Server-authored or not, nothing goes to innerHTML.
 *
 * Opened with a RIGHT CLICK, deliberately. Left click already means "choose
 * this card" whenever a decision is open, and a second meaning for it would
 * make asking a question indistinguishable from answering one.
 */

import { createFlyout } from "./flyout.js";
import { renderMessage } from "./panels.js";

export function createCardInfo(host, { fetchInfo } = {}) {
  let body = null;
  let title = "Card";

  const panel = createFlyout(host, {
    id: "cardinfo",
    title: "Card",
    tab: null,
    side: "left",
    width: 320,
    height: 220,
    render: (node) => {
      const doc = node.ownerDocument;
      node.textContent = "";
      const box = doc.createElement("div");
      box.className = "cardinfo-body";
      if (body == null) box.textContent = "Looking…";
      else if (!body.trim()) box.textContent = "Nothing is modifying this card.";
      else box.appendChild(renderMessage(body, doc));
      node.appendChild(box);
      panel.root.querySelector(".flyout-title").textContent = title;
    }
  });

  host.addEventListener("contextmenu", async (e) => {
    const card = e.target.closest?.(".card[data-card-id]");
    if (!card) return;
    // Only for real cards: "temp" ids belong to the picker and the server
    // rejects them outright (`cardIdStr.startsWith("extra")` and friends).
    const id = card.dataset.cardId;
    if (!id || !/^\d+$/.test(id)) return;
    e.preventDefault();

    title = "Card " + id;
    body = null;
    panel.open();
    panel.paint();
    try {
      body = await fetchInfo(id);
    } catch (err) {
      body = "";
      title = "Card " + id + " — unavailable";
    }
    panel.paint();
  });

  return panel;
}
