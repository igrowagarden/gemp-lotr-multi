package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
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
 * The fellowship's archery wounds are assigned by the LEAD Shadow player --
 * automatically, with no chooser prompt -- and across ANY minion in play.
 *
 * RULED in the five-player playtest (2026-08-09), matching the Council text
 * "archery wounds can be doled out by the primary shadow player to other
 * minions". Upstream's free-for-all code did BOTH halves differently: the
 * Free Peoples player picked which Shadow player assigns ("Choose shadow
 * player to assign archery damage to"), and that player could then only
 * wound their OWN minions (Filters.owner). The playtest hit the consequence
 * in one archery phase: the human, forced to choose, picked a seat whose
 * pool did not hold the goblin runner that one wound should have killed.
 *
 * THE FIXTURE IS THE POINT: the cross-seat minion belongs to the seat that
 * is NOT the lead, so the lead assigning onto it proves both halves of the
 * ruling at once. At two players the lead is the only opponent and the
 * owner filter was the identity, so 2p behaviour is unchanged by
 * construction -- the existing 2p archery tests stand guard on that.
 */
public class MultiplayerArcheryAtTest {

    // Legolas, an archer companion: fellowship archery total 1.
    private static final String ARCHER = "1_50";
    // Goblin Runner, vitality 1 -- the one wound kills it, as the playtest
    // said it should have.
    private static final String RUNNER = "1_178";
    // Uruk Savage for the lead's own pool, so the cross-seat pick is a choice.
    private static final String SAVAGE = "1_151";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_157", "1_159", "1_173",
            "1_177", "1_294", "1_312", "1_195", "1_140",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("archer", ARCHER);
            put("runner", RUNNER);
            put("savage", SAVAGE);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
        }});
    }

    /** Opponents of the current Free Peoples player, in CLOCKWISE order --
     *  the first is the lead Shadow player the ruling names. */
    private static List<String> ClockwiseShadowSeats(VirtualTableScenario scn) {
        String fp = scn.FreePeoplesPlayer();
        PlayOrder order = scn.game().getGameState().getPlayerOrder()
                .getClockwisePlayOrder(fp, false);
        order.getNextPlayer();                       // fp himself
        List<String> seats = new ArrayList<>();
        String next;
        while ((next = order.getNextPlayer()) != null && !next.equals(fp))
            seats.add(next);
        return seats;
    }

    @Test
    public void theLeadShadowPlayerAssignsFellowshipArcheryAcrossSeats() throws Exception {
        var scn = ThreeSeats();

        var archer = scn.GetCardFor(P1, "archer");
        scn.MoveCompanionsToTable(archer);
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        List<String> shadows = ClockwiseShadowSeats(scn);
        String lead = shadows.get(0);
        String other = shadows.get(1);

        // The killable minion belongs to the seat that is NOT the lead; the
        // lead holds a healthier one of their own, so choosing across the
        // seat boundary is a real decision rather than the only option.
        var runner = scn.GetCardFor(other, "runner");
        var savage = scn.GetCardFor(lead, "savage");
        scn.MoveMinionsToTable(runner);
        scn.MoveMinionsToTable(savage);

        // SkipToPhase stalls above two seats (a third seat's decisions are
        // never answered -- the same trap MultiplayerMinionPreventsAtTest
        // documents), so walk there by hand: pass every decision until the
        // archery wound assignment shows. Along the way, nobody may be asked
        // to CHOOSE a shadow player, and the assignment must land on the
        // lead unasked.
        String assignedTo = null;
        StringBuilder trace = new StringBuilder();
        for (int step = 0; step < 300; step++) {
            var waiting = new ArrayList<>(scn.userFeedback().getUsersPendingDecision());
            if (waiting.isEmpty()) break;
            java.util.Collections.sort(waiting);
            String who = waiting.get(0);
            var d = scn.userFeedback().getAwaitingDecision(who);
            if (d == null) continue;
            String text = d.getText() == null ? "(null)" : d.getText();
            trace.append("<").append(who).append("@")
                 .append(scn.game().getGameState().getCurrentPhase()).append(": ")
                 .append(text).append("> ");
            assertFalse("the chooser prompt must be GONE: " + trace,
                    text.contains("Choose shadow player to assign archery damage"));
            if (text.contains("archery wound")) {
                assignedTo = who;
                break;
            }
            // Past the archery phase with no assignment seen means the wound
            // never happened at all; fail with the walk in hand.
            assertNotEquals("walked past ARCHERY without an assignment: " + trace,
                    Phase.REGROUP, scn.game().getGameState().getCurrentPhase());
            scn.PlayerDecided(who, "");
        }

        assertNotNull("someone must be assigning the archery wound. trace: " + trace, assignedTo);
        assertEquals("the LEAD shadow player assigns, not a chosen one. trace: " + trace,
                lead, assignedTo);

        // The cross-seat minion is a legal target, and taking it kills the
        // runner -- the outcome the playtest said a normal game produces.
        var decision = scn.userFeedback().getAwaitingDecision(lead);
        String[] offered = (String[]) decision.getDecisionParameters().get("cardId");
        boolean runnerOffered = false;
        for (String id : offered)
            if (id.equals(String.valueOf(runner.getCardId()))) runnerOffered = true;
        assertTrue("the other seat's minion must be assignable. offered: "
                + String.join(",", offered) + " runner=" + runner.getCardId(), runnerOffered);

        scn.PlayerDecided(lead, String.valueOf(runner.getCardId()));

        assertNotEquals("one wound kills the vitality-1 runner, so it leaves play",
                Zone.SHADOW_CHARACTERS, runner.getZone());
    }
}
