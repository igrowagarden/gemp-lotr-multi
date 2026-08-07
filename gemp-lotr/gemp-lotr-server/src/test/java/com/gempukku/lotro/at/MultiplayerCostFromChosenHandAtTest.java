package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Zone;
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
 * A COST whose size depends on which opponent you name.
 *
 * Stern People (7_251): "Regroup: Discard cards from hand equal to the number of
 * cards in an opponent's hand to make the move limit +1 for this turn. Each
 * Shadow player may take up to 4 cards into hand from his or her discard pile.
 * Discard this condition."
 *
 * The count said {forEachInHand, hand: shadow} -- getFirstShadowPlayer, a fixed
 * seat -- and it sat in the action's `cost:` block, which is why it stayed open
 * for so long. A cost is registered as a play requirement, so it is evaluated
 * before any effect has run and `fromMemory(opponent)` is not set yet.
 *
 * It is now a `costToEffect` in the effect list, after the ChooseOpponent. GEMP
 * built that appender for this exact case and says so in its own comment.
 *
 * The two opponents are given DIFFERENT hand sizes on purpose, and both are
 * affordable. A fixture where only one is affordable would let a wrong-seat
 * control fail as "nothing happened", which is a weaker and more ambiguous
 * signal than "the wrong NUMBER of cards was discarded".
 */
public class MultiplayerCostFromChosenHandAtTest {

    private static final String STERN_PEOPLE = "7_251";
    // Filler only -- the fixture cares about hand SIZES, not what is in them.
    private static final String FILLER_A = "1_133";
    private static final String FILLER_B = "1_157";
    private static final String FILLER_C = "1_159";
    private static final String FILLER_D = "1_173";
    private static final String FILLER_E = "1_195";
    private static final String FILLER_F = "1_198";

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("stern", STERN_PEOPLE);
            put("a", FILLER_A);
            put("b", FILLER_B);
            put("c", FILLER_C);
            put("d", FILLER_D);
            put("e", FILLER_E);
            put("f", FILLER_F);
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

    /**
     * Replace a hand with exactly the named cards.
     *
     * Set rather than trimmed: by Regroup every hand here is empty, because the
     * walk down to the phase answers decisions with whatever keeps the game
     * moving. The surplus goes UNDER the draw deck rather than into the discard
     * on purpose -- the card's second half offers every Shadow player cards back
     * out of their discard pile, and a non-empty discard turns that into a
     * decision this test would have to answer for reasons unrelated to what it
     * is measuring.
     *
     * Every seat holds its OWN copy of each fixture name, so the three hands are
     * disjoint even where the keys overlap.
     */
    private static void SetHandTo(VirtualTableScenario scn, String player, String... keys) {
        for (PhysicalCard card : new ArrayList<>(scn.GetHand(player)))
            scn.MoveCardsToBottomOfDeck((PhysicalCardImpl) card);
        for (String key : keys)
            scn.MoveCardsToHand(scn.GetCardFor(player, key));
        assertEquals("hand of " + player, keys.length, scn.GetHand(player).size());
    }

    /**
     * Stern People in the Free Peoples player's support area, the table in
     * Regroup, and three hands of known and different sizes.
     */
    private VirtualTableScenario TableInRegroup() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "stern"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        assertEquals("fixture assumes the first seat is what getFirstShadowPlayer names",
                opponents.get(0), GameUtils.getFirstShadowPlayer(scn.game()));

        scn.PassUntilPhase(Phase.REGROUP);

        // Set AFTER reaching Regroup: reconcile brings every hand to 8, and it
        // runs later in this same phase.
        SetHandTo(scn, P1, "a", "b", "c", "d", "e");
        SetHandTo(scn, opponents.get(0), "a", "b", "c");
        SetHandTo(scn, opponents.get(1), "a");
        return scn;
    }

    /**
     * The cost is the CHOSEN opponent's hand size.
     *
     * Deliberately the second opponent, who holds one card while the seat
     * getFirstShadowPlayer names holds three -- so a fixed-seat count discards
     * three and this discards one.
     */
    @Test
    public void theDiscardCountsTheChosenOpponentsHand() throws Exception {
        var scn = TableInRegroup();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        var stern = scn.GetCardFor(P1, "stern");

        assertEquals(3, scn.GetHand(defaultOpponent).size());
        assertEquals(1, scn.GetHand(chosenOpponent).size());

        String actionId = scn.GetCardActionId(P1, stern);
        // GetCardActionId returns null when the action is not on offer, and
        // PlayerDecided(player, null) passes the phase rather than throwing --
        // so without this the card silently never fires.
        assertNotNull("Stern People's regroup action should be on offer; pending: "
                + Pending(scn), actionId);
        scn.PlayerDecided(P1, actionId);

        assertTrue("the Free Peoples player should be asked which opponent; pending: "
                + Pending(scn), scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        int handBefore = scn.GetHand(P1).size();
        assertEquals(5, handBefore);

        assertTrue("the Free Peoples player should be choosing cards to discard; pending: "
                + Pending(scn), scn.DecisionAvailable(P1, "Choose cards from hand"));

        // The sharp assertion, and the one a wrong-seat control fails on. It
        // reads the SIZE the game is demanding rather than the outcome, so it
        // fires before the answer below can be rejected for having the wrong
        // number of cards in it -- which would be an exception rather than a
        // legible failure.
        var params = scn.userFeedback().getAwaitingDecision(P1).getDecisionParameters();
        assertEquals("the cost is the CHOSEN opponent's hand size, not the first seat's",
                "1", params.get("min")[0]);
        assertEquals("1", params.get("max")[0]);

        scn.ChooseCards(P1, (PhysicalCardImpl) scn.GetHand(P1).get(0));

        assertEquals("one card, because the chosen opponent holds one",
                handBefore - 1, scn.GetHand(P1).size());
        assertEquals("the chosen opponent's own hand is only counted, not touched",
                1, scn.GetHand(chosenOpponent).size());
        assertEquals("the other opponent is untouched", 3, scn.GetHand(defaultOpponent).size());
        assertEquals("the condition discards itself once the cost is paid",
                Zone.DISCARD, stern.getZone());
    }
}
