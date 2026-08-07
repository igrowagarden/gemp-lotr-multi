package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
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
 * "If NO OPPONENT controls a site" -- a REQUIREMENT, not an effect.
 *
 * Last Throw (10_34) was written as Not(controlsSite player: shadow). That asks
 * about one seat, so at five players the event stayed playable while three
 * opponents each held sites. Nothing about the card's effect was wrong; the
 * question it asks before the effect was.
 *
 * The fix needed no engine change and that is worth recording: GEMP already had
 * `opponentDoesNotControlSite`, already N-player correct
 * (getControlledSitesCountOfOpponents sums over getOpponents), and already used
 * by 4_71 and its errata. The first draft of this fix widened ControlsSite to a
 * PlayersSource before anyone read the requirement list.
 *
 * This test asserts the SECOND opponent, because giving the first one a site
 * blocks the event under the old code too.
 */
public class MultiplayerNoOpponentControlsSiteAtTest {

    private static final String LAST_THROW = "10_34";
    // Two [gondor] Men, because the card also requires spotting them. Both are
    // plain companions with no card text of their own, so nothing they do can
    // be mistaken for the requirement under test.
    private static final String KNIGHT = "5_35";     // Gondorian Knight
    private static final String ARAGORN = "4_109";   // Aragorn

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("throw", LAST_THROW);
            put("knight", KNIGHT);
            put("aragorn", ARAGORN);
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

    private static String Pending(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            sb.append(p).append("=").append(d == null ? "(null)" : d.getText()).append("  ");
        }
        return sb.length() == 0 ? "(nobody)" : sb.toString();
    }

    /**
     * The table in Regroup with Last Throw in hand and the twilight to play it.
     *
     * `controller` names the opponent to hand the current site to, or null for
     * nobody. It has to be set BEFORE the phase is reached: an action list in
     * this harness is a SNAPSHOT of the pending decision's parameters, taken
     * when the decision was sent, so changing game state afterwards and asking
     * again returns the same list. The first version of this test set the
     * controller after arriving and read "still playable" -- which looks exactly
     * like the fix not working.
     */
    private VirtualTableScenario TableInRegroup(int controllerIndex) throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "knight"), scn.GetCardFor(P1, "aragorn"));
        scn.MoveCardsToHand(scn.GetCardFor(P1, "throw"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        assertEquals("fixture assumes the first seat is what getFirstShadowPlayer names",
                opponents.get(0), GameUtils.getFirstShadowPlayer(scn.game()));

        if (controllerIndex >= 0) {
            var site = scn.GetCurrentSite();
            assertNotNull("the fellowship should be standing on a site", site);
            site.setCardController(opponents.get(controllerIndex));
            assertEquals("fixture: that opponent now controls a site", 1,
                    GameUtils.getControlledSitesCountByPlayer(scn.game(),
                            opponents.get(controllerIndex)));
            assertEquals("fixture: and the OTHER opponent does not", 0,
                    GameUtils.getControlledSitesCountByPlayer(scn.game(),
                            opponents.get(1 - controllerIndex)));
        }

        scn.PassUntilPhase(Phase.REGROUP);
        scn.SetTwilight(9);
        return scn;
    }

    private static String PlayAction(VirtualTableScenario scn) {
        return "Play " + GameUtils.getFullName(scn.GetCardFor(P1, "throw"));
    }

    /**
     * The vacuity guard for the two below. Without it, a card that had simply
     * stopped being playable at all would satisfy both of them.
     */
    @Test
    public void withNobodyControllingASiteTheEventIsPlayable() throws Exception {
        var scn = TableInRegroup(-1);
        assertTrue("nobody controls a site, so the event should be playable; pending: "
                + Pending(scn), scn.ActionAvailable(P1, PlayAction(scn)));
    }

    /** Held before the fix too -- included so the pair reads as a contrast. */
    @Test
    public void aSiteHeldByTheFirstOpponentBlocksTheEvent() throws Exception {
        var scn = TableInRegroup(0);
        assertFalse("the first opponent controls a site; pending: " + Pending(scn),
                scn.ActionAvailable(P1, PlayAction(scn)));
    }

    /**
     * The whole point. Not(controlsSite player: shadow) asked only about the seat
     * getFirstShadowPlayer names, so a site held by anybody else was invisible
     * and the event stayed playable.
     */
    @Test
    public void aSiteHeldByTheSecondOpponentBlocksTheEventToo() throws Exception {
        var scn = TableInRegroup(1);
        assertFalse("the SECOND opponent controls a site, and \"no opponent controls"
                        + " a site\" means every one of them; pending: " + Pending(scn),
                scn.ActionAvailable(P1, PlayAction(scn)));
    }
}
