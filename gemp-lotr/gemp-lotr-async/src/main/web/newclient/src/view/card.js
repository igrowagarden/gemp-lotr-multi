/**
 * One card, at one of three densities.
 *
 * board   - in play. Stats, wounds, token count, attachment fan.
 * compact - a pile. Art only; at ~57px nothing else is legible, and a stat strip
 *           that cannot be read is noise pretending to be information.
 * zoom    - the hover preview. Everything.
 *
 * Takes state rather than a pre-baked view model, so there is one place that
 * knows how a card is drawn and it always agrees with the store.
 */

import { cardStats, wounds, burdens, cultureTokens } from "../state/reduce.js";
import { imageUrl, CARD_BACK, isMetaSiteModifier, META_OVERLAY_PERCENT, hasErrata }
  from "../model/images.js";

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

/**
 * @param card    the card from state
 * @param state   the whole state, for stats that live on GAME_STATS not the card
 * @param opts    { density, ownerTag, eligible, doc }
 */
export function renderCard(card, state, opts = {}) {
  const { density = "board", ownerTag = false, eligible = false, doc = document } = opts;

  const side = card.zone === "SHADOW_CHARACTERS" ? "s"
             : card.zone === "FREE_CHARACTERS" ? "f"
             : card.zone === "ADVENTURE_PATH" ? "p" : "";

  const node = el(doc, "div", `card card--${density} ${side}`.trim());
  node.dataset.cardId = card.cardId;
  if (card.blueprintId) node.dataset.blueprintId = card.blueprintId;
  // Who this minion has been assigned to fight. The board reads it back off the
  // DOM to line the minion up under its companion; see alignAssignments.
  if (card.assignedTo != null) node.dataset.assignedTo = card.assignedTo;

  // The art sits on top of the tinted placeholder, so a card that fails to load
  // -- offline, or a blueprint whose override table we have not imported --
  // degrades to the coloured rectangle instead of a broken image.
  // A meta-site modifier is drawn as the SITE it modifies, with its own art as
  // a strip across the bottom -- so the board shows where the fellowship stands
  // and what has been done to it at once. Without the map (it arrives on
  // PRE_GAME_SETUP) the card simply draws as itself, which is the old
  // behaviour and not wrong, only less informative.
  const metaVisual = !card.flipped && isMetaSiteModifier(card.blueprintId)
    ? state.metaSites?.[card.blueprintId] ?? null
    : null;

  const src = card.flipped ? CARD_BACK
            : metaVisual ? imageUrl(metaVisual)
            : imageUrl(card.blueprintId);
  if (src) {
    const img = doc.createElement("img");
    img.className = "c-art";
    img.loading = "lazy";
    img.decoding = "async";
    img.alt = "";
    img.src = src;
    img.addEventListener("error", () => img.remove(), { once: true });
    node.appendChild(img);
  }
  if (metaVisual) {
    const strip = doc.createElement("img");
    strip.className = "c-meta";
    strip.alt = "";
    strip.loading = "lazy";
    strip.src = imageUrl(card.blueprintId);
    strip.style.height = META_OVERLAY_PERCENT + "%";
    strip.addEventListener("error", () => strip.remove(), { once: true });
    node.appendChild(strip);
    node.classList.add("is-metasite");
  }
  if (card.flipped) node.classList.add("is-flipped");
  if (eligible) node.classList.add("is-eligible");
  // Marked inactive for this turn by TURN_CHANGE.otherCardIds. The reducer has
  // always tracked these and nothing drew them, so a card that cannot be used
  // looked exactly like one that can.
  if (state.inactive?.includes(card.cardId)) node.classList.add("is-inactive");
  // An errata card's printed text is not the text being played, which matters
  // most to the player who does not own it.
  if (!card.flipped && hasErrata(card.blueprintId)) node.classList.add("has-errata");
  if (card.inSkirmish) node.classList.add("is-skirmishing");

  if (density === "compact" || card.flipped) return node;

  const stats = cardStats(state, card.cardId);
  const w = wounds(card);
  const b = burdens(card);
  const t = cultureTokens(card);

  // There was a spine "fan" here, driven by `card.attachments` -- a field the
  // reducer never set, so it drew nothing, ever. Attachments are now rendered as
  // the actual cards, grouped with their host by the board, which says WHICH
  // card is attached rather than merely how many.

  // Wounds are the loudest thing on the card. A wounded character is what you
  // most need to notice, and it must never blend into culture-token bookkeeping.
  if (w > 0) {
    const marks = el(doc, "span", "c-wounds");
    for (let i = 0; i < Math.min(w, 6); i++) marks.appendChild(el(doc, "span", "c-pip"));
    if (w > 6) marks.appendChild(el(doc, "span", "c-more", `+${w - 6}`));
    node.appendChild(marks);
  }

  if (b > 0) node.appendChild(el(doc, "span", "c-burden", `☉${b}`));
  if (t > 0) node.appendChild(el(doc, "span", "c-token", `◆${t}`));

  // Numbers go where the card already prints them: three icon badges down the
  // left edge. A single "4/3 R10" strip reads as a label stuck on top of the
  // art; these read as part of the card. The geometry is entirely in board.css,
  // transcribed from the reference client -- see the note there, and do not
  // re-derive it by eye.
  if (stats) {
    const badge = (kind, value) => {
      const b = el(doc, "span", `c-stat c-stat--${kind}`, String(value));
      node.appendChild(b);
    };
    badge("strength", stats.strength);
    badge("vitality", stats.vitality);
    if (stats.siteNumber != null) badge("site", stats.siteNumber);
    else if (stats.resistance != null) {
      const b = el(doc, "span", "c-stat c-stat--resistance", String(stats.resistance));
      // The signet travels with the resistance on the wire (RF10 = Frodo, 10),
      // and the reference client colours the badge by it.
      if (stats.signet) b.classList.add("signet-" + stats.signet.toLowerCase());
      node.appendChild(b);
    }
  }

  // Only shared zones say whose the card is. Everywhere else the band already
  // does, so a tag would be noise.
  if (ownerTag && card.owner) {
    const tag = el(doc, "span", "c-owner");
    const seat = state.players.indexOf(card.owner);
    if (seat >= 0) tag.appendChild(el(doc, "b", null, String(seat + 1)));
    tag.appendChild(doc.createTextNode(card.owner === state.viewerId ? "you" : card.owner));
    if (card.owner === state.viewerId) tag.classList.add("is-mine");
    node.appendChild(tag);
  }

  return node;
}
