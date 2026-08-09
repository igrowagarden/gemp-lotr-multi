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

/**
 * Top to bottom, as ruled in the first five-player playtest: hand at the
 * bottom, then your support, then your fellowship, then the minions, then ONE
 * opposing view -- the focused seat's fellowship with its support above it.
 * There is no separate "fellowship under attack" row (removed by ruling); the
 * default focus follows the Free Peoples seat, so the active fellowship is
 * what the opponent rows show unless the player deliberately looks elsewhere.
 * The viewer's own rows are sacred and never move.
 */
export const DISPLAY = Object.freeze([
  {
    id: "focusSupport", weight: 6, collapsed: 18,
    label: (ctx) => `${ctx.focusId} · support area${ctx.filtered ? " · unfiltered" : ""}`,
    tone: "shadow"
  },
  {
    id: "focusFree", weight: 15, collapsed: 18,
    label: (ctx) => ctx.focusId === ctx.fpId
      ? `${ctx.focusId} · fellowship · Free Peoples`
      : `${ctx.focusId} · fellowship`,
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
    id: "selfFree", weight: 9, collapsed: 0,
    label: (ctx) => ctx.fpId === ctx.viewerId
      ? "Your fellowship · under attack"
      : "Your fellowship",
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
export const SKIRMISH_DISPLAY = Object.freeze((() => {
  // The fight sits MID-SCREEN (playtest ruling): the focused opponent's
  // support and fellowship stay above it, the minions and the viewer's own
  // rows below. The other bands shrink to give it the height.
  const scaled = DISPLAY.map((b) => ({ ...b, weight: Math.max(3, Math.round(b.weight * 0.6)) }));
  const skirmish = {
    id: "skirmish", weight: 26, collapsed: 20, ownerTag: true,
    label: (ctx, n, seats) => `Skirmish · ${n} cards from ${seats} seats · focus suspended`,
    tone: "shared"
  };
  const at = scaled.findIndex((b) => b.id === "minions");
  return [...scaled.slice(0, at), skirmish, ...scaled.slice(at)];
})());

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
    if (id === "path" || cards.length === 0) continue;
    // Deliberately hidden -- auto-hidden by ruling or retracted by the
    // player (ctx.hidden, computed by the board) -- is not a drop.
    if (ctx.hidden?.has(id)) continue;
    if (shown.has(id)) continue;
    missed.push({ band: id, count: cards.length });
  }
  return missed;
}

/**
 * Bands are HIDDEN, not removed: every entry stays in the list so the
 * retract rail can offer it back, and `autoHide: true` marks the ones a
 * ruling hides by default -- today the shadow viewer's own fellowship
 * during a skirmish ("you can hide my fellowship bar"): their companions
 * are not in this fight and the vertical fight display wants the height.
 * The board combines autoHide with the player's own retractions and passes
 * the effective set to `undrawn` as ctx.hidden, so a deliberate hide is
 * never reported as a drop.
 */
export function displayFor(ctx) {
  if (!ctx.skirmishing) return DISPLAY;
  if (ctx.viewerId != null && ctx.fpId != null && ctx.viewerId !== ctx.fpId) {
    return SKIRMISH_DISPLAY.map((b) => b.id === "selfFree" ? { ...b, autoHide: true } : b);
  }
  return SKIRMISH_DISPLAY;
}
