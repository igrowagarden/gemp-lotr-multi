/**
 * The one connection.
 *
 * GEMP's game channel is a long poll:
 *   GET  /game/{id}?participantId=X          -> full state, and the channel
 *                                               number in the root's `cn`
 *   POST /game/{id}  participantId, channelNumber
 *                    [+ decisionId, decisionValue]
 *                                            -> blocks until there are events
 *
 * Answering a decision is not a separate call: it rides on the same POST that
 * polls. So an answer must interrupt the poll in flight rather than wait for it.
 *
 * Status codes carry meaning and must not be treated alike:
 *   409  another client claimed this player's channel (SubscriptionConflict).
 *        FATAL. Retrying would fight the other client for the seat, which is
 *        exactly how this project produced stuck bots. Stop and report.
 *   410  the channel expired (SubscriptionExpired). Re-handshake for a new one.
 *   403  private information refused.  404  no such game.
 *
 * Only this module and net/protocol.js know anything about HTTP or XML.
 */

import { decodeResponse } from "./protocol.js";

export const TransportStatus = Object.freeze({
  IDLE: "idle",
  CONNECTING: "connecting",
  LIVE: "live",
  RETRYING: "retrying",
  CONFLICT: "conflict",
  STOPPED: "stopped",
  FAILED: "failed"
});

const BACKOFF_MS = [500, 1000, 2000, 4000, 8000];

export function createTransport({
  baseUrl = "/gemp-lotr-server",
  gameId,
  participantId,
  onEvents,
  // Optional: the untouched response text, for anything that needs to parse it
  // its own way -- the differential harness feeds the SAME xml to the old
  // client, and decoded events would already have this client's reading baked
  // in, which is the one thing a comparison must not do.
  onRaw = null,
  onStatus = () => {},
  fetchImpl = (...args) => fetch(...args),
  sleep = (ms) => new Promise((r) => setTimeout(r, ms))
}) {
  let running = false;
  let channelNumber = null;
  let pendingAnswer = null;
  let controller = null;
  let failures = 0;
  let status = TransportStatus.IDLE;

  const url = `${baseUrl}/game/${encodeURIComponent(gameId)}`;

  function setStatus(next, detail) {
    if (status === next) return;
    status = next;
    onStatus(next, detail);
  }

  /** An HTTP status we must not retry through. */
  function fatal(code) {
    return code === 409 || code === 403 || code === 404;
  }

  async function handshake() {
    setStatus(TransportStatus.CONNECTING);
    const res = await fetchImpl(`${url}?participantId=${encodeURIComponent(participantId)}`, {
      method: "GET",
      credentials: "same-origin",
      headers: { Accept: "text/xml" }
    });
    if (!res.ok) throw httpError(res.status);

    const text = await res.text();
    const { channelNumber: cn } = decodeResponse(text);
    channelNumber = cn ?? 0;
    deliver(text);
    setStatus(TransportStatus.LIVE);
  }

  function httpError(code) {
    const err = new Error(`HTTP ${code}`);
    err.status = code;
    return err;
  }

  /**
   * The clocks live on the response root, not inside a <ge>, so they are folded
   * in as a synthetic CLOCK_UPDATE. They are the only signal of which seat the
   * server is waiting on: a client is never sent another player's decision.
   */
  function deliver(xml) {
    onRaw?.(xml);
    const { events, clocks, decisionClock } = decodeResponse(xml);
    const batch = Object.keys(clocks).length
      ? events.concat({ type: "CLOCK_UPDATE", clocks, decisionClock })
      : events;
    if (batch.length) onEvents(batch);
    return batch.length;
  }

  async function poll() {
    const answer = pendingAnswer;
    pendingAnswer = null;

    const body = new URLSearchParams({
      participantId,
      channelNumber: String(channelNumber)
    });
    if (answer) {
      body.set("decisionId", String(answer.decisionId));
      body.set("decisionValue", answer.value);
    }

    controller = new AbortController();
    let res;
    try {
      res = await fetchImpl(url, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
        signal: controller.signal
      });
    } catch (err) {
      // An answer aborts the poll in flight on purpose; that is not a failure.
      if (err.name === "AbortError") {
        if (answer && !pendingAnswer) pendingAnswer = answer;
        return true;
      }
      throw err;
    } finally {
      controller = null;
    }

    if (!res.ok) {
      // The answer was never accepted, so keep it for the retry.
      if (answer && !pendingAnswer && !fatal(res.status)) pendingAnswer = answer;
      throw httpError(res.status);
    }

    deliver(await res.text());
    return true;
  }

  async function run() {
    try {
      await handshake();
    } catch (err) {
      setStatus(err.status === 409 ? TransportStatus.CONFLICT : TransportStatus.FAILED, err);
      running = false;
      return;
    }

    while (running) {
      try {
        await poll();
        failures = 0;
        setStatus(TransportStatus.LIVE);
      } catch (err) {
        if (!running) break;

        if (err.status === 409) {
          // Someone else is on this seat. Backing off and retrying would take
          // the channel back off them and neither client would keep it.
          setStatus(TransportStatus.CONFLICT, err);
          running = false;
          break;
        }
        if (err.status === 410) {
          // The channel expired; a fresh one is legitimate and not a conflict.
          try {
            await handshake();
            continue;
          } catch (again) {
            setStatus(again.status === 409 ? TransportStatus.CONFLICT : TransportStatus.FAILED, again);
            running = false;
            break;
          }
        }
        if (fatal(err.status)) {
          setStatus(TransportStatus.FAILED, err);
          running = false;
          break;
        }

        const wait = BACKOFF_MS[Math.min(failures, BACKOFF_MS.length - 1)];
        failures++;
        setStatus(TransportStatus.RETRYING, err);
        await sleep(wait);
      }
    }
    if (status !== TransportStatus.CONFLICT && status !== TransportStatus.FAILED) {
      setStatus(TransportStatus.STOPPED);
    }
  }

  return {
    start() {
      if (running) return;
      running = true;
      failures = 0;
      run();
    },

    stop() {
      running = false;
      if (controller) controller.abort();
      setStatus(TransportStatus.STOPPED);
    },

    /**
     * Answer the pending decision. Interrupts the poll in flight so the answer
     * goes now rather than after the server's next timeout.
     */
    answer(decisionId, value) {
      pendingAnswer = { decisionId, value: String(value) };
      if (controller) controller.abort();
    },

    concede() {
      return fetchImpl(`${url}/concede`, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ participantId })
      });
    },

    cardInfo(cardId) {
      const q = new URLSearchParams({ cardId: String(cardId), participantId });
      return fetchImpl(`${url}/cardInfo?${q}`, {
        method: "GET",
        credentials: "same-origin"
      }).then((r) => (r.ok ? r.text() : Promise.reject(httpError(r.status))));
    },

    get status() { return status; },
    get channelNumber() { return channelNumber; }
  };
}
