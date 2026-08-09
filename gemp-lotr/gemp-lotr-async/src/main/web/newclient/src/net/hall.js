/**
 * The hall: what tables exist, and getting into one.
 *
 * Like the game channel this is a long poll -- `channelNumber` on the root, and
 * the same 409/410 meanings -- but the payload is tables and queues rather than
 * game events, and the elements are `<table>` not `<ge>`. Kept separate from
 * net/protocol.js for that reason: they are two formats that happen to share a
 * transport shape.
 *
 * Endpoints, from HallRequestHandler:
 *   GET  /hall?participantId=X          list
 *   POST /hall                          create   (format, deckName, seatCount, ...)
 *   POST /hall/{tableId}                join     (deckName)
 *   POST /hall/{tableId}/leave          leave
 */

const attr = (el, name) => (el.hasAttribute(name) ? el.getAttribute(name) : undefined);

/** A table row, with the strings turned into things a view can use. */
export function decodeTable(el) {
  const gameId = attr(el, "gameId");
  return {
    id: attr(el, "id"),
    // gameId is present but EMPTY while a table is still filling. An empty
    // string here would look like a joinable game and produce a 404 board.
    gameId: gameId ? gameId : null,
    status: attr(el, "status"),
    statusDescription: attr(el, "statusDescription"),
    format: attr(el, "format"),
    tournament: attr(el, "tournament"),
    players: (attr(el, "players") ?? "").split(",").filter(Boolean),
    watchable: attr(el, "watchable") === "true",
    private: attr(el, "isPrivate") === "true",
    inviteOnly: attr(el, "isInviteOnly") === "true",
    description: attr(el, "userDescription") || ""
  };
}

export function decodeHall(xml) {
  const doc = typeof xml === "string"
    ? new DOMParser().parseFromString(xml, "text/xml")
    : xml;
  const err = doc.getElementsByTagName("parsererror")[0];
  if (err) throw new Error("Malformed hall XML: " + err.textContent.trim());

  const root = doc.documentElement;
  return {
    channelNumber: root?.getAttribute("channelNumber"),
    serverTime: root?.getAttribute("serverTime"),
    tables: Array.from(doc.getElementsByTagName("table"), decodeTable)
  };
}

/** Tables you can actually open a board for, and why. */
export function playableFor(tables, participantId) {
  return tables
    .filter((t) => t.gameId)
    .map((t) => ({
      ...t,
      seated: t.players.includes(participantId),
      // `watchable` is false on ordinary casual tables, yet the game endpoint
      // serves a non-seated account perfectly well -- it just withholds hands.
      // So a live game is watchable in practice; the flag is a hint, not a gate.
      role: t.players.includes(participantId) ? "play"
          : t.gameId && t.status !== "FINISHED" ? "watch"
          : null
    }))
    .filter((t) => t.role);
}

export function createHallClient({
  baseUrl = "/gemp-lotr-server",
  participantId,
  fetchImpl = (...args) => fetch(...args)
} = {}) {
  const form = (data) =>
    new URLSearchParams({ participantId, ...data });

  const post = async (path, data) => {
    const res = await fetchImpl(baseUrl + path, {
      method: "POST",
      credentials: "same-origin",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: form(data)
    });
    const text = await res.text().catch(() => "");
    if (!res.ok) {
      const err = new Error(`HTTP ${res.status}`);
      err.status = res.status;
      throw err;
    }
    // The server answers a refused action with <error message="..."/> and a 200,
    // so a bare status check is not enough to know it worked.
    const message = /<error[^>]*message="([^"]*)"/.exec(text);
    if (message) throw new Error(message[1]);
    return text;
  };

  return {
    async list() {
      const res = await fetchImpl(
        `${baseUrl}/hall?participantId=${encodeURIComponent(participantId)}`,
        { method: "GET", credentials: "same-origin" }
      );
      if (!res.ok) {
        const err = new Error(`HTTP ${res.status}`);
        err.status = res.status;
        throw err;
      }
      return decodeHall(await res.text());
    },

    create({ format = "fotr_block", deckName, seatCount = 2, description = "" }) {
      return post("/hall", {
        format,
        deckName,
        timer: "default",
        desc: description,
        isPrivate: "false",
        isInviteOnly: "false",
        seatCount: String(seatCount)
      });
    },

    join(tableId, deckName) {
      return post(`/hall/${encodeURIComponent(tableId)}`, { deckName });
    },

    leave(tableId) {
      return post(`/hall/${encodeURIComponent(tableId)}/leave`, {});
    }
  };
}
