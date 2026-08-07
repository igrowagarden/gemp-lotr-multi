package com.gempukku.lotro.at;

import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * A cost GRANTED to somebody else's card is paid by the wrong player.
 *
 * Balin Avenged (17_2) is the last `unclear` token in the census, and it is a
 * shape none of the other conversions had:
 *
 *   "While you can spot 4 [dwarven] tokens and 2 Dwarves, each [orc] Orc gains
 *    this text 'To play, remove an [orc] card from YOUR discard pile from the
 *    game.'"
 *
 * "Your" is the player playing the Orc -- which above two players is whichever
 * Shadow player that happens to be. The card data says `player: shadow`, so it
 * is GameUtils.getFirstShadowPlayer, a fixed seat.
 *
 * WHY NO TOKEN CAN FIX THIS, which is the point of the test.
 * BuiltLotroCardBlueprint.getModifiers (:1487-1497) builds every in-play
 * modifier's context as
 *
 *     new DefaultActionContext(self.getOwner(), game, self, null, null)
 *
 * where `self` is BALIN AVENGED. ExtraCostToPlay.appendExtraCosts then appends
 * the granted cost against that same captured context, ignoring both the
 * `action` and the `card` it is handed. So inside the granted cost:
 *
 *     player: you      -> getPerformingPlayer() -> Balin Avenged's owner
 *     player: owner    -> getSource().getOwner() -> Balin Avenged's owner
 *     player: shadow   -> getFirstShadowPlayer() -> a fixed Shadow seat
 *
 * All three name somebody who is not playing the Orc. The context never sees
 * the Orc at all. The card therefore cannot express what its printed text says,
 * whatever token is written -- the gap is in the engine, not in the data.
 *
 * Two seats' worth of state tell the readings apart, so the fixture puts the
 * Orc in the hand of the Shadow player getFirstShadowPlayer does NOT name --
 * the same trick MultiplayerShadowMeansMeAtTest uses on 5_3.
 *
 * These tests PIN THE BUG. When 17_2 is fixed they must go red, and the
 * assertions below say what each should become.
 */
public class MultiplayerGrantedCostAtTest {

    private static final String BALIN_AVENGED = "17_2";

    // Two Dwarves for `canSpot dwarf count: 2`. Spotting is global, so both
    // sit with the Free Peoples player. Gimli is Damage +1 and nothing else;
    // Dwarf Guard's "To play, spot a Dwarf" never runs because the fixture
    // teleports it into play rather than playing it.
    private static final String GIMLI = "0_62";
    private static final String DWARF_GUARD = "1_7";

    // The card whose play triggers the granted cost: culture Orc AND race Orc,
    // which is what the modifier's `filter: culture(orc),orc` wants. Lurker is
    // a skirmish-ordering keyword, so it is inert here -- the walk stops in the
    // Shadow phase and never reaches a skirmish.
    private static final String ORC_SKULKER = "12_95";

    // The [orc] card that sits in a Shadow player's discard pile waiting to be
    // removed. One per seat, so "which pile shrank" is the whole measurement.
    private static final String DENIZEN = "11_116";

    // MultiplayerTable puts ONE copy of each named card into every seat's deck,
    // so a five-card fixture is a five-card deck. Nothing here draws 8, but
    // regroup reconciliation does draw, and an empty deck changes what the walk
    // can do. Filler is cheap insurance.
    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "4_165",
            "7_193", "1_143", "12_157", "1_294", "1_312",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("balin", BALIN_AVENGED);
            put("gimli", GIMLI);
            put("guard", DWARF_GUARD);
            put("orc", ORC_SKULKER);
            put("denizen", DENIZEN);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
        }});
    }

    /** Opponents counter-clockwise from the Free Peoples player, in Shadow-phase order. */
    private static List<String> ShadowSeats(VirtualTableScenario scn) {
        String fp = scn.FreePeoplesPlayer();
        PlayOrder order = scn.game().getGameState().getPlayerOrder()
                .getCounterClockwisePlayOrder(fp, false);
        order.getNextPlayer();
        List<String> seats = new ArrayList<>();
        String next;
        while ((next = order.getNextPlayer()) != null && !next.equals(fp))
            seats.add(next);
        return seats;
    }

    private static String Pending(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            sb.append(p).append("=").append(d == null ? "(null)" : d.getText()).append("  ");
        }
        return sb.length() == 0 ? "(nobody)" : sb.toString();
    }

    /** Every seat's discard pile, and where each seat's [orc] card actually is. */
    private static String Board(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new String[]{P1, P2, P3}) {
            sb.append(p)
              .append(" discard=").append(scn.gameState().getDiscard(p).size())
              .append(" denizen@").append(scn.GetCardFor(p, "denizen").getZone())
              .append("; ");
        }
        return sb.toString();
    }

    /**
     * Arms the table: Balin Avenged in play with its two spot requirements met,
     * an Orc in each opponent's hand, and an [orc] card in each opponent's
     * discard pile. Everything is placed BEFORE the game starts, because
     * placing cards mid-game does not put them into play properly -- and at
     * that point the seating is not yet known, so both opponents get armed.
     */
    private VirtualTableScenario ArmedTable() throws Exception {
        var scn = ThreeSeats();

        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "balin"));
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "gimli"), scn.GetCardFor(P1, "guard"));
        scn.MoveCardsToHand(scn.GetCardFor(P2, "orc"), scn.GetCardFor(P3, "orc"));

        scn.StartMultiplayerGame();

        // Tokens go on after the start; the requirement is
        // `canSpotCultureTokens dwarven amount: 4`, and AddTokensToCard adds
        // tokens of the card's OWN culture, which for Balin Avenged is Dwarven.
        scn.AddTokensToCard(scn.GetCardFor(P1, "balin"), 4);

        return scn;
    }

    /**
     * The measurement: with both opponents holding an [orc] card in their
     * discard pile, the one who plays the Orc should be the one who pays.
     *
     * Today the removal comes out of getFirstShadowPlayer's pile instead. At
     * two players those are the same seat, which is why this has never shown.
     */
    @Test
    public void theOrcsOwnPlayerPaysTheGrantedCost() throws Exception {
        var scn = ArmedTable();

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);   // the seat `player: shadow` names
        String actingShadow = opponents.get(1);  // the seat that plays the Orc
        assertNotEquals(firstShadow, actingShadow);

        assertEquals("getFirstShadowPlayer should name the first opponent counter-clockwise",
                firstShadow, GameUtils.getFirstShadowPlayer(scn.game()));

        // Both opponents' [orc] cards go to their own discards. Done after the
        // start so the deal cannot move them back.
        scn.MoveCardsToDiscard(scn.GetCardFor(firstShadow, "denizen"),
                scn.GetCardFor(actingShadow, "denizen"));

        scn.SetTwilight(10);
        scn.PassUntilDecision(actingShadow, "Shadow action");

        var orc = scn.GetCardFor(actingShadow, "orc");
        String actionId = scn.GetCardActionId(actingShadow, orc);
        // GetCardActionId returns null when the action is not on offer, and
        // PlayerDecided(player, null) passes the phase rather than throwing --
        // so the card under test would silently never fire.
        assertNotNull("Orc Skulker should be playable by " + actingShadow
                + " in their own Shadow phase (pending: " + Pending(scn)
                + ", board: " + Board(scn) + ")", actionId);

        String before = Board(scn);
        scn.PlayerDecided(actingShadow, actionId);

        // Drain whatever the granted cost asks, recording every step. Written
        // as a trace rather than a fixed sequence because the whole question is
        // WHO gets asked, and a rigid walk cannot tell a wrong seat from a card
        // that fizzled.
        StringBuilder trace = new StringBuilder();
        for (int step = 0; step < 10; step++) {
            var waiting = new ArrayList<>(scn.userFeedback().getUsersPendingDecision());
            if (waiting.isEmpty()) {
                trace.append("[nobody waiting] ");
                break;
            }
            java.util.Collections.sort(waiting);
            String who = waiting.get(0);
            var d = scn.userFeedback().getAwaitingDecision(who);
            if (d == null) continue;
            String text = d.getText() == null ? "(null)" : d.getText();
            trace.append("<").append(who).append(": ").append(text).append("> ");

            if (text.toLowerCase().contains("discard")) {
                // The granted cost's own decision. Stop as soon as it is
                // answered -- letting the walk run on marches through whole
                // extra turns, and regroup would rewrite every pile.
                var params = d.getDecisionParameters();
                String[] ids = params.get("cardId");
                scn.PlayerDecided(who, ids == null || ids.length == 0 ? "" : ids[0]);
                trace.append("[answered the granted cost] ");
                break;
            }

            var params = d.getDecisionParameters();
            String answer;
            switch (d.getDecisionType()) {
                case INTEGER -> answer = params.containsKey("min") ? params.get("min")[0] : "0";
                case MULTIPLE_CHOICE, ACTION_CHOICE -> answer = "0";
                default -> answer = "";
            }
            try {
                scn.PlayerDecided(who, answer);
            } catch (Exception e) {
                trace.append("[!! ").append(e.getClass().getSimpleName())
                     .append(" answering ").append(d.getDecisionType())
                     .append(" with '").append(answer).append("'] ");
                break;
            }
        }

        var actingCard = scn.GetCardFor(actingShadow, "denizen");
        var firstCard = scn.GetCardFor(firstShadow, "denizen");
        String dump = "acting=" + actingShadow + " first=" + firstShadow
                + " | before: " + before + " | after: " + Board(scn)
                + " | trace: " + trace;

        // ------------------------------------------------------------------
        // These two assertions are the finding. The card says "YOUR discard
        // pile" on text granted to the Orc, so the Orc's own player pays.
        // ------------------------------------------------------------------
        assertNotEquals("the player who played the Orc is the one who pays the granted"
                        + " cost -- \"your discard pile\" is the Orc's player. " + dump,
                com.gempukku.lotro.common.Zone.DISCARD, actingCard.getZone());
        assertEquals("the OTHER Shadow player's discard pile is untouched --"
                        + " getFirstShadowPlayer does not decide who pays. " + dump,
                com.gempukku.lotro.common.Zone.DISCARD, firstCard.getZone());
    }

    /**
     * The sharper half, and the cheaper one: the PLAYABILITY gate reads the
     * same wrong seat.
     *
     * ExtraCostToPlay.canPayExtraCostsToPlay runs isPlayableInFull against the
     * captured context, so with only the acting player holding an [orc] card in
     * their discard, the Orc is refused -- the engine looks in a pile belonging
     * to somebody else and finds nothing to remove.
     *
     * This one needs no walk past the play action, so it cannot be confounded
     * by anything the trace stumbles into.
     */
    @Test
    public void theOrcIsPlayableWhenItsOwnPlayerCanPay() throws Exception {
        var scn = ArmedTable();

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String actingShadow = opponents.get(1);
        assertNotEquals(firstShadow, actingShadow);

        // ONLY the acting player is armed. The other opponent's discard pile
        // stays empty of [orc] cards.
        scn.MoveCardsToDiscard(scn.GetCardFor(actingShadow, "denizen"));

        scn.SetTwilight(10);
        scn.PassUntilDecision(actingShadow, "Shadow action");

        var orc = scn.GetCardFor(actingShadow, "orc");
        String actionId = scn.GetCardActionId(actingShadow, orc);

        assertNotNull("the Orc's own player holds an [orc] card in their discard, so"
                        + " they can pay the granted cost and the Orc should be playable."
                        + " If this is null the playability gate is reading"
                        + " getFirstShadowPlayer's pile instead. acting=" + actingShadow
                        + " first=" + firstShadow + " | board: " + Board(scn)
                        + " | pending: " + Pending(scn),
                actionId);
    }
}
