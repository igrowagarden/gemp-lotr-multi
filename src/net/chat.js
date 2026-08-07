/**
 * Table chat: its own connection, because the game channel does not carry it.
 *
 * `GameEvent.Type` declares CHAT_MESSAGE("CM") and NOTHING in the server ever
 * emits it -- the same dead-enum situation as `_side`. So a client that only
 * reads the game channel shows an empty chat forever, which is what ours did.
 * Chat lives at its own endpoint, in its own format:
 *
 *   GET  /chat/{room}?participantId=X   join; returns the room's history
 *   POST /chat/{room}  participantId, message      send
 *   POST /chat/{room}  participantId (no message)  long-poll for new messages
 *
 *   <chat roomName="Game14">
 *     <message date="1786064635074" from="System">Welcome to room: Game14</message>
 *     <user>watcher</user>
 *   </chat>
 *
 * The room for a game is "Game" + gameId (gameUi.js:787).
 *
 * Two things the live server settled, neither guessable from the handler:
 *  - Messages come back as rendered markdown, so "hi" arrives as
 *    "<p>hi</p><br/>". They are flattened to text here rather than injected as
 *    HTML: the server sanitises, but a chat line from another player is not
 *    something to hand to innerHTML on trust.
 *  - Server lines are `from="System"`. That is the only thing separating a
 *    person talking from the room narrating itself.
 */

export const SYSTEM = "System";

/** Flatten one server-rendered markdown message to plain text. */
function flatten(raw, doc) {
  if (!raw) return "";
  const parsed = (doc ?? document).implementation.createHTMLDocument("");
  parsed.body.innerHTML = raw;
  return parsed.body.textContent.replace(/\s+/g, " ").trim();
}

export function decodeChat(xml, doc) {
  const parsed = typeof xml === "string"
    ? new DOMParser().parseFromString(xml, "text/xml")
    : xml;
  const err = parsed.getElementsByTagName("parsererror")[0];
  if (err) throw new Error("Malformed chat XML: " + err.textContent.trim());

  return {
    room: parsed.documentElement?.getAttribute("roomName") ?? null,
    users: Array.from(parsed.getElementsByTagName("user"), (u) => u.textContent),
    messages: Array.from(parsed.getElementsByTagName("message"), (m) => {
      const from = m.getAttribute("from") ?? "";
      return {
        from,
        date: Number(m.getAttribute("date")) || null,
        text: flatten(m.textContent, doc),
        // Not "did the server send it" but "is this the room talking rather
        // than a person" -- which is what an unread badge should ignore.
        system: from === SYSTEM
      };
    })
  };
}

export function createChatClient({
  baseUrl = "/gemp-lotr-server",
  room,
  participantId,
  fetchImpl = (...args) => fetch(...args),
  doc
} = {}) {
  const url = `${baseUrl}/chat/${encodeURIComponent(room)}`;
  let stopped = false;
  let inFlight = null;   // the poll currently parked on the server

  const post = (data, signal) => fetchImpl(url, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ participantId, ...data }),
    signal
  });

  return {
    get stopped() { return stopped; },

    /**
     * Stop, and abort the poll already parked on the server. A flag alone only
     * prevents the NEXT poll: the current one holds its connection open by
     * design, which leaves the page permanently busy and is exactly what makes
     * a headless `--dump-dom` run wait forever.
     */
    stop() {
      stopped = true;
      inFlight?.abort();
      inFlight = null;
    },

    /** Join, and get whatever has already been said. */
    async join() {
      const res = await fetchImpl(
        `${url}?participantId=${encodeURIComponent(participantId)}`,
        { method: "GET", credentials: "same-origin" });
      if (!res.ok) {
        const e = new Error(`HTTP ${res.status}`); e.status = res.status; throw e;
      }
      return decodeChat(await res.text(), doc);
    },

    /** Long-poll. Resolves with only what is new, which may be nothing. */
    async poll() {
      inFlight = new AbortController();
      let res;
      try {
        res = await post({}, inFlight.signal);
      } catch (e) {
        if (stopped) return { messages: [], users: [] };   // our own abort
        throw e;
      } finally {
        inFlight = null;
      }
      if (!res.ok) {
        const e = new Error(`HTTP ${res.status}`); e.status = res.status; throw e;
      }
      const text = await res.text();
      return text.trim() ? decodeChat(text, doc) : { messages: [], users: [] };
    },

    /** Say something. The message comes back through the poll, not from here. */
    async send(message) {
      if (!message?.trim()) return;
      const res = await post({ message });
      if (!res.ok) {
        const e = new Error(`HTTP ${res.status}`); e.status = res.status; throw e;
      }
    }
  };
}
