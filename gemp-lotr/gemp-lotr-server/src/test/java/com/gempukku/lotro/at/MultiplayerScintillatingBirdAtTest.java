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
 * "Choose an opponent to NAME either Free Peoples or Shadow" -- the opponent
 * does the naming, not the card's owner.
 *
 * Scintillating Bird (17_23) shipped with a chooseYesOrNo that had no player
 * field, and the appender's default is "you" (ChooseYesOrNo.java:29) -- so the
 * owner answered the question the printed text hands to an opponent. That is
 * wrong even at two players; at three-plus there is additionally no opponent
 * CHOICE at all. The fix inserts a ChooseOpponent and gives the yes/no to the
 * remembered player.
 *
 * The fixture asks for the opponent who is NOT first in the offer order, so a
 * regression to any fixed seat cannot produce the same answer.
 */
public class MultiplayerScintillatingBirdAtTest {

    private static final String BIRD = "17_23";
    private static final String GANDALF = "1_72";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("bird", BIRD);
            put("gandalf", GANDALF);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
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

    @Test
    public void theChosenOpponentDoesTheNaming() throws Exception {
        var scn = ThreeSeats();

        var gandalf = scn.GetCardFor(P1, "gandalf");
        var bird = scn.GetCardFor(P1, "bird");
        scn.MoveCompanionsToTable(gandalf);
        scn.MoveCardsToSupportArea(bird);
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String otherOpponent = opponents.get(1);

        String actionId = scn.GetCardActionId(P1, bird);
        assertNotNull("the Bird's fellowship action should be available", actionId);
        scn.PlayerDecided(P1, actionId);

        // The owner picks WHICH opponent names; the cost (exert your wizard)
        // auto-resolves on the only wizard.
        assertTrue("the owner should be asked which opponent names",
                scn.AnyDecisionsAvailable(P1));
        scn.ChooseOption(P1, otherOpponent);

        assertTrue("the CHOSEN opponent should hold the naming decision",
                scn.DecisionAvailable(otherOpponent, "Name Free Peoples"));
        assertFalse("the unchosen opponent must not hold it",
                scn.AnyDecisionsAvailable(firstShadow));
        assertFalse("the owner must not hold it -- that was the 2p bug",
                scn.AnyDecisionsAvailable(P1));
    }
}
