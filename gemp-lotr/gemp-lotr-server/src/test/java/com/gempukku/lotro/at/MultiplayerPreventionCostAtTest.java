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

        assertNotNull("the opponent who accepted should be the one asked to pay; pending: "
                + Pending(scn), scn.userFeedback().getAwaitingDecision(accepter));
        assertNull("the opponent who declined should not be asked to pay; pending: "
                + Pending(scn), scn.userFeedback().getAwaitingDecision(decliner));

        scn.ChooseCards(accepter, acceptersMinion);

        assertEquals("the accepting player's own minion carries the cost",
                2, scn.GetWoundsOn(acceptersMinion));
        assertEquals("the declining player's minion is untouched",
                0, scn.GetWoundsOn(declinersMinion));
    }

    /**
     * Every opponent is offered the prevention when only ONE of them has a
     * minion, because `select: choose(minion)` is not scoped to the payer.
     *
     * This test started life asserting the opposite -- that an opponent with no
     * minion could not pay, so `areCostsPlayable` reading the wrong seat's board
     * would suppress the offer entirely. It failed, and the failure is the
     * finding: with an unscoped selection there is no such thing as an opponent
     * who cannot pay, as long as ANY minion is on the table. A player with no
     * minions of their own is offered the prevention and can pay it by exerting
     * somebody else's.
     *
     * Whether that is right is a rules question this project cannot settle from
     * the source. The card says "an opponent may exert A MINION twice", with no
     * "his or her" -- and at two players every minion belongs to the only
     * opponent, so the text never had to disambiguate. Left as it is, and
     * recorded, rather than guessed at. See HANDOFF.md.
     */
    @Test
    public void everyOpponentIsOfferedIt_becauseTheCostIsNotScopedToThePayer() throws Exception {
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

        assertTrue("the first opponent is offered it even owning no minion; pending: "
                + Pending(scn), scn.DecisionAvailable(withoutAMinion, "prevent"));
        scn.ChooseOption(withoutAMinion, "No");

        assertTrue("and so is the opponent who does own one; pending: " + Pending(scn),
                scn.DecisionAvailable(withAMinion, "prevent"));
    }
}
