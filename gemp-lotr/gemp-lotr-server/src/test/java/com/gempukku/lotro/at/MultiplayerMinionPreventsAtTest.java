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
 * "A MINION IN THIS SKIRMISH may exert to prevent this" -- the actor is a CARD,
 * and the player who acts is the one controlling it.
 *
 * Rohirrim Helm (5_89) is the only prevention in the pool phrased this way.
 * Every other one names a seat ("any Shadow player may..."), and at two players
 * the two phrasings coincide, because the only minion in the skirmish belongs
 * to the only opponent. Above two they come apart: the Free Peoples player
 * assigns from every opponent's minions at once, so one skirmish can hold
 * minions belonging to different Shadow players.
 *
 * RULED: the minion's controller is the player who may exert it, and nobody
 * else is involved. The card shipped as `player: shadow` with an unscoped
 * select, so a fixed seat was asked and could spend somebody else's minion.
 *
 * WHY THIS NEEDED NO ENGINE CHANGE. PreventableEffectAppender only offers to
 * candidates who can actually pay, evaluating the cost in a context delegated
 * to each candidate (PreventableEffectAppender.java:50). Scoping the cost to
 * `your` therefore filters the offer for free: a Shadow player with no minion in
 * this skirmish cannot pay and is skipped rather than asked.
 *
 * THE FIXTURE IS THE WHOLE POINT. The minion in the skirmish is deliberately
 * given to the opponent that getFirstShadowPlayer does NOT name, and the other
 * opponent is given none. Under the shipped card the first seat is asked and
 * exerts a minion it does not control; under the ruling only the controller is
 * asked. Those two produce different players holding the decision, which is
 * what this test reads.
 */
public class MultiplayerMinionPreventsAtTest {

    private static final String HELM = "5_89";
    // Rohirrim Helm's bearer must be a [rohan] Man. Strength 6 / vitality 3.
    private static final String ROHAN_MAN = "4_292";
    // A minion with vitality 2 so it can actually exert to pay.
    private static final String WARRIOR = "4_165";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("helm", HELM);
            put("man", ROHAN_MAN);
            put("warrior", WARRIOR);
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

    /**
     * Only the player controlling a minion in the skirmish is offered the
     * prevention -- and it is not the seat getFirstShadowPlayer names.
     */
    @Test
    public void onlyTheControllerOfAMinionInTheSkirmishIsOffered() throws Exception {
        var scn = ThreeSeats();

        var man = scn.GetCardFor(P1, "man");
        var helm = scn.GetCardFor(P1, "helm");
        scn.MoveCompanionsToTable(man);
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());
        scn.AttachCardsTo(man, helm);

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);   // the seat `player: shadow` named
        String minionOwner = opponents.get(1);   // the seat that actually has one
        assertNotEquals(firstShadow, minionOwner);
        assertEquals("getFirstShadowPlayer should name the first opponent",
                firstShadow, GameUtils.getFirstShadowPlayer(scn.game()));

        // ONLY the non-first opponent gets a minion. The other has none at all,
        // so under the ruling they have nothing to exert and must not be asked;
        // under the shipped card they were asked and could exert this one.
        var warrior = scn.GetCardFor(minionOwner, "warrior");
        scn.MoveMinionsToTable(warrior);

        // PassUntilSkirmishBetween, not SkipToAssignments + FreepsResolveSkirmish:
        // those are written as P1/P2 and stall above two seats -- the first run
        // of this test died with "Attempting to skip to phase ASSIGNMENT, halted
        // in phase SHADOW", because a third seat's turn to act was never
        // answered.
        scn.PassUntilSkirmishBetween(man, warrior);

        String actionId = scn.GetCardActionId(P1, helm);
        assertNotNull("the Helm's skirmish action should be available. pending: "
                + Pending(scn), actionId);
        scn.PlayerDecided(P1, actionId);

        // Record who is asked to exert. Who holds this decision IS the result.
        String offeredTo = null;
        StringBuilder trace = new StringBuilder();
        for (int step = 0; step < 10; step++) {
            var waiting = new ArrayList<>(scn.userFeedback().getUsersPendingDecision());
            if (waiting.isEmpty()) break;
            java.util.Collections.sort(waiting);
            String who = waiting.get(0);
            var d = scn.userFeedback().getAwaitingDecision(who);
            if (d == null) continue;
            String text = d.getText() == null ? "(null)" : d.getText();
            trace.append("<").append(who).append(": ").append(text).append("> ");
            if (text.toLowerCase().contains("would you like to exert")) {
                offeredTo = who;
                break;
            }
            try {
                scn.PlayerDecided(who, "");
            } catch (Exception e) {
                trace.append("[!! ").append(e.getClass().getSimpleName()).append("] ");
                break;
            }
        }

        String dump = "minionOwner=" + minionOwner + " firstShadow=" + firstShadow
                + " | trace: " + trace;

        assertNotNull("somebody should have been offered the prevention -- the"
                + " minion's controller has a minion in this skirmish and can"
                + " exert it. " + dump, offeredTo);
        assertEquals("the prevention belongs to the player controlling a minion in"
                        + " the skirmish. " + dump,
                minionOwner, offeredTo);
        assertNotEquals("the seat getFirstShadowPlayer names controls no minion"
                        + " here and must not be asked. " + dump,
                firstShadow, offeredTo);
    }

    private static String Pending(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            sb.append(p).append("=").append(d == null ? "(null)" : d.getText()).append("  ");
        }
        return sb.length() == 0 ? "(nobody)" : sb.toString();
    }
}
