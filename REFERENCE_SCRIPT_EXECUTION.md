# The reference client executes `<script>` in a log message

**Status: BACKLOGGED. Measured, recorded, not acted on.**
Not reported upstream, and deliberately so — see "Why this is not filed as a
vulnerability". This file is written to be upstream-ready, so filing it later is
a copy rather than a rewrite.

**Where:** `gemp-lotr-async/.../web/js/gemp-022/chat.js:340-378`
(`ChatBoxUI.appendMessage`), reached from
`gameAnimations.js:1175-1197` (`message`, `warning`) and from the chat poll at
`chat.js:428`.
**Found by:** the five-player board client project (`gemp_gui`), while building
the log differential — `src/dev/logfuzz.html`.
**Re-runnable:** `src/dev/scriptprobe.html` in that project reproduces every
measurement below in about ten seconds.

---

## What happens

`appendMessage` builds its line by string interpolation and inserts it with
jQuery:

```js
if (msgClass == "gameMessage")
    message = "<div class='msg-content'>" + message + "</div>";

var messageDiv = $("<div class='message " + msgClass + "'>" + message + "</div>");
this.chatMessagesDiv.append(messageDiv);
```

jQuery 1.6.2 evaluates `<script>` elements when a parsed fragment is **inserted**
into the document. So any script tag inside `message` runs, with the page's full
privileges, on the origin serving the game.

Both the game log and chat go through this one method, so the exposure is the
same for a `SEND_MESSAGE` game event, a `SEND_WARNING`, and a chat line.

## Measured, four ways

The first observation came through a chat-box test double, and a test double is
exactly the kind of thing that manufactures its own results. So it was re-asked
three more ways that do not involve one:

| probe | result |
|---|---|
| jQuery 1.6.2 parse only, never inserted | inert |
| jQuery parsed then `.append()`ed | **EXECUTED** |
| `ChatBoxUI.prototype.appendMessage` borrowed onto a bare object | **EXECUTED** |
| an `M` game event through the whole client feed path | **EXECUTED** |

Three of the four never touch the harness. Parse-only being inert locates the
behaviour in **insertion**, not parsing — which is what a fix has to address.

## Why this is NOT filed as a vulnerability

**The sink is unsafe — proven. That an attacker can reach it — not shown.**
Those are different claims and conflating them would overstate this.

Both player-controlled routes into a log line are closed already:

- **Chat is escaped server-side before it is ever sent.** `MarkdownParser` builds
  its renderer with `escapeHtml(true)` and `sanitizeUrls(true)`
  (`MarkdownParser.java:33-43`), so a script typed into chat arrives as text.
- **Player names cannot carry `<`.** `validLoginChars` is alphanumerics plus `-`
  and `_` (`DbPlayerDAO.java:15`, enforced at `389-396`).

The fourth probe above reached the sink through an `M` game event — engine
authored, and fed by hand in the harness. So this is a defect held harmless by
input handling somewhere else entirely, which is a fragile arrangement rather
than a live hole.

**"Unreachable" is not claimed either.** Every place the engine composes a game
log message has not been audited. What is claimed is that no reachable route was
found. Closing that gap is the backlog item below.

## What would settle it

Audit the producers of `SEND_MESSAGE` / `SEND_WARNING` for any externally
supplied string reaching the message text without escaping. The candidates worth
checking first, because they are free text a player controls:

- deck names and deck notes
- table descriptions (the `desc` field on table creation)
- anything echoing a card's own text or a format's preamble

If one of those reaches a log message unescaped, this stops being defence in
depth and becomes a stored XSS, and this file should be filed upstream
immediately with that route named.

## Suggested fix, if it is taken up

The narrow fix is to stop inserting message text as markup:

```js
var messageDiv = $("<div class='message " + msgClass + "'></div>");
messageDiv.append($.parseHTML(message, document, /* keepScripts */ false));
```

`$.parseHTML(..., false)` is the modern spelling and drops script elements
rather than executing them. On jQuery 1.6.2 it does not exist, so either the
library moves or the message is sanitised before it reaches `appendMessage`.

Note the log legitimately carries markup — card references arrive as
`<div class='cardHint' value='1_340'>Rivendell Terrace</div>` and must keep
working — so escaping the whole string is not an option. The rebuilt client
takes the other route: parse into a detached document and copy across only the
node types it understands, so unknown markup can make a line look plain but
never make it dangerous (`gemp_gui/src/view/panels.js`, `renderMessage`).

---

## Backlog entry

**Deferred, not dropped.** Nothing here is urgent while the two input routes
stay closed. Two things would move it:

1. **A reachable route is found** (see "What would settle it") — then file this
   file upstream, with the route named, as a stored XSS.
2. **Someone decides defence-in-depth is worth reporting anyway** — then file it
   as written, beside `GEMP_CARD_SELECTION_MAX0.md` and
   `GEMP_AUTOPASS_COOKIE.md` in `gemp_multiplayer/docs/`, untracked.

Until one of those, this stays here and `logfuzz.html` keeps reporting it as
`ORACLE` rather than as a failure — it is not this client's defect, and counting
it would make every log run red for someone else's bug.
