package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * Brooding on Tomorrow (10_15): "Each time you lose initiative (except during
 * the fellowship phase), you may spot a [gandalf] companion to choose an
 * opponent who must discard one of his or her conditions."
 *
 * The last card carrying the "shape and text match only" label for a reason
 * other than a rules question. This file said for several sessions that it was
 * blocked because "nothing makes initiative change hands mid-turn at three seats
 * yet".
 *
 * IT IS NOT BLOCKED, AND NEVER WAS. Initiative is not a mechanism that has to be
 * driven -- `ModifiersLogic.hasInitiative` (:1158-1166) is, absent modifiers,
 * one comparison:
 *
 *     hand(currentPlayer).size() + voidFromHand(currentPlayer).size() < 4
 *         -> Side.SHADOW
 *
 * So "the Free Peoples player loses initiative" is "their hand drops below
 * four", and InitiativeChangeRule emits the result the moment that becomes true
 * outside PUT_RING_BEARER. Thinning the hand in a non-fellowship phase is the
 * whole fixture.
 *
 * The card is scoped -- "one of HIS OR HER conditions" -- so each opponent gets
 * TWO conditions, per the rule that a scoped selection over a single candidate
 * resolves without asking and lets a wrong-seat control pass silently.
 */
public class MultiplayerLosesInitiativeAtTest {

    private static final String BROODING = "10_15";
    private static final String GANDALF = "6_30";       // a [gandalf] companion to spot
    // Two Shadow support-area conditions per opponent, all different.
    private static final String COND_A1 = "1_133";
    private static final String COND_A2 = "1_157";
    private static final String COND_B1 = "1_173";
    private static final String COND_B2 = "1_195";

    private static String[] conditionKeys(String seat) {
        return seat.equals(P2) ? new String[]{"a1", "a2"} : new String[]{"b1", "b2"};
    }

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("brooding", BROODING);
            put("gandalf", GANDALF);
            put("a1", COND_A1);
            put("a2", COND_A2);
            put("b1", COND_B1);
            put("b2", COND_B2);
            put("f1", "1_159"); put("f2", "1_198"); put("f3", "4_165");
            put("f4", "7_193"); put("f5", "1_143"); put("f6", "12_157");
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

    @Test
    public void losingInitiativeLetsTheChosenOpponentDiscardTheirOwnCondition()
            throws Exception {
        var scn = ThreeSeats();
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "brooding"));
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "gandalf"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertEquals("fixture assumes the first seat is what getFirstShadowPlayer names",
                defaultOpponent, GameUtils.getFirstShadowPlayer(scn.game()));

        for (String seat : new String[]{P2, P3})
            for (String key : conditionKeys(seat))
                scn.MoveCardsToSupportArea(scn.GetCardFor(seat, key));

        // Anywhere but the fellowship phase -- the card excludes it explicitly.
        // REGROUP rather than MANEUVER: with no minions on the table the
        // maneuver phase is never reached, and PassUntilPhase runs out of
        // decisions in regroup ("Nobody has a decision, and the phase is
        // REGROUP rather than MANEUVER").
        scn.PassUntilPhase(Phase.REGROUP);
        assertEquals(Phase.REGROUP, scn.game().getGameState().getCurrentPhase());

        // Lose initiative: drop the hand below four. Nothing else is needed.
        for (PhysicalCard card : new ArrayList<>(scn.GetHand(P1)))
            scn.MoveCardsToBottomOfDeck((PhysicalCardImpl) card);
        assertTrue("the hand must be under 4 for the Shadow side to take initiative",
                scn.GetHand(P1).size() < 4);

        scn.PassUntilDecision(P1, "Optional");
        assertTrue("Brooding on Tomorrow's trigger should be offered; pending: "
                        + Pending(scn),
                scn.FreepsHasOptionalTriggerAvailable(scn.GetCardFor(P1, "brooding")));
        scn.FreepsAcceptOptionalTrigger();

        assertTrue("the Free Peoples player should be asked which opponent; pending: "
                + Pending(scn), scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        // Kills a wrong-seat control: only visible because each holds two.
        assertTrue("the CHOSEN opponent should be the one discarding; pending: "
                + Pending(scn), scn.DecisionAvailable(chosenOpponent, "Choose"));
        assertFalse("the other opponent should not be asked; pending: " + Pending(scn),
                scn.DecisionAvailable(defaultOpponent, "Choose"));

        // Kills a no-scoping control: "one of HIS OR HER conditions".
        var theirs = scn.GetCardFor(chosenOpponent, conditionKeys(chosenOpponent)[0]);
        var others = scn.GetCardFor(defaultOpponent, conditionKeys(defaultOpponent)[0]);
        assertTrue("the chosen opponent should be offered their own condition",
                scn.HasCardChoiceAvailable(chosenOpponent, theirs));
        assertFalse("...and NOT the other opponent's",
                scn.HasCardChoiceAvailable(chosenOpponent, others));

        scn.ChooseCards(chosenOpponent, theirs);
        assertEquals("their own condition was discarded",
                com.gempukku.lotro.common.Zone.DISCARD, theirs.getZone());
        assertNotEquals("the other opponent's is untouched",
                com.gempukku.lotro.common.Zone.DISCARD, others.getZone());
    }
}
