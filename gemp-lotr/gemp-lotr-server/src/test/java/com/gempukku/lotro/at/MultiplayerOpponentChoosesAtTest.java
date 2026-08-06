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
 * "Choose a Shadow player who must do one thing OR the other."
 *
 * Duality (13_47): "Spot Sméagol to choose a Shadow player who must discard a
 * Shadow card from hand or remove (2)." The data said {choice, player: shadow}
 * with {discardFromHand, hand: shadow, player: shadow} underneath -- three
 * getFirstShadowPlayer lookups and no choice offered at all.
 *
 * This is a shape the earlier cards did not cover. Far-seeing Eyes proved a
 * `hand:` needs no card filter; what is new here is that the OPPONENT is the one
 * being asked a question rather than merely being the target of one. `choice`
 * builds a DelegateActionContext around whoever `player:` names, so getting that
 * wrong sends the whole fork to the wrong seat -- and at two players the wrong
 * seat and the right seat are the same person.
 */
public class MultiplayerOpponentChoosesAtTest {

    private static final String DUALITY = "13_47";
    private static final String SMEAGOL = "13_55";
    // Fixture rule: a DIFFERENT Shadow card per seat. The same unique card on
    // two seats is discarded down to one, and then an opponent has nothing to
    // discard and the card under test looks broken.
    private static final String SHADOW_CARD_A = "1_144";    // Uruk Bloodlust
    private static final String SHADOW_CARD_B = "1_133";    // Saruman's Ambition

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("duality", DUALITY);
            put("smeagol", SMEAGOL);
            put("shadowA", SHADOW_CARD_A);
            put("shadowB", SHADOW_CARD_B);
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
     * The fork is put to whichever opponent was chosen, and the discard comes
     * out of that opponent's hand.
     *
     * Deliberately picks the opponent who is NOT first in the offer order, so
     * that following the choice and defaulting to getFirstShadowPlayer cannot
     * produce the same answer.
     */
    @Test
    public void theForkIsPutToTheOpponentWhoWasChosen() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "smeagol"));
        scn.MoveCardsToHand(scn.GetCardFor(P1, "duality"));
        scn.MoveCardsToHand(scn.GetCardFor(P2, "shadowA"));
        scn.MoveCardsToHand(scn.GetCardFor(P3, "shadowB"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertNotEquals(defaultOpponent, chosenOpponent);

        scn.PassUntilDecision(P1, "Fellowship action");
        // Both halves must be playable or Choice resolves itself and never asks:
        // "remove (2)" is unplayable with an empty twilight pool, which collapses
        // the fork to the discard and quietly tests a different path -- measured,
        // not assumed: without this the opponent is handed "Choose cards from hand
        // to discard" directly.
        scn.SetTwilight(5);

        int defaultBefore = scn.GetHand(defaultOpponent).size();
        int chosenBefore = scn.GetHand(chosenOpponent).size();

        scn.PlayerDecided(P1, scn.GetCardActionId(P1, scn.GetCardFor(P1, "duality")));

        assertTrue("the Free Peoples player should be asked which opponent; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        // Whether a fork is even shown depends on how many of the two halves are
        // playable -- Choice resolves itself when only one is -- so assert who is
        // holding the game up rather than the shape of the question.
        assertNotNull("the chosen opponent should be the one asked; pending: " + Pending(scn),
                scn.userFeedback().getAwaitingDecision(chosenOpponent));
        assertNull("the unchosen opponent should not be asked; pending: " + Pending(scn),
                scn.userFeedback().getAwaitingDecision(defaultOpponent));

        assertTrue("the fork itself should be what the chosen opponent is holding; pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "Choose action to perform"));
        scn.ChooseOption(chosenOpponent, "Discard a Shadow card from hand");

        // Their hand holds more than one Shadow card, so the discard does ask
        // which one. Answering it is what makes the hand actually shrink.
        assertNotNull("the discard should be asked of the chosen opponent too; pending: " + Pending(scn),
                scn.userFeedback().getAwaitingDecision(chosenOpponent));
        scn.PlayerDecided(chosenOpponent, scn.GetADParamAsList(chosenOpponent, "cardId").getFirst());

        assertEquals("the chosen opponent lost a card from hand",
                chosenBefore - 1, scn.GetHand(chosenOpponent).size());
        assertEquals("the other opponent's hand is untouched",
                defaultBefore, scn.GetHand(defaultOpponent).size());
    }
}
