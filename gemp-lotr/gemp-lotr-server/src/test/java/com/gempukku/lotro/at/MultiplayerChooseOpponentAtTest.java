package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Choose an opponent" has to mean the opponent who was chosen.
 *
 * Crashed Gate (8_119): "At the start of the regroup phase, the Free Peoples
 * player must add 3 threats or choose an opponent who may take control of a
 * site." The card data does choose one and store it -- {type: ChooseOpponent,
 * memorize: opponent} -- and then asked `player: shadow`, which resolves to
 * getFirstShadowPlayer: a fixed seat, not the answer.
 *
 * At two players the chosen opponent is the only opponent, so the two agree and
 * the six existing tests in Card_08_119_Tests pass either way. Only a third seat
 * can tell them apart, which is why this lives here rather than beside them.
 */
public class MultiplayerChooseOpponentAtTest {

    /**
     * Crashed Gate is `site: 5` in its own definition, so it can only sit at
     * position five -- an attempt to place it under the fellowship at position
     * one leaves the path with no site 1 and the engine dereferences a null
     * current site. Reaching it means walking the fellowship there.
     *
     * The companions are there for the threat limit: the card offers "add 3
     * threats" as the other half of the choice, and without headroom for them
     * the choice collapses to one option.
     */
    private VirtualTableScenario ThreeSeats() throws Exception {
        return new VirtualTableScenario(3,
                new HashMap<>() {{
                    put("merry", "4_310");
                    put("pippin", "4_314");
                    put("sam", "1_311");
                }},
                new HashMap<>() {{
                    put("site1", "7_330");
                    put("site2", "7_335");
                    put("site3", "8_117");
                    put("site4", "7_342");
                    put("site5", "8_119");   // Crashed Gate
                    put("site6", "7_350");
                    put("site7", "8_120");
                    put("site8", "10_120");
                    put("site9", "7_360");
                }},
                VirtualTableScenario.FOTRFrodo,
                VirtualTableScenario.RulingRing,
                VirtualTableScenario.Multipath);
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

    @Test
    public void aThreeSeatTableHasTwoOpponents() throws Exception {
        var scn = ThreeSeats();
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());
        assertEquals(2, ShadowSeats(scn).size());
    }

    /**
     * The site control must be offered to whichever opponent was picked, not to
     * whichever seat getFirstShadowPlayer happens to name.
     *
     * Deliberately picks the opponent who is NOT first in the offer order, so
     * that following the choice and defaulting to the first Shadow player cannot
     * produce the same answer -- the mistake that made the original bug
     * invisible.
     */
    @Ignore("Needs the fellowship walked to site 5, where Crashed Gate is fixed by "
            + "its own `site: 5` field. SkipToSite is a P1/P2 helper like StartGame "
            + "was, so it stalls above two seats; generalising it the way "
            + "StartMultiplayerGame generalised StartGame is the remaining step. "
            + "Until then the fix rests on reading the code and on the six "
            + "two-player tests in Card_08_119_Tests still passing.")
    @Test
    public void theSiteControlFollowsTheOpponentWhoWasChosen() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable("sam", "merry", "pippin");
        scn.StartMultiplayerGame();

        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String otherOpponent = opponents.get(1);
        assertNotEquals(defaultOpponent, otherOpponent);

        scn.PassUntilPhase(Phase.REGROUP);

        // Crashed Gate is a site, so it fires at the start of Regroup only when
        // the fellowship is standing on it. Fail loudly rather than returning
        // early: a test that quietly passes when it never reached the thing it
        // is testing is worse than no test.
        if (!scn.DecisionAvailable(P1, "Choose an opponent")
                && !scn.DecisionAvailable(P1, "opponent")) {
            fail("never reached the Crashed Gate trigger; the fellowship has to be "
                    + "standing on it at the start of Regroup. Pending: "
                    + scn.userFeedback().getUsersPendingDecision());
        }

        scn.ChooseOption(P1, otherOpponent);

        assertTrue("the chosen opponent should be asked",
                scn.DecisionAvailable(otherOpponent, "take control of a site"));
        assertFalse("the unchosen opponent should not be asked",
                scn.DecisionAvailable(defaultOpponent, "take control of a site"));
    }
}
