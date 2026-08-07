/**
 * A floating window: drag, resize, dock, and come back where you left it.
 *
 * Chat and the site path are two instances of this, not two implementations.
 * Everything here was learned the expensive way in the prototype:
 *
 *  - No size transition. A window that animates open cannot be measured at the
 *    moment it opens, and the position it is restored to depends on that
 *    measurement -- which is how the path once opened off the side of the board
 *    with its title bar out of reach.
 *  - Title-bar controls are excluded from the drag by `closest("button")`, not
 *    by naming each one. Naming them meant the transpose button, added later,
 *    was swallowed by the drag.
 *  - Escape closes the ACTIVE window only, then the next one down.
 *  - A window fades only while it actually covers something that wants
 *    attention, never merely because the pointer left it.
 */

const MIN_W = 92;
const MIN_H = 62;

let active = null;
const registry = new Set();

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

function setActive(panel) {
  for (const p of registry) p.root.classList.toggle("active", p === panel);
  active = panel;
}

export function createFlyout(host, {
  id,
  title,
  tab,
  side = "right",
  width = 200,
  height = 120,
  render,
  extraControls = []
}) {
  const doc = host.ownerDocument;

  const root = el(doc, "div", `flyout flyout--${id} shut side-${side}`);
  const tabBtn = el(doc, "button", "flyout-tab");
  tabBtn.type = "button";
  const flag = el(doc, "span", "flyout-flag");
  const word = el(doc, "span", "flyout-word", tab ?? title);
  tabBtn.append(flag, word);

  const head = el(doc, "div", "flyout-head");
  head.append(el(doc, "span", "grip", "⠿"), el(doc, "span", "flyout-title", title));
  for (const control of extraControls) {
    const b = el(doc, "button", "flyout-ctl", control.label);
    b.type = "button";
    b.title = control.title ?? "";
    // The button goes through too, so a control that toggles can relabel itself.
    b.addEventListener("click", (e) => { e.stopPropagation(); control.onClick(api, b); });
    head.appendChild(b);
  }
  const close = el(doc, "button", "flyout-close", "✕");
  close.type = "button";
  close.setAttribute("aria-label", "Close " + title);
  head.appendChild(close);

  const body = el(doc, "div", "flyout-body");
  const grip = el(doc, "div", "flyout-resize");

  // `tab: null` means no docked tab: the window is opened from somewhere else
  // and leaves nothing parked at the edge of the board. The button is still
  // built, so notify() needs no special case, it is simply never shown -- and
  // `.no-tab` lets a shut window disappear entirely rather than collapse to a
  // tab-sized stub.
  const docked = tab !== null;
  if (!docked) root.classList.add("no-tab");
  root.append(...(docked ? [tabBtn] : []), head, body, grip);
  host.appendChild(root);

  let pos = null;    // { x, y } within the host, remembered across closes
  let userSized = false;   // once true, fitTo() stops second-guessing the user
  let size = null;   // { w, h } likewise
  let unread = 0;

  function clamp(x, y) {
    const box = host.getBoundingClientRect();
    return {
      x: Math.max(2, Math.min(x, box.width - root.offsetWidth - 2)),
      y: Math.max(2, Math.min(y, box.height - root.offsetHeight - 2))
    };
  }

  function apply() {
    root.style.width = size.w + "px";
    root.style.height = size.h + "px";
    pos = clamp(pos.x, pos.y);
    root.style.left = pos.x + "px";
    root.style.top = pos.y + "px";
    root.style.right = "auto";
    root.style.bottom = "auto";
  }

  const api = {
    id,
    root,
    body,
    get isOpen() { return !root.classList.contains("shut"); },

    open() {
      root.classList.remove("shut");
      // Safe to measure only because the open state has no size transition.
      const box = host.getBoundingClientRect();
      const rect = root.getBoundingClientRect();
      pos ??= { x: rect.left - box.left, y: rect.top - box.top };
      size ??= { w: rect.width || width, h: rect.height || height };
      apply();
      unread = 0;
      root.dataset.unread = "0";
      setActive(api);
      api.paint();
      return api;
    },

    close() {
      root.classList.add("shut");
      root.classList.remove("active");
      for (const prop of ["left", "top", "right", "bottom", "width", "height"]) {
        root.style[prop] = "";
      }
      if (active === api) {
        const next = [...registry].find((p) => p !== api && p.isOpen);
        setActive(next ?? null);
      }
      return api;
    },

    toggle() { return api.isOpen ? api.close() : api.open(); },

    /** Unread count on the docked tab. Cleared by opening it. */
    notify(n = 1) {
      if (api.isOpen) return api;
      unread += n;
      root.dataset.unread = String(unread);
      flag.textContent = unread > 9 ? "9+" : String(unread);
      return api;
    },

    paint(...args) {
      if (api.isOpen && render) render(body, ...args);
      return api;
    },

    /**
     * Fade only while covering one of these rectangles. Parked in dead space a
     * window costs nothing, so it should not dim for merely existing.
     */
    yieldTo(hotRects) {
      if (!api.isOpen) { root.classList.remove("blocking"); return; }
      const rect = root.getBoundingClientRect();
      const hit = hotRects.some((h) =>
        !(rect.right <= h.left || rect.left >= h.right ||
          rect.bottom <= h.top || rect.top >= h.bottom));
      root.classList.toggle("blocking", hit);
    },

    resizeTo(w, h) { size = { w, h }; userSized = true; if (api.isOpen) apply(); return api; },

    /**
     * Size to fit content. Ignored once the user has sized the window by hand,
     * because their choice outranks ours -- a panel that snaps back to its
     * computed size every repaint cannot be resized at all.
     */
    fitTo(w, h) {
      if (userSized) return api;
      // Clamped to the board: a full nine-site path is taller than most screens
      // at readable card size, and a window that grows past the host cannot be
      // dragged back into reach. The body scrolls instead.
      const box = host.getBoundingClientRect();
      const cap = (v, min, limit) =>
        Math.max(min, Math.round(limit > min ? Math.min(v, limit) : v));
      size = { w: cap(w, MIN_W, box.width - 12), h: cap(h, MIN_H, box.height - 12) };
      if (api.isOpen) apply();
      return api;
    },

    get userSized() { return userSized; },

    /**
     * What the frame costs around the body's CONTENT box, measured not assumed.
     *
     * `clientWidth` includes padding, so subtracting it alone leaves the body's
     * own padding out of the total: fitTo then sized the window so its content
     * area was padding-narrower than what the caller asked for, and the panel
     * showed scrollbars over content that was supposed to fit exactly.
     */
    get chrome() {
      const pad = (root.ownerDocument.defaultView ?? window).getComputedStyle(body);
      const px = (v) => parseFloat(v) || 0;
      return {
        w: root.offsetWidth - body.clientWidth + px(pad.paddingLeft) + px(pad.paddingRight),
        h: root.offsetHeight - body.clientHeight + px(pad.paddingTop) + px(pad.paddingBottom)
      };
    }
  };

  tabBtn.addEventListener("click", () => api.open());
  close.addEventListener("click", (e) => { e.stopPropagation(); api.close(); });
  root.addEventListener("pointerdown", () => { if (api.isOpen) setActive(api); });

  head.addEventListener("pointerdown", (e) => {
    // Any control in the title bar keeps its click; capturing the pointer for a
    // drag would retarget it and the button would never fire.
    if (e.target.closest("button")) return;
    e.preventDefault();
    head.setPointerCapture(e.pointerId);
    const box = host.getBoundingClientRect();
    const rect = root.getBoundingClientRect();
    const dx = e.clientX - rect.left;
    const dy = e.clientY - rect.top;
    root.classList.add("dragging");

    const move = (ev) => {
      pos = clamp(ev.clientX - box.left - dx, ev.clientY - box.top - dy);
      root.style.left = pos.x + "px";
      root.style.top = pos.y + "px";
      root.style.right = "auto";
      root.style.bottom = "auto";
    };
    const up = (ev) => {
      root.classList.remove("dragging");
      head.releasePointerCapture(ev.pointerId);
      head.removeEventListener("pointermove", move);
      head.removeEventListener("pointerup", up);
    };
    head.addEventListener("pointermove", move);
    head.addEventListener("pointerup", up);
  });

  grip.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    e.stopPropagation();
    grip.setPointerCapture(e.pointerId);
    const rect = root.getBoundingClientRect();
    const box = host.getBoundingClientRect();
    const x0 = e.clientX;
    const y0 = e.clientY;

    const move = (ev) => {
      userSized = true;
      size = {
        w: Math.max(MIN_W, Math.min(rect.width + ev.clientX - x0, box.width - pos.x - 2)),
        h: Math.max(MIN_H, Math.min(rect.height + ev.clientY - y0, box.height - pos.y - 2))
      };
      root.style.width = size.w + "px";
      root.style.height = size.h + "px";
      api.paint();
    };
    const up = (ev) => {
      grip.releasePointerCapture(ev.pointerId);
      grip.removeEventListener("pointermove", move);
      grip.removeEventListener("pointerup", up);
    };
    grip.addEventListener("pointermove", move);
    grip.addEventListener("pointerup", up);
  });

  registry.add(api);
  return api;
}

/** Escape closes one window: the active one, then the next down. */
export function closeTopmost() {
  const target = active?.isOpen ? active : [...registry].find((p) => p.isOpen);
  if (target) { target.close(); return true; }
  return false;
}

export function allFlyouts() {
  return [...registry];
}
