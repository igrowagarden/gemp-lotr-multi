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
 * "The Free Peoples player may not look at or reveal cards in ANY Shadow
 * player's hand."
 *
 * No Business of Ours (2_44) is a *modifier*, not an effect, and that is why it
 * was left alone when the `preventable` and `forEachPlayer` groups were done:
 * `cantLookOrRevealHand` resolved its `hand:` field through
 * PlayerResolver.resolvePlayer, which returns exactly one player. "Any Shadow
 * player" was not expressible at all -- the vocabulary had no way to say it, so
 * the card protected whichever seat getFirstShadowPlayer happened to name and
 * left every other opponent as readable as before.
 *
 * The fix is the same move ForEachPlayer and PreventableAppenderProducer already
 * made: take a PlayersSource instead. Single-player tokens wrap as one-element
 * lists, so `hand: shadow` and `hand: fp` are unaffected.
 *
 * This test is the reason the fix is not just a reading. It asserts the SECOND
 * opponent as hard as the first, because the old behaviour satisfies every
 * assertion about the first one -- a test that only checked that seat would
 * pass against the bug.
 */
public class MultiplayerCantRevealAnyHandAtTest {

    private static final String NO_BUSINESS = "2_44";      // the condition under test
    private static final String URUK = "1_151";            // an [isengard] minion, to play it
    /** Spot Gandalf, reveal an opponent's hand -- the thing that must be blocked. */
    private static final String TREACHERY = "1_86";
    private static final String GANDALF = "1_72";
    /** Erland (2_21): the same modifier with the group on the OTHER field. */
    private static final String ERLAND = "2_21";

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("business", NO_BUSINESS);
            put("uruk", URUK);
            put("treachery", TREACHERY);
            put("gandalf", GANDALF);
            put("erland", ERLAND);
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

    /**
     * Neither opponent's hand may be looked at, and the second one is the point.
     *
     * Asks the rules engine directly rather than driving a card that reveals a
     * hand: canLookOrRevealCardsInHand is the single choke point every reveal
     * goes through, so querying it covers revealHand, lookAtHand and
     * revealRandomCardsFromHand at once. Driving one card would have measured
     * that card.
     */
    @Test
    public void neitherOpponentsHandCanBeRevealed() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCardsToSupportArea(scn.GetCardFor(P2, "business"));
        scn.MoveMinionsToTable(scn.GetCardFor(P2, "uruk"));
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "gandalf"));

        scn.StartMultiplayerGame();

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);   // the seat `hand: shadow` used to name
        String otherShadow = opponents.get(1);   // the seat it silently left exposed
        assertNotEquals(firstShadow, otherShadow);

        String fp = scn.FreePeoplesPlayer();
        var modifiers = scn.game().getModifiersQuerying();

        // Guard against a vacuous pass: if the modifier were not active at all
        // both assertions below would still hold for the wrong reason, so prove
        // the condition is doing something first by checking a player it must
        // NOT restrict -- an opponent looking at another opponent's hand.
        assertTrue("fixture is blind: the modifier is restricting somebody it should"
                        + " not, so 'the Free Peoples player is blocked' proves nothing",
                modifiers.canLookOrRevealCardsInHand(scn.game(), otherShadow, firstShadow));

        assertFalse("the Free Peoples player must not be able to reveal the FIRST"
                        + " opponent's hand -- this held before the fix too",
                modifiers.canLookOrRevealCardsInHand(scn.game(), firstShadow, fp));

        // The whole point. `hand: shadow` resolved to one seat, so this one was
        // readable while its owner sat behind the same condition.
        assertFalse("the Free Peoples player must not be able to reveal the SECOND"
                        + " opponent's hand either -- \"ANY Shadow player's hand\"",
                modifiers.canLookOrRevealCardsInHand(scn.game(), otherShadow, fp));
    }

    /**
     * The same modifier with the group on the other field.
     *
     * Erland (2_21): "SHADOW PLAYERS may not look at or reveal cards in your
     * hand." Plural, and `player:` was a single PlayerSource -- the comment on
     * that field said there is only ever one player on that side, which was true
     * of the two cards that prompted the `hand:` change and false of this one.
     * `player: shadowPlayer` stopped the first opponent and left the rest
     * reading the hand the card exists to hide.
     *
     * Asserting the SECOND opponent is again the whole point: everything about
     * the first one held before the fix.
     */
    @Test
    public void neitherOpponentCanRevealTheHandErlandProtects() throws Exception {
        var scn = ThreeSeats();
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "erland"));

        scn.StartMultiplayerGame();

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String otherShadow = opponents.get(1);
        assertNotEquals(firstShadow, otherShadow);

        String fp = scn.FreePeoplesPlayer();
        var modifiers = scn.game().getModifiersQuerying();

        // Vacuity guard, and it is a different one from the test above: Erland
        // protects only ITS OWNER's hand, so an opponent reading the OTHER
        // opponent's hand must still be allowed. If this went false the modifier
        // would be restricting everybody and the two assertions below would pass
        // for the wrong reason.
        assertTrue("fixture is blind: Erland is protecting a hand that is not its"
                        + " owner's, so the assertions below prove nothing",
                modifiers.canLookOrRevealCardsInHand(scn.game(), otherShadow, firstShadow));

        assertFalse("the FIRST opponent must not be able to reveal the protected"
                        + " hand -- this held before the fix too",
                modifiers.canLookOrRevealCardsInHand(scn.game(), fp, firstShadow));

        assertFalse("the SECOND opponent must not be able to either --"
                        + " \"SHADOW PLAYERS may not look at\"",
                modifiers.canLookOrRevealCardsInHand(scn.game(), fp, otherShadow));
    }
}
