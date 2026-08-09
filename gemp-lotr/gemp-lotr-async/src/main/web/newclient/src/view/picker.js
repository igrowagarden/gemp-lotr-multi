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
  const chosen = new Set();        // temp ids, in click order
  // BATCH PICKS OVER A ONE-AT-A-TIME WIRE. The starting fellowship is asked
  // one character per decision (each play reprices the rest against the
  // twilight budget), but the playtest wants to check off several and
  // Confirm once. So for the pick-one-or-pass shape the picker lets several
  // be chosen; Confirm sends the FIRST and queues the rest by blueprintId,
  // and each re-ask of the SAME question auto-answers the next queued pick
  // that is still selectable. A queued pick the budget has since priced out
  // is dropped, and the window reappears only when the queue is spent --
  // reality always wins over the plan.
  let queue = [];                  // blueprintIds still to play
  let queueForText = null;         // the question the queue belongs to

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

      // LIVE BUDGET, when the decision carries one. The starting fellowship
      // sends each companion's current twilight cost and the unspent budget
      // (engine: PlayerPlaysStartingFellowshipGameProcess), so the picker can
      // grey out what the growing selection prices out BEFORE anything is
      // sent -- the playtest checked off four companions the budget could
      // never fit and watched the overflow silently drop. The engine's
      // selectable flags stay authoritative; this only tightens further.
      const costRaw = current.parameters?.twilightCost ?? [];
      const budgetRaw = parseInt(current.parameters?.budgetRemaining?.[0] ?? "", 10);
      const liveBudget = min === 0 && max === 1 &&
        !Number.isNaN(budgetRaw) && costRaw.length === cards.length;
      const costOf = new Map(cards.map((c, i) => [c.id, parseInt(costRaw[i] ?? "0", 10) || 0]));
      const spent = [...chosen].reduce((sum, id) => sum + (costOf.get(id) ?? 0), 0);
      const left = budgetRaw - spent;

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
        const pricedOut = liveBudget && !chosen.has(card.id) && (costOf.get(card.id) ?? 0) > left;
        if (!card.selectable || pricedOut) node.classList.add("is-locked");
        else {
          node.classList.add("is-pickable");
          if (chosen.has(card.id)) node.classList.add("is-chosen");
          node.addEventListener("click", () => {
            // Every pick goes through Confirm, min 0 / max 1 included. A
            // click-commits shortcut shipped here once and was REVOKED by the
            // playtest within one game: "I didn't press confirm and it just
            // went." The starting fellowship plays a character per answer and
            // the last affordable pick auto-advances the phase, so an
            // unconfirmed click could spend the player's whole budget before
            // they meant to have decided anything.
            if (chosen.has(card.id)) chosen.delete(card.id);
            // The pick-one-or-pass shape may choose SEVERAL -- Confirm plays
            // them one per re-ask (see the queue above). Everything else is
            // capped here: choosing past the maximum would only be rejected
            // by the engine, so the limit is enforced rather than discovered.
            else if (chosen.size < max || (min === 0 && max === 1)) chosen.add(card.id);
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
        const queueable = min === 0 && max === 1;
        bar.appendChild(el(doc, "span", "picknote",
          liveBudget ? `pick any number · they play in click order · ${left} twilight left`
                     : queueable ? "pick any number · they play in click order"
                     : min === max ? `choose ${min}`
                     : `choose ${min}–${max}` + (count < cards.length ? ` of ${count}` : "")));
        const confirm = el(doc, "button", "pbtn primary",
          chosen.size ? `Confirm ${chosen.size}` : "Confirm");
        confirm.type = "button";
        confirm.disabled = chosen.size < min;
        confirm.addEventListener("click", () => {
          const picks = [...chosen];
          if (queueable && picks.length > 1) {
            // Send the first now; the rest replay across the re-asks of this
            // same question, by blueprintId -- temp ids are per-decision.
            const byId = new Map(pickable(current).map((c) => [c.id, c.blueprintId]));
            queue = picks.slice(1).map((id) => byId.get(id)).filter(Boolean);
            queueForText = current.text ?? "";
            answer(picks[0]);
            return;
          }
          answer(picks.join(","));
        });
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

      // A re-ask of the question the queue belongs to: play the next queued
      // pick that is STILL selectable, without reopening the window. One
      // priced out since it was chosen is dropped -- the engine's greyed
      // list is the truth, the queue is only a plan. A different question
      // abandons the queue outright.
      if (queue.length && (decision.text ?? "") === queueForText) {
        const cards = pickable(decision);
        while (queue.length) {
          const bp = queue.shift();
          const hit = cards.find((c) => c.selectable && c.blueprintId === bp);
          if (!hit) continue;
          const id = decision.id;
          current = null;
          panel.close();
          onAnswer?.(id, hit.id);
          return;
        }
        // Queue spent with nothing playable: fall through and show the board.
        queueForText = null;
      } else if (queue.length) {
        queue = [];
        queueForText = null;
      }

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
