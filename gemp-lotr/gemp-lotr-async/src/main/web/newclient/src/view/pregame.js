/**
 * The screen before the game: who is at the table, and what was agreed.
 *
 * `PRE_GAME_SETUP` carries four things (EventSerializer:57-72) and the client
 * was using none of them except the participant list:
 *
 *   summary    the format's own preamble, as HTML
 *   notes      per-player notes, addressed to the seat that receives them
 *   maps       "player:blueprint,player:blueprint" -- a card each player brings
 *   metaSites  handled elsewhere; it changes how cards are DRAWN, not shown here
 *
 * The reference client fixes this into two columns, "you" against everyone
 * else. At five seats that means four names crushed into one heading, so this
 * lists every seat in the table's own order and marks which one is yours --
 * the pre-game is the one screen where seeing the whole table matters.
 *
 * It hides itself the moment the game actually starts. A pre-game panel still
 * up over a live board is worse than never showing one.
 */

import { createFlyout } from "./flyout.js";
import { imageUrl } from "../model/images.js";
import { renderMessage } from "./panels.js";

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

/** "player:blueprint,player:blueprint" -> { player: blueprint } */
export function parseMaps(raw) {
  const out = {};
  if (!raw) return out;
  for (const entry of String(raw).split(",")) {
    const colon = entry.indexOf(":");
    if (colon > 0) out[entry.slice(0, colon)] = entry.slice(colon + 1);
  }
  return out;
}

export function createPreGame(host, store) {
  let dismissed = false;

  const panel = createFlyout(host, {
    id: "pregame",
    title: "Before the game",
    tab: null,
    side: "left",
    width: 520,
    height: 340,
    render: (body, state) => {
      const s = state ?? store.getState();
      const doc = body.ownerDocument;
      body.textContent = "";
      const info = s.preGame;
      if (!info) return;

      if (info.summary) {
        const sum = el(doc, "div", "pregame-summary");
        sum.appendChild(renderMessage(info.summary, doc));
        body.appendChild(sum);
      }
      if (info.notes) {
        const notes = el(doc, "div", "pregame-notes");
        notes.appendChild(renderMessage(info.notes, doc));
        body.appendChild(notes);
      }

      const maps = parseMaps(info.maps);
      const seats = el(doc, "div", "pregame-seats");
      for (const player of s.players) {
        const seat = el(doc, "div", "pregame-seat" + (player === s.viewerId ? " is-you" : ""));
        seat.appendChild(el(doc, "span", "pregame-who",
          player + (player === s.viewerId ? " (you)" : "")));
        const src = imageUrl(maps[player]);
        if (src) {
          const img = doc.createElement("img");
          img.alt = "";
          img.src = src;
          img.addEventListener("error", () => img.remove(), { once: true });
          seat.appendChild(img);
        }
        seats.appendChild(seat);
      }
      if (s.players.length) body.appendChild(seats);
    }
  });

  return {
    ...panel,
    // `isOpen` is a GETTER on the flyout, and spreading copies its VALUE at
    // spread time -- permanently false. Re-expose it, or every caller asking
    // "is this window open?" gets the answer it had before it ever opened.
    get isOpen() { return panel.isOpen; },
    get userSized() { return panel.userSized; },
    /**
     * Show while there is pre-game information and the game has not begun.
     * "Begun" is the first card on the table: the pre-game events arrive before
     * any PUT_CARD_INTO_PLAY, so that is the honest boundary and it needs no
     * extra state to track.
     */
    update(s) {
      const started = Object.keys(s.cards).length > 0;
      if (started || dismissed || !s.preGame) {
        if (panel.isOpen) panel.close();
        return;
      }
      if (!panel.isOpen) panel.open();
      panel.paint(s);
    },
    dismiss() { dismissed = true; panel.close(); }
  };
}
