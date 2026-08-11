package com.gempukku.lotro.at;

import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.framework.VirtualTableScenario;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * THE ONE-FELLOWSHIP RULE, PINNED BY A TURN HANDOVER.
 *
 * The ruling: "Only one set of fellowship stuff is active on the game board at
 * a time." Sixty-six Phase-1 verdicts (now 87) say CORRECT *because* an
 * unscoped Free Peoples select cannot reach another seat's companion -- there
 * being no other seat's companion ACTIVE to reach. Until this test, no test in
 * the branch advanced a turn at three seats, so that premise was an inference,
 * not a measurement. This is the check that would have caught the
 * twenty-two-verdict retraction, made at last.
 *
 * What it measures: P1 has a companion on the table on P1's turn. The turn
 * passes to the next seat. Is P1's companion still reachable by the filter
 * machinery -- the same countActive/choose path every unscoped select goes
 * through -- on somebody else's turn?
 *
 * If this test fails, the engine does not match the ruling, and the defect is
 * ONE engine bug, not eighty-seven card bugs. Either way the answer is worth
 * more than any further reading.
 */
public class MultiplayerTurnHandoverAtTest {

    private static final String ARWEN = "1_30";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("arwen", ARWEN);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
        }});
    }

    /** Pass every pending decision until the given seat holds the turn. */
    private static String AdvanceToTurnOf(VirtualTableScenario scn, String target) {
        StringBuilder trace = new StringBuilder();
        for (int step = 0; step < 500; step++) {
            if (target.equals(scn.game().getGameState().getCurrentPlayerId()))
                return trace.toString();
            var waiting = new ArrayList<>(scn.userFeedback().getUsersPendingDecision());
            if (waiting.isEmpty())
                break;
            Collections.sort(waiting);
            String who = waiting.get(0);
            var d = scn.userFeedback().getAwaitingDecision(who);
            trace.append("<").append(who).append(": ")
                 .append(d == null ? "(null)" : d.getText()).append("> ");
            try {
                String text = d == null || d.getText() == null ? "" : d.getText();
                if (text.contains("another move"))
                    scn.ChooseOption(who, "No");   // stay: end the turn
                else
                    scn.PlayerDecided(who, "");
            } catch (Exception e) {
                trace.append("[!! ").append(e.getClass().getSimpleName()).append("] ");
                break;
            }
        }
        return trace.toString();
    }

    @Test
    public void anotherSeatsCompanionIsNotReachableAfterTheTurnPasses() throws Exception {
        var scn = ThreeSeats();
        var arwen = scn.GetCardFor(P1, "arwen");
        scn.MoveCompanionsToTable(arwen);
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        assertEquals("on P1's own turn their companion is active and reachable",
                1, Filters.countActive(scn.game(), Filters.sameCard(arwen)));

        String trace = AdvanceToTurnOf(scn, P2);
        assertEquals("the turn should have passed to P2. walk: " + trace,
                P2, scn.game().getGameState().getCurrentPlayerId());

        int reachable = Filters.countActive(scn.game(), Filters.sameCard(arwen));
        assertEquals("THE RULING: only one fellowship is active at a time, so"
                        + " P1's companion (zone now " + arwen.getZone() + ") must not"
                        + " be reachable by the filter machinery on P2's turn."
                        + " If this fails, every unscoped Free Peoples select CAN"
                        + " reach across seats and the ledger's CORRECT column"
                        + " rests on a false premise -- one engine bug, not"
                        + " eighty-seven card bugs.",
                0, reachable);
    }
}
