package com.gempukku.lotro.at;

import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "AN opponent" -- the singular twin of "each opponent".
 *
 * Change of Plans (1_99): "Regroup: Exert a ranger to make an opponent shuffle
 * his hand into his draw deck and draw 8 cards." The data said
 * {shuffleHandIntoDrawDeck, player: shadowPlayer} and {drawCards,
 * player: shadowPlayer} -- getFirstShadowPlayer twice, and no choice offered.
 *
 * This is deliberately the same effect as Shield of the White Tree (18_57),
 * which is already measured for `eachShadow`. Running the identical assertions
 * against the identical effect, with only the player selection differing, is
 * what makes the two readings distinguishable: 18_57 must hit BOTH opponents,
 * 1_99 must hit exactly one, and it must be the one that was chosen.
 *
 * It stands in for six other cards converted alongside it that redirect a hand
 * or a draw deck the same way (1_36, 3_33, 7_66, 10_29, 13_36, 15_50).
 */
public class MultiplayerOneOpponentRedrawsAtTest {

    private static final String CHANGE_OF_PLANS = "1_99";
    private static final String RANGER = "1_96";        // Boromir, a ranger

    /**
     * MultiplayerTable puts ONE copy of each named card into every seat's deck,
     * so without filler a "draw 8" draws whatever few cards exist. Measured on
     * 18_57, where the first version of that test failed expected:<8> was:<2>.
     */
    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "4_165",
            "7_193", "1_143", "12_157", "1_294", "1_312",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("plans", CHANGE_OF_PLANS);
            put("ranger", RANGER);
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

    /**
     * Put 3 of a player's cards back on their deck, so their hand is not 8.
     *
     * `keep` is not optional politeness: thinning P1's hand blind puts the card
     * under test back on the deck about a third of the time, and the symptom is
     * "the card is not playable" -- which reads like a broken card definition.
     */
    private static void ThinHand(VirtualTableScenario scn, String player,
                                 com.gempukku.lotro.game.PhysicalCardImpl keep) {
        var hand = new ArrayList<>(scn.GetHand(player));
        int moved = 0;
        for (var card : hand) {
            if (moved == 3) break;
            if (card == keep) continue;
            scn.MoveCardsToTopOfDeck((com.gempukku.lotro.game.PhysicalCardImpl) card);
            moved++;
        }
    }

    /** Who the game is actually waiting on, for a failure message worth reading. */
    private static String Pending(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            sb.append(p).append("=").append(d == null ? "(null)" : d.getText()).append("  ");
        }
        return sb.length() == 0 ? "(nobody)" : sb.toString();
    }

    /**
     * Exactly the chosen opponent redraws. Deliberately picks the opponent who
     * is NOT first in the offer order, so following the choice and defaulting
     * to getFirstShadowPlayer cannot give the same answer.
     */
    @Test
    public void onlyTheChosenOpponentShufflesAndRedraws() throws Exception {
        var scn = ThreeSeats();
        var plans = scn.GetCardFor(P1, "plans");
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "ranger"));
        scn.MoveCardsToHand(plans);

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String untouched = opponents.get(0);
        String chosen = opponents.get(1);
        assertNotEquals(untouched, chosen);

        // The opening hand is already 8, so "redrew to 8" would be satisfied by
        // an effect that does nothing. Thin every hand, including P1's, and
        // guard it -- an assertion nothing can falsify is worse than none.
        ThinHand(scn, P1, plans);
        ThinHand(scn, untouched, null);
        ThinHand(scn, chosen, null);
        int untouchedBefore = scn.GetHand(untouched).size();
        int ownBefore = scn.GetHand(P1).size();
        assertNotEquals("the chosen opponent must not already hold 8", 8, scn.GetHand(chosen).size());
        assertNotEquals("nor may the other one", 8, untouchedBefore);
        assertNotEquals("nor P1", 8, ownBefore);

        scn.PassUntilDecision(P1, "Regroup action");

        // GetCardActionId returns null when the action is absent and
        // PlayerDecided(null) passes the phase rather than throwing, so the
        // card would silently never fire.
        String playPlans = scn.GetCardActionId(P1, scn.GetCardFor(P1, "plans"));
        assertNotNull("Change of Plans should be playable; pending: " + Pending(scn), playPlans);
        scn.PlayerDecided(P1, playPlans);

        assertTrue("the Free Peoples player should be asked which opponent; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosen);

        assertEquals("the chosen opponent redrew to 8; pending: " + Pending(scn),
                8, scn.GetHand(chosen).size());
        assertEquals("the OTHER opponent is untouched -- this is the singular reading",
                untouchedBefore, scn.GetHand(untouched).size());
        // P1 is down exactly the event they played and nothing else -- they did
        // not redraw. `ownBefore - 1`, not `ownBefore`: Change of Plans is an
        // event and leaves the hand when it resolves.
        assertEquals("the player who played the card did not redraw",
                ownBefore - 1, scn.GetHand(P1).size());
    }
}
