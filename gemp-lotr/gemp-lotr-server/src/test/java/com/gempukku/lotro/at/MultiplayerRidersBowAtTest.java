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
 * "Each time bearer wins a skirmish, you may choose a Shadow player who must
 * wound one of HIS OR HER minions."
 *
 * Rider's Bow (13_135) is Eomer's Bow's twin: same scoping form, same
 * choose-an-opponent shape, but reached through a skirmish trigger rather than
 * an archery action. Both shipped on "shape and text match only".
 *
 * Measuring the second one is not redundant. 18_95 proves the tokens behave in
 * an ACTIVATED ability; this proves them in a TRIGGER, where the action is built
 * by the trigger machinery rather than by a player choosing to use a card, and
 * where the branch has already been surprised once -- see 103_38 in errors.log,
 * whose trigger turned out to have neither the controller as performing player
 * nor the card as `getSource().getOwner()`.
 *
 * The skirmish is deliberately against the DEFAULT opponent's minion while the
 * OTHER opponent is chosen for the wound, so the skirmish's own damage can never
 * be mistaken for the card's, and neither defaulting to getFirstShadowPlayer nor
 * "it wounded whoever was in the skirmish" reproduces the result.
 */
public class MultiplayerRidersBowAtTest {

    private static final String RIDERS_BOW = "13_135";
    // Strength 8, not the strength-7 printing. Measured, not chosen: with a
    // strength-7 Eomer the skirmish against Orthanc Warrior (also 7) is a TIE,
    // he never wins, and the trigger never fires -- and because which minion he
    // faces depends on the shuffle, that failed only on some seatings. The
    // strength-8 printing beats both minions this fixture can put in front of
    // him (Orthanc Warrior 7, Morgul Lackey 6).
    private static final String EOMER = "7_227";

    // Two minions per opponent so the scoped selection is a real decision --
    // with one apiece it auto-resolves and "who was asked" cannot be seen at
    // all, which is what would let the wrong-seat control pass silently.
    private static final String WARRIOR = "4_165";      // vitality 2
    private static final String LACKEY = "7_193";       // vitality 2
    private static final String TROOP = "1_143";        // vitality 4
    private static final String URUK_TROOP = "12_157";  // vitality 4

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("bow", RIDERS_BOW);
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

    @Test
    public void theChosenOpponentWoundsOneOfTheirOwnMinions() throws Exception {
        var scn = ThreeSeats();

        var eomer = scn.GetCardFor(P1, "eomer");
        scn.MoveCompanionsToTable(eomer);
        scn.AttachCardsTo(eomer, scn.GetCardFor(P1, "bow"));

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

        // Skirmish the OTHER opponent's minion, so nothing the skirmish itself
        // does can be confused with what the bow does.
        scn.PassUntilSkirmishBetween(eomer, others.get(0));
        // The bow's trigger is `optional: true`, so it arrives as an Optional
        // decision rather than the "Required responses" one that 11_34's
        // required trigger produces. Waiting for the wrong text just walks the
        // game to the end of the turn and reports "nobody has a decision".
        scn.PassUntilDecision(P1, "Optional");

        assertTrue("the bow's optional trigger should be offered; pending: " + Pending(scn),
                scn.FreepsHasOptionalTriggerAvailable(scn.GetCardFor(P1, "bow")));
        scn.FreepsAcceptOptionalTrigger();

        assertTrue("the Free Peoples player should be asked which opponent; pending: "
                + Pending(scn), scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        // Kills a wrong-seat control: only visible because they hold two minions.
        assertTrue("the CHOSEN opponent should be the one wounding; pending: " + Pending(scn),
                scn.DecisionAvailable(chosenOpponent, "Choose"));
        assertFalse("the other opponent should not be asked; pending: " + Pending(scn),
                scn.DecisionAvailable(defaultOpponent, "Choose"));

        // Kills the no-scoping control: "one of HIS OR HER minions".
        assertTrue("the chosen opponent should be offered their own minions",
                scn.HasCardChoiceAvailable(chosenOpponent, theirs.get(0)));
        assertFalse("the chosen opponent must NOT be offered the other opponent's minion",
                scn.HasCardChoiceAvailable(chosenOpponent, others.get(1)));

        int theirsBefore = scn.GetWoundsOn(theirs.get(0));
        scn.ChooseCards(chosenOpponent, theirs.get(0));

        assertEquals("the chosen opponent's own minion took the wound",
                theirsBefore + 1, scn.GetWoundsOn(theirs.get(0)));
        assertEquals("their other minion is untouched", 0, scn.GetWoundsOn(theirs.get(1)));
        assertEquals("the other opponent's uninvolved minion is untouched",
                0, scn.GetWoundsOn(others.get(1)));
    }
}
