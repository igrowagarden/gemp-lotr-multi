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
 * One choice, several wounds.
 *
 * Éowyn (10_72): "Skirmish: Exert Éowyn to choose an opponent. That opponent
 * must wound a minion for each wound on each minion skirmishing Éowyn." The
 * data said {repeat, effect: {wound, player: shadow}} -- getFirstShadowPlayer,
 * and no choice offered.
 *
 * What is new here is WHERE the choice goes. The wound sits inside a `repeat`,
 * so a ChooseOpponent placed alongside it would be re-asked once per wound and
 * the card would let the Free Peoples player switch victims mid-effect. The
 * text chooses once and then repeats, so the ChooseOpponent is hoisted out of
 * the repeat. Knights of His House (7_238) has the same shape and the same
 * hoist; this is the card that measures it.
 */
public class MultiplayerRepeatedWoundAtTest {

    private static final String EOWYN = "10_72";
    // Fixture rule: a DIFFERENT minion per seat. Vitality 4, not 2, because the
    // skirmishing minion carries two wounds before the card is even used and a
    // wounded-to-death minion reports from the discard pile.
    private static final String TROOP_A = "1_143";       // Troop of Uruk-hai
    private static final String TROOP_B = "12_157";      // Uruk-hai Troop

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("eowyn", EOWYN);
            put("troopA", TROOP_A);
            put("troopB", TROOP_B);
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
     * The opponent is chosen once and then wounds twice; the Free Peoples player
     * is not asked again between the two wounds.
     */
    @Test
    public void theOpponentIsChosenOnceAndThenWoundsForEachWound() throws Exception {
        var scn = ThreeSeats();
        var eowyn = scn.GetCardFor(P1, "eowyn");
        scn.MoveCompanionsToTable(eowyn);

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertNotEquals(defaultOpponent, chosenOpponent);

        var skirmisher = scn.GetCardFor(chosenOpponent, "troopA");
        var bystander = scn.GetCardFor(defaultOpponent, "troopB");
        scn.MoveMinionsToTable(skirmisher, bystander);

        scn.PassUntilSkirmishBetween(eowyn, skirmisher);
        // Two wounds on the skirmishing minion means the repeat runs twice.
        // Applied after the skirmish starts so nothing resolves them early.
        scn.AddWoundsToChar(skirmisher, 2);

        scn.PlayerDecided(P1, scn.GetCardActionId(P1, eowyn));

        assertTrue("the Free Peoples player should be asked which opponent; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        int bystanderBefore = scn.GetWoundsOn(bystander);

        assertTrue("the chosen opponent should be the one wounding; pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "Choose"));
        scn.ChooseCards(chosenOpponent, bystander);

        // The hoist: one choice covers every repetition.
        assertFalse("the opponent should be chosen once, not once per wound; pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        assertTrue("the same opponent should be asked for the second wound; pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "Choose"));
        scn.ChooseCards(chosenOpponent, bystander);

        assertEquals("both wounds land where the chosen opponent put them",
                bystanderBefore + 2, scn.GetWoundsOn(bystander));
    }
}
