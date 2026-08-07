/**
 * A five-player game as an event stream, in the server's own wire format.
 *
 * This is not a mock of the state -- it is XML the real decoder parses, so the
 * board below it is driven by exactly the path a live game takes. When the
 * transport lands, this file is the only thing that gets replaced.
 *
 * Once a real game can be recorded, capture a genuine stream and diff against
 * this; the harness already works that way for the engine.
 */

export const PLAYERS = ["carol", "asdf", "dave", "qwer", "Librarian"];

let nextId = 1;
const ids = {};
function id(key) {
  if (!ids[key]) ids[key] = nextId++;
  return ids[key];
}

const put = (key, zone, owner) =>
  `<ge type="PCIP" cardId="${id(key)}" blueprintId="1_${id(key)}" zone="${zone}" participantId="${owner}"/>`;

const tokens = (key, token, count) =>
  `<ge type="AT" cardId="${id(key)}" token="${token}" count="${count}"/>`;

/** Opening: seats, roles, and everyone's fellowship on the table. */
export function setup() {
  const seats = PLAYERS.map((p, i) => `<ge type="PP" participantId="${p}" index="${i}"/>`).join("");

  const boards = PLAYERS.flatMap((p, seat) => {
    const companions = 3 + (seat % 2);
    const out = [];
    for (let c = 0; c < companions; c++) out.push(put(`${p}-fp-${c}`, "FREE_CHARACTERS", p));
    out.push(put(`${p}-sup-0`, "SUPPORT", p));
    if (seat % 2 === 0) out.push(put(`${p}-sup-1`, "SUPPORT", p));
    return out;
  }).join("");

  const hand = Array.from({ length: 8 }, (_, i) => put(`hand-${i}`, "HAND", "asdf")).join("");

  // Real site blueprint ids, taken from a captured five-player game. They used
  // to be `site_1`..`site_4`, which no image derivation can resolve -- so the
  // fixture drew sites with no art and could not have caught the preview
  // showing a bare label for every one of them.
  const SITE_IDS = ["1_320", "1_327", "1_340", "1_346"];
  const sites = ["carol", "Librarian", "dave", "qwer"]
    .map((owner, i) => `<ge type="PCIP" cardId="${id(`site-${i}`)}" blueprintId="${SITE_IDS[i]}" zone="ADVENTURE_PATH" participantId="${owner}" index="${i + 1}"/>`)
    .join("");

  return `<events>
    <ge type="P" allParticipantIds="${PLAYERS.join(",")}" discardPublic="false"/>
    ${seats}
    <ge type="TC" participantId="carol"/>
    <ge type="GPC" phase="Fellowship"/>
    <ge type="TP" count="4"/>
    ${boards}${hand}${sites}
    ${tokens("carol-fp-1", "WOUND", 1)}
    ${tokens("carol-fp-2", "WOUND", 2)}
    ${tokens("carol-fp-2", "ROHAN", 3)}
    ${tokens("asdf-fp-0", "WOUND", 1)}
    ${stats(4, 1)}
  </events>`;
}

/** GAME_STATS carries strength/vitality for every character, packed. */
function stats(moveCount, moveLimit = 4, extra = {}) {
  const chars = Object.entries(ids)
    .filter(([k]) => k.includes("-fp-") || k.startsWith("minion-"))
    .map(([k, cardId]) => {
      const strength = 4 + (cardId % 6);
      const vitality = 2 + (cardId % 3);
      return `${cardId}=${strength}|${vitality}`;
    })
    .join(",");

  const zones = PLAYERS.map(
    (p) => `<playerZones name="${p}" HAND="${p === "asdf" ? 8 : 6 + (p.length % 3)}" DECK="41"/>`
  ).join("");
  const threats = PLAYERS.map((p) => `<threats name="${p}" value="${p === "carol" ? 2 : 0}"/>`).join("");

  return `<ge type="GS" moveLimit="${moveLimit}" moveCount="${moveCount}"
      initiative="${extra.initiative ?? "FREE_PEOPLE"}" shadowArchery="${extra.archery ?? 0}"
      charStats="${chars}">${zones}${threats}</ge>`;
}

/** The shadow phase: three seats commit minions against carol's fellowship. */
export function shadowPhase() {
  const minions = [
    ["dave", 3], ["qwer", 1], ["Librarian", 2], ["asdf", 1]
  ].flatMap(([owner, n]) =>
    Array.from({ length: n }, (_, i) => put(`minion-${owner}-${i}`, "SHADOW_CHARACTERS", owner))
  ).join("");

  return `<events>
    <ge type="GPC" phase="Shadow"/>
    <ge type="TP" count="10"/>
    ${minions}
    ${tokens(`minion-dave-0`, "WOUND", 1)}
    ${tokens(`minion-Librarian-1`, "SAURON", 2)}
    ${stats(1, 4, { archery: 3 })}
  </events>`;
}

/** Assignment, then a skirmish that pulls in two seats' minions at once. */
export function skirmish() {
  return `<events>
    <ge type="GPC" phase="Assignment"/>
    <ge type="AA" cardId="${id("carol-fp-0")}" otherCardIds="${id("minion-dave-0")},${id("minion-dave-1")}"/>
    <ge type="AA" cardId="${id("carol-fp-1")}" otherCardIds="${id("minion-asdf-0")}"/>
    <ge type="GPC" phase="Skirmish"/>
    <ge type="SS" cardId="${id("carol-fp-0")}" otherCardIds="${id("minion-dave-0")},${id("minion-dave-1")}"/>
  </events>`;
}

export function endSkirmish() {
  return `<events>
    <ge type="ES"/>
    <ge type="GPC" phase="Regroup"/>
  </events>`;
}

/** A decision addressed to you, with three playable cards lit. */
export function decision() {
  const cards = [0, 3, 6].map((i) => `<parameter name="cardId" value="${id(`hand-${i}`)}"/>`).join("");
  return `<events>
    <ge type="D" id="7" decisionType="CARD_ACTION_CHOICE" text="Play a card or use a special ability, or pass">
      ${cards}
    </ge>
  </events>`;
}

/** Regroup on the stay branch: every minion is discarded and the turn ends. */
export function regroupStay() {
  const gone = Object.entries(ids)
    .filter(([k]) => k.startsWith("minion-"))
    .map(([, v]) => v)
    .join(",");
  return `<events>
    <ge type="RCFP" otherCardIds="${gone}" participantId="carol"/>
    <ge type="GPC" phase="Fellowship"/>
    <ge type="TC" participantId="asdf"/>
    <ge type="TP" count="2"/>
  </events>`;
}
