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
 * "If you have more cards in hand than EACH opponent" -- more than every one of
 * them, which is more than the LARGEST of their hands.
 *
 * Glamdring (7_39):
 *
 *   "Skirmish: If you have more cards in hand than each opponent, discard 2
 *    cards from hand to make an opponent exert a minion."
 *
 * The requirement compared against `forEachInHand hand: shadow` -- one seat --
 * so at five players the card fired happily while three opponents held more
 * cards than you. `hand: anyShadow` now yields the maximum across opponents.
 *
 * THIS TEST EXISTS TO SETTLE TWO SEPARATE THINGS, and the second is the one a
 * multiplayer-only fixture would have missed:
 *
 *   1. the multiplayer bug -- the non-first opponent's hand must count
 *   2. MAX versus SUM -- widening `forEachInHand` to a group meant choosing an
 *      aggregate, and that choice was mine rather than the source's. The
 *      hand sizes below are picked so the two disagree: with 5 / 2 / 3, the max
 *      is 3 (5 > 3, playable) and the sum is 5 (5 > 5, NOT playable). The
 *      printed text is unambiguous -- 5 > 2 and 5 > 3 -- so max is right and
 *      sum is wrong, and this fixture is what says so rather than my reading.
 */
public class MultiplayerMoreThanEachOpponentAtTest {

    private static final String GLAMDRING = "7_39";
    // Glamdring's target is title(Gandalf). This printing has NO gametext at
    // all, so nothing of its own can move a hand size or a strength.
    private static final String GANDALF = "0_64";

    // A minion per opponent, different per seat, both vitality 2 so the exert
    // the card grants has something legal to land on.
    private static final String WARRIOR = "4_165";
    private static final String LACKEY = "7_193";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
            "1_198", "1_143", "12_157", "4_10", "4_15",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("glamdring", GLAMDRING);
            put("gandalf", GANDALF);
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

    /**
     * Force a hand to exactly `size`, never touching `protect`.
     *
     * Thinning blind is the trap the harness notes already record: taking the
     * first N cards puts the card under test back on the deck about a third of
     * the time, and the symptom is "not playable", which reads exactly like a
     * broken requirement.
     */
    private static void SetHandSize(VirtualTableScenario scn, String player, int size,
                                    PhysicalCardImpl protect) {
        var hand = new ArrayList<>(scn.gameState().getHand(player));
        for (PhysicalCardImpl card : hand.toArray(new PhysicalCardImpl[0])) {
            if (scn.gameState().getHand(player).size() <= size) break;
            if (card == protect) continue;
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
     * Walks to a skirmish with Glamdring on the table and the hands set, then
     * reports whether the card's action is on offer.
     */
    private boolean glamdringPlayableWith(int fpHand, int firstOpponentHand,
                                          int otherOpponentHand, StringBuilder dump)
            throws Exception {
        var scn = ThreeSeats();

        var gandalf = scn.GetCardFor(P1, "gandalf");
        scn.MoveCompanionsToTable(gandalf);
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        var glamdring = scn.GetCardFor(P1, "glamdring");
        scn.AttachCardsTo(gandalf, glamdring);

        var warrior = scn.GetCardFor(P2, "warrior");
        var lackey = scn.GetCardFor(P3, "lackey");
        scn.MoveMinionsToTable(warrior, lackey);

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String otherShadow = opponents.get(1);

        SetHandSize(scn, P1, fpHand, glamdring);
        SetHandSize(scn, firstShadow, firstOpponentHand, null);
        SetHandSize(scn, otherShadow, otherOpponentHand, null);

        // The minion Gandalf faces has to be one that is actually on the table
        // for the seat whose skirmish we walk into; either will do.
        var minion = scn.GetCardFor(P2, "warrior");
        scn.PassUntilSkirmishBetween(gandalf, minion);

        String actionId = scn.GetCardActionId(P1, glamdring);
        dump.append("fp=").append(fpHand)
            .append(" first(").append(firstShadow).append(")=").append(firstOpponentHand)
            .append(" other(").append(otherShadow).append(")=").append(otherOpponentHand)
            .append(" | actual hands: ").append(Hands(scn))
            .append(" | actionId=").append(actionId);
        return actionId != null;
    }

    /**
     * The multiplayer bug: the opponent getFirstShadowPlayer does NOT name is
     * the one holding more cards, and the card must not fire.
     *
     * With `hand: shadow` this compared 5 against the first opponent's 2 and
     * happily offered the action while the other opponent sat on 7.
     */
    @Test
    public void theCardIsRefusedWhenTheOtherOpponentHoldsMore() throws Exception {
        var dump = new StringBuilder();
        boolean playable = glamdringPlayableWith(5, 2, 7, dump);
        assertFalse("you do not have more cards in hand than EACH opponent -- the"
                        + " other opponent holds 7 to your 5, so Glamdring must not"
                        + " be on offer. " + dump, playable);
    }

    /**
     * The mirror: more than both, so the card fires.
     *
     * This is not decoration -- without it, a requirement that simply never
     * passes would satisfy the test above.
     */
    @Test
    public void theCardIsOfferedWhenYouHoldMoreThanBoth() throws Exception {
        var dump = new StringBuilder();
        boolean playable = glamdringPlayableWith(8, 2, 7, dump);
        assertTrue("8 beats both 2 and 7, so Glamdring should be on offer."
                + " " + dump, playable);
    }

    /**
     * MAX versus SUM, and the only case where they disagree.
     *
     * 5 against 2 and 3. Max is 3, so 5 > 3 and the card fires -- which is what
     * "more cards in hand than each opponent" plainly says. Sum is 5, so 5 > 5
     * is false and a sum reading would refuse it.
     *
     * Widening forEachInHand to a group forced a choice of aggregate that the
     * source did not make for me. This is the case that says the choice was
     * right.
     */
    @Test
    public void maxNotSum() throws Exception {
        var dump = new StringBuilder();
        boolean playable = glamdringPlayableWith(5, 2, 3, dump);
        assertTrue("5 is more than each of 2 and 3, so the card fires. If this is"
                        + " false, forEachInHand is summing the opponents' hands"
                        + " (2+3=5) rather than taking the largest. " + dump,
                playable);
    }
}
