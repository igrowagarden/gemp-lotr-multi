/**
 * The auto-pass settings panel.
 *
 * Seven checkboxes, the same seven the reference offers, and one extra switch
 * the reference declares and never wires up. See `model/autopass.js` for why
 * this is a cookie panel rather than a behaviour: the passing is done by the
 * engine, and all a client gets to say is which phases.
 *
 * The panel paints from `effectivePhases(document.cookie)` -- what the SERVER
 * will apply -- and never from the last thing clicked. That is the whole point
 * of it. A cookie write can fail silently, and the failure this feature exists
 * to avoid is precisely a settings panel that shows a tick for something the
 * server never heard about. If a click does not survive the read-back, the box
 * springs back and the panel says so.
 */

import { createFlyout } from "./flyout.js";
import {
  PHASES, SERVER_DEFAULT, effectivePhases, applyPhases
} from "../model/autopass.js";

/** The reference's own labels (gameUi.js:729-735), not our own wording. */
const LABEL = {
  FELLOWSHIP: "Fellowship",
  SHADOW: "Shadow",
  MANEUVER: "Maneuver",
  ARCHERY: "Archery",
  ASSIGNMENT: "Assignment",
  SKIRMISH: "Skirmish",
  REGROUP: "Regroup"
};

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

/**
 * @param onClientArm  told when the "pass immediately" switch moves, so the
 *   board can start or stop answering zero-action choices itself. Off unless
 *   the user asks, because the reference's equivalent branch is unreachable
 *   and a client that answers where the oracle waits is a divergence.
 */
export function createSettingsPanel(host, { onClientArm = () => {}, doc = document } = {}) {
  let clientArm = false;
  let note = "";
  // Built once and then SYNCED, never rebuilt. Rebuilding the body on every
  // toggle destroys the checkbox that was just clicked, which throws away
  // keyboard focus mid-list and leaves anything holding a node reference --
  // a caller, a test -- pointing at a detached element.
  let nodes = null;

  const panel = createFlyout(host, {
    id: "settings",
    title: "Auto-pass",
    // No docked tab: this is opened from the status bar and has no business
    // parked at the edge of the board for a setting touched once a session.
    tab: null,
    width: 260,
    height: 250,
    render
  });

  function setPhases(next) {
    const applied = applyPhases(next, doc);
    const wanted = new Set(next);
    const disagreed = PHASES.filter((p) => wanted.has(p) !== applied.includes(p));
    // Read back, not assumed. A wrong path or a blocked cookie shows up here
    // and nowhere else -- the game simply carries on with the old setting.
    note = disagreed.length
      ? `not saved: the browser refused the cookie (${disagreed.join(", ")})`
      : "";
    sync();
  }

  /** Every visible thing here is derived from the cookie, so this is all of it. */
  function sync() {
    if (!nodes) return;
    const active = effectivePhases(doc.cookie);
    for (const [phase, box] of nodes.boxes) box.checked = active.includes(phase);
    nodes.armBox.checked = clientArm;
    nodes.armHint.textContent = clientArm
      ? "This client answers a no-action choice itself. The reference does not — expect the differential to see it."
      : "Off. The reference declares this and never enables it.";
    nodes.warn.textContent = note;
    nodes.warn.hidden = !note;
  }

  function build(body) {
    body.textContent = "";
    const boxes = new Map();

    body.appendChild(el(doc, "div", "settings-hint",
      "The server skips these phases when you have no playable action."));

    const list = el(doc, "div", "settings-list");
    for (const phase of PHASES) {
      const row = el(doc, "label", "settings-row");
      const box = doc.createElement("input");
      box.type = "checkbox";
      box.addEventListener("change", () => {
        const next = new Set(effectivePhases(doc.cookie));
        box.checked ? next.add(phase) : next.delete(phase);
        setPhases([...next]);
      });
      boxes.set(phase, box);
      row.append(box, el(doc, "span", null, LABEL[phase]));
      // The two the server does not pass by default are worth marking, so
      // "why does Shadow always ask me" has an answer on screen.
      if (!SERVER_DEFAULT.includes(phase)) {
        row.appendChild(el(doc, "span", "settings-off", "off by default"));
      }
      list.appendChild(row);
    }
    body.appendChild(list);

    const arm = el(doc, "label", "settings-row settings-arm");
    const armBox = doc.createElement("input");
    armBox.type = "checkbox";
    armBox.addEventListener("change", () => {
      clientArm = armBox.checked;
      onClientArm(clientArm);
      sync();
    });
    arm.append(armBox, el(doc, "span", null, "Pass instantly, without waiting"));
    body.appendChild(arm);

    const armHint = el(doc, "div", "settings-hint settings-sub");
    const warn = el(doc, "div", "settings-warn");
    body.append(armHint, warn);

    nodes = { boxes, armBox, armHint, warn };
    sync();
  }

  function render(body) {
    // paint() runs on every open. Rebuild only if the body is not already ours
    // -- the flyout clears nothing on close, so the nodes survive.
    if (!nodes || !body.contains(nodes.armBox)) build(body);
    else sync();
  }

  return {
    panel,
    open: () => panel.open(),
    toggle: () => panel.toggle(),
    get clientArm() { return clientArm; },
    /** Exposed for the check suite, which drives this without clicking. */
    setPhases
  };
}
