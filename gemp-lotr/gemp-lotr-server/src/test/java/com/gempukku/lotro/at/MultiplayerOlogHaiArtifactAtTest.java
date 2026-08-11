package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
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
 * "The Free Peoples player may discard an artifact from play to prevent this"
 * -- a COST, and a cost cannot be paid with another player's card (ruled).
 *
 * Beastly Olog-hai (11_108) shipped with `discard choose(artifact)` unscoped,
 * so the Free Peoples player could pay by discarding an artifact a SHADOW
 * player owns. At two players the only artifacts the FP player would ever
 * volunteer are their own, but the offer machinery counts affordability -- an
 * unscoped select makes everyone's artifacts legal tender.
 *
 * With a Shadow artifact in play and exactly one FP artifact, the scoped cost
 * has one candidate and auto-resolves; the shipped card had two and asked.
 * Where the discarded card ends up is the result.
 */
public class MultiplayerOlogHaiArtifactAtTest {

    private static final String OLOG = "11_108";
    private static final String GANDALF = "1_72";
    private static final String STAFF = "2_22";     // FP artifact, target Gandalf
    private static final String WHIP = "2_74";      // SHADOW artifact
    private static final String PATROL = "1_177";   // a minion to carry the whip

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195", "1_178",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("olog", OLOG);
            put("gandalf", GANDALF);
            put("staff", STAFF);
            put("whip", WHIP);
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
    public void thePreventionIsPaidWithTheFreePeoplesPlayersOwnArtifact() throws Exception {
        var scn = ThreeSeats();
        var gandalf = scn.GetCardFor(P1, "gandalf");
        var staff = scn.GetCardFor(P1, "staff");
        scn.MoveCompanionsToTable(gandalf);
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String actor = opponents.get(0);
        String other = opponents.get(1);

        scn.AttachCardsTo(gandalf, staff);

        // A Shadow artifact in play, owned by the OTHER opponent: legal tender
        // under the shipped card, out of reach under the ruling.
        var olog = scn.GetCardFor(actor, "olog");
        var patrol = scn.GetCardFor(other, "patrol");
        var whip = scn.GetCardFor(other, "whip");
        scn.MoveMinionsToTable(olog, patrol);
        scn.AttachCardsTo(patrol, whip);

        scn.PassUntilPhase(Phase.ASSIGNMENT);
        scn.PassUntilDecision(actor, "action");
        String actionId = scn.GetCardActionId(actor, olog);
        assertNotNull("the Olog-hai's assignment action should be available",
                actionId);
        scn.PlayerDecided(actor, actionId);

        // The one artifact-bearing companion auto-selects; the FP player is
        // offered the prevention.
        assertTrue("the Free Peoples player should be offered the prevention",
                scn.AnyDecisionsAvailable(P1));
        scn.PlayerDecided(P1, "0");

        // Scoped, the cost has exactly one candidate (the staff) and resolves
        // without asking. Unscoped, a which-artifact decision interposes and
        // none of the below holds yet.
        assertEquals("the FP player's own artifact pays",
                Zone.DISCARD, staff.getZone());
        assertEquals("the Shadow player's artifact is not the FP player's to spend",
                Zone.ATTACHED, whip.getZone());
        assertFalse("the assignment was prevented",
                scn.IsCharAssignedAgainst(gandalf, olog));
    }
}
