/**
 * The three events that are pure feedback, and were being thrown away.
 *
 * The reducer drops CARD_AFFECTED_BY_CARD, FLASH_CARD_IN_PLAY and
 * SHOW_CARD_ON_SCREEN as "presentation-only", which is right -- none of them
 * changes the game. But nothing then implemented them, so the client never told
 * you WHY anything happened: a wound appeared and the card that caused it was
 * never named. That is the main thing the game log is for, and the board can say
 * it far more directly.
 *
 * What the server sends (GameCommunicationChannel:232-242):
 *
 *   CARD_AFFECTED_BY_CARD  card + participantId + otherCardIds(affected)
 *   FLASH_CARD_IN_PLAY     card + participantId
 *   SHOW_CARD_ON_SCREEN    card                (usually an event being played,
 *                                               which is nowhere on the board)
 *
 * These are transient by nature, so they are applied to the DOM directly rather
 * than routed through state: a repaint is allowed to wipe them, and the next
 * one will.
 */

import { imageUrl } from "../model/images.js";

const FLASH_MS = 900;
const SHOW_MS = 1600;

export function createEffects(root) {
  const doc = root.ownerDocument;
  const timers = new Set();

  const later = (fn, ms) => {
    const t = setTimeout(() => { timers.delete(t); fn(); }, ms);
    timers.add(t);
  };

  const mark = (cardId, cls, ms) => {
    for (const node of root.querySelectorAll(`.card[data-card-id="${cardId}"]`)) {
      node.classList.add(cls);
      later(() => node.classList.remove(cls), ms);
    }
  };

  /** A card that is not on the board, held up briefly so it can be read. */
  function showOnScreen(blueprintId) {
    const src = imageUrl(blueprintId);
    if (!src) return;
    root.querySelector(".onscreen")?.remove();
    const holder = doc.createElement("div");
    holder.className = "onscreen";
    const img = doc.createElement("img");
    img.alt = "";
    img.src = src;
    holder.appendChild(img);
    root.appendChild(holder);
    later(() => holder.remove(), SHOW_MS);
  }

  return {
    /** Call with the decoded batch, before or after dispatching it. */
    handle(events) {
      for (const e of events ?? []) {
        if (e.type === "FLASH_CARD_IN_PLAY") {
          mark(e.cardId, "is-flash", FLASH_MS);
        } else if (e.type === "CARD_AFFECTED_BY_CARD") {
          // The source and what it reached, marked differently: "this did that"
          // is the whole content of the event.
          mark(e.cardId, "is-source", FLASH_MS);
          for (const id of e.otherCardIds ?? []) mark(id, "is-affected", FLASH_MS);
        } else if (e.type === "SHOW_CARD_ON_SCREEN") {
          showOnScreen(e.blueprintId);
        }
      }
    },

    destroy() {
      for (const t of timers) clearTimeout(t);
      timers.clear();
      root.querySelector(".onscreen")?.remove();
    }
  };
}
