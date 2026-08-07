package com.gempukku.lotro.at;

import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Any Shadow player may ... INSTEAD" -- the offer that is not a prevention.
 *
 * Morning Came (7_243): "Make a [rohan] Man strength +4. Any Shadow player may
 * place a random card from hand beneath his or her draw deck to make that Man
 * strength +2 instead."
 *
 * It was written as an `optional` with `player: shadow`, and `Optional` resolves
 * that through PlayerResolver.resolvePlayer, which returns exactly one seat. So
 * the offer went to whoever getFirstShadowPlayer named and the other opponents
 * were never asked -- and the cost said `hand: shadow`, which names the same
 * fixed seat, so widening one without the other would have changed nothing.
 *
 * The card is now a `preventable` with an `instead:` branch, which is the shape
 * the printed text actually has: an offer round the table that stops at the
 * first acceptance, with a different outcome when somebody takes it. That
 * machinery already loops over players and is already measured by
 * MultiplayerPreventionAtTest -- so this test is about whether the CARD reaches
 * it, and about who pays once it does.
 *
 * Two seats are enough to make both failures visible, and three is the smallest
 * table with two Shadow players.
 */
public class MultiplayerAnyShadowMayInsteadAtTest {

    private static final String MORNING_CAME = "7_243";
    /** Eomer, Skilled Tactician: [rohan] Man, strength 8, no strength text of his own. */
    private static final String EOMER = "7_227";
    // A different minion per seat -- the same unique card on two seats is
    // discarded down to one, and then one opponent has nothing to skirmish.
    private static final String WARRIOR = "4_165";      // Orthanc Warrior, vitality 2
    private static final String LACKEY = "7_193";       // Morgul Lackey, vitality 2

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("morningCame", MORNING_CAME);
            put("eomer", EOMER);
            put("warrior", WARRIOR);
            put("lackey", LACKEY);
        }});
    }

    /** Opponents counter-clockwise from the Free Peoples player -- the offer order. */
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
     * Eomer in a skirmish with Morning Came in the Free Peoples player's hand and
     * the twilight to play it. The event is a Skirmish timeword, so the only
     * window where it can be played is inside a skirmish that has already begun.
     */
    private VirtualTableScenario TableInASkirmish() throws Exception {
        var scn = ThreeSeats();

        var eomer = scn.GetCardFor(P1, "eomer");
        scn.MoveCompanionsToTable(eomer);
        scn.MoveCardsToHand(scn.GetCardFor(P1, "morningCame"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        assertEquals(2, opponents.size());
        assertEquals("fixture assumes the first seat is what getFirstShadowPlayer names",
                opponents.get(0), GameUtils.getFirstShadowPlayer(scn.game()));

        // Only the FIRST opponent puts a minion out, so the skirmish is against a
        // known card and cannot land on the seat under test by accident.
        var minion = scn.GetCardFor(opponents.get(0), "warrior");
        scn.MoveMinionsToTable(minion);

        scn.PassUntilSkirmishBetween(eomer, minion);
        scn.SetTwilight(3);
        return scn;
    }

    private void PlayMorningCame(VirtualTableScenario scn) {
        var card = scn.GetCardFor(P1, "morningCame");
        assertTrue("the Free Peoples player should be able to play Morning Came; pending: "
                        + Pending(scn),
                scn.ActionAvailable(P1, "Play " + GameUtils.getFullName(card)));
        String actionId = scn.GetCardActionId(P1, card, "Play");
        // A null id is not a failure to play -- PlayerDecided(player, null) passes
        // the phase instead of throwing, so the card would silently never fire.
        assertNotNull("Morning Came should be on offer", actionId);
        scn.PlayerDecided(P1, actionId);
    }

    /**
     * The offer goes round the table. Under `player: shadow` only the first
     * opponent is ever asked, so this fails at the second seat -- and the first
     * seat's assertion still passes, which is exactly what a test written against
     * one opponent would have missed.
     */
    @Test
    public void everyShadowPlayerIsOfferedTheReductionInTurn() throws Exception {
        var scn = TableInASkirmish();
        List<String> opponents = ShadowSeats(scn);
        var eomer = scn.GetCardFor(P1, "eomer");

        int strengthBefore = scn.GetStrength(eomer);
        PlayMorningCame(scn);

        for (String opponent : opponents) {
            assertTrue(opponent + " should have been offered the reduction; pending: "
                            + Pending(scn),
                    scn.DecisionAvailable(opponent, "beneath your draw deck"));
            scn.ChooseOption(opponent, "No");
        }

        assertEquals("nobody took the offer, so the Man is +4",
                strengthBefore + 4, scn.GetStrength(eomer));
    }

    /**
     * The seat that accepts is the seat that pays.
     *
     * Deliberately the SECOND opponent, because `hand: shadow` and `hand: you`
     * agree on the first one. The card is placed beneath a draw deck, so an
     * accepting player loses one from hand and gains one at the bottom of their
     * deck; a player who was merely asked loses nothing.
     */
    @Test
    public void theSecondOpponentCanAcceptAndIsTheOneWhoPays() throws Exception {
        var scn = TableInASkirmish();
        List<String> opponents = ShadowSeats(scn);
        String decliner = opponents.get(0);
        String accepter = opponents.get(1);
        assertNotEquals(decliner, accepter);
        var eomer = scn.GetCardFor(P1, "eomer");

        int strengthBefore = scn.GetStrength(eomer);
        PlayMorningCame(scn);

        assertTrue(decliner + " should be asked first; pending: " + Pending(scn),
                scn.DecisionAvailable(decliner, "beneath your draw deck"));
        scn.ChooseOption(decliner, "No");

        assertTrue(accepter + " should be asked once the first seat declines; pending: "
                        + Pending(scn),
                scn.DecisionAvailable(accepter, "beneath your draw deck"));

        // Measured immediately before the acceptance, so nothing between the
        // start of the turn and here can be mistaken for the cost being paid.
        int accepterHand = scn.GetHand(accepter).size();
        int accepterDeck = scn.GetDrawDeck(accepter).size();
        int declinerHand = scn.GetHand(decliner).size();
        int declinerDeck = scn.GetDrawDeck(decliner).size();
        assertTrue("the accepting seat needs a card to place, or the offer is skipped",
                accepterHand > 0);

        scn.ChooseOption(accepter, "Yes");

        assertEquals("the accepting player placed one of their own cards",
                accepterHand - 1, scn.GetHand(accepter).size());
        assertEquals("...and it went beneath their own draw deck",
                accepterDeck + 1, scn.GetDrawDeck(accepter).size());
        assertEquals("the seat that declined pays nothing from hand",
                declinerHand, scn.GetHand(decliner).size());
        assertEquals("the seat that declined pays nothing to its deck",
                declinerDeck, scn.GetDrawDeck(decliner).size());

        assertEquals("somebody took the offer, so the Man is +2 rather than +4",
                strengthBefore + 2, scn.GetStrength(eomer));
    }
}
