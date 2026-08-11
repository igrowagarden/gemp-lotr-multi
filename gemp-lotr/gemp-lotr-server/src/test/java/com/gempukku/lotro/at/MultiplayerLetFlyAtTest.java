package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Choose a Shadow player to wound each of THAT PLAYER'S minions which is not
 * of that race" -- the wounds land on one chosen player's minions.
 *
 * Let Fly (13_19) shipped with no player choice at all: it wounded every
 * off-race minion at the table. At two players the one Shadow player owns all
 * of them, so the two agree; at three-plus the card wounds people the player
 * never chose. The fix inserts a ChooseOpponent and scopes the wound select
 * with ownedByPlayerFromMemory.
 *
 * The fixture targets the opponent who is NOT first in the offer order, and
 * seats an identical off-race minion on each opponent, so the shipped
 * wound-everyone behaviour and the fixed wound-one-player behaviour cannot
 * agree on the untouched seat.
 */
public class MultiplayerLetFlyAtTest {

    private static final String LET_FLY = "13_19";
    private static final String ARWEN = "1_30";
    private static final String LEGOLAS = "1_50";
    private static final String LORIEN_ELF = "1_53";
    private static final String URUK = "1_127";     // race Uruk-hai -- the spared race
    private static final String PATROL = "1_177";   // race Orc, vitality 3

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195", "1_178",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("letfly", LET_FLY);
            put("arwen", ARWEN);
            put("legolas", LEGOLAS);
            put("lorien", LORIEN_ELF);
            put("uruk", URUK);
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
    public void onlyTheChosenPlayersOffRaceMinionsAreWounded() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable(
                scn.GetCardFor(P1, "arwen"),
                scn.GetCardFor(P1, "legolas"),
                scn.GetCardFor(P1, "lorien"));
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String target = opponents.get(1);

        // Both opponents hold an identical Orc-race minion; the first also
        // holds the Uruk-hai exemplar whose race will be spared.
        var uruk = scn.GetCardFor(firstShadow, "uruk");
        var firstOrc = scn.GetCardFor(firstShadow, "patrol");
        var targetOrc = scn.GetCardFor(target, "patrol");
        scn.MoveMinionsToTable(uruk, firstOrc, targetOrc);
        scn.MoveCardsToHand(scn.GetCardFor(P1, "letfly"));

        scn.PassUntilPhase(Phase.ARCHERY);
        String actionId = scn.GetCardActionId(P1, scn.GetCardFor(P1, "letfly"));
        assertNotNull("Let Fly should be playable in the archery phase", actionId);
        scn.PlayerDecided(P1, actionId);

        // Cost: pick the minion whose race is spared (the Uruk).
        scn.ChooseCards(P1, uruk);

        // Effect: pick WHICH Shadow player -- the one who is not first.
        assertTrue("the owner should be asked which Shadow player",
                scn.AnyDecisionsAvailable(P1));
        scn.ChooseOption(P1, target);

        assertEquals("the chosen player's off-race minion is wounded",
                1, scn.GetWoundsOn(targetOrc));
        assertEquals("the UNCHOSEN player's off-race minion is untouched --"
                        + " wounding it was the shipped bug",
                0, scn.GetWoundsOn(firstOrc));
        assertEquals("the spared race is spared wherever it lives",
                0, scn.GetWoundsOn(uruk));
    }
}
