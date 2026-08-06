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
 * "Choose an opponent to discard a card from hand."
 *
 * Far-seeing Eyes (1_43) said {discardFromHand, hand: shadowPlayer,
 * player: shadowPlayer} -- getFirstShadowPlayer twice, so above two players it
 * emptied a card from one fixed seat's hand and never asked.
 *
 * This card is the first of five hand and deck effects, and it tests a claim
 * the others depend on: that a hand needs no card filter, because `hand:` names
 * whose hand it is and a hand is genuinely per-player. That is a DIFFERENT claim
 * from the one Introspection disproved -- there a `side()` card filter failed to
 * scope a selection over a shared pool. Worth checking on one card before
 * trusting it for the other four.
 */
public class MultiplayerDiscardFromHandAtTest {

    private static final String FAR_SEEING_EYES = "1_43";
    private static final String ELF = "1_60";           // any Elven companion, to trigger it
    // Different cards per seat: the same card on two seats is unique-discarded.
    private static final String SHADOW_CARD_A = "1_144";
    private static final String SHADOW_CARD_B = "1_133";

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("eyes", FAR_SEEING_EYES);
            put("elf", ELF);
            put("shadowA", SHADOW_CARD_A);
            put("shadowB", SHADOW_CARD_B);
        }});
    }

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
     * The discard comes out of the chosen opponent's hand, and the other
     * opponent's hand is untouched. Deliberately picks the opponent who is NOT
     * first in the offer order, so following the choice and defaulting to
     * getFirstShadowPlayer cannot give the same answer.
     */
    @Test
    public void theChosenOpponentDiscardsFromTheirOwnHand() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "eyes"));
        scn.MoveCardsToHand(scn.GetCardFor(P1, "elf"));
        scn.MoveCardsToHand(scn.GetCardFor(P2, "shadowA"));
        scn.MoveCardsToHand(scn.GetCardFor(P3, "shadowB"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);

        int defaultBefore = scn.GetHand(defaultOpponent).size();
        int chosenBefore = scn.GetHand(chosenOpponent).size();

        scn.PassUntilDecision(P1, "Fellowship action");
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, scn.GetCardFor(P1, "elf")));

        assertTrue("the Free Peoples player should be asked which opponent",
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        // A forced discard with one card in hand resolves without asking, so
        // assert the outcome rather than a prompt.
        assertEquals("the chosen opponent lost a card from hand",
                chosenBefore - 1, scn.GetHand(chosenOpponent).size());
        assertEquals("the other opponent's hand is untouched",
                defaultBefore, scn.GetHand(defaultOpponent).size());
    }
}
