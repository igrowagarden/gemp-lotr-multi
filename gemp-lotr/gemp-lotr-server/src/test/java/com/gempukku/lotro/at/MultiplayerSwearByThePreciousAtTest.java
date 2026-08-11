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
 * "THAT MINION'S OWNER may make each minion strength -2 ... to prevent that" --
 * the preventer is the owner of the chosen minion, and the strength penalty is
 * a cost, so it lands on the payer's own minions (a cost cannot be paid with
 * another player's card, ruled).
 *
 * Swear by the Precious (15_52) shipped as `player: Shadow` -- the fixed seat
 * getFirstShadowPlayer names -- with `all(minion)` as the cost. At two players
 * the owner IS the first Shadow player and every minion is theirs, so both
 * defects are invisible there.
 *
 * The fixture gives the only minion to the opponent getFirstShadowPlayer does
 * NOT name, so the fixed seat and the owner produce different holders of the
 * prevention decision.
 */
public class MultiplayerSwearByThePreciousAtTest {

    private static final String SWEAR = "15_52";
    private static final String SMEAGOL = "9_30";
    private static final String PATROL = "1_177";   // Goblin Patrol, vitality 3

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195", "1_178",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("swear", SWEAR);
            put("smeagol", SMEAGOL);
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
    public void theChosenMinionsOwnerHoldsThePrevention() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "smeagol"));
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String minionOwner = opponents.get(1);
        assertEquals("getFirstShadowPlayer should name the first opponent",
                firstShadow, GameUtils.getFirstShadowPlayer(scn.game()));

        // ONLY the non-first opponent has a minion: under the shipped card the
        // first seat held the prevention for a minion it does not own.
        var patrol = scn.GetCardFor(minionOwner, "patrol");
        scn.MoveMinionsToTable(patrol);
        scn.MoveCardsToHand(scn.GetCardFor(P1, "swear"));

        // A Maneuver event: P1's fellowship window comes first and also
        // matches "action", so reach the maneuver phase before looking.
        scn.PassUntilPhase(com.gempukku.lotro.common.Phase.MANEUVER);
        String actionId = scn.GetCardActionId(P1, scn.GetCardFor(P1, "swear"));
        assertNotNull("Swear by the Precious should be playable", actionId);

        int strengthBefore = scn.GetStrength(patrol);
        scn.PlayerDecided(P1, actionId);

        // The one minion auto-selects; the prevention offer is the result.
        assertTrue("the minion's OWNER should hold the prevention decision",
                scn.DecisionAvailable(minionOwner, "strength -2"));
        assertFalse("the seat getFirstShadowPlayer names owns nothing here and"
                        + " must not be asked -- that was the shipped bug",
                scn.AnyDecisionsAvailable(firstShadow));

        // Pay it: the penalty lands on the payer's own minions and the chosen
        // minion survives.
        scn.PlayerDecided(minionOwner, "0");
        assertEquals("the minion stays in play after paying",
                strengthBefore - 2, scn.GetStrength(patrol));
    }
}
