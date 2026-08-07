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
 * A skirmish trigger that chooses an opponent -- the first test in this project
 * to reach a skirmish above two seats at all.
 *
 * Gandalf's Staff (11_34): "Each time bearer wins a skirmish, choose a Shadow
 * player who must wound a minion." The data said {wound, player: shadow,
 * select: choose(minion)} -- getFirstShadowPlayer, and no choice offered.
 *
 * The card says "wound A minion", NOT "his or her minion", so unlike Pippin and
 * the bows the selection must stay unscoped: the chosen player may wound any
 * minion on the table, including one belonging to somebody else. This test
 * makes them do exactly that, so applying the ownedByPlayerFromMemory pattern
 * here would fail it rather than pass it.
 */
public class MultiplayerSkirmishWoundAtTest {

    private static final String GANDALF = "6_30";        // strength 10, wins comfortably
    private static final String STAFF = "11_34";
    // Fixture rule: a DIFFERENT minion per seat, and both vitality 2 -- a
    // vitality-1 minion dies to the skirmish and reports its wounds from the
    // discard pile.
    private static final String URUK = "4_165";          // Orthanc Warrior, strength 7
    private static final String ORC = "7_193";           // Morgul Lackey, strength 6

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("gandalf", GANDALF);
            put("staff", STAFF);
            put("uruk", URUK);
            put("orc", ORC);
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
     * The wound is asked of the opponent who was chosen, and they may put it on
     * a minion the other opponent controls.
     *
     * Deliberately picks the opponent who is NOT first in the offer order, and
     * then has them wound the other opponent's minion -- so neither defaulting
     * to getFirstShadowPlayer nor scoping the selection by owner reproduces it.
     */
    @Test
    public void theChosenOpponentWoundsAnyMinionInPlay() throws Exception {
        var scn = ThreeSeats();
        var gandalf = scn.GetCardFor(P1, "gandalf");
        scn.MoveCompanionsToTable(gandalf);
        scn.AttachCardsTo(gandalf, scn.GetCardFor(P1, "staff"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertNotEquals(defaultOpponent, chosenOpponent);

        var chosensMinion = scn.GetCardFor(chosenOpponent, "uruk");
        var othersMinion = scn.GetCardFor(defaultOpponent, "orc");
        scn.MoveMinionsToTable(chosensMinion, othersMinion);

        // Gandalf skirmishes the CHOSEN opponent's minion, so the one wound the
        // skirmish itself deals is on a known card and cannot be confused with
        // the one the card under test deals.
        scn.PassUntilSkirmishBetween(gandalf, chosensMinion);
        // PassCurrentPhaseActions passes for P1 and P2 only, so a third seat's
        // skirmish action is left holding the phase.
        scn.PassUntilDecision(P1, "Required responses");

        assertTrue("the staff's trigger should be waiting; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Required responses"));
        scn.ChooseAction(P1, "actionText", "Gandalf's Staff");

        assertTrue("the Free Peoples player should be asked which opponent; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        assertTrue("the chosen opponent should be the one wounding; pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "Choose"));
        assertFalse("the unchosen opponent should not be asked; pending: " + Pending(scn),
                scn.DecisionAvailable(defaultOpponent, "Choose"));

        int othersWoundsBefore = scn.GetWoundsOn(othersMinion);
        int chosensWoundsBefore = scn.GetWoundsOn(chosensMinion);

        // The point of the card: a minion they do not control is a legal target.
        scn.ChooseCards(chosenOpponent, othersMinion);

        assertEquals("the wound lands on the minion they named, not one of their own",
                othersWoundsBefore + 1, scn.GetWoundsOn(othersMinion));
        assertEquals("their own minion takes nothing beyond what the skirmish did",
                chosensWoundsBefore, scn.GetWoundsOn(chosensMinion));
    }
}
