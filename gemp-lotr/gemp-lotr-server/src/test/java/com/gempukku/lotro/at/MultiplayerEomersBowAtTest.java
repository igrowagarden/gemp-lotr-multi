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
 * "Choose a Shadow player who must exert one of HIS OR HER minions."
 *
 * Eomer's Bow (18_95) is one of five cards this branch converted on shape and
 * printed text alone, with no behavioural test behind it. The handoff has listed
 * them as "shape and text match only" ever since, which is an honest label and a
 * standing invitation to find out that one of them is wrong.
 *
 * It is worth measuring rather than trusting because it carries the scoping
 * form, `ownedByPlayerFromMemory`, and that is the token whose failure is
 * silent: scope a selection to the wrong player and the usual result is a single
 * candidate, which resolves without asking anybody, so the card does the wrong
 * thing and no prompt appears to say so.
 *
 * FIXTURE: TWO minions per opponent, not one. With one apiece the selection has
 * a single candidate and auto-resolves, and then "the chosen opponent was asked"
 * cannot be observed at all -- which is exactly what would make the wrong-seat
 * control pass silently. Two apiece means a real decision exists, so the test
 * can assert WHO holds it and WHICH cards it offers.
 */
public class MultiplayerEomersBowAtTest {

    private static final String EOMERS_BOW = "18_95";
    private static final String EOMER = "4_266";     // [rohan] Man, no triggers of its own

    // Four DIFFERENT minions, two per opponent, all vitality 2+ so that they can
    // actually be exerted -- a vitality-1 minion cannot, and the effect would
    // silently do nothing.
    private static final String WARRIOR = "4_165";   // vitality 2
    private static final String LACKEY = "7_193";    // vitality 2
    private static final String TROOP = "1_143";     // vitality 4
    private static final String URUK_TROOP = "12_157";  // vitality 4

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("bow", EOMERS_BOW);
            put("eomer", EOMER);
            put("warrior", WARRIOR);
            put("lackey", LACKEY);
            put("troop", TROOP);
            put("urukTroop", URUK_TROOP);
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

    private static String[] minionKeys(String seat) {
        return seat.equals(P2) ? new String[]{"warrior", "troop"}
                               : new String[]{"lackey", "urukTroop"};
    }

    /**
     * The chosen opponent is asked, is offered only their OWN minions, and one of
     * those takes the exertion. The other opponent is untouched throughout.
     *
     * Deliberately chooses the opponent who is NOT first in the offer order, so
     * that following the choice and defaulting to getFirstShadowPlayer cannot
     * produce the same board.
     */
    @Test
    public void theChosenOpponentExertsOneOfTheirOwnMinions() throws Exception {
        var scn = ThreeSeats();

        var eomer = scn.GetCardFor(P1, "eomer");
        var bow = scn.GetCardFor(P1, "bow");
        scn.MoveCompanionsToTable(eomer);
        scn.AttachCardsTo(eomer, bow);

        for (String seat : new String[]{P2, P3})
            for (String key : minionKeys(seat))
                scn.MoveMinionsToTable(scn.GetCardFor(seat, key));

        scn.StartMultiplayerGame();

        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertNotEquals(defaultOpponent, chosenOpponent);
        assertEquals("fixture assumes the first seat is what getFirstShadowPlayer names",
                defaultOpponent, GameUtils.getFirstShadowPlayer(scn.game()));

        List<PhysicalCardImpl> theirs = new ArrayList<>();
        for (String key : minionKeys(chosenOpponent))
            theirs.add(scn.GetCardFor(chosenOpponent, key));
        List<PhysicalCardImpl> others = new ArrayList<>();
        for (String key : minionKeys(defaultOpponent))
            others.add(scn.GetCardFor(defaultOpponent, key));

        scn.PassUntilDecision(P1, "Archery action");
        String actionId = scn.GetCardActionId(P1, bow);
        // GetCardActionId returns null when the action is not on offer, and
        // PlayerDecided(player, null) passes the phase rather than throwing.
        assertNotNull("Eomer's Bow should offer its archery action (pending: "
                + Pending(scn) + ")", actionId);
        scn.PlayerDecided(P1, actionId);

        assertTrue("the Free Peoples player should be asked which opponent (pending: "
                + Pending(scn) + ")", scn.DecisionAvailable(P1, "opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        // Kills the fixed-seat control. Only observable because each opponent
        // has two minions -- with one apiece this decision would not exist.
        assertNotNull("the CHOSEN opponent should be the one asked which minion to exert,"
                        + " not " + defaultOpponent + " (pending: " + Pending(scn) + ")",
                scn.userFeedback().getAwaitingDecision(chosenOpponent));
        assertNull("the other opponent should not be asked anything (pending: "
                + Pending(scn) + ")", scn.userFeedback().getAwaitingDecision(defaultOpponent));

        // Kills the no-scoping control: "one of HIS OR HER minions" means the
        // menu is their own two, not all four on the table.
        assertTrue("the chosen opponent should be offered their own minions",
                scn.HasCardChoiceAvailable(chosenOpponent, theirs.get(0)));
        assertFalse("the chosen opponent must NOT be offered the other opponent's minion"
                        + " -- the card says \"one of his or her minions\"",
                scn.HasCardChoiceAvailable(chosenOpponent, others.get(0)));

        scn.ChooseCards(chosenOpponent, theirs.get(0));

        assertEquals("the chosen opponent's own minion took the exertion",
                1, scn.GetWoundsOn(theirs.get(0)));
        assertEquals("their other minion is untouched", 0, scn.GetWoundsOn(theirs.get(1)));
        for (PhysicalCardImpl m : others)
            assertEquals("the OTHER opponent's minions are untouched",
                    0, scn.GetWoundsOn(m));
    }
}
