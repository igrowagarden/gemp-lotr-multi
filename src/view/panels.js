/**
 * The two floating panels: the adventure path and chat.
 *
 * Both are instances of the same flyout, differing only in what they draw.
 */

import { createFlyout } from "./flyout.js";
import { sitePath, currentSite } from "../state/reduce.js";
import { imageUrl } from "../model/images.js";
import { cardActions, isActionChoice } from "../model/actions.js";
import { isAssignment } from "../model/assign.js";
import { wireActions } from "./actionmenu.js";

const el = (doc, tag, cls, text) => {
  const node = doc.createElement(tag);
  if (cls) node.className = cls;
  if (text != null) node.textContent = text;
  return node;
};

const SITES = 9;

// A site's drawn size, and how far along the path the next one sits. The gap is
// smaller than the card, so each site covers the one before and leaves an
// exposed edge carrying its number. Everything is px in both orientations: the
// vertical layout used to place by percentage, which is what made the window
// start out the size of all nine sites and merely spread two of them across it.
// Sized so a site's art is properly readable -- its name and its text, not just
// a coloured tile. A full nine-site path is taller than most boards at this
// size, so fitTo clamps to the host and the body scrolls; the window is also
// resizable, and a hand resize is respected from then on.
const SITE_H = 151;                   // site cards are landscape, 7:5
const SITE_W = SITE_H * 7 / 5;
const STEP_DOWN = 74;                 // ~half the card stays exposed
const STEP_ACROSS = 104;

// Breathing room around the cards, so the path is not wedged against the frame
// and a site's border is not the window's border.
const PAD = 10;

/** How big the body has to be to hold `n` sites, in the current orientation. */
const bodySize = (n, horizontal) => horizontal
  ? { w: SITE_W + Math.max(0, n - 1) * STEP_ACROSS + PAD * 2, h: SITE_H + PAD * 2 }
  : { w: SITE_W + PAD * 2, h: SITE_H + Math.max(0, n - 1) * STEP_DOWN + PAD * 2 };

/**
 * The path is revealed as you go: only site 1 is pre-played, and every other
 * site is played on demand by whoever sits two seats around from the current
 * player, in the direction the CURRENT site sets. So the window draws what has
 * been played plus one placeholder -- never nine known sites, which would show
 * the player information the game has not given them.
 *
 * Sites lie on their side and overlap, each covering the one before, so the
 * exposed edge carries the number. `⇄` swaps the overlap axis; the cards keep
 * their orientation either way.
 */
/**
 * @param onAnswer  optional; without it the path is read-only. A SITE can carry
 *   a card action ("Use Rivendell Terrace", every sanctuary) and it is an
 *   ordinary CARD_ACTION_CHOICE target -- but sites are drawn here, not in a
 *   band, so without this wiring those actions cannot be taken at all.
 */
export function createPathPanel(host, store, { onAnswer = null, selection = null } = {}) {
  // Left to right by default. The path IS a road, and reading it the way the
  // fellowship walks it beats a stack. `⇄` still turns it on its side.
  let horizontal = true;

  const panel = createFlyout(host, {
    id: "path",
    title: "Site path",
    tab: "Site path",
    side: "right",
    // Opens at the size of one site plus the placeholder, laid across, and
    // grows from there. render() sizes it properly on the first paint anyway;
    // these just stop it appearing at the wrong shape for one frame.
    width: Math.round(SITE_W + STEP_ACROSS) + 14,
    height: Math.round(SITE_H) + 26,
    extraControls: [{
      label: "⇄",
      title: "Lay the path top to bottom",
      onClick: (api, button) => {
        horizontal = !horizontal;
        api.root.classList.toggle("horiz", horizontal);
        if (button) {
          button.title = horizontal ? "Lay the path top to bottom"
                                    : "Lay the path left to right";
        }
        // Left alone, render() sizes it for the new axis. Only a window the
        // user has sized by hand needs its own dimensions swapped, since
        // fitTo() will decline to touch that one.
        if (api.userSized) {
          const r = api.root.getBoundingClientRect();
          api.resizeTo(r.height, r.width);
        }
        api.paint(store.getState());
      }
    }],
    render: (body, state) => {
      body.textContent = "";
      const doc = body.ownerDocument;
      const col = el(doc, "div", "sitecol");
      body.appendChild(col);

      const path = sitePath(state ?? store.getState());
      const here = currentSite(state ?? store.getState());
      const shown = path.length + (path.length < SITES ? 1 : 0);

      // Grow the window to what has actually been played, before laying
      // anything out. Declined outright once the user has resized by hand.
      const want = bodySize(shown, horizontal);
      const chrome = panel.chrome;
      panel.fitTo(want.w + chrome.w, want.h + chrome.h);

      // Offset by PAD along the path, centred across it -- the cross axis has
      // PAD of slack at each end because bodySize asked for it.
      const place = (node, i) => {
        node.style.height = SITE_H + "px";        // aspect-ratio gives the width
        if (horizontal) {
          node.style.left = (PAD + i * STEP_ACROSS).toFixed(1) + "px";
          node.style.top = "50%";
          node.style.transform = "translateY(-50%)";
        } else {
          node.style.top = (PAD + i * STEP_DOWN).toFixed(1) + "px";
          node.style.left = "50%";
          node.style.transform = "translateX(-50%)";
        }
      };

      path.forEach((site, i) => {
        const node = el(doc, "div", "site");
        if (here && site.siteNumber === here.siteNumber) node.classList.add("now");
        else if (here && site.siteNumber < here.siteNumber) node.classList.add("past");
        place(node, i);

        // Site art is natively landscape (496x357 against 357x497 for a
        // character), which is why `.site` is aspect-ratio 7/5 and why nothing
        // here rotates the way the reference client does for a sideways card in
        // play. Same failure mode as a card: on error the <img> goes and the
        // tinted placeholder underneath shows through, so a missing image is a
        // blank site rather than a broken path.
        // The card id, not just the blueprint: a decision names sites by card
        // id like anything else, and without it a site can never be matched to
        // the action offered on it.
        node.dataset.cardId = site.cardId;
        if (site.blueprintId) node.dataset.blueprintId = site.blueprintId;
        const src = imageUrl(site.blueprintId);
        if (src) {
          const img = doc.createElement("img");
          img.className = "s-art";
          img.loading = "lazy";
          img.decoding = "async";
          img.alt = "";
          img.src = src;
          img.addEventListener("error", () => img.remove(), { once: true });
          node.appendChild(img);
        }

        // A site offered by the current decision is lit and clickable, exactly
        // as a card in a band would be.
        //
        // BOTH kinds of decision, not just actions. The first version of this
        // block tested `isActionChoice(d)` alone, so a CARD_SELECTION naming a
        // site lit nothing and the site could not be chosen at all -- the
        // sanctuary-ability bug over again, in the decision type next door.
        // `dev/decisionfuzz.html` catches it by running the same shape against
        // every placement: "action on a SITE" passed while "selects a SITE"
        // did not.
        const s = state ?? store.getState();
        const d = s.decision;
        const mine = onAnswer && d && d.forPlayer === s.viewerId && !s.spectating;
        if (mine && isActionChoice(d)) {
          const own = cardActions(d).byCard.get(site.cardId) ?? [];
          if (own.length) {
            node.classList.add("is-eligible");
            wireActions(node, own, host, (actionId) => onAnswer(d.id, actionId));
          }
        } else if (mine && selection && d.shape === "cards" &&
                   d.decisionType !== "ARBITRARY_CARDS" && !isAssignment(d) &&
                   (d.parameters?.cardId ?? []).map(Number).includes(site.cardId)) {
          // The selection itself lives in the board, which owns the count and
          // the Confirm button; the panel only joins it. Keeping a second set
          // here would let the two disagree about what is chosen.
          node.classList.add("is-eligible");
          if (selection.has(site.cardId)) node.classList.add("is-chosen");
          node.addEventListener("click", () => selection.toggle(site.cardId));
        }

        node.append(
          el(doc, "span", "snum", String(site.siteNumber ?? i + 1)),
          el(doc, "span", "sowner", site.owner ?? "")
        );
        col.appendChild(node);
      });

      if (path.length < SITES) {
        const next = el(doc, "div", "site pending");
        place(next, shown - 1);
        next.append(
          el(doc, "span", "snum", String(path.length + 1)),
          el(doc, "span", "sowner", "not yet played")
        );
        col.appendChild(next);
      }
      panel.root.querySelector(".flyout-word").textContent =
        `Site path · ${here?.siteNumber ?? path.length}/${SITES}`;
    }
  });

  // Match the class to the default the layout already uses, so anything styling
  // off `.horiz` agrees with what is actually drawn from the first frame.
  panel.root.classList.toggle("horiz", horizontal);

  return panel;
}

/**
 * One log line, as safe DOM.
 *
 * The server's game log is NOT plain text. It embeds card references as markup:
 *
 *   <div class='cardHint' value='1_340'>Rivendell Terrace</div> required ...
 *
 * Rendered with textContent those tags show up literally, which is what the log
 * looked like. Rendered with innerHTML they would work, and would also hand
 * arbitrary server- and player-authored markup straight to the DOM.
 *
 * So: parse into a DETACHED document, then copy across only what we understand
 * -- text, and cardHint turned into a span carrying its blueprint id. Anything
 * else contributes its text and nothing more. Unknown markup can therefore make
 * a line look plain, but it can never make it dangerous.
 *
 * The blueprint id is what `imageUrl` takes, so a card named in the log gets the
 * same hover preview as a card on the board, through the `data-blueprint-id`
 * fallback the zoom already has.
 */
export function renderMessage(raw, doc = document) {
  const frag = doc.createDocumentFragment();
  if (!raw) return frag;

  const parsed = doc.implementation.createHTMLDocument("");
  parsed.body.innerHTML = raw;

  const walk = (node, into) => {
    for (const child of node.childNodes) {
      if (child.nodeType === 3) {
        into.appendChild(doc.createTextNode(child.nodeValue));
      } else if (child.nodeType === 1) {
        const id = child.getAttribute?.("value");
        if (child.classList?.contains("cardHint") && id) {
          const hint = el(doc, "span", "cardhint", child.textContent);
          hint.dataset.blueprintId = id;
          into.appendChild(hint);
        } else {
          walk(child, into);          // keep the words, drop the element
        }
      }
    }
  };
  walk(parsed.body, frag);
  return frag;
}

/**
 * Chat and the game log, newest last, with a box to type in.
 *
 * `onSend` is optional: without it the panel is read-only, which is what a
 * replay or a spectator with no seat should get. The input is NOT rebuilt on
 * every paint -- a repaint arrives with every game event, and rebuilding would
 * throw away whatever was half-typed and the caret with it.
 */
export function createChatPanel(host, store, { onSend = null } = {}) {
  let form = null;

  const panel = createFlyout(host, {
    id: "chat",
    title: "Table chat",
    tab: "Chat",
    side: "right",
    width: 440,
    height: 260,
    render: (body, state) => {
      const s = state ?? store.getState();
      const doc = body.ownerDocument;

      // Where the reader is, BEFORE the transcript is replaced. Following the
      // tail is only wanted by someone already at the tail -- yanking the view
      // down while they are reading back is how a log becomes unusable the
      // moment it gets busy. 24px of slack so "near enough the bottom" counts.
      const old = body.querySelector(".chat-lines");
      const following = !old ||
        old.scrollHeight - old.scrollTop - old.clientHeight < 24;

      // Keep the form across repaints; replace only the transcript.
      old?.remove();
      const lines = el(doc, "div", "chat-lines");
      const recent = s.log.slice(-80);
      if (!recent.length) lines.appendChild(el(doc, "span", "band-empty", "Nothing said yet."));
      for (const entry of recent) {
        const line = el(doc, "span", entry.warning ? "chat-warn" : null);
        if (entry.from && !entry.system) line.appendChild(el(doc, "b", "chat-who", entry.from));
        line.appendChild(renderMessage(entry.text, doc));
        if (entry.system) line.classList.add("chat-system");
        lines.appendChild(line);
      }
      body.insertBefore(lines, form);

      if (onSend && !form) {
        form = doc.createElement("form");
        form.className = "chat-say";
        const input = doc.createElement("input");
        input.type = "text";
        input.placeholder = "Say something";
        input.maxLength = 400;
        const send = el(doc, "button", null, "Send");
        send.type = "submit";
        form.append(input, send);
        form.addEventListener("submit", (e) => {
          e.preventDefault();
          const said = input.value;
          if (!said.trim()) return;
          input.value = "";
          onSend(said);
        });
        // The board listens for bare keys (c, p, arrows); typing here must not
        // also drive the board.
        form.addEventListener("keydown", (e) => e.stopPropagation());
        body.appendChild(form);
      }

      if (following) lines.scrollTop = lines.scrollHeight;
      else lines.scrollTop = old.scrollTop;   // hold the reader's place
    }
  });

  return panel;
}
