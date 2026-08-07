/**
 * The decision shapes the GUI has to handle, as data.
 *
 * WHY THIS EXISTS. 400 games and 2000 decks came back clean overnight, and the
 * `max=0` bug the run was meant to confirm was never once exercised: measuring
 * afterwards found 59 CARD_SELECTION decisions across six formats and not a
 * single one with `max=0`. Random game walks cannot reliably reach rare decision
 * shapes, because the GAME state space is astronomical while the DECISION shape
 * space is tiny. This file is that tiny space, written down.
 *
 * WHERE THE SHAPES COME FROM. Not invented. Two sources, both authoritative:
 *
 *   the schema      the `setParam` calls in the engine's eight decision classes
 *                   (gemp-lotr-logic/.../logic/decisions/) are the complete
 *                   parameter alphabet -- eleven names, no more:
 *                     actionId actionText blueprintId cardId defaultValue
 *                     freeCharacters max min minions results selectable
 *   the boundaries  the conditionals inside each class's own validator. When
 *                   `CardsSelectionDecision.getSelectedCardsByResponse` branches
 *                   on `_minimum == 0`, on `length < _minimum || > _maximum`,
 *                   and on `result.contains(card)`, those three branches ARE the
 *                   boundary cases. `max=0` is one of them and cost a session.
 *
 * THE SECOND AXIS. Shape alone is not enough, and this project already paid for
 * that lesson twice: actions on ATTACHED cards and actions on SITES were both
 * unreachable, and both were IDENTICAL decision shapes on the wire. What
 * differed was where the referenced card was drawn. So every card-referencing
 * shape is also run against each placement class in `WHERE` below. That is the
 * difference between testing the protocol and testing the GUI.
 *
 * A case that is expected to differ is not a failure -- see `expect`.
 */

/**
 * Card ids with KNOWN placements, so a decision can point at a specific class of
 * card rather than at whatever a game happened to deal.
 *
 * The last two are the interesting ones. `SITE` and `ATTACHED` are where the two
 * real bugs the differential has found so far lived, and `ABSENT` is a card the
 * decision names that is on no board at all -- the engine never emits that, but
 * it is the difference between "offers nothing" and "crashes".
 */
export const WHERE = Object.freeze({
  SELF_FREE: "101",      // your own free character, in play
  SELF_FREE_2: "102",
  SELF_SUPPORT: "105",   // your support area
  MINION: "110",         // a shadow character -- the shared minion band
  MINION_2: "111",
  CONTESTED: "120",      // the Free Peoples player's fellowship, always drawn
  ATTACHED: "130",       // attached to 101, drawn ON its host, not in a band
  HAND: "140",           // in your hand
  HAND_2: "141",
  SITE: "150",           // the adventure path -- a flyout window, not a band
  ABSENT: "999"          // named by the decision, present nowhere
});

export const VIEWER = "asdf";
const PLAYERS = ["carol", "asdf", "dave", "qwer", "Librarian"];

const esc = (s) =>
  String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
           .replace(/>/g, "&gt;").replace(/"/g, "&quot;");

/**
 * The board every case starts from, as the wire format both clients decode.
 *
 * Deliberately hand-built rather than reused from `fixture.js`: the whole point
 * is that each id's placement is CHOSEN, so a case can say "put the action on
 * the attached card" and mean it.
 */
export function baseBoard() {
  const put = (id, zone, owner, extra = "") =>
    `<ge type="PCIP" cardId="${id}" blueprintId="1_${id}" zone="${zone}" participantId="${owner}"${extra}/>`;

  const seats = PLAYERS.map((p, i) => `<ge type="PP" participantId="${p}" index="${i}"/>`).join("");

  // charStats must name every character or the new client renders no numbers and
  // the old one can throw reading them. Cheap to include; expensive to debug.
  const chars = ["101", "102", "110", "111", "120"]
    .map((c) => `${c}=4|2`).join(",");
  const zones = PLAYERS.map(
    (p) => `<playerZones name="${p}" HAND="${p === VIEWER ? 8 : 6}" DECK="40"/>`
  ).join("");

  return `<events>
    <ge type="P" allParticipantIds="${PLAYERS.join(",")}" discardPublic="false"/>
    ${seats}
    <ge type="TC" participantId="carol"/>
    <ge type="GPC" phase="Shadow"/>
    <ge type="TP" count="8"/>
    ${put("101", "FREE_CHARACTERS", VIEWER)}
    ${put("102", "FREE_CHARACTERS", VIEWER)}
    ${put("105", "SUPPORT", VIEWER)}
    ${put("110", "SHADOW_CHARACTERS", "dave")}
    ${put("111", "SHADOW_CHARACTERS", "dave")}
    ${put("120", "FREE_CHARACTERS", "carol")}
    ${/* `targetType` IS NOT OPTIONAL. EventSerializer writes it in the same
          branch as `targetCardId` (EventSerializer.java:37-40) and it is the
          only thing that distinguishes attached from stacked -- the reducer
          sets `attachedTo` on `type === "attached"` and nothing else
          (state/reduce.js:81). Written without it, card 130 had no host, was
          dropped from `attachedBy` entirely, and the suite reported "the new
          client never lights an attached card" for three cases running. That
          was this fixture, not the client: the same lesson as the fixture whose
          sites carried `blueprintId="site_1"`, which no derivation could
          resolve. A fixture with an invented shape tests the shape and silently
          skips everything downstream of it. */""}
    ${put("130", "ATTACHED", VIEWER, ` targetCardId="101" targetType="attached"`)}
    ${put("140", "HAND", VIEWER)}
    ${put("141", "HAND", VIEWER)}
    ${put("150", "ADVENTURE_PATH", "carol", ` index="1"`)}
    <ge type="GS" moveLimit="4" moveCount="1" initiative="FREE_PEOPLE"
        shadowArchery="0" charStats="${chars}">${zones}</ge>
  </events>`;
}

/** One decision, in the exact form `EventSerializer.serializeDecision` writes. */
export function decisionXml({ id = "1", type, text = "test decision", params = {} }) {
  const ps = Object.entries(params).flatMap(([k, v]) =>
    (Array.isArray(v) ? v : [v]).map(
      (one) => `<parameter name="${esc(k)}" value="${esc(one)}"/>`)
  ).join("");
  return `<events><ge type="D" id="${id}" decisionType="${esc(type)}" ` +
         `text="${esc(text)}">${ps}</ge></events>`;
}

/**
 * Build a CARD_ACTION_CHOICE's four parallel arrays.
 *
 * `blueprintId` is the literal string "inPlay" when the action's source is on
 * the board. ANYTHING ELSE is a virtual action -- a card in a discard pile or
 * draw deck, or a trigger added by another card -- which has no board node and
 * must be offered in the prompt strip or it is unreachable.
 */
function actions(list) {
  return {
    actionId: list.map((a) => a.actionId),
    cardId: list.map((a) => a.cardId),
    blueprintId: list.map((a) => a.blueprint ?? "inPlay"),
    actionText: list.map((a) => a.text ?? `action ${a.actionId}`)
  };
}

/**
 * The catalogue.
 *
 * `expect` marks a case where the two clients are KNOWN to differ for a reason
 * that is not a defect -- an unreachable shape the engine cannot emit, or a
 * capability only one client has. A case with `expect` set still runs and is
 * still compared; it is reported separately instead of counting as a failure.
 * Nothing is silenced: a case that stops differing is reported too, because a
 * documented difference that quietly disappears means the note is now a lie.
 */
export const CASES = [

  // ---------------------------------------------------------------- CARD_SELECTION
  // Boundaries taken from CardsSelectionDecision.getSelectedCardsByResponse:40-49.
  // THE REFERENCE IS WRONG HERE, and this is the only case in the catalogue
  // where that is true. It sends "140" -- a card id -- for a decision whose only
  // legal answer is "". Two independent confirmations:
  //   the validator  CardsSelectionDecision:40-49 accepts "" only when min is 0
  //                  and throws on any count outside min/max, so 1 > 0 throws.
  //   the engine     it was OBSERVED refusing exactly this, 13-36 times per game,
  //                  in the session where the harness had the same bug:
  //                  `decision 1 CARD_SELECTION params {min:[0],max:[0],cardId:[250]}`
  // Presumably still rejecting real players' answers whenever a "discard one for
  // each X" resolves to zero.
  { name: "max=0 with a card still listed", type: "CARD_SELECTION",
    oracleWrong: "reference sends a card id where only \"\" is legal",
    note: "THE BUG. A discard whose count evaluator resolved to zero. The only " +
          "legal answer is the empty string, and the card is still offered.",
    params: { min: "0", max: "0", cardId: [WHERE.HAND] } },

  { name: "min=0 max=1, optional single", type: "CARD_SELECTION",
    params: { min: "0", max: "1", cardId: [WHERE.HAND, WHERE.HAND_2] } },

  { name: "min=1 max=1, one card offered", type: "CARD_SELECTION",
    params: { min: "1", max: "1", cardId: [WHERE.SELF_FREE] } },

  { name: "min=1 max=1, many offered", type: "CARD_SELECTION",
    params: { min: "1", max: "1",
              cardId: [WHERE.SELF_FREE, WHERE.SELF_FREE_2, WHERE.MINION] } },

  { name: "min=2 max=3, a real range", type: "CARD_SELECTION",
    params: { min: "2", max: "3",
              cardId: [WHERE.HAND, WHERE.HAND_2, WHERE.SELF_FREE, WHERE.SELF_FREE_2] } },

  { name: "min=max=N, exactly all of them", type: "CARD_SELECTION",
    params: { min: "2", max: "2", cardId: [WHERE.MINION, WHERE.MINION_2] } },

  { name: "empty card list", type: "CARD_SELECTION",
    note: "No cards at all. Both clients must offer nothing rather than throw.",
    params: { min: "0", max: "0", cardId: [] } },

  { name: "duplicate ids in the offer", type: "CARD_SELECTION",
    note: "The validator rejects a duplicate in the ANSWER (result.contains). " +
          "A duplicate in the OFFER is untested territory.",
    params: { min: "1", max: "1", cardId: [WHERE.SELF_FREE, WHERE.SELF_FREE] } },

  { name: "min > max, inverted range", type: "CARD_SELECTION",
    oracleWrong: "reference answers an unsatisfiable range instead of declining",
    note: "The engine should never emit this. Robustness only.",
    params: { min: "3", max: "1", cardId: [WHERE.HAND, WHERE.HAND_2] } },

  { name: "names a card on no board", type: "CARD_SELECTION",
    note: "Neither client can light a card it does not have. The interesting " +
          "question is whether either one pretends it can.",
    params: { min: "1", max: "1", cardId: [WHERE.ABSENT] } },

  // Placement axis, same shape throughout.
  { name: "selects an ATTACHED card", type: "CARD_SELECTION",
    note: "Attachments are drawn on their host, not in a band. This placement " +
          "made card ACTIONS unreachable once already.",
    params: { min: "1", max: "1", cardId: [WHERE.ATTACHED] } },

  { name: "selects a SITE", type: "CARD_SELECTION",
    note: "Sites live in the path flyout, not a band. Same class of bug.",
    params: { min: "1", max: "1", cardId: [WHERE.SITE] } },

  { name: "selects across placements at once", type: "CARD_SELECTION",
    params: { min: "1", max: "1",
              cardId: [WHERE.SELF_FREE, WHERE.ATTACHED, WHERE.SITE, WHERE.HAND] } },

  // ------------------------------------------------------------ CARD_ACTION_CHOICE
  // The commonest decision in the game -- every "play a Shadow card or pass".
  // Answers with an INDEX into the engine's action list, never a card id.
  { name: "one action on a card in play", type: "CARD_ACTION_CHOICE",
    params: actions([{ actionId: "0", cardId: WHERE.SELF_FREE }]) },

  { name: "two actions on ONE card", type: "CARD_ACTION_CHOICE",
    note: "Clicking the card is not choosing an action -- it must open a menu. " +
          "actionId/cardId are parallel arrays, so one card can appear twice.",
    params: actions([{ actionId: "0", cardId: WHERE.SELF_FREE, text: "Heal" },
                     { actionId: "1", cardId: WHERE.SELF_FREE, text: "Discard" }]) },

  { name: "action on an ATTACHED card", type: "CARD_ACTION_CHOICE",
    note: "A REAL BUG ONCE: attachments were drawn without the eligibility flag " +
          "or a click handler, so 'Transfer Hobbit Sword' could not be taken.",
    params: actions([{ actionId: "0", cardId: WHERE.ATTACHED, text: "Transfer" }]) },

  { name: "action on a SITE", type: "CARD_ACTION_CHOICE",
    note: "A REAL BUG ONCE: sites are drawn in the path window and nothing there " +
          "lit them, so sanctuary abilities were simply unavailable.",
    params: actions([{ actionId: "0", cardId: WHERE.SITE, text: "Use Rivendell Terrace" }]) },

  { name: "virtual action, source not in play", type: "CARD_ACTION_CHOICE",
    note: "blueprintId != 'inPlay' means the source is in a discard or deck, or " +
          "is a trigger added by another card. It has NO board node, so it must " +
          "be offered as a prompt button or it is unreachable.",
    params: actions([{ actionId: "0", cardId: "500", blueprint: "1_50",
                       text: "Play from discard" }]) },

  { name: "virtual and board actions together", type: "CARD_ACTION_CHOICE",
    params: actions([{ actionId: "0", cardId: WHERE.SELF_FREE },
                     { actionId: "1", cardId: "500", blueprint: "1_50",
                       text: "From discard" }]) },

  { name: "action on an opponent's minion", type: "CARD_ACTION_CHOICE",
    params: actions([{ actionId: "0", cardId: WHERE.MINION, text: "Target minion" }]) },

  { name: "action on a card in hand", type: "CARD_ACTION_CHOICE",
    params: actions([{ actionId: "0", cardId: WHERE.HAND, text: "Play from hand" }]) },

  { name: "no actions at all, pass only", type: "CARD_ACTION_CHOICE",
    note: "The reference AUTO-PASSES here. In a five-player game this is most " +
          "of the decisions you are asked.",
    params: actions([]) },

  { name: "action on a card on no board", type: "CARD_ACTION_CHOICE",
    params: actions([{ actionId: "0", cardId: WHERE.ABSENT }]) },

  { name: "many actions, index above 9", type: "CARD_ACTION_CHOICE",
    note: "Guards against string/number confusion in the index -- '10' sorting " +
          "before '2' is exactly the sort of thing a set comparison hides.",
    params: actions(Array.from({ length: 12 }, (_, i) => ({
      actionId: String(i), cardId: i % 2 ? WHERE.SELF_FREE : WHERE.SELF_FREE_2,
      text: `option ${i}` }))) },

  // -------------------------------------------------------------- ARBITRARY_CARDS
  // Cards the client was NEVER SENT -- an opponent's hand, a deck, a reveal.
  // Ids are already `temp<i>`; the engine reads the index back with
  // Integer.parseInt(cardId.substring(4)). There is no lookup at either end.
  { name: "informational: shown, nothing selectable", type: "ARBITRARY_CARDS",
    note: "'Look at an opponent's hand.' min=max=0, and it STILL has to be " +
          "answered with the empty string or the game waits for ever on a " +
          "player who thinks they were only shown something.",
    params: { cardId: ["temp0", "temp1", "temp2"],
              blueprintId: ["1_10", "1_11", "1_12"],
              selectable: ["false", "false", "false"], min: "0", max: "0" } },

  // THE ENGINE CANNOT EMIT THIS, and believing otherwise was a misreading.
  // `ArbitraryCardsSelectionDecision`'s two-argument constructor delegates --
  // `this(id, text, physicalCards, physicalCards, min, max)` -- to the six-arg
  // one, which ALWAYS calls `setParam("selectable", ...)`. All three
  // constructors write the parameter, so "absent" never reaches a client; the
  // two-arg case arrives as `selectable` all-"true", which is the case below.
  // The old client requires the literal string (`if (selectable[i] == "true")`),
  // so it selects nothing when the array is missing -- correct behaviour for
  // input it can never receive. Kept as `expect` to record that the shape was
  // considered and ruled out, rather than deleted so the question gets asked again.
  { name: "selectable absent entirely (unreachable)", type: "ARBITRARY_CARDS",
    expect: "engine cannot emit this — every constructor writes `selectable`",
    // No answer comparison is meaningful for input the engine cannot produce:
    // whatever the two clients do with it, neither will ever be asked.
    unreachable: true,
    params: { cardId: ["temp0", "temp1"], blueprintId: ["1_10", "1_11"],
              min: "1", max: "1" } },

  { name: "selectable all true, as the 2-arg constructor sends it", type: "ARBITRARY_CARDS",
    note: "What `new ArbitraryCardsSelectionDecision(id, text, cards, min, max)` " +
          "actually puts on the wire: the same collection twice, serialised as " +
          "an all-true array.",
    params: { cardId: ["temp0", "temp1"], blueprintId: ["1_10", "1_11"],
              selectable: ["true", "true"], min: "1", max: "1" } },

  { name: "selectable is a mixed subset", type: "ARBITRARY_CARDS",
    note: "'Look at a hand and discard a Shadow card' -- shows all, permits some. " +
          "SHOWN IS NOT SELECTABLE.",
    params: { cardId: ["temp0", "temp1", "temp2", "temp3"],
              blueprintId: ["1_10", "1_11", "1_12", "1_13"],
              selectable: ["false", "true", "true", "false"], min: "1", max: "1" } },

  { name: "pick 2 of 4", type: "ARBITRARY_CARDS",
    params: { cardId: ["temp0", "temp1", "temp2", "temp3"],
              blueprintId: ["1_10", "1_11", "1_12", "1_13"],
              selectable: ["true", "true", "true", "true"], min: "2", max: "2" } },

  { name: "min>0 but nothing selectable", type: "ARBITRARY_CARDS",
    note: "Contradictory: no legal answer exists. Must not offer a Pass that " +
          "would be refused, and must not hang.",
    params: { cardId: ["temp0", "temp1"], blueprintId: ["1_10", "1_11"],
              selectable: ["false", "false"], min: "1", max: "1" } },

  // THE max=0 SHAPE, IN THE PICKER. Mirror of the CARD_SELECTION case above,
  // and the reference has the same defect in both places: its `finishChoice`
  // tests `if (selectedCardIds.length < min) return;` (gameUi.js:2394) and never
  // tests `max`, while the engine throws on either bound --
  // `ArbitraryCardsSelectionDecision:92`, `cardIds.length > _maximum`.
  // Cards are still listed as selectable, exactly as at CARD_SELECTION max=0.
  //
  // NOT marked `oracleWrong`, deliberately. The source says the reference should
  // send an illegal answer here; the RUN does not show it doing so -- it sends
  // nothing, because this page's driver cannot reach the picker's submit button
  // (it lives on `cardActionDialog`, not the `#Done` in `alertButtons` that the
  // other types use). So the reference is unproven here, not proven wrong, and
  // the marker that says "we are right and the oracle is not" needs a
  // measurement, not a reading.
  //
  // NARROWED, still open. Instrumenting `ui.selectionFunction` shows it is
  // ENTERED ONCE during the drive, with no exception recorded -- so the click
  // arrives and the reference's own selection logic runs. Four rounds were spent
  // on a "the click does not land" theory that this measurement kills outright.
  // What is left is the gap between `selectionFunction` running and
  // `decisionFunction` being reached: the picker's Done lives on
  // `cardActionDialog`'s jQuery-UI button pane, and `oldFinish()` looks in
  // `.alertButtons` and `#smallDialog`, which the picker does not use. The
  // buttonpane fallback added for this does not appear to connect either.
  //
  // MEASURED, and the question is now fully characterised as a HARNESS gap.
  // Instrumenting both entry points gives, for this case:
  //
  //     selectionFunction 1x, decisionFunction 0x
  //
  // The click lands, the reference's selection logic runs, and the submit is
  // never reached. Every OTHER picker case shows `decisionFunction 1x` because
  // they submit through AUTO-ACCEPT (`selectedCardIds.length == max`), which by
  // definition cannot fire at max=0 -- so this is the only case that needs the
  // Done button.
  //
  // CORRECTION. This note previously said the dialog's button pane is never
  // materialised. That was inferred from two selectors failing, and it is
  // wrong: a census at drive time reports `pane=1 allBtn=4 dlg=14`. The pane
  // exists and there are buttons in the document, so this is a TARGETING
  // problem, not a missing pane -- fourteen dialogs are present and the Done
  // being pressed, if any, may belong to another of them.
  //
  // Next: dump the four buttons' text and their owning dialog rather than
  // filtering blind. Two selectors failing is not evidence about the DOM;
  // counting is.
  //
  // So: the reference's behaviour here is UNMEASURED, and will stay so until the
  // can opener renders dialog button panes. It is not evidence of anything about
  // the reference, and the case is not marked `oracleWrong`. What IS established:
  // this client sends "", the only legal answer.
  //
  // ATTEMPTED AND STILL OPEN. The driver now also presses
  // `.ui-dialog-buttonpane button`, and it changes nothing: every other picker
  // case submits through AUTO-ACCEPT (`selectedCardIds.length == max`), which by
  // definition never fires at max=0, so this is the one case that truly needs
  // the button -- and the dialog's button pane is not rendered in the can opener
  // at all. Settling it needs the pane to exist headlessly, or a different way
  // in that is still the client's own submit path rather than a call to
  // `finishChoice` (which would be teaching the client, not watching it).
  //
  // What IS established: this client sends "", the only legal answer.
  { name: "max=0 with cards still selectable", type: "ARBITRARY_CARDS",
    note: "Zero cards wanted, cards still offered. Only \"\" is legal. The " +
          "reference has no `max` test in finishChoice (gameUi.js:2394) and the " +
          "engine throws on `cardIds.length > _maximum` " +
          "(ArbitraryCardsSelectionDecision:92) -- but see above: not yet shown.",
    params: { cardId: ["temp0", "temp1"], blueprintId: ["1_10", "1_11"],
              selectable: ["true", "true"], min: "0", max: "0" } },

  { name: "empty picker", type: "ARBITRARY_CARDS",
    params: { cardId: [], blueprintId: [], min: "0", max: "0" } },

  // --------------------------------------------------------------- ASSIGN_MINIONS
  // Carries freeCharacters and minions, and NO cardId. A client that builds its
  // eligible set from `cardId` finds nothing and silently offers no way to
  // assign -- which is exactly what this client did.
  // The two clients light DIFFERENT cards here, by design rather than by defect.
  // The old client lights only `minions` -- gameUi.js:2883 does
  // `$(".card:cardId(" + minions + ")")` and keeps freeCharacters in an
  // assignment map as targets, never as selectable cards. The new client draws
  // assignment as columns (DESIGN.md, "Skirmishes suspend the focus"), so the
  // companion is part of the interaction and is lit too. Marked `expect` so the
  // suite's baseline stays at zero and a REAL assignment regression is visible;
  // still compared, and still reported if it ever stops differing.
  { name: "one companion, one minion", type: "ASSIGN_MINIONS", expect: "design",
    params: { freeCharacters: [WHERE.CONTESTED], minions: [WHERE.MINION] } },

  { name: "one companion, several minions", type: "ASSIGN_MINIONS", expect: "design",
    params: { freeCharacters: [WHERE.CONTESTED], minions: [WHERE.MINION, WHERE.MINION_2] } },

  // The two clients ENCODE this differently and both answers are correct.
  // Old sends "120 110,101" -- a group for every freeCharacter, including ones
  // with nothing assigned. New sends "120 110", omitting them.
  // `PlayerAssignMinionsDecision.getAssignmentsBasedOnResponse:36-53` splits on
  // "," then " " and gives a lone companion id an EMPTY minion set, so the two
  // build `{120:{110}, 101:{}}` and `{120:{110}}` -- the same assignment. Marked
  // `equivalent` rather than `design`, because that word means something
  // stronger here: checked against the engine's parser, not merely intended.
  { name: "several companions, one minion", type: "ASSIGN_MINIONS",
    expect: "design", equivalent: "engine parses both to the same assignment",
    params: { freeCharacters: [WHERE.CONTESTED, WHERE.SELF_FREE], minions: [WHERE.MINION] } },

  { name: "no minions to assign", type: "ASSIGN_MINIONS", expect: "design",
    params: { freeCharacters: [WHERE.CONTESTED], minions: [] } },

  { name: "no companions to assign to", type: "ASSIGN_MINIONS",
    params: { freeCharacters: [], minions: [WHERE.MINION] } },

  // ---------------------------------------------------------------------- INTEGER
  // Three constructors, so `max` and `defaultValue` are each optional.
  { name: "min only, no max", type: "INTEGER",
    note: "IntegerAwaitingDecision(id, text, min) -- the one-argument form. " +
          "A client that reads max unconditionally has no upper bound here.",
    params: { min: "0" } },

  { name: "min and max", type: "INTEGER",
    params: { min: "1", max: "5" } },

  { name: "min, max and defaultValue", type: "INTEGER",
    note: "KNOWN GAP. gameUi.js:2079-2081 pre-fills the box with defaultValue; " +
          "`defaultValue` appears nowhere in src/. The current live differential " +
          "cannot see this because INTEGER answers are self-compared.",
    params: { min: "0", max: "10", defaultValue: "7" } },

  { name: "min == max, no choice", type: "INTEGER",
    params: { min: "3", max: "3" } },

  { name: "min > max, inverted", type: "INTEGER",
    note: "Robustness only; the engine should not emit it.",
    params: { min: "5", max: "2" } },

  // -------------------------------------------------- MULTIPLE_CHOICE / ACTION_CHOICE
  { name: "yes/no", type: "MULTIPLE_CHOICE",
    note: "YesNoDecision extends MultipleChoiceAwaitingDecision -- same shape.",
    params: { results: ["Yes", "No"] } },

  { name: "a single option", type: "MULTIPLE_CHOICE",
    params: { results: ["Continue"] } },

  { name: "no options at all", type: "MULTIPLE_CHOICE",
    note: "Nothing legal to send. Must not render a dead dialog.",
    params: { results: [] } },

  { name: "ten options", type: "MULTIPLE_CHOICE",
    params: { results: Array.from({ length: 10 }, (_, i) => `choice ${i}`) } },

  { name: "options that look like ids", type: "MULTIPLE_CHOICE",
    note: "Answers with the INDEX. Options that are themselves numbers are how " +
          "an index/value confusion stays invisible.",
    params: { results: ["101", "110", "150"] } },

  { name: "action choice, no cards", type: "ACTION_CHOICE",
    params: { actionId: ["0", "1"], actionText: ["First", "Second"],
              blueprintId: ["inPlay", "inPlay"] } }
];

/** Group the catalogue by decision type, for the coverage report. */
export function byType() {
  const out = {};
  for (const c of CASES) (out[c.type] ||= []).push(c);
  return out;
}
