/**
 * The wire format, and nothing else.
 *
 * This is the only module that knows GEMP's event XML: the two-to-four letter
 * type codes, the attribute names, the comma-packed fields. Everything above it
 * speaks plain objects. If the server's serialisation ever changes, it changes
 * here and nowhere else.
 *
 * Mirrors com.gempukku.lotro.game.state.EventSerializer and the Type enum in
 * GameEvent. All 27 codes are mapped; an unknown code decodes to
 * { type: "UNKNOWN", code } rather than throwing, so a server ahead of this
 * client degrades instead of breaking.
 */

/** GameEvent.Type code -> name. All 27, in enum declaration order. */
export const EVENT_TYPES = Object.freeze({
  P:    "PARTICIPANTS",
  GPC:  "GAME_PHASE_CHANGE",
  TC:   "TURN_CHANGE",
  PP:   "PLAYER_POSITION",
  TP:   "TWILIGHT_POOL_UPDATE",
  PCIP: "PUT_CARD_INTO_PLAY",
  MCIP: "MOVE_CARD_IN_PLAY",
  FCIP: "FLIP_CARDS_IN_PLAY",
  RCFP: "REMOVE_CARD_FROM_PLAY",
  AA:   "ADD_ASSIGNMENT",
  RA:   "REMOVE_ASSIGNMENT",
  SS:   "START_SKIRMISH",
  RFS:  "REMOVE_FROM_SKIRMISH",
  ATS:  "ADD_TO_SKIRMISH",
  ES:   "END_SKIRMISH",
  AT:   "ADD_TOKENS",
  RT:   "REMOVE_TOKENS",
  M:    "SEND_MESSAGE",
  W:    "SEND_WARNING",
  GS:   "GAME_STATS",
  CM:   "CHAT_MESSAGE",
  EG:   "GAME_ENDED",
  CAC:  "CARD_AFFECTED_BY_CARD",
  EP:   "SHOW_CARD_ON_SCREEN",
  CA:   "FLASH_CARD_IN_PLAY",
  D:    "DECISION",
  PGS:  "PRE_GAME_SETUP"
});

/** AwaitingDecisionType -> the interaction shape the view should render. */
export const DECISION_SHAPES = Object.freeze({
  INTEGER: "number",
  MULTIPLE_CHOICE: "button",
  ACTION_CHOICE: "button",
  ARBITRARY_CARDS: "cards",
  CARD_ACTION_CHOICE: "cards",
  CARD_SELECTION: "cards",
  ASSIGN_MINIONS: "cards"
});

const str = (el, name) => (el.hasAttribute(name) ? el.getAttribute(name) : undefined);

function int(el, name) {
  const raw = str(el, name);
  if (raw === undefined || raw === "") return undefined;
  const n = parseInt(raw, 10);
  return Number.isNaN(n) ? undefined : n;
}

function bool(el, name) {
  const raw = str(el, name);
  return raw === undefined ? undefined : raw === "true";
}

function csv(el, name) {
  const raw = str(el, name);
  if (raw === undefined || raw === "") return undefined;
  return raw.split(",");
}

function csvInts(el, name) {
  const parts = csv(el, name);
  return parts && parts.map((p) => parseInt(p, 10)).filter((n) => !Number.isNaN(n));
}

/** Signet letters GameStats prefixes onto a companion's resistance. */
const SIGNETS = Object.freeze({
  A: "ARAGORN", F: "FRODO", G: "GANDALF", T: "THEODEN"
});

/**
 * charStats packs every character's numbers into one attribute:
 *   "12=7|3,15=4|2|5,18=6|3|R2,356=4|2|RF10"
 * as cardId=strength|vitality, then optionally a site number (minions) or a
 * resistance prefixed with R (characters). The two are mutually exclusive --
 * GameStats writes one or the other.
 *
 * A companion's resistance additionally carries its SIGNET as a letter, so `RF10`
 * is signet Frodo, resistance 10 -- not resistance "F10". Reading that as a plain
 * number yields NaN, which is what a live capture caught.
 */
export function parseCharStats(raw) {
  const out = {};
  if (!raw) return out;
  for (const entry of raw.split(",")) {
    const [idPart, statPart] = entry.split("=");
    if (statPart === undefined) continue;
    const id = parseInt(idPart, 10);
    if (Number.isNaN(id)) continue;
    const bits = statPart.split("|");
    const stats = {
      strength: parseInt(bits[0], 10),
      vitality: parseInt(bits[1], 10)
    };
    if (bits[2] !== undefined && bits[2] !== "") {
      if (bits[2].startsWith("R")) {
        const m = /^([AFGT])?(-?\d+)$/.exec(bits[2].slice(1));
        if (m) {
          if (m[1]) stats.signet = SIGNETS[m[1]];
          stats.resistance = parseInt(m[2], 10);
        }
      } else {
        const n = parseInt(bits[2], 10);
        if (!Number.isNaN(n)) stats.siteNumber = n;
      }
    }
    out[id] = stats;
  }
  return out;
}

function parseDecision(el) {
  const type = str(el, "decisionType");
  const parameters = {};
  for (const p of el.getElementsByTagName("parameter")) {
    const name = p.getAttribute("name");
    (parameters[name] ||= []).push(p.getAttribute("value"));
  }
  return {
    id: int(el, "id"),
    decisionType: type,
    shape: DECISION_SHAPES[type] || "button",
    text: str(el, "text"),
    parameters
  };
}

function parseGameStats(el) {
  const zoneSizes = {};
  for (const pz of el.getElementsByTagName("playerZones")) {
    const player = pz.getAttribute("name");
    const sizes = {};
    for (const attr of pz.attributes) {
      if (attr.name !== "name") sizes[attr.name] = parseInt(attr.value, 10);
    }
    zoneSizes[player] = sizes;
  }

  const collect = (tag) => {
    const out = {};
    for (const node of el.getElementsByTagName(tag)) {
      out[node.getAttribute("name")] = parseInt(node.getAttribute("value"), 10);
    }
    return out;
  };

  return {
    wearingRing: bool(el, "wearingRing"),
    fellowshipArchery: int(el, "fellowshipArchery"),
    shadowArchery: int(el, "shadowArchery"),
    fellowshipStrength: int(el, "fellowshipStrength"),
    shadowStrength: int(el, "shadowStrength"),
    fellowshipDamageBonus: int(el, "fellowshipDamageBonus"),
    shadowDamageBonus: int(el, "shadowDamageBonus"),
    fpOverwhelmed: bool(el, "fpOverwhelmed"),
    moveLimit: int(el, "moveLimit"),
    moveCount: int(el, "moveCount"),
    initiative: str(el, "initiative"),
    ruleOfFour: int(el, "ruleof4"),
    charStats: parseCharStats(str(el, "charStats")),
    hindered: csvInts(el, "hindered") || [],
    zoneSizes,
    threats: collect("threats"),
    threatTotals: collect("threatTotals")
  };
}

/** One <ge> element -> a plain event object. */
export function decodeEvent(el) {
  const code = el.getAttribute("type");
  const type = EVENT_TYPES[code];
  if (!type) return { type: "UNKNOWN", code };

  const event = { type, timestamp: str(el, "timestamp") };

  const put = (key, value) => {
    if (value !== undefined) event[key] = value;
  };

  put("blueprintId", str(el, "blueprintId"));
  put("cardId", int(el, "cardId"));
  put("index", int(el, "index"));
  put("controllerId", str(el, "controllerId"));
  put("participantId", str(el, "participantId"));
  put("allParticipantIds", csv(el, "allParticipantIds"));
  put("phase", str(el, "phase"));
  put("zone", str(el, "zone"));
  put("token", str(el, "token"));
  put("count", int(el, "count"));
  put("otherCardIds", csvInts(el, "otherCardIds"));
  put("message", str(el, "message"));
  put("version", int(el, "version"));
  put("side", str(el, "side"));      // declared by GameEvent but never populated yet

  // `hindered` is a boolean on card events and a comma list on GAME_STATS.
  if (type !== "GAME_STATS") put("hindered", bool(el, "hindered"));

  if (el.hasAttribute("targetCardId")) {
    event.target = { cardId: int(el, "targetCardId"), type: str(el, "targetType") };
  }
  if (type === "PARTICIPANTS") put("discardPublic", bool(el, "discardPublic"));
  if (type === "DECISION" || el.hasAttribute("decisionType")) event.decision = parseDecision(el);
  if (el.getElementsByTagName("playerZones").length || el.hasAttribute("moveLimit")) {
    event.gameStats = parseGameStats(el);
  }
  if (type === "PRE_GAME_SETUP") {
    event.preGame = {
      summary: str(el, "summary"),
      notes: str(el, "notes"),
      maps: str(el, "maps"),
      metaSites: str(el, "metaSites")
    };
  }
  return event;
}

function parse(xml) {
  const doc =
    typeof xml === "string"
      ? new DOMParser().parseFromString(xml, "text/xml")
      : xml;
  const error = doc.getElementsByTagName("parsererror")[0];
  if (error) throw new Error("Malformed event XML: " + error.textContent.trim());
  return doc;
}

/**
 * A whole response -> events, in order.
 * Accepts an XML string or an already-parsed Document.
 */
export function decodeEvents(xml) {
  return Array.from(parse(xml).getElementsByTagName("ge"), decodeEvent);
}

/**
 * The whole response, including what does NOT live inside a <ge>.
 *
 * The root carries the channel number and a <clocks> block of every player's
 * remaining seconds. Those clocks matter more than they look: a client only ever
 * receives decisions addressed to itself -- GameCommunicationChannel appends a
 * DECISION event only `if (playerId.equals(_self))` -- so the running clock is
 * the ONLY signal of which seat the server is currently waiting on.
 */
export function decodeResponse(xml) {
  const doc = parse(xml);
  const root = doc.documentElement;

  // The clocks block mixes per-player time banks with a pseudo-entry named
  // `decisionClock` -- the countdown on the current decision, not a seat. Left
  // in, it would appear as a sixth player at the table.
  const clocks = {};
  let decisionClock = null;
  for (const node of doc.getElementsByTagName("clock")) {
    const who = node.getAttribute("participantId");
    const seconds = parseInt(node.textContent, 10);
    if (!who || Number.isNaN(seconds)) continue;
    if (who === "decisionClock") decisionClock = seconds;
    else clocks[who] = seconds;
  }

  const cn = root?.getAttribute("cn");
  return {
    channelNumber: cn == null ? null : parseInt(cn, 10),
    events: Array.from(doc.getElementsByTagName("ge"), decodeEvent),
    clocks,
    decisionClock
  };
}
