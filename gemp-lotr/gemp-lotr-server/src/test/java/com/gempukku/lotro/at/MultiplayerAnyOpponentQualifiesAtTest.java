package com.gempukku.lotro.at;

import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayOrder;
import com.gempukku.lotro.common.Phase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "If AN OPPONENT has at least 7 cards in hand" -- ANY of them qualifies, and
 * then the card picks which.
 *
 * The Mirror of Galadriel (1_55):
 *
 *   "Maneuver: If an opponent has at least 7 cards in hand, exert Galadriel to
 *    look at 2 of those cards at random. Discard one and replace the other."
 *
 * The REQUIREMENT was the half that was wrong, which is why this card survived
 * every earlier sweep of effect tokens: `cardsInHandMoreThan player: shadowPlayer`
 * asked one seat, so above two players the action was hidden while three
 * opponents sat on full hands. The effect half then had to follow -- a
 * requirement that says "somebody qualifies" needs a choice to say which.
 *
 * ANY, NOT EVERY. Widening the requirement to a group forced that choice too:
 * an `every` reading would hide the action whenever a single opponent had a
 * small hand, which is the opposite of what the card says. The fixture below
 * deliberately gives one opponent 2 cards and the other 7, so an `every`
 * reading and a fixed-seat reading BOTH refuse it and only `any` allows it.
 */
public class MultiplayerAnyOpponentQualifiesAtTest {

    private static final String MIRROR = "1_55";
    // The exert cost is `choose(name(Galadriel))`, so a Galadriel is required.
    // This printing's own text keys off the starting fellowship, which a
    // teleported card never enters, so it is inert here.
    private static final String GALADRIEL = "10_11";

    // Minions on the table, one per opponent and different per seat. Without
    // any, the maneuver phase is never reached at all and the walk dies in
    // regroup -- the trap the harness notes record against 10_15.
    private static final String WARRIOR = "4_165";
    private static final String LACKEY = "7_193";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
            "1_198", "1_143", "12_157", "4_10", "4_15",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("mirror", MIRROR);
            put("galadriel", GALADRIEL);
            put("warrior", WARRIOR);
            put("lackey", LACKEY);
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

    /** Force a hand to exactly `size`. Never thin blind -- see 7_39's test. */
    private static void SetHandSize(VirtualTableScenario scn, String player, int size) {
        var hand = new ArrayList<>(scn.gameState().getHand(player));
        for (PhysicalCardImpl card : hand.toArray(new PhysicalCardImpl[0])) {
            if (scn.gameState().getHand(player).size() <= size) break;
            scn.MoveCardsToBottomOfDeck(card);
        }
        while (scn.gameState().getHand(player).size() < size)
            scn.gameState().playerDrawsCard(player);
        assertEquals("fixture: could not set " + player + "'s hand to " + size,
                size, scn.gameState().getHand(player).size());
    }

    private static String Hands(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new String[]{P1, P2, P3})
            sb.append(p).append("=").append(scn.gameState().getHand(p).size()).append(" ");
        return sb.toString();
    }

    /**
     * The action is offered because SOME opponent qualifies -- and it is not the
     * one getFirstShadowPlayer names.
     */
    @Test
    public void theActionIsOfferedWhenTheOtherOpponentIsTheOneHoldingSeven() throws Exception {
        var scn = ThreeSeats();

        var galadriel = scn.GetCardFor(P1, "galadriel");
        scn.MoveCompanionsToTable(galadriel);
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "mirror"));
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        scn.MoveMinionsToTable(scn.GetCardFor(P2, "warrior"), scn.GetCardFor(P3, "lackey"));

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String qualifying = opponents.get(1);
        assertNotEquals(firstShadow, qualifying);
        assertEquals(firstShadow, GameUtils.getFirstShadowPlayer(scn.game()));

        // The seat `player: shadowPlayer` used to ask holds 2; the OTHER holds
        // the 7 that satisfies the requirement. A fixed-seat reading refuses,
        // an `every` reading refuses, only `any` allows.
        SetHandSize(scn, firstShadow, 2);
        SetHandSize(scn, qualifying, 7);

        scn.PassUntilPhase(Phase.MANEUVER);

        var mirror = scn.GetCardFor(P1, "mirror");
        String actionId = scn.GetCardActionId(P1, mirror);
        String dump = "first(" + firstShadow + ")=2 qualifying(" + qualifying
                + ")=7 | hands: " + Hands(scn);

        assertNotNull("an opponent DOES have 7 cards in hand, so the Mirror should"
                        + " be on offer. If this is null the requirement is still"
                        + " asking one seat. " + dump, actionId);

        int qualifyingBefore = scn.gameState().getHand(qualifying).size();
        int otherBefore = scn.gameState().getHand(firstShadow).size();
        scn.PlayerDecided(P1, actionId);

        // Drain the action, choosing the qualifying opponent by NAME when asked.
        StringBuilder trace = new StringBuilder();
        boolean sawOpponentChoice = false;
        for (int step = 0; step < 12; step++) {
            var waiting = new ArrayList<>(scn.userFeedback().getUsersPendingDecision());
            if (waiting.isEmpty()) break;
            java.util.Collections.sort(waiting);
            String who = waiting.get(0);
            var d = scn.userFeedback().getAwaitingDecision(who);
            if (d == null) continue;
            var dp = d.getDecisionParameters();
            trace.append("<").append(who).append(" ").append(d.getDecisionType())
                 .append(": ").append(d.getText()).append("> ");

            // The opponent choice's options live under "results", not "choice" --
            // getting that wrong once left a sibling test picking the opponent by
            // index and passing on the luck of the ordering.
            if (dp.get("results") != null
                    && java.util.Arrays.asList(dp.get("results")).contains(qualifying)) {
                sawOpponentChoice = true;
                scn.ChooseOption(who, qualifying);
                continue;
            }
            String answer;
            switch (d.getDecisionType()) {
                case CARD_SELECTION, ARBITRARY_CARDS -> {
                    String[] ids = dp.get("cardId");
                    answer = (ids == null || ids.length == 0) ? "" : ids[0];
                }
                case MULTIPLE_CHOICE -> answer = "0";
                case INTEGER -> answer = dp.containsKey("min") ? dp.get("min")[0] : "0";
                default -> answer = "";
            }
            try {
                scn.PlayerDecided(who, answer);
            } catch (Exception e) {
                trace.append("[!! ").append(e.getClass().getSimpleName()).append("] ");
                break;
            }
            if (scn.gameState().getHand(qualifying).size() < qualifyingBefore) break;
        }

        String after = "before: q=" + qualifyingBefore + " other=" + otherBefore
                + " | after: " + Hands(scn) + " | trace: " + trace;

        assertTrue("the Free Peoples player was never asked which opponent, so the"
                + " assertions below cannot tell the seats apart. " + after,
                sawOpponentChoice);
        assertEquals("the CHOSEN opponent is the one who loses a card from hand."
                        + " " + after,
                qualifyingBefore - 1, scn.gameState().getHand(qualifying).size());
        assertEquals("the other opponent's hand is untouched. " + after,
                otherBefore, scn.gameState().getHand(firstShadow).size());
    }

    /**
     * The mirror image, and the guard against the requirement simply always
     * passing: with NOBODY over six, the action must not be offered.
     */
    @Test
    public void theActionIsRefusedWhenNoOpponentQualifies() throws Exception {
        var scn = ThreeSeats();

        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "galadriel"));
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "mirror"));
        scn.StartMultiplayerGame();

        scn.MoveMinionsToTable(scn.GetCardFor(P2, "warrior"), scn.GetCardFor(P3, "lackey"));

        List<String> opponents = ShadowSeats(scn);
        SetHandSize(scn, opponents.get(0), 2);
        SetHandSize(scn, opponents.get(1), 6);   // six is NOT "at least 7"

        scn.PassUntilPhase(Phase.MANEUVER);

        assertNull("no opponent has more than 6 cards in hand, so the Mirror must"
                        + " not be on offer. Hands: " + Hands(scn),
                scn.GetCardActionId(P1, scn.GetCardFor(P1, "mirror")));
    }
}
