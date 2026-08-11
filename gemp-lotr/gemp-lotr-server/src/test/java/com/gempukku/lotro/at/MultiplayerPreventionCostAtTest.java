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
 * Whoever accepts a prevention is the one who pays for it.
 *
 * `MultiplayerPreventionAtTest` proved the OFFER goes round the table. It could
 * not have caught this, and the reason is worth stating: the card it uses, The
 * Faithful Stone (18_50), pays with `removeTwilight` -- the twilight pool
 * belongs to nobody, so no seat can be charged wrongly. Every card whose
 * prevention costs something a PLAYER owns was left untested.
 *
 * Four of them said `player: shadow` inside the cost. `PreventableEffectAppender`
 * appends that cost in a DelegateActionContext built around whoever accepted, so
 * `you` there is the accepting player -- but `shadow` goes through
 * PlayerResolver to getFirstShadowPlayer and ignores the context completely. So
 * converting the OFFER to `anyShadow` without converting the COST made a
 * fixed-seat cost reachable by a player who is not that seat. Before that
 * conversion the two agreed, because only the first Shadow player was ever
 * asked. This branch introduced the disagreement.
 */
public class MultiplayerPreventionCostAtTest {

    /**
     * Be Back Soon (5_21): "Maneuver: Discard Sméagol to discard a minion. An
     * opponent may exert a minion twice to prevent this." Chosen over the other
     * three because it needs no skirmish and no sanctuary -- a Maneuver event
     * and two minions on the table is the whole fixture.
     */
    private static final String BE_BACK_SOON = "5_21";
    private static final String SMEAGOL = "13_55";
    // Vitality 3, not 2: the cost exerts the SAME minion twice, and a vitality-2
    // minion dies on the second exert and reports from the discard pile.
    // Different cards per seat, per the fixture rules.
    private static final String MINION_A = "1_151";      // Uruk Savage
    private static final String MINION_B = "1_177";      // Goblin Patrol Troop

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("beBackSoon", BE_BACK_SOON);
            put("smeagol", SMEAGOL);
            put("minionA", MINION_A);
            put("minionB", MINION_B);
        }});
    }

    /** Opponents counter-clockwise from the Free Peoples player — the offer order. */
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
     * The first opponent offered the prevention declines; the second accepts.
     * The exertions must land on the second one's minion.
     *
     * The bug this pins down is invisible unless somebody OTHER than the first
     * Shadow player accepts, which is why the offer order is read rather than
     * assumed.
     */
    @Test
    public void theOpponentWhoAcceptsIsTheOneWhoPays() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "smeagol"));
        scn.MoveCardsToHand(scn.GetCardFor(P1, "beBackSoon"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String decliner = opponents.get(0);
        String accepter = opponents.get(1);
        assertNotEquals(decliner, accepter);

        var declinersMinion = scn.GetCardFor(decliner, "minionA");
        var acceptersMinion = scn.GetCardFor(accepter, "minionB");
        scn.MoveMinionsToTable(declinersMinion, acceptersMinion);

        scn.PassUntilDecision(P1, "Maneuver action");
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, scn.GetCardFor(P1, "beBackSoon")));

        // Sméagol is the play cost; the minion to discard is the effect, and the
        // prevention is offered before it resolves.
        scn.PassUntilDecision(decliner, "prevent");
        // Answer by TEXT, not index. A YesNoDecision's options are not in a
        // fixed order across the codebase, and answering "0" here accepted the
        // offer rather than declining it -- which looks exactly like the
        // wrong-seat bug this test is about.
        scn.ChooseOption(decliner, "No");

        assertTrue("the second opponent should be offered it next; pending: " + Pending(scn),
                scn.DecisionAvailable(accepter, "prevent"));
        scn.ChooseOption(accepter, "Yes");

        // With the cost scoped to `your` (ruled 2026-08-10), the accepter's
        // only minion is the only candidate and the choice auto-resolves --
        // no ChooseCards decision appears.
        assertEquals("the accepting player's own minion carries the cost",
                2, scn.GetWoundsOn(acceptersMinion));
        assertEquals("the declining player's minion is untouched",
                0, scn.GetWoundsOn(declinersMinion));
    }

    /**
     * An opponent with no minion of their own is NOT offered the prevention.
     *
     * An earlier version of this test pinned the OPPOSITE: with the cost
     * unscoped, every opponent could "afford" it by exerting somebody else's
     * minion, so everyone was offered it. That was left in place deliberately,
     * "so that a future ruling has something concrete to change" -- and the
     * ruling came (2026-08-10, the card audit): a cost cannot be paid with
     * another player's card. The cost is now `choose(your,minion)`, and
     * PreventableEffectAppender's affordability check therefore skips the
     * seat that owns nothing -- the same mechanism 5_89 uses.
     */
    @Test
    public void anOpponentWithNoMinionOfTheirOwnIsNotOffered() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "smeagol"));
        scn.MoveCardsToHand(scn.GetCardFor(P1, "beBackSoon"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String withoutAMinion = opponents.get(0);
        String withAMinion = opponents.get(1);

        // Only the SECOND opponent owns a minion.
        scn.MoveMinionsToTable(scn.GetCardFor(withAMinion, "minionB"));

        scn.PassUntilDecision(P1, "Maneuver action");
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, scn.GetCardFor(P1, "beBackSoon")));

        assertTrue("the opponent who owns a minion is offered it; pending: "
                + Pending(scn), scn.DecisionAvailable(withAMinion, "prevent"));
        assertFalse("the opponent with no minion of their own cannot pay and"
                        + " must not be asked -- being asked was the pre-ruling"
                        + " behaviour; pending: " + Pending(scn),
                scn.DecisionAvailable(withoutAMinion, "prevent"));
    }
}
