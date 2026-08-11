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
 * "Choose an opponent to discard the top card of his or her draw deck" -- the
 * deck milled is the CHOSEN opponent's.
 *
 * Din of Arms (7_267) shipped with `deck: free people` and no choice at all: it
 * always milled the Free Peoples player. At two players the one opponent IS the
 * Free Peoples player, so the two agree; at three-plus seats the card offers a
 * choice the data never asked. The fix hoists a ChooseOpponent above the mill
 * (`deck: fromMemory(...)` resolves during the playability check, so the memory
 * must exist by then) and mills the remembered player.
 *
 * The fixture picks the opponent who is NOT the Free Peoples player, so a
 * regression to the fixed deck cannot produce the same answer.
 */
public class MultiplayerDinOfArmsAtTest {

    private static final String DIN = "7_267";
    private static final String PATROL = "1_177";   // any minion, to give the turn a maneuver phase

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195", "1_178",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("din", DIN);
            put("patrol", PATROL);
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
    public void theChosenOpponentsDeckIsMilled() throws Exception {
        var scn = ThreeSeats();
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String actor = opponents.get(0);
        String victim = opponents.get(1);

        scn.MoveMinionsToTable(scn.GetCardFor(actor, "patrol"));
        scn.MoveCardsToHand(scn.GetCardFor(actor, "din"));
        scn.SetTwilight(5);

        // A Maneuver event: the actor's shadow-phase window comes first and
        // also matches "action", so reach the maneuver phase before looking.
        scn.PassUntilPhase(com.gempukku.lotro.common.Phase.MANEUVER);
        scn.PassUntilDecision(actor, "action");
        String actionId = scn.GetCardActionId(actor, scn.GetCardFor(actor, "din"));
        assertNotNull("Din of Arms should be playable", actionId);

        int fpDeckBefore = scn.GetDrawDeck(P1).size();
        int victimDeckBefore = scn.GetDrawDeck(victim).size();

        scn.PlayerDecided(actor, actionId);

        // The actor now chooses whose deck: pick the opponent who is NOT the
        // Free Peoples player -- the seat the shipped card could never reach.
        assertTrue("the actor should be asked which opponent",
                scn.AnyDecisionsAvailable(actor));
        scn.ChooseOption(actor, victim);

        assertEquals("the chosen opponent's deck loses its top card",
                victimDeckBefore - 1, scn.GetDrawDeck(victim).size());
        assertEquals("the Free Peoples player's deck is untouched -- milling it"
                        + " was the shipped bug",
                fpDeckBefore, scn.GetDrawDeck(P1).size());
    }
}
