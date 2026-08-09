/**
 * How the bands are presented, as data.
 *
 * The old client computed layout imperatively from magic index arrays --
 * `heightScales = [5,9,9,10,6,10]` fed into `yScales`, then two hundred lines of
 * setBounds arithmetic. Adding a band there means editing that arithmetic. Here
 * a band is a row in a table, and the board is generated from it.
 *
 * `weight` is a flex-grow share, not a pixel height. `collapsed` is what a band
 * shrinks to when it holds nothing, so it keeps its position and its label --
 * you always know where minions live even in the fellowship phase, when none can
 * exist yet.
 */

import { BANDS } from "../model/zones.js";

export const DISPLAY = Object.freeze([
  {
    id: "focusSupport", weight: 5, collapsed: 18,
    label: (ctx) => `${ctx.focusId} · support area${ctx.filtered ? " · unfiltered" : ""}`,
    tone: "shadow"
  },
  {
    id: "focusFree", weight: 10, collapsed: 18,
    label: (ctx) => `${ctx.focusId} · free characters`,
    tone: "free",
    // Auto shows a seat's fellowship only while they hold the Free Peoples role.
    dimWhen: (ctx) => ctx.filter === "auto" ? ctx.focusId !== ctx.fpId
                    : ctx.filter === "shadow"
  },
  {
    id: "minions", weight: 14, collapsed: 20, ownerTag: true,
    label: (ctx, n, seats) =>
      n ? `Minions in play · ${n} from ${seats} seats · never filtered`
        : "Minions in play",
    empty: "No minions in play — none can be until the shadow phase.",
    tone: "shadow"
  },
  {
    id: "contested", weight: 12, collapsed: 18,
    label: (ctx) =>
      ctx.fpId === ctx.viewerId
        ? "Your fellowship · under attack"
        : `${ctx.fpId} · fellowship under attack`,
    tone: "free"
  },
  {
    id: "contestedSupport", weight: 5, collapsed: 0,
    label: (ctx) => `${ctx.fpId} · support area`,
    tone: "free"
  },
  {
    id: "selfFree", weight: 9, collapsed: 0,
    label: () => "Your free characters",
    tone: "free", mine: true
  },
  {
    id: "selfSupport", weight: 5, collapsed: 0,
    label: () => "Your support area",
    tone: "free", mine: true
  },
  {
    id: "hand", weight: 11, collapsed: 0,
    label: (ctx, n) => `Your hand · ${n}`,
    tone: "mine", mine: true
  }
]);

/**
 * While a skirmish runs it takes the top of the board and most of the height,
 * but it does NOT replace the rest. Dropping the other bands would hide the
 * unassigned minions and the rest of the fellowship -- cards the registry still
 * claims, so nothing would report them missing. They would simply be gone.
 */
export const SKIRMISH_DISPLAY = Object.freeze([
  {
    id: "skirmish", weight: 26, collapsed: 20, ownerTag: true,
    label: (ctx, n, seats) => `Skirmish · ${n} cards from ${seats} seats · focus suspended`,
    tone: "shared"
  },
  ...DISPLAY.map((b) => ({ ...b, weight: Math.max(3, Math.round(b.weight * 0.6)) }))
]);

const KNOWN = new Set(BANDS.map((b) => b.id));

/**
 * Every display band must name a real band in the registry. Static, cheap, and
 * it stops a typo silently rendering nothing.
 *
 * The stronger invariant -- that no band holding cards goes undrawn -- cannot be
 * checked statically, because a band legitimately appears in only one display
 * list (the skirmish band exists only while a skirmish runs). It is checked
 * against real data at paint time instead; see `undrawn` below.
 */
export function validate() {
  const bad = [...DISPLAY, ...SKIRMISH_DISPLAY]
    .map((b) => b.id)
    .filter((id) => !KNOWN.has(id));
  return { ok: bad.length === 0, unknown: [...new Set(bad)] };
}

/**
 * Bands that hold cards but are not in the current display list. Non-empty means
 * cards have been claimed by the registry and then quietly dropped by the
 * presentation layer -- the same drift the old client shipped, one layer up.
 * The path is excluded: it renders in its own floating window, not in the stack.
 */
export function undrawn(byBand, ctx) {
  const shown = new Set(displayFor(ctx).map((b) => b.id));
  const missed = [];
  for (const [id, cards] of byBand) {
    if (id === "path" || shown.has(id) || cards.length === 0) continue;
    missed.push({ band: id, count: cards.length });
  }
  return missed;
}

export function displayFor({ skirmishing }) {
  return skirmishing ? SKIRMISH_DISPLAY : DISPLAY;
}
