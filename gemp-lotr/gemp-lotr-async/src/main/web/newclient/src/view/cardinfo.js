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
import { cardInfoFor } from "../model/cardinfo.js";

/**
 * @param replay  a recording has no live game to ask for modifiers. The card is
 *   still shown -- the reference shows it and skips only the query.
 */
export function createCardInfo(host, { fetchInfo, replay = false } = {}) {
  let body = null;
  let title = "Card";
  let asked = false;                   // was the server queried for this card?

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
      // Three states, not two. "Nothing is modifying this card" is a claim
      // about the game; for a card we never asked about it would be a lie, and
      // it is exactly the answer a rejected id used to produce.
      if (!asked) box.textContent = "No modifiers to look up for this card.";
      else if (body == null) box.textContent = "Looking…";
      else if (!body.trim()) box.textContent = "Nothing is modifying this card.";
      else box.appendChild(renderMessage(body, doc));
      node.appendChild(box);
      panel.root.querySelector(".flyout-title").textContent = title;
    }
  });

  /**
   * Open on a card. `id` is the id as the DOM carries it, so "temp3" and
   * "hint" reach here rather than being filtered out before -- deciding what to
   * do with them is `model/cardinfo.js`'s job, and it was previously done here
   * by a `/^\d+$/` test that simply declined to open at all.
   */
  async function show(id, label) {
    const { show: showIt, modifiers } = cardInfoFor(id, { replay });
    if (!showIt) return;

    title = label ?? ("Card " + id);
    asked = modifiers;
    body = null;
    panel.open();
    panel.paint();
    if (!modifiers) return;            // shown, but there is nothing to ask

    try {
      body = await fetchInfo(id);
    } catch (err) {
      body = "";
      title = title + " — unavailable";
    }
    panel.paint();
  }

  host.addEventListener("contextmenu", (e) => {
    const card = e.target.closest?.(".card[data-card-id]");
    if (!card) return;
    const id = card.dataset.cardId;
    if (!id) return;
    e.preventDefault();
    show(id);
  });

  /**
   * A card named in the GAME LOG is inspectable too. The reference opens card
   * info when one is clicked (gameUi.js:808-814), building a Card from the
   * blueprint id with cardId "hint" -- so it shows the card and asks for no
   * modifiers, there being no instance in play to have any. This client drew
   * the hints, previewed them on hover, and had no way to open them.
   *
   * Left click, not right: a hint is not a decision target, so the objection
   * that made right-click the choice for board cards does not apply here.
   */
  host.addEventListener("click", (e) => {
    const hint = e.target.closest?.(".cardhint[data-blueprint-id]");
    if (!hint) return;
    e.preventDefault();
    show("hint", hint.textContent || "Card");
  });

  return { ...panel, get isOpen() { return panel.isOpen; }, show };
}
