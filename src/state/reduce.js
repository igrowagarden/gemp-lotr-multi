/**
 * (state, event) -> state. Pure, no DOM, no network.
 *
 * Everything the board draws is derived from here, which is what makes the
 * client testable without a browser and what makes extra windows free: they are
 * additional readers of one state, never additional clients.
 *
 * Semantics are taken from GameCommunicationChannel, not guessed. The ones that
 * would otherwise be surprising are commented at their case.
 */

import { parseMetaSites } from "../model/images.js";


export function initialState(viewerId, { spectating = false } = {}) {
  return Object.freeze({
    viewerId,
    spectating,
    players: [],          // seating order, from PARTICIPANTS
    sitePositions: {},    // playerId -> the SITE NUMBER they stand on
    discardPublic: false,
    currentPlayer: null,  // the Free Peoples player this turn
    phase: null,
    twilight: 0,
    cards: {},            // cardId -> card
    assignments: {},      // free-peoples cardId -> [minion cardIds]
    pending: {},          // same, for assignments not yet committed
    skirmish: null,       // { fpCardId, minionIds } while one is running
    inactive: [],         // card ids the engine has marked inactive this turn
    stats: null,          // the last GAME_STATS payload
    decision: null,
    // ONE log, in arrival order, holding both the game's commentary and what
    // people say. They were two lists rendered one after the other, which put
    // every chat line after every game line no matter when it was said.
    //
    // Arrival order rather than timestamps because the server does not date its
    // game log at all -- only chat messages carry a `date`. Interleaving is
    // therefore exact for anything said while connected, and approximate only
    // for the backlog handed over at join, which cannot be placed better than
    // "before everything that has happened since".
    log: [],
    ended: null,
    preGame: null,
    metaSites: {},
    clocks: {},        // playerId -> seconds left
    waitingOn: [],     // seats whose clock is running
    decisionClock: null // seconds left on the current decision, not a seat
  });
}

const MAX_LOG = 200;

/** Shallow-clone a card and apply changes. Cards are plain and never mutated. */
function patchCard(state, cardId, changes) {
  const existing = state.cards[cardId];
  if (!existing) return state.cards;
  return { ...state.cards, [cardId]: { ...existing, ...changes } };
}

/** Build a card from the attributes GameEvent.card() packs. */
function cardFrom(event) {
  const card = {
    cardId: event.cardId,
    blueprintId: event.blueprintId,
    zone: event.zone,
    // GameEvent.card() writes the owner into participantId.
    owner: event.participantId,
    controller: event.controllerId ?? event.participantId,
    // The wire attribute is called "hindered" but carries isFlipped(). The same
    // attribute name means a list of card ids on GAME_STATS. Name it honestly
    // here and never propagate the confusion upward.
    flipped: event.hindered === true,
    tokens: {},
    inSkirmish: false,
    assignedTo: null,
    // PUT_CARD_INTO_PLAY carries index(card.getSiteNumber()), which is what
    // orders the adventure path -- not the card id, which is arrival order.
    siteNumber: event.index
  };
  if (event.target) {
    if (event.target.type === "attached") card.attachedTo = event.target.cardId;
    if (event.target.type === "stacked") card.stackedOn = event.target.cardId;
  }
  return card;
}

function addTokens(state, event, sign) {
  const card = state.cards[event.cardId];
  if (!card || !event.token) return state;
  const count = event.count ?? 1;
  const next = (card.tokens[event.token] ?? 0) + sign * count;
  const tokens = { ...card.tokens };
  if (next > 0) tokens[event.token] = next;
  else delete tokens[event.token];
  return { ...state, cards: patchCard(state, event.cardId, { tokens }) };
}

function markSkirmish(state, ids, inSkirmish) {
  const cards = { ...state.cards };
  for (const id of ids) {
    if (cards[id]) cards[id] = { ...cards[id], inSkirmish };
  }
  return cards;
}

function appendLog(list, entry) {
  const next = list.concat(entry);
  return next.length > MAX_LOG ? next.slice(next.length - MAX_LOG) : next;
}

export function reduce(state, event) {
  switch (event.type) {
    // PRE_GAME_SETUP also carries allParticipantIds, and during bidding it
    // arrives BEFORE any PARTICIPANTS event -- so treating it as presentation
    // only leaves the table empty for the whole pre-game. Found by connecting to
    // a real server, not by reading the serialiser.
    case "PRE_GAME_SETUP":
    case "PARTICIPANTS": {
      const players = event.allParticipantIds ?? state.players;
      return {
        ...state,
        players,
        preGame: event.preGame ?? state.preGame,
        // Which site each meta-site modifier should be DRAWN as. Decoded since
        // the beginning and never used, so those cards showed their own art
        // instead of the site they modify.
        // NESTED under preGame, not a top-level attribute -- `event.metaSites`
        // is undefined and would silently leave every modifier drawn as itself.
        metaSites: event.preGame?.metaSites
          ? parseMetaSites(event.preGame.metaSites) : state.metaSites,
        discardPublic: event.discardPublic ?? state.discardPublic,
        // A viewer whose id is not among the players is a spectator. The server
        // has already decided this by never sending them a hand; we only need to
        // agree with it.
        spectating: state.spectating || (players.length > 0 && !players.includes(state.viewerId))
      };
    }

    /**
     * Not a seat index -- the site number this player is standing on. The
     * reference client feeds it straight into advPathGroup.setPositions() and
     * matches it against each site's siteNumber. Using it to order the seat
     * strip would re-sort the table by how far along the path people are.
     */
    case "PLAYER_POSITION":
      return {
        ...state,
        sitePositions: { ...state.sitePositions, [event.participantId]: event.index }
      };

    case "GAME_PHASE_CHANGE":
      return { ...state, phase: event.phase };

    // participantId is the new current player; otherCardIds are cards the engine
    // has just marked inactive for the turn.
    case "TURN_CHANGE":
      return { ...state, currentPlayer: event.participantId, inactive: event.otherCardIds ?? [] };

    case "TWILIGHT_POOL_UPDATE":
      return { ...state, twilight: event.count ?? 0 };

    case "PUT_CARD_INTO_PLAY": {
      const card = cardFrom(event);
      return { ...state, cards: { ...state.cards, [card.cardId]: card } };
    }

    // A move re-sends the whole card, so zone and owner are simply refreshed.
    // Tokens and skirmish state are ours and must survive the move.
    case "MOVE_CARD_IN_PLAY": {
      const existing = state.cards[event.cardId];
      const moved = cardFrom(event);
      const merged = existing
        ? { ...moved, tokens: existing.tokens, inSkirmish: existing.inSkirmish, assignedTo: existing.assignedTo }
        : moved;
      return { ...state, cards: { ...state.cards, [event.cardId]: merged } };
    }

    case "FLIP_CARDS_IN_PLAY": {
      const cards = { ...state.cards };
      for (const id of event.otherCardIds ?? []) {
        if (cards[id]) cards[id] = { ...cards[id], flipped: event.hindered === true };
      }
      return { ...state, cards };
    }

    case "REMOVE_CARD_FROM_PLAY": {
      const cards = { ...state.cards };
      for (const id of event.otherCardIds ?? []) delete cards[id];
      return { ...state, cards };
    }

    // cardId is the Free Peoples card; otherCardIds are the minions on it. A
    // NEGATIVE cardId means a pending assignment -- one the player has proposed
    // but not committed. Losing that distinction would show speculative
    // assignments as real ones.
    case "ADD_ASSIGNMENT": {
      const fp = event.cardId;
      const minions = event.otherCardIds ?? [];
      if (fp < 0) {
        const key = -fp;
        const existing = state.pending[key] ?? [];
        return { ...state, pending: { ...state.pending, [key]: existing.concat(minions) } };
      }
      const cards = { ...state.cards };
      for (const id of minions) {
        if (cards[id]) cards[id] = { ...cards[id], assignedTo: fp };
      }
      return { ...state, cards, assignments: { ...state.assignments, [fp]: minions } };
    }

    case "REMOVE_ASSIGNMENT": {
      const fp = event.cardId;
      if (fp < 0) {
        const key = -fp;
        const drop = new Set(event.otherCardIds ?? []);
        const kept = (state.pending[key] ?? []).filter((id) => !drop.has(id));
        const pending = { ...state.pending };
        if (kept.length) pending[key] = kept;
        else delete pending[key];
        return { ...state, pending };
      }
      const cards = { ...state.cards };
      for (const id of state.assignments[fp] ?? []) {
        if (cards[id]) cards[id] = { ...cards[id], assignedTo: null };
      }
      const assignments = { ...state.assignments };
      delete assignments[fp];
      return { ...state, cards, assignments };
    }

    // cardId is optional: a skirmish can start with no Free Peoples card.
    case "START_SKIRMISH": {
      const minionIds = event.otherCardIds ?? [];
      const involved = event.cardId != null ? minionIds.concat(event.cardId) : minionIds;
      return {
        ...state,
        skirmish: { fpCardId: event.cardId ?? null, minionIds },
        cards: markSkirmish(state, involved, true)
      };
    }

    case "ADD_TO_SKIRMISH":
      return { ...state, cards: markSkirmish(state, [event.cardId], true) };

    case "REMOVE_FROM_SKIRMISH":
      return { ...state, cards: markSkirmish(state, [event.cardId], false) };

    case "END_SKIRMISH": {
      const cards = { ...state.cards };
      for (const id of Object.keys(cards)) {
        if (cards[id].inSkirmish) cards[id] = { ...cards[id], inSkirmish: false };
      }
      return { ...state, skirmish: null, cards };
    }

    case "ADD_TOKENS":
      return addTokens(state, event, +1);

    case "REMOVE_TOKENS":
      return addTokens(state, event, -1);

    case "GAME_STATS":
      return { ...state, stats: event.gameStats ?? state.stats };

    // A decision only ever reaches the player it is addressed to, so this is
    // always ours. `participantId` is kept anyway so the view never has to
    // assume it.
    case "DECISION":
      return {
        ...state,
        decision: event.decision
          ? { ...event.decision, forPlayer: event.participantId ?? state.viewerId }
          : null
      };

    /**
     * Client-side, not a wire event: assembled from the <clocks> block on the
     * response root. Whoever's clock has gone down since the last poll is the
     * seat the server is waiting on -- the only way to know, because decisions
     * for other players are never sent to us.
     */
    case "CLOCK_UPDATE": {
      const clocks = event.clocks ?? {};
      const ticking = Object.keys(clocks).filter(
        (p) => state.clocks[p] !== undefined && clocks[p] < state.clocks[p]
      );
      return {
        ...state,
        clocks,
        decisionClock: event.decisionClock ?? null,
        // Keep the last known answer when nothing moved, so the marker does not
        // flicker off between polls in which no time passed.
        waitingOn: ticking.length ? ticking : state.waitingOn
      };
    }

    case "SEND_MESSAGE":
    case "SEND_WARNING":
      return {
        ...state,
        log: appendLog(state.log, {
          kind: "game", text: event.message, warning: event.type === "SEND_WARNING"
        })
      };

    // Fed by net/chat.js, not by the game channel -- the server never emits the
    // CM event this used to wait for. `from` and `system` are carried because a
    // chat line says who spoke, and because an unread badge must be able to
    // ignore the room narrating itself.
    case "CHAT_MESSAGE":
      return {
        ...state,
        log: appendLog(state.log, {
          kind: "chat",
          text: event.message,
          from: event.from ?? null,
          system: event.system === true,
          date: event.date ?? null
        })
      };

    case "GAME_ENDED":
      return { ...state, ended: { message: event.message ?? null }, decision: null };

    // Presentation-only: highlights and flashes. They belong to the view layer's
    // transient effects, not to game state, so the reducer deliberately ignores
    // them rather than storing state nothing reads.
    case "CARD_AFFECTED_BY_CARD":
    case "FLASH_CARD_IN_PLAY":
    case "SHOW_CARD_ON_SCREEN":
    case "UNKNOWN":
      return state;

    default:
      return state;
  }
}

export function reduceAll(state, events) {
  return events.reduce(reduce, state);
}

/* ------------------------------------------------------------- selectors -- */

export const allCards = (state) => Object.values(state.cards);

/** The Free Peoples player this turn: the one whose fellowship is contested. */
export const fpId = (state) => state.currentPlayer;

/**
 * Seats in table order, eliminated players included -- they still hold cards.
 * The order is the one PARTICIPANTS sent; nothing else reorders the table.
 */
export function seats(state) {
  return state.players.slice();
}

/** Which site a player is standing on. */
export const siteOf = (state, playerId) => state.sitePositions[playerId];

export function handSize(state, playerId) {
  return state.stats?.zoneSizes?.[playerId]?.HAND ?? 0;
}

export function threats(state, playerId) {
  return state.stats?.threats?.[playerId] ?? 0;
}

export const clockOf = (state, playerId) => state.clocks[playerId];

/** Is the server waiting on this seat? Derived from the running clock. */
export const isWaitingOn = (state, playerId) => state.waitingOn.includes(playerId);

/** mm:ss, the way a game clock is read. */
export function formatClock(seconds) {
  if (seconds == null) return "";
  const m = Math.floor(Math.max(seconds, 0) / 60);
  const s = Math.max(seconds, 0) % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

/** Minions in play for a seat -- what the shared band draws, per owner. */
export function minionsOf(state, playerId) {
  return allCards(state).filter(
    (c) => c.zone === "SHADOW_CHARACTERS" && c.owner === playerId
  );
}

/** The game's own commentary. */
export const gameLog = (state) => state.log.filter((e) => e.kind === "game");

/** What people said, including the room's own notices. */
export const chatLog = (state) => state.log.filter((e) => e.kind === "chat");

/**
 * Chat lines from an actual person. What an unread badge should count: the game
 * log narrates every action, so counting it leaves the badge permanently lit,
 * and the room's own "X joined" notices are not somebody talking either.
 */
export const spokenTo = (state) =>
  state.log.filter((e) => e.kind === "chat" && !e.system);

export function cardStats(state, cardId) {
  return state.stats?.charStats?.[cardId] ?? null;
}

/** The adventure path, in site order. Only what has actually been played. */
export function sitePath(state) {
  return allCards(state)
    .filter((c) => c.zone === "ADVENTURE_PATH")
    .sort((a, b) => (a.siteNumber ?? 0) - (b.siteNumber ?? 0));
}

/**
 * The site the contested fellowship stands on. charStats' siteNumber is the
 * MINION site number, not this -- PLAYER_POSITION is the source.
 */
export function currentSite(state) {
  const n = state.sitePositions[state.currentPlayer];
  if (n == null) return null;
  return sitePath(state).find((c) => c.siteNumber === n) ?? null;
}

/**
 * Cards in a player's pile that we actually hold.
 *
 * GameCommunicationChannel sends a non-public card only to its owner (or to
 * everyone when the format makes discards public), so this is complete for your
 * own piles and usually empty for everyone else's. `pileSize` is always right,
 * because it comes from GameStats zone sizes rather than from cards -- which is
 * why the viewer shows a count even when it holds nothing to draw.
 */
export function pileOf(state, playerId, zone) {
  return allCards(state).filter((c) => c.zone === zone && c.owner === playerId);
}

export function pileSize(state, playerId, zone) {
  return state.stats?.zoneSizes?.[playerId]?.[zone] ?? 0;
}

export const wounds = (card) => card.tokens?.WOUND ?? 0;
export const burdens = (card) => card.tokens?.BURDEN ?? 0;

/** Culture tokens only -- wounds and burdens are drawn separately. */
export function cultureTokens(card) {
  let total = 0;
  for (const [name, n] of Object.entries(card.tokens ?? {})) {
    if (name !== "WOUND" && name !== "BURDEN") total += n;
  }
  return total;
}

/** The context the zone registry needs, assembled in one place. */
export function bandContext(state, focusId) {
  return {
    viewerId: state.viewerId,
    focusId,
    fpId: fpId(state),
    spectating: state.spectating,
    skirmishing: state.skirmish != null
  };
}
