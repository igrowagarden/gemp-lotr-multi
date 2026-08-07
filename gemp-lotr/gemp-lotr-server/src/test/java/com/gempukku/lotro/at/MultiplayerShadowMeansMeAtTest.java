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
 * On a SHADOW-side card, `player: shadow` was written to mean "me".
 *
 * Every conversion on this branch so far read `shadow` as *an opponent of the
 * Free Peoples player*, which is what it means on a Free Peoples card. On a
 * Shadow card the author meant the card's own controller, because at two
 * players those are the same person.
 *
 * They are not the same person at five, and that much is mechanical:
 * GameUtils.getFirstShadowPlayer (GameUtils.java:67-75) walks counter-clockwise
 * from getCurrentPlayerId() -- the Free Peoples player -- and never consults the
 * card's controller.
 *
 * WHAT THE ENGINE ACTUALLY DOES WITH THE WRONG TOKEN HAD NOT BEEN MEASURED.
 * An earlier draft of HANDOFF.md asserted a consequence off a code read and had
 * to be retracted; this test exists so the next person has a measurement instead
 * of a story. It was written as a PROBE that pinned the bug, then INVERTED when
 * 5_3 was converted -- so both boards have been run, and that inversion is the
 * control behind the fix rather than a separate patched-card sweep.
 *
 * The card is Leaping Blaze (5_3), chosen because its effect is the easiest
 * thing in the game to observe -- hand sizes:
 *
 *   "Shadow: Spot 3 [dunland] minions to shuffle YOUR hand into YOUR draw deck
 *    and draw 8 cards. The Free Peoples player may discard 3 cards from hand to
 *    prevent this."
 *
 *   {shuffleHandIntoDrawDeck, player: shadow} and {drawCards, count: 8,
 *   player: shadow}
 *
 * The fixture deliberately makes the ACTING Shadow player the one that
 * getFirstShadowPlayer does NOT name, so "the token followed the controller" and
 * "the token fell back to the first seat" cannot produce the same board.
 */
public class MultiplayerShadowMeansMeAtTest {

    private static final String LEAPING_BLAZE = "5_3";

    // Three DIFFERENT [dunland] minions, all non-unique, all with no game text
    // worth speaking of. Only the count matters: 5_3's requirement is
    // `canSpot culture(dunland),minion count: 3`, which is NOT scoped to the
    // player, so any three on the table satisfy either Shadow player.
    private static final String BRIGAND = "4_10";     // no game text
    private static final String SAVAGE = "4_17";      // no game text
    private static final String RAVAGER = "4_15";     // passive strength bonus only

    /**
     * MultiplayerTable puts ONE copy of each named card into every seat's deck,
     * so without filler a "draw 8" draws whatever few cards exist. The first
     * run of this probe had decks of 0-4 cards and reported expected:<8> was:<0>
     * -- the same signature MultiplayerOneOpponentRedrawsAtTest records hitting
     * on 18_57 (was:<2>). The fixture, not the card.
     */
    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "4_165",
            "7_193", "1_143", "12_157", "1_294", "1_312",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("blaze", LEAPING_BLAZE);
            put("brigand", BRIGAND);
            put("savage", SAVAGE);
            put("ravager", RAVAGER);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
        }});
    }

    /** Opponents counter-clockwise from the Free Peoples player. */
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

    /** Hand and deck sizes for every seat -- the state dump this probe exists for. */
    private static String Board(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new String[]{P1, P2, P3}) {
            sb.append(p)
              .append(" hand=").append(scn.gameState().getHand(p).size())
              .append(" deck=").append(scn.gameState().getDeck(p).size())
              .append("; ");
        }
        return sb.toString();
    }

    /**
     * The acting Shadow player plays a card that says "shuffle YOUR hand", and
     * is the one whose hand is shuffled.
     *
     * Before 5_3 was converted this asserted the opposite and passed -- see the
     * block comment on the assertions for both boards. The Free Peoples player
     * is never offered the prevention here, because their hand is empty and the
     * cost is "discard 3 cards from hand", so the effect resolves unprevented;
     * that is why the trace shows no "prevent" step.
     */
    @Test
    public void theActingShadowPlayerIsTheOneAffected() throws Exception {
        var scn = ThreeSeats();

        // Cards have to be placed BEFORE the game starts -- placing them
        // mid-game does not put them into play properly -- and at that point
        // the seating is not yet known. So arm BOTH opponents: three dunland
        // minions go down for P2 (spotting is global, so three anywhere is
        // enough for either), and each opponent gets a copy of the event.
        scn.MoveMinionsToTable(scn.GetCardFor(P2, "brigand"),
                scn.GetCardFor(P2, "savage"),
                scn.GetCardFor(P2, "ravager"));
        scn.MoveCardsToHand(scn.GetCardFor(P2, "blaze"), scn.GetCardFor(P3, "blaze"));

        scn.StartMultiplayerGame();

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);   // the seat `player: shadow` names
        String actingShadow = opponents.get(1);  // the seat that plays the card
        assertNotEquals(firstShadow, actingShadow);

        // The premise of the whole Shadow-side finding, asserted rather than
        // assumed: the token does not follow the card's controller.
        assertEquals("getFirstShadowPlayer should name the first opponent counter-clockwise",
                firstShadow, GameUtils.getFirstShadowPlayer(scn.game()));
        assertNotEquals("the fixture is pointless unless the acting player is NOT the one"
                        + " getFirstShadowPlayer names",
                actingShadow, GameUtils.getFirstShadowPlayer(scn.game()));

        scn.SetTwilight(10);
        scn.PassUntilDecision(actingShadow, "Shadow action");

        var blaze = scn.GetCardFor(actingShadow, "blaze");
        String actionId = scn.GetCardActionId(actingShadow, blaze);
        // GetCardActionId returns null when the action is not on offer, and
        // PlayerDecided(player, null) passes the phase rather than throwing --
        // so the card would silently never fire.
        assertNotNull("Leaping Blaze should be playable by " + actingShadow
                + " in their own Shadow phase (pending: " + Pending(scn)
                + ", board: " + Board(scn) + ")", actionId);

        int actingBefore = scn.gameState().getHand(actingShadow).size();
        int firstBefore = scn.gameState().getHand(firstShadow).size();
        String before = Board(scn);

        scn.PlayerDecided(actingShadow, actionId);

        // Drain whatever the card asks, recording every step. Written as a
        // trace rather than a fixed sequence because the first run of this
        // probe died on PassUntilDecision(P1, "prevent") with "nobody has a
        // decision" -- the offer did not arrive where it was expected, and a
        // rigid walk cannot tell you that from a card that fizzled.
        StringBuilder trace = new StringBuilder();
        for (int step = 0; step < 12; step++) {
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

            // The Free Peoples player may discard 3 to prevent. Decline by
            // TEXT -- a YesNoDecision's options are not in a fixed order, and
            // answering "0" has accepted rather than declined elsewhere here.
            if (text.toLowerCase().contains("prevent")) {
                scn.ChooseOption(who, "No");
                trace.append("[declined -> effect resolves] ");
                // Stop here. The effect resolves as this decision is processed,
                // and letting the walk run on marches through whole extra turns
                // -- the first trace ran two full turns past the card and the
                // regroup reconciliation rewrote every hand, which would have
                // been read as the card's doing.
                break;
            }
            // Anything else: the least eventful answer, so the probe measures
            // the card rather than whatever the walk stumbled into. The switch
            // mirrors GameProcedures.AnswerThatKeepsMoving, which is private --
            // answering "" for every type threw DecisionResultInvalidException
            // on the second run of this probe.
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
                // Record and stop rather than dying with a bare stack trace:
                // the trace so far IS the measurement this probe exists for.
                trace.append("[!! ").append(e.getClass().getSimpleName())
                     .append(" answering ").append(d.getDecisionType())
                     .append(" with '").append(answer).append("'] ");
                break;
            }
        }

        int actingAfter = scn.gameState().getHand(actingShadow).size();
        int firstAfter = scn.gameState().getHand(firstShadow).size();
        String dump = "acting=" + actingShadow + " first=" + firstShadow
                + " | before: " + before + " | after: " + Board(scn)
                + " | trace: " + trace;

        // ------------------------------------------------------------------
        // These assertions were INVERTED when 5_3 was converted, and the
        // inversion is the measurement. Both states were run:
        //
        //   before the fix (player: shadow)
        //     acting Shadow Player  hand 1 -> 0   deck 10 -> 10   nothing
        //     first  Third Player   hand 1 -> 8   deck 13 -> 6    everything
        //
        //   after the fix (player: you)
        //     acting Third Player   hand 1 -> 8   deck 13 -> 5    everything
        //     first  Shadow Player  hand 1 -> 1   deck 10 -> 10   nothing
        //
        // The seating order differs between those two runs -- the acting seat
        // is Shadow Player in one and Third Player in the other -- which is
        // the shuffle, and means the result does not depend on which seat drew
        // which role.
        //
        // Be precise about what the bug was NOT. No player's hand went into
        // another player's deck; the game cannot express that and GEMP does
        // not do it. The effect stayed entirely coherent WITHIN one player and
        // was simply applied to the wrong one -- the seat that paid the
        // twilight and spent the card got nothing, and an uninvolved opponent
        // got a fresh hand of eight.
        // ------------------------------------------------------------------
        assertEquals("the ACTING Shadow player is the one shuffled away and redrawn"
                + " to 8 -- the card says \"shuffle YOUR hand\", and on a side: Shadow"
                + " card \"your\" is the controller. " + dump,
                8, actingAfter);
        assertEquals("the other Shadow player is untouched -- getFirstShadowPlayer no"
                + " longer decides who this card affects. " + dump,
                firstBefore, firstAfter);

        // Guard against the assertion being vacuous: if the acting seat had
        // already held 8, "redrew to 8" would prove nothing.
        assertNotEquals("fixture is blind: the acting seat already held 8 cards, so"
                        + " redrawing to 8 is indistinguishable from doing nothing. " + dump,
                8, actingBefore);
    }
}
