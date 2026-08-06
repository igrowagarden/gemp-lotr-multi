package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Choose an opponent and make him or her choose FOR you."
 *
 * Birthday Present (10_104): "Add a burden to choose 2 [shire] events with
 * different card titles from your discard pile. Choose an opponent and make him
 * or her choose 1 of those cards for you to take into hand." The data said
 * {chooseArbitraryCards, player: shadow} for the opponent's half --
 * getFirstShadowPlayer, and no choice offered at all.
 *
 * The pool the opponent picks from is a `fromMemory` list, not a zone and not
 * the table, so unlike the "his or her minions" cards there is nothing here to
 * scope: the two cards were already narrowed by the controller. Only the player
 * token is wrong. That makes this the cheapest possible check of the claim that
 * a memory-backed pool needs no filter -- the same claim Far-seeing Eyes settled
 * for a hand.
 */
public class MultiplayerOpponentPicksForYouAtTest {

    private static final String BIRTHDAY_PRESENT = "10_104";
    // Two [shire] events with DIFFERENT titles: the card demands it, and
    // uniqueTitles would otherwise collapse the pool to one and resolve the
    // opponent's choice without asking.
    private static final String SHIRE_EVENT_A = "1_294";     // Hobbit Appetite
    private static final String SHIRE_EVENT_B = "1_312";     // Sorry About Everything

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("present", BIRTHDAY_PRESENT);
            put("eventA", SHIRE_EVENT_A);
            put("eventB", SHIRE_EVENT_B);
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
     * The chosen opponent is the one who picks, and the card they pick comes
     * back to the Free Peoples player's hand.
     *
     * Deliberately picks the opponent who is NOT first in the offer order, so
     * defaulting to getFirstShadowPlayer cannot produce the same answer.
     */
    @Test
    public void theChosenOpponentPicksTheCardYouGetBack() throws Exception {
        var scn = ThreeSeats();
        var eventA = scn.GetCardFor(P1, "eventA");
        var eventB = scn.GetCardFor(P1, "eventB");
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "present"));
        scn.MoveCardsToDiscard(eventA, eventB);

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertNotEquals(defaultOpponent, chosenOpponent);

        scn.PassUntilDecision(P1, "Fellowship action");
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, scn.GetCardFor(P1, "present")));

        // The controller narrows the pool to two first; only then is an opponent
        // brought into it.
        assertTrue("the controller should choose the two cards; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose cards to give to opponent to choose"));
        // An ARBITRARY_CARDS decision numbers its cards "temp0", "temp1" rather
        // than by card id, so it has to be answered by position.
        scn.ChooseCardBPFromSelection(P1, eventA, eventB);

        assertTrue("the Free Peoples player should be asked which opponent; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        assertTrue("the chosen opponent should be the one picking; pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "Choose card to return"));
        assertFalse("the unchosen opponent should not be asked; pending: " + Pending(scn),
                scn.DecisionAvailable(defaultOpponent, "Choose card to return"));

        scn.ChooseCardBPFromSelection(chosenOpponent, eventB);

        assertEquals("the card the opponent picked goes to the controller's hand",
                Zone.HAND, eventB.getZone());
        assertEquals("the card they did not pick stays in the discard pile",
                Zone.DISCARD, eventA.getZone());
    }
}
