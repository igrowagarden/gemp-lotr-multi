package com.gempukku.lotro.at;

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
 * The other half of the choose-an-opponent group: cards that redirect the
 * PLAYER but must NOT scope the selection.
 *
 * Argument Ready to Hand (12_1): "Spot a Dwarf to choose a Shadow player who
 * must spot a race. Discard from play all minions of all other races." The data
 * said {chooseActiveCards, player: shadow} -- getFirstShadowPlayer, and no choice
 * offered.
 *
 * Every card measured before this one said "his or her minions" and needed
 * ownedByPlayerFromMemory to scope the selection. This one says "spot a race"
 * and names no owner, so the chosen player may name the race of ANY minion in
 * play. Applying the earlier pattern here would be a bug, not a fix -- which is
 * exactly what this test pins down: the chosen opponent is made to spare the
 * OTHER opponent's minion, which a scoped selection could not offer.
 */
public class MultiplayerOpponentPicksAnyMinionAtTest {

    private static final String ARGUMENT = "12_1";
    private static final String DWARF = "1_7";           // Dwarf Guard, to spot
    // Fixture rule: a DIFFERENT minion per seat, and different RACES, or
    // "all minions of all other races" discards nothing observable.
    private static final String URUK = "4_165";          // Orthanc Warrior, Uruk-hai
    private static final String ORC = "7_193";           // Morgul Lackey, Orc

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("argument", ARGUMENT);
            put("dwarf", DWARF);
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
     * The chosen opponent is the one asked, and the minion they may name is not
     * restricted to their own.
     *
     * Deliberately picks the opponent who is NOT first in the offer order, and
     * then has them spare a minion belonging to the other opponent -- so neither
     * defaulting to getFirstShadowPlayer nor scoping the selection by owner can
     * produce this outcome.
     */
    @Test
    public void theChosenOpponentNamesTheRaceOfAnyMinionInPlay() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "dwarf"));
        scn.MoveCardsToHand(scn.GetCardFor(P1, "argument"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertNotEquals(defaultOpponent, chosenOpponent);

        // One minion each, so "all minions of all other races" has exactly one
        // survivor and one casualty and cannot be satisfied by accident.
        var chosensMinion = scn.GetCardFor(chosenOpponent, "uruk");
        var othersMinion = scn.GetCardFor(defaultOpponent, "orc");
        scn.MoveMinionsToTable(chosensMinion, othersMinion);

        scn.PassUntilDecision(P1, "Maneuver action");
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, scn.GetCardFor(P1, "argument")));

        assertTrue("the Free Peoples player should be asked which opponent; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        assertTrue("the chosen opponent should be asked to name a race; pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "Choose a race to spare"));
        assertFalse("the unchosen opponent should not be asked; pending: " + Pending(scn),
                scn.DecisionAvailable(defaultOpponent, "Choose a race to spare"));

        // The point of the card, and the reason it must not be scoped: they
        // spare the OTHER opponent's Orc, and their own Uruk-hai is discarded.
        scn.ChooseCards(chosenOpponent, othersMinion);

        assertEquals("the spared minion stays in play",
                Zone.SHADOW_CHARACTERS, othersMinion.getZone());
        assertEquals("the minion of the other race is discarded, whoever owns it",
                Zone.DISCARD, chosensMinion.getZone());
    }
}
