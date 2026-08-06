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
 * "Any Shadow player may ... to prevent this" at more than two players.
 *
 * Sixteen cards carry that sentence. The prevention used to be offered to a
 * single player -- getFirstShadowPlayer, the seat counter-clockwise from the
 * Free Peoples player -- so at five seats one opponent was asked and the other
 * three never were. It is invisible at two players, where the only opponent is
 * always the one asked, which is why nothing caught it.
 *
 * These tests exist because no two-player test can express the question.
 */
public class MultiplayerPreventionAtTest {

    /**
     * The Faithful Stone (18_50): "...Any Shadow player may remove (2) to prevent this."
     * Chosen because its two-player prevention already has a passing test, so the
     * only new variable here is the number of seats.
     */
    private static final String STONE = "18_50";
    /** The stone's action has to spot a minion, so the table needs one in play. */
    private static final String URUK = "4_187";

    private VirtualTableScenario FiveSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(5, new HashMap<>() {{
            put("stone", STONE);
            put("uruk", URUK);
        }});
    }

    /**
     * The Shadow players, in the order a round-the-table offer must use:
     * counter-clockwise from the Free Peoples player, which is the order the
     * Shadow phases themselves run in.
     */
    private static List<String> ShadowSeatsInOfferOrder(VirtualTableScenario scn) {
        String fp = scn.FreePeoplesPlayer();
        PlayOrder order = scn.game().getGameState().getPlayerOrder()
                .getCounterClockwisePlayOrder(fp, false);
        order.getNextPlayer();
        List<String> shadowPlayers = new ArrayList<>();
        String next;
        while ((next = order.getNextPlayer()) != null && !next.equals(fp))
            shadowPlayers.add(next);
        return shadowPlayers;
    }

    /**
     * The table with the stone in play and a minion to spot, advanced to the
     * Maneuver phase with the Free Peoples player holding the action.
     */
    private VirtualTableScenario TableReadyToUseTheStone() throws Exception {
        var scn = FiveSeats();
        // P1 takes the first turn: BidAndSeatPlayers gives it the only non-zero
        // bid, so it wins the seat choice outright.
        var stone = scn.GetCardFor(P1, "stone");
        scn.MoveCardsToSupportArea(stone);
        scn.MoveMinionsToTable(scn.GetCardFor(P2, "uruk"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        scn.AddTokensToCard(stone, 3);
        scn.PassUntilPhase(Phase.MANEUVER);
        scn.SetTwilight(2);
        return scn;
    }

    @Test
    public void aFiveSeatTableSeatsFivePlayers() throws Exception {
        var scn = FiveSeats();
        assertEquals(5, scn.game().getGameState().getPlayerOrder().getPlayerCount());
    }

    @Test
    public void everySeatIsDealtItsOwnCards() throws Exception {
        var scn = FiveSeats();
        for (String seat : VirtualTableScenario.SeatNames(5)) {
            assertTrue(seat + " should have a deck",
                    scn.game().getGameState().getDeck(seat).size() > 0);
        }
    }

    /**
     * The Free Peoples player is only meaningful once the first turn has begun.
     * Read earlier, getCurrentPlayerId returns a pre-turn value that varies
     * between runs -- which is what made an earlier version of this test pass
     * five runs alone, pass a full suite, and then fail when the deck changed.
     */
    @Test
    public void theFirstSeatTakesTheFirstTurn() throws Exception {
        var scn = FiveSeats();
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());
        assertEquals(4, ShadowSeatsInOfferOrder(scn).size());
    }

    /**
     * The whole point of the harness work: at five seats every Shadow player
     * must get a turn to decline before the effect resolves. Under the old
     * behaviour exactly one was asked, so this fails from the second seat on.
     */
    @Test
    public void everyShadowPlayerIsOfferedThePreventionInTurn() throws Exception {
        var scn = TableReadyToUseTheStone();
        List<String> shadowSeats = ShadowSeatsInOfferOrder(scn);
        assertEquals(4, shadowSeats.size());

        var stone = scn.GetCardFor(P1, "stone");
        assertTrue("the Free Peoples player should be able to use the stone",
                scn.ActionAvailable(P1, "Use " + GameUtils.getFullName(stone)));
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, stone));

        for (String shadowSeat : shadowSeats) {
            assertTrue(shadowSeat + " should have been offered the prevention",
                    scn.DecisionAvailable(shadowSeat, "prevent"));
            scn.ChooseOption(shadowSeat, "No");
        }
    }

    /** The first acceptance ends the offer; later seats are never asked. */
    @Test
    public void theOfferStopsAtTheFirstAcceptance() throws Exception {
        var scn = TableReadyToUseTheStone();
        List<String> shadowSeats = ShadowSeatsInOfferOrder(scn);

        var stone = scn.GetCardFor(P1, "stone");
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, stone));

        assertTrue(scn.DecisionAvailable(shadowSeats.get(0), "prevent"));
        scn.ChooseOption(shadowSeats.get(0), "No");

        assertTrue(scn.DecisionAvailable(shadowSeats.get(1), "prevent"));
        scn.ChooseOption(shadowSeats.get(1), "Yes");
        assertEquals("the preventing player paid the twilight", 0, scn.GetTwilight());

        for (String notAsked : shadowSeats.subList(2, shadowSeats.size()))
            assertFalse(notAsked + " should not be asked after someone prevented",
                    scn.DecisionAvailable(notAsked, "prevent"));
    }
}
