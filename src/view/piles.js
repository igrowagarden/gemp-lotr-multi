/**
 * The pile viewer, and card zoom.
 *
 * Piles are reached from the seat that owns them: five seats times four piles is
 * twenty, far too many to give each its own control, and the seat chip already
 * carries the counts.
 *
 * This is the one place a grid beats a row. Board zones never reach the
 * threshold -- a single row of full-height cards wastes nothing until roughly
 * 2*M of them -- but a discard runs past forty against an M near three, so the
 * viewer wraps. See layout/rank.js.
 */

import { createFlyout } from "./flyout.js";
import { pileOf, pileSize } from "../state/reduce.js";
import { renderCard } from "./card.js";
import { imageUrl, CARD_BACK } from "../model/images.js";

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

const PILES = [
  { zone: "DISCARD", label: "Discard" },
  { zone: "DEAD", label: "Dead" },
  { zone: "ADVENTURE_DECK", label: "Adventure" },
  { zone: "REMOVED", label: "Removed" }
];

export function createPileViewer(host, store) {
  let who = null;
  let zone = "DISCARD";

  const panel = createFlyout(host, {
    id: "piles",
    title: "Piles",
    // No docked tab. Every seat chip carries its own ⊞ button, so a permanent
    // tab parked at the edge of the board was a second way in that nothing
    // needed.
    tab: null,
    side: "left",
    width: 300,
    height: 220,
    render: (body, state) => {
      const s = state ?? store.getState();
      const doc = body.ownerDocument;
      body.textContent = "";
      if (!who) {
        body.appendChild(el(doc, "span", "band-empty", "Pick a seat's ⊞ to see its piles."));
        return;
      }

      const tabs = el(doc, "div", "piletabs");
      for (const pile of PILES) {
        const n = pileSize(s, who, pile.zone);
        const b = el(doc, "button", "ptab" + (pile.zone === zone ? " on" : ""),
                     `${pile.label} ${n}`);
        b.type = "button";
        b.addEventListener("click", () => { zone = pile.zone; panel.paint(store.getState()); });
        tabs.appendChild(b);
      }
      body.appendChild(tabs);

      const cards = pileOf(s, who, zone);
      const total = pileSize(s, who, zone);
      const grid = el(doc, "div", "pilegrid");

      if (!total) {
        grid.appendChild(el(doc, "span", "band-empty", "Nothing here yet."));
      } else if (!cards.length) {
        // The count is known but the cards are not: a non-public pile belonging
        // to someone else is never sent to us. Say that, rather than showing an
        // empty pile that looks like a bug.
        grid.appendChild(el(doc, "span", "band-empty",
          `${total} card${total === 1 ? "" : "s"} — face down to you.`));
      } else {
        for (const card of cards) {
          grid.appendChild(renderCard(card, s, { density: "compact", doc }));
        }
      }
      body.appendChild(grid);
      panel.root.querySelector(".flyout-title").textContent = `${who} · piles`;
    }
  });

  return {
    ...panel,
    // `isOpen` is a GETTER on the flyout, and spreading copies its VALUE at
    // spread time -- permanently false. Re-expose it, or every caller asking
    // "is this window open?" gets the answer it had before it ever opened.
    get isOpen() { return panel.isOpen; },
    get userSized() { return panel.userSized; },
    /** Open on a particular seat, or toggle if it is already showing that seat. */
    show(playerId) {
      if (panel.isOpen && who === playerId) return panel.close();
      who = playerId;
      zone = "DISCARD";
      panel.open();
      panel.paint(store.getState());
      return panel;
    }
  };
}

/**
 * Hover preview, anchored to the far side of the board from whatever you are
 * pointing at -- so it is never under the cursor and never jitters the way a
 * cursor-following preview does. Sites preview on their side, the way they sit
 * in the path; an unplayed site has nothing to preview.
 */
export function createZoom(host) {
  const doc = host.ownerDocument;
  const node = el(doc, "div", "zoom");
  const art = doc.createElement("img");
  art.className = "z-art";
  art.alt = "";
  const label = el(doc, "span");
  node.append(art, label);
  host.appendChild(node);

  host.addEventListener("mouseover", (e) => {
    // `.cardhint` is a card named in the game log; it has no art of its own and
    // resolves through the blueprint-id fallback below.
    const card = e.target.closest(".card, .site, .cardhint");
    if (!card || card.classList.contains("pending")) {
      node.classList.remove("on");
      return;
    }
    const isSite = card.classList.contains("site");
    node.classList.toggle("landscape", isSite);

    // Reuse the art already on the card rather than deriving the URL twice; the
    // browser has it cached, so the preview appears without a second request.
    //
    // BOTH class names, because a site's art is `.s-art` and a card's is
    // `.c-art`. Matching only the card's is what left the site path previewing
    // a label and no picture -- a guard that was correct when it was written and
    // rotted the moment sites got art, which is trap 2 in this file over again.
    // Falling back to the blueprint id covers a card whose own <img> was dropped
    // because it failed to load: the preview can still try, and if it also
    // fails the label is what shows.
    const onCard = card.querySelector(".c-art, .s-art");
    const derived = onCard ? null : imageUrl(card.dataset.blueprintId);
    if (onCard || derived) {
      art.src = onCard ? onCard.src : derived;
      art.style.display = "";
      label.textContent = "";
    } else {
      art.removeAttribute("src");
      art.style.display = "none";
      label.textContent = isSite
        ? `Site ${card.querySelector(".snum")?.textContent ?? ""} · ${card.querySelector(".sowner")?.textContent ?? ""}`
        : `card ${card.dataset.cardId ?? ""}`;
    }

    const box = host.getBoundingClientRect();
    const rect = card.getBoundingClientRect();
    const onLeft = rect.left + rect.width / 2 < box.left + box.width / 2;
    node.classList.toggle("at-right", onLeft);
    node.classList.toggle("at-left", !onLeft);
    node.classList.add("on");
  });

  host.addEventListener("mouseleave", () => node.classList.remove("on"));
  return { node, hide: () => node.classList.remove("on") };
}
