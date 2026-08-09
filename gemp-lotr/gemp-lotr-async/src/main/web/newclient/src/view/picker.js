/**
 * The card picker: choosing among cards the client has never been sent.
 *
 * ARBITRARY_CARDS is how the engine asks about cards that are not on the board
 * and never will be -- an opponent's hand, a draw deck, a discard pile, a
 * reveal. Because the client cannot look them up, the decision DESCRIBES them,
 * and the mechanics of that are specific enough to be worth writing down
 * (ArbitraryCardsSelectionDecision.java, ChooseArbitraryCardsEffect.java):
 *
 *  - `cardId` is ALREADY "temp0", "temp1", ... -- not real card ids. The answer
 *    is those same strings, comma-joined, and the engine reads the index back
 *    out with `Integer.parseInt(cardId.substring(4))`. There is no id lookup to
 *    do at either end: answer with the values you were given.
 *  - `blueprintId` is a parallel array, and is the only way to draw the cards.
 *  - `selectable` is a parallel array of "true"/"false". SHOWN IS NOT
 *    SELECTABLE: "look at an opponent's hand and discard a Shadow card" shows
 *    the whole hand and lets you pick only part of it. The engine rejects a
 *    response naming an unselectable card outright.
 *  - `min`/`max` bound the count, and the engine throws
 *    DecisionResultInvalidException outside them. An empty answer is legal only
 *    when min is 0.
 *
 * Two shapes fall out of that, and both are real:
 *
 *  - min 0, max 0, nothing selectable -- "look at an opponent's hand". Purely
 *    informational, but it STILL has to be answered with "" or the game waits
 *    for ever on a player who thinks they were just shown something.
 *  - min..max with some subset selectable -- an actual choice.
 *
 * A decision only arrives when there is something to decide: the engine
 * auto-resolves when max is 0 or when exactly `minimum` cards match, so the
 * picker never has to handle "one candidate, no real choice".
 */

import { imageUrl } from "../model/images.js";
import { createFlyout } from "./flyout.js";

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

/** The decision's parallel arrays, zipped into something a view can loop over. */
export function pickable(decision) {
  const p = decision?.parameters ?? {};
  const ids = p.cardId ?? [];
  const blueprints = p.blueprintId ?? [];
  const selectable = p.selectable ?? [];
  return ids.map((id, i) => ({
    id,                                   // already "temp<i>"
    blueprintId: blueprints[i] ?? null,
    // Absent `selectable` means everything shown can be chosen; that is the
    // two-argument constructor, which passes the same collection twice.
    selectable: selectable.length ? selectable[i] === "true" : true
  }));
}

export const boundsOf = (decision) => {
  const p = decision?.parameters ?? {};
  const n = (v, fallback) => {
    const parsed = parseInt(v?.[0] ?? "", 10);
    return Number.isNaN(parsed) ? fallback : parsed;
  };
  return { min: n(p.min, 0), max: n(p.max, 0) };
};

export function createPicker(host, { onAnswer } = {}) {
  let current = null;              // the decision being shown
  const chosen = new Set();        // temp ids

  const panel = createFlyout(host, {
    id: "picker",
    title: "Choose",
    tab: null,                     // opened by a decision, not by the player
    side: "left",
    width: 460,
    height: 300,
    render: (body) => {
      const doc = body.ownerDocument;
      body.textContent = "";
      if (!current) return;

      const cards = pickable(current);
      const { min, max } = boundsOf(current);

      const grid = el(doc, "div", "pickgrid");
      for (const card of cards) {
        const node = el(doc, "div", "card card--compact pick");
        node.dataset.cardId = card.id;
        if (card.blueprintId) node.dataset.blueprintId = card.blueprintId;
        const src = imageUrl(card.blueprintId);
        if (src) {
          const img = doc.createElement("img");
          img.className = "c-art";
          img.loading = "lazy";
          img.alt = "";
          img.src = src;
          img.addEventListener("error", () => img.remove(), { once: true });
          node.appendChild(img);
        }
        if (!card.selectable) node.classList.add("is-locked");
        else {
          node.classList.add("is-pickable");
          if (chosen.has(card.id)) node.classList.add("is-chosen");
          node.addEventListener("click", () => {
            if (chosen.has(card.id)) chosen.delete(card.id);
            // Choosing past the maximum would be rejected by the engine, so the
            // limit is enforced here rather than discovered as an error.
            else if (chosen.size < max) chosen.add(card.id);
            panel.paint();
          });
        }
        grid.appendChild(node);
      }
      body.appendChild(grid);

      const bar = el(doc, "div", "pickbar");
      const count = cards.filter((c) => c.selectable).length;

      if (max === 0 || count === 0) {
        // Informational: there is nothing to choose, but the engine is still
        // waiting. Answering "" is what releases it.
        bar.appendChild(el(doc, "span", "picknote",
          `${cards.length} card${cards.length === 1 ? "" : "s"}`));
        const done = el(doc, "button", "pbtn primary", "Done");
        done.type = "button";
        done.addEventListener("click", () => answer(""));
        bar.appendChild(done);
      } else {
        bar.appendChild(el(doc, "span", "picknote",
          min === max ? `choose ${min}`
                      : `choose ${min}–${max}` + (count < cards.length ? ` of ${count}` : "")));
        const confirm = el(doc, "button", "pbtn primary",
          chosen.size ? `Confirm ${chosen.size}` : "Confirm");
        confirm.type = "button";
        confirm.disabled = chosen.size < min;
        confirm.addEventListener("click", () => answer([...chosen].join(",")));
        bar.appendChild(confirm);

        // Only offered when the engine would accept it. Below the minimum an
        // empty answer is thrown out, and the prompt would come straight back.
        if (min === 0) {
          const pass = el(doc, "button", "pbtn", "None");
          pass.type = "button";
          pass.addEventListener("click", () => answer(""));
          bar.appendChild(pass);
        }
      }
      body.appendChild(bar);
      panel.root.querySelector(".flyout-title").textContent = current.text || "Choose";
    }
  });

  function answer(value) {
    const id = current?.id;
    current = null;
    chosen.clear();
    panel.close();
    if (id != null) onAnswer?.(id, value);
  }

  return {
    ...panel,
    // `isOpen` is a GETTER on the flyout, and spreading copies its VALUE at
    // spread time -- permanently false. Re-expose it, or every caller asking
    // "is this window open?" gets the answer it had before it ever opened.
    get isOpen() { return panel.isOpen; },
    get userSized() { return panel.userSized; },
    get decision() { return current; },

    /**
     * Show a decision, or leave an already-shown one alone. Compared by
     * IDENTITY: the engine hardcodes decision ids (22 call sites pass 1), so
     * an id check would treat the next question as the one already answered
     * and refuse to show it.
     */
    show(decision) {
      if (current === decision) return;
      current = decision;
      chosen.clear();
      panel.open();
      panel.paint();
    },

    /** The decision went away (answered elsewhere, or the game moved on). */
    hide() {
      if (!current) return;
      current = null;
      chosen.clear();
      panel.close();
    }
  };
}
