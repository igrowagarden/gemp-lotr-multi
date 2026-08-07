package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
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
 * Denethor (10_28) and his errata (60_28): "Choose an opponent who may draw 2
 * cards."
 *
 * The conversion itself shipped a while ago -- ChooseOpponent, then
 * `player: fromMemory(opponent)` on both the optional and the draw. What never
 * happened was measuring it, because the ability needs the fellowship AT A
 * SANCTUARY and no fixture on this branch could put it there. That is what left
 * this card labelled "shape and text match only" for three sessions.
 *
 * It turns out to need no new fixture at all: KingSites already has The Dimholt
 * (8_117) at site 3, and The Dimholt is a Sanctuary. The blocker was the belief
 * that a sanctuary had to be placed; it only had to be walked to.
 *
 * The test picks the SECOND opponent on purpose. Everything about the first one
 * passes whether or not the card ever offered a choice.
 */
public class MultiplayerDenethorAtTest {

    private static final String DENETHOR = "10_28";
    private static final String KNIGHT = "5_35";      // a second [gondor] Man
    /** Stays in the draw deck: the ability takes a [gondor] card out of it. */
    private static final String LAST_THROW = "10_34";

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("denethor", DENETHOR);
            put("knight", KNIGHT);
            put("gondorCard", LAST_THROW);
            // Filler. MultiplayerTable puts ONE copy of each named id in every
            // deck, so a three-card fixture is a three-card deck -- and after a
            // round of turns the opponent has nothing left to draw. Measured:
            // "the chosen opponent drew two" failed 3 -> 3 without these.
            put("f1", "1_133"); put("f2", "1_157"); put("f3", "1_159");
            put("f4", "1_173"); put("f5", "1_195"); put("f6", "1_198");
            put("f7", "4_165"); put("f8", "7_193"); put("f9", "1_143");
            put("f10", "12_157");
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

    /**
     * Advance to P1's OWN fellowship phase with P1 at `siteNumber`.
     *
     * P1 arrives at the site at the end of their turn, so their next fellowship
     * phase there is a full round of other players' turns away.
     */
    private static void PassUntilOwnFellowshipPhaseAtSite(VirtualTableScenario scn,
                                                          int siteNumber) {
        for (int turn = 0; turn < 40; turn++) {
            scn.PassUntilDecision(P1, "Play Fellowship action or Pass");
            if (scn.GetCurrentSiteNumber() >= siteNumber)
                return;
            scn.PlayerDecided(P1, "");
        }
        throw new RuntimeException("P1 never got a fellowship phase at site "
                + siteNumber + "; stopped at " + scn.GetCurrentSiteNumber());
    }

    @Test
    public void theChosenOpponentIsTheOneOfferedTheDraw() throws Exception {
        var scn = ThreeSeats();
        var denethor = scn.GetCardFor(P1, "denethor");
        scn.MoveCompanionsToTable(denethor, scn.GetCardFor(P1, "knight"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertEquals("fixture assumes the first seat is what getFirstShadowPlayer names",
                defaultOpponent, GameUtils.getFirstShadowPlayer(scn.game()));

        // The Dimholt is site 3 in KingSites and is a Sanctuary, so this is a
        // walk rather than a placement -- which is the whole reason the card was
        // reachable after all.
        // NOT PassUntilSite(3) followed by PassUntilPhase(FELLOWSHIP). Every
        // seat has its OWN fellowship and its own position on the path, so the
        // next fellowship phase after P1 reaches site 3 belongs to the NEXT
        // player, at THEIR site 1 -- measured: the walk reported
        // "num=1 site=Edoras Hall" and read like an overshoot. What this card
        // needs is P1's own fellowship phase with P1 standing on site 3, which
        // is a turn later.
        PassUntilOwnFellowshipPhaseAtSite(scn, 3);
        assertEquals("P1 should be the one taking the turn", P1, scn.FreePeoplesPlayer());
        assertEquals("P1 should be standing on site 3", 3, scn.GetCurrentSiteNumber());
        assertTrue("site 3 should be a sanctuary; it is " + scn.GetCurrentSite()
                        .getBlueprint().getTitle(),
                scn.HasKeyword(scn.GetCurrentSite(), com.gempukku.lotro.common.Keyword.SANCTUARY));

        String actionId = scn.GetCardActionId(P1, denethor);
        assertNotNull("Denethor's fellowship action should be on offer at a sanctuary;"
                + " pending: " + Pending(scn), actionId);
        scn.PlayerDecided(P1, actionId);

        // Taking the [gondor] card out of the draw deck comes first and is not
        // what this test is about. It arrives as more than one decision -- the
        // first is "You may inspect the contents of your deck while retrieving
        // cards" -- so answer whatever P1 is asked until the opponent choice
        // shows up rather than naming the prompts.
        for (int i = 0; i < 6; i++) {
            var d = scn.userFeedback().getAwaitingDecision(P1);
            if (d == null || d.getText() == null
                    || d.getText().contains("Choose an opponent")) break;
            String[] ids = d.getDecisionParameters().get("cardId");
            scn.PlayerDecided(P1, ids != null && ids.length > 0 ? ids[0] : "");
        }

        assertTrue("the Free Peoples player should be asked which opponent;"
                        + " pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        // The point of the card, and the assertion the old fixed-seat form fails.
        assertTrue(chosenOpponent + " should be the seat offered the draw;"
                        + " pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "draw"));
        assertFalse(defaultOpponent + " should not be asked anything;"
                        + " pending: " + Pending(scn),
                scn.DecisionAvailable(defaultOpponent, "draw"));

        int chosenBefore = scn.GetHand(chosenOpponent).size();
        int otherBefore = scn.GetHand(defaultOpponent).size();
        scn.ChooseOption(chosenOpponent, "Yes");

        assertEquals("the chosen opponent drew two", chosenBefore + 2,
                scn.GetHand(chosenOpponent).size());
        assertEquals("the other opponent drew nothing", otherBefore,
                scn.GetHand(defaultOpponent).size());
    }
}
