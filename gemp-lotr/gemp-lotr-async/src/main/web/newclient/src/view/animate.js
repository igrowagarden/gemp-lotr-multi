/**
 * Cards travel between zones instead of jumping.
 *
 * The board is a pure function of state and re-renders wholesale, so a card is a
 * different DOM node on every paint and has no identity to animate. FLIP solves
 * exactly that: measure where each card was BEFORE the repaint (First), let the
 * repaint happen (Last), apply the inverse transform so the new node appears at
 * the old position (Invert), then animate the transform away (Play).
 *
 * This keeps animation as a decoration over the render rather than a constraint
 * on it -- no keyed reconciliation, no node reuse, nothing the renderer has to
 * know about. `cardId` is the identity, and it is stable because the server
 * assigns it.
 */

const MOVE_MS = 260;
const ENTER_MS = 180;
const EASE = "cubic-bezier(.4, 0, .2, 1)";

function reducedMotion(win) {
  return win.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches ?? false;
}

/**
 * Where every card is right now, by cardId. Call before a repaint.
 *
 * Any in-flight FLIP is finished first. `getBoundingClientRect` on an animating
 * element returns its ANIMATED position, not its settled layout position, so
 * measuring mid-flight makes a board that has not moved look like it moved --
 * every card picking up the offset of the previous animation. Two updates
 * arriving close together is enough to trigger it. A new state supersedes the
 * transition to the old one anyway.
 */
export function captureRects(root) {
  const rects = new Map();
  for (const node of root.querySelectorAll(".card[data-card-id]")) {
    for (const running of node.getAnimations?.() ?? []) running.finish();
    rects.set(node.dataset.cardId, node.getBoundingClientRect());
  }
  return rects;
}

/**
 * Animate the new nodes from where their cards used to be.
 *
 * A card that moved slides; one that is new fades up in place. A card that left
 * is not animated -- its node is already gone, and keeping a ghost around to
 * fly it out costs more complexity than the effect is worth.
 */
export function playFlip(root, before) {
  const win = root.ownerDocument.defaultView;
  if (!before || reducedMotion(win)) return;

  for (const node of root.querySelectorAll(".card[data-card-id]")) {
    const id = node.dataset.cardId;
    const now = node.getBoundingClientRect();
    if (!now.width) continue;               // in a collapsed band; nothing to show

    const was = before.get(id);
    if (!was) {
      node.animate(
        [{ opacity: 0, transform: "translateY(6px) scale(.94)" }, { opacity: 1, transform: "none" }],
        { duration: ENTER_MS, easing: EASE }
      );
      continue;
    }

    const dx = was.left - now.left;
    const dy = was.top - now.top;
    const sx = was.width / now.width;
    const sy = was.height / now.height;
    // Sub-pixel churn is not movement; animating it makes a still board shimmer.
    if (Math.abs(dx) < 2 && Math.abs(dy) < 2 && Math.abs(sx - 1) < 0.02) continue;

    node.animate(
      [
        { transform: `translate(${dx}px, ${dy}px) scale(${sx}, ${sy})`, zIndex: 20 },
        { transform: "none", zIndex: 20 }
      ],
      { duration: MOVE_MS, easing: EASE }
    );
  }
}
