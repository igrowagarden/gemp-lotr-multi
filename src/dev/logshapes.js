/**
 * The log space: every shape of line the game log and chat can carry.
 *
 * Third application of the method (`decisionshapes.js`, `pileshapes.js`). The
 * axes here are not configurations but CONTENT, because the reference funnels
 * all three sources into one list and the only thing that varies is what is in
 * each line:
 *
 *   source   M (game) | W (warning) | chat from a person | chat from System
 *   content  plain, card hints, foreign markup, entities, empty, dangerous
 *
 * Both clients put game and chat into ONE list in arrival order. That is not a
 * choice this client made independently -- `gameAnimations.message` and
 * `.warning` both call `chatBox.appendMessage`, the same method the chat poll
 * calls, into the same `chatMessagesDiv` (chat.js:340-378). So interleaving is
 * the reference's behaviour and the ordering cases below check we match it.
 *
 * What is compared per line, and why each matters:
 *
 *   text   the words a player reads. The log embeds card references as MARKUP
 *          (`<div class='cardHint' value='1_340'>Rivendell Terrace</div>`), so
 *          a client rendering with textContent shows the tags literally and one
 *          rendering with innerHTML hands server- and player-authored markup to
 *          the DOM. Text is where both mistakes show up.
 *   hints  the blueprint ids a line exposes. A card named in the log should
 *          preview like a card on the board; a hint whose id is lost is a dead
 *          reference that still looks right.
 *   class  the reference's own classification -- gameMessage, warningMessage,
 *          chatMessage. A warning that reads as an ordinary line is how a
 *          rejected answer becomes invisible.
 */

export const VIEWER = "asdf";
export const PLAYERS = ["asdf", "qwer", "Librarian"];

const hint = (id, text) => `<div class='cardHint' value='${id}'>${text}</div>`;

/**
 * `kind` is how the line ARRIVES: "M" and "W" are game events, "chat" comes
 * from net/chat.js. `expectText` and `expectHints` are what a reader should
 * end up with, written out so a disagreement names the intended behaviour
 * rather than only the two clients' outputs.
 */
export const CASES = [
  { name: "a plain game message", kind: "M",
    raw: "The Fellowship moves.",
    expectText: "The Fellowship moves.", expectHints: [] },

  { name: "a game message naming one card", kind: "M",
    raw: `${hint("1_340", "Rivendell Terrace")} required a discard`,
    expectText: "Rivendell Terrace required a discard", expectHints: ["1_340"] },

  { name: "two cards in one line", kind: "M",
    raw: `${hint("1_90", "Aragorn")} skirmishes ${hint("1_170", "Orc Soldier")}`,
    expectText: "Aragorn skirmishes Orc Soldier", expectHints: ["1_90", "1_170"] },

  // A hint with no `value` is not a card reference at all. The reference's
  // click handler reads `attr("value")` (gameUi.js:809) and would build a Card
  // with an undefined blueprint; the sane reading is "keep the words, drop the
  // reference".
  { name: "a cardHint carrying no value", kind: "M",
    raw: "<div class='cardHint'>Nameless</div> did something",
    expectText: "Nameless did something", expectHints: [] },

  { name: "a hint nested inside other markup", kind: "M",
    raw: `<b>${hint("1_55", "Gandalf")}</b> spoke`,
    expectText: "Gandalf spoke", expectHints: ["1_55"] },

  { name: "foreign markup contributes its words only", kind: "M",
    raw: "<b>bold</b> and <i>italic</i>",
    expectText: "bold and italic", expectHints: [] },

  { name: "HTML entities decode once, not twice", kind: "M",
    raw: "Sam &amp; Frodo &lt;3",
    expectText: "Sam & Frodo <3", expectHints: [] },

  { name: "an empty message", kind: "M",
    raw: "", expectText: "", expectHints: [] },

  // The point of parsing into a detached document. Neither client should run
  // this, and neither should show its source as words either.
  { name: "a script tag is inert and contributes nothing", kind: "M",
    raw: "before<script>window.__pwned = 1;</script>after",
    expectText: "beforeafter", expectHints: [] },

  { name: "a warning", kind: "W",
    raw: "You cannot do that.",
    expectText: "You cannot do that.", expectHints: [] },

  { name: "a warning naming a card", kind: "W",
    raw: `${hint("1_12", "Sting")} cannot be played now`,
    expectText: "Sting cannot be played now", expectHints: ["1_12"] },

  // Chat arrives already rendered as markdown by the server -- `hi` comes back
  // as `<p>hi</p><br/>`. Shown raw it reads as source; shown as text it reads
  // as what was said.
  { name: "chat, as the server renders it", kind: "chat", from: "qwer",
    raw: "<p>hello there</p><br/>",
    expectText: "hello there", expectHints: [] },

  { name: "chat from the room itself", kind: "chat", from: "System", system: true,
    raw: "<p>qwer has joined</p><br/>",
    expectText: "qwer has joined", expectHints: [] },

  { name: "chat containing markup a player typed", kind: "chat", from: "qwer",
    raw: "<p>look: <b>bold</b></p><br/>",
    expectText: "look: bold", expectHints: [] }
];

/**
 * Interleaving. Fed in this order, both clients must show this order -- the
 * reference appends to one div whatever the source, so a client keeping two
 * lists would draw every chat line after every game line however long ago it
 * was said.
 */
export const ORDER_CASE = [
  { kind: "M", raw: "one" },
  { kind: "chat", from: "qwer", raw: "<p>two</p><br/>" },
  { kind: "M", raw: "three" },
  { kind: "W", raw: "four" },
  { kind: "chat", from: "System", system: true, raw: "<p>five</p><br/>" }
];

/** The wire form of a game message or warning. */
export function messageXml(kind, raw) {
  const attr = String(raw)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
  return `<ge type="${kind}" message="${attr}"/>`;
}

export function baseBoard() {
  const seats = PLAYERS.map((p, i) =>
    `<ge type="PP" participantId="${p}" index="${i}"/>`).join("");
  const participants =
    `<ge type="P" participantId="${VIEWER}" ` +
    `allParticipantIds="${PLAYERS.join(",")}" discardPublic="false"/>`;
  return `<gameState>${participants}${seats}</gameState>`;
}
