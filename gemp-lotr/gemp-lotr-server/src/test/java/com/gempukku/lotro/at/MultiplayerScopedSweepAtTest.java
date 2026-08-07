package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Make an opponent discard ALL BUT 2 Shadow conditions."
 *
 * Fool's Hope (7_35) is the only card in the `one` bucket whose second half is
 * an `all(...)` sweep rather than a `choose(...)`, and that makes it the one
 * card where this branch's usual reading is not safe to apply blind:
 *
 *   - everywhere else, "no owner word in the printed text" leaves the selection
 *     unscoped, which merely lets the chosen opponent reach FURTHER (11_34,
 *     10_72, 12_1 -- all measured);
 *   - here an unscoped sweep would DISCARD the conditions of Shadow players the
 *     card never names. At five seats that is four other players, turning "make
 *     AN OPPONENT discard" into a table-wide effect.
 *
 * So both halves are scoped to the one opponent the text names. That is a
 * reading, it is labelled as one in set07-Gandalf.hjson, and this test exists to
 * make it a measured one.
 *
 * It also measures something no other card on the branch does:
 * ownedByPlayerFromMemory has only ever been used inside `choose(...)`, and this
 * is its first use inside `all(...)`.
 *
 * Fixture notes, all of them load-bearing:
 *
 *   - THREE conditions per opponent, not one. Two reasons. "All but 2" needs a
 *     third before it discards anything at all, and `chooseActiveCards count: 2`
 *     over exactly two candidates resolves without asking -- which would make
 *     the wrong-seat control silently pass, the failure mode this codebase hits
 *     more than any other.
 *   - SIX distinct conditions, three per seat, so the fixture is identical
 *     whichever opponent the shuffle offers second.
 *   - All six are non-unique, so nothing is unique-discarded down to one.
 */
public class MultiplayerScopedSweepAtTest {

    private static final String FOOLS_HOPE = "7_35";
    private static final String GANDALF = "1_72";            // [gandalf] Wizard, to spot

    // Three [isengard] and three [moria] Shadow conditions, all non-unique, all
    // Support Area, all inert enough not to fire during a single fellowship
    // phase.
    private static final String AMBITION = "1_133";          // Saruman's Ambition
    private static final String URUK_ARMORY = "1_157";       // Uruk-hai Armory
    private static final String RAMPAGE = "1_159";           // Uruk-hai Rampage
    private static final String GOBLIN_ARMORY = "1_173";     // Goblin Armory
    private static final String RELICS = "1_195";            // Relics of Moria
    private static final String MISTY_MOUNTAINS = "1_198";   // Through the Misty Mountains

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("hope", FOOLS_HOPE);
            put("gandalf", GANDALF);
            put("ambition", AMBITION);
            put("urukArmory", URUK_ARMORY);
            put("rampage", RAMPAGE);
            put("goblinArmory", GOBLIN_ARMORY);
            put("relics", RELICS);
            put("misty", MISTY_MOUNTAINS);
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

    /** Every pending decision, for the assertion messages. */
    private static String Pending(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            sb.append(p).append("=").append(d == null ? "(null)" : d.getText()).append("  ");
        }
        return sb.length() == 0 ? "(nobody)" : sb.toString();
    }

    private static String[] conditionKeys(String seat) {
        return seat.equals(P2)
                ? new String[]{"ambition", "urukArmory", "rampage"}
                : new String[]{"goblinArmory", "relics", "misty"};
    }

    private static int inZone(List<PhysicalCardImpl> cards, Zone zone) {
        int n = 0;
        for (PhysicalCardImpl c : cards)
            if (c.getZone() == zone) n++;
        return n;
    }

    private static String zones(List<PhysicalCardImpl> cards) {
        StringBuilder sb = new StringBuilder();
        for (PhysicalCardImpl c : cards)
            sb.append(c.getBlueprint().getTitle()).append("=").append(c.getZone()).append(" ");
        return sb.toString();
    }

    /**
     * The sweep stays inside the opponent who was chosen: they keep exactly two
     * of their three conditions, and the other opponent keeps all three.
     *
     * Deliberately chooses the opponent who is NOT first in the offer order, so
     * that following the choice and defaulting to getFirstShadowPlayer cannot
     * give the same answer.
     */
    @Test
    public void theSweepStaysInsideTheChosenOpponent() throws Exception {
        var scn = ThreeSeats();
        var hope = scn.GetCardFor(P1, "hope");
        scn.MoveCardsToHand(hope);
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "gandalf"));

        for (String seat : new String[]{P2, P3})
            for (String key : conditionKeys(seat))
                scn.MoveCardsToSupportArea(scn.GetCardFor(seat, key));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);

        List<PhysicalCardImpl> chosens = new ArrayList<>();
        for (String key : conditionKeys(chosenOpponent))
            chosens.add(scn.GetCardFor(chosenOpponent, key));
        List<PhysicalCardImpl> others = new ArrayList<>();
        for (String key : conditionKeys(defaultOpponent))
            others.add(scn.GetCardFor(defaultOpponent, key));

        assertEquals("fixture: the chosen opponent starts with 3 conditions in play ("
                        + zones(chosens) + ")", 3, inZone(chosens, Zone.SUPPORT));
        assertEquals("fixture: the other opponent starts with 3 conditions in play ("
                        + zones(others) + ")", 3, inZone(others, Zone.SUPPORT));

        scn.PassUntilDecision(P1, "Fellowship action");
        String actionId = scn.GetCardActionId(P1, hope);
        // GetCardActionId returns null when the action is not on offer, and
        // PlayerDecided(player, null) passes the phase rather than throwing --
        // so the card would silently never fire.
        assertNotNull("Fool's Hope should be playable (pending: " + Pending(scn) + ")",
                actionId);
        scn.PlayerDecided(P1, actionId);

        // Cost: discard 2 cards from hand. The hand is dealt, so answer from
        // whatever is on offer rather than by name -- but never the event
        // itself, which has already left the hand.
        List<String> discardable = scn.GetADParamAsList(P1, "cardId");
        assertTrue("the discard-2-from-hand cost should offer at least 2 cards (pending: "
                        + Pending(scn) + ")", discardable.size() >= 2);
        scn.PlayerDecided(P1, discardable.get(0) + "," + discardable.get(1));

        assertTrue("the Free Peoples player should be asked which opponent (pending: "
                        + Pending(scn) + ")", scn.DecisionAvailable(P1, "opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        // Kills the fixed-seat control. With player: shadow the prompt goes to
        // getFirstShadowPlayer instead, and the outcome alone cannot tell the
        // two apart -- somebody spares two either way.
        assertNotNull("the CHOSEN opponent should be the one asked which conditions to"
                        + " spare, not " + defaultOpponent + " (pending: " + Pending(scn) + ")",
                scn.userFeedback().getAwaitingDecision(chosenOpponent));
        assertNull("the other opponent should not be asked anything (pending: "
                        + Pending(scn) + ")",
                scn.userFeedback().getAwaitingDecision(defaultOpponent));

        // Spare two of their own three; the third is the one that goes.
        scn.ChooseCards(chosenOpponent, chosens.get(0), chosens.get(1));

        assertEquals("the chosen opponent kept the 2 they spared (" + zones(chosens) + ")",
                Zone.SUPPORT, chosens.get(0).getZone());
        assertEquals("the chosen opponent kept the 2 they spared (" + zones(chosens) + ")",
                Zone.SUPPORT, chosens.get(1).getZone());
        assertEquals("the chosen opponent's third condition was discarded ("
                        + zones(chosens) + ")", Zone.DISCARD, chosens.get(2).getZone());

        // Kills the unscoped-sweep control. This is the whole reading: an
        // unscoped all(...) would take these three as well.
        assertEquals("the OTHER opponent keeps all 3 of their conditions -- the card"
                        + " names one opponent (" + zones(others) + ")",
                3, inZone(others, Zone.SUPPORT));
    }
}
