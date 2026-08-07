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
 * "EACH Shadow player" -- the third meaning `player: shadow` was carrying.
 *
 * The other two are settled. `anyShadow` offers a prevention round the table
 * until somebody accepts; `ChooseOpponent` + `fromMemory` picks one. This is the
 * remaining one: everybody does it, nobody chooses.
 *
 * It could not be written before, and the reason is worth stating precisely.
 * `ForEachPlayer` already existed, but it iterated GameUtils.getAllPlayers with
 * no way to narrow it -- so "each player" was expressible and "each SHADOW
 * player" was not. It now takes a `player:` field over the same PlayersSource
 * vocabulary `anyShadow` uses, defaulting to `eachPlayer` so its two existing
 * users are untouched.
 *
 * Shield of the White Tree (18_57) is the card measured here because its effect
 * is unambiguous: "each opponent shuffle his or her hand into his or her draw
 * deck and draw 8 cards". Eight is a number no accident produces, and a hand
 * belongs to exactly one seat, so "every opponent" and "one fixed opponent" are
 * impossible to confuse.
 */
public class MultiplayerEachShadowPlayerAtTest {

    private static final String SHIELD = "18_57";
    private static final String GONDOR_MAN = "1_96";     // Boromir, to bear it

    /**
     * MultiplayerTable puts exactly ONE copy of each named card into every
     * seat's deck, so a two-card fixture gives a two-card deck and "draw 8"
     * quietly draws 2. Ten fillers make the decks big enough for 8 to mean 8.
     * Measured, not assumed -- this test first failed with expected:<8> was:<2>.
     */
    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "4_165",
            "7_193", "1_143", "12_157", "1_294", "1_312",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("shield", SHIELD);
            put("boromir", GONDOR_MAN);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
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

    /** Put 3 of a player's cards back on their deck, so their hand is not 8. */
    private static void ThinHand(VirtualTableScenario scn, String player) {
        var hand = new ArrayList<>(scn.GetHand(player));
        for (int i = 0; i < 3 && i < hand.size(); i++)
            scn.MoveCardsToTopOfDeck((com.gempukku.lotro.game.PhysicalCardImpl) hand.get(i));
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
     * BOTH opponents redraw, not just whichever seat getFirstShadowPlayer names.
     *
     * Asserts on the second opponent as hard as on the first: the old behaviour
     * satisfies every assertion about the first one, so a test that only checked
     * that seat would pass against the bug.
     */
    @Test
    public void everyOpponentShufflesAndRedraws() throws Exception {
        var scn = ThreeSeats();
        var boromir = scn.GetCardFor(P1, "boromir");
        scn.MoveCompanionsToTable(boromir);
        scn.AttachCardsTo(boromir, scn.GetCardFor(P1, "shield"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String first = opponents.get(0);
        String second = opponents.get(1);
        assertNotEquals(first, second);

        // The opening hand is ALREADY 8, so "redrew to 8" would be satisfied by
        // doing nothing at all. Thin every hand first so 8 is a result and not
        // the starting position. The guards below are what caught this.
        //
        // P1's hand is thinned for the same reason and it is not optional: left
        // at 8, the "P1 was excluded" assertion holds whether P1 redraws or not,
        // and the eachPlayer control passes. Measured -- that control DID pass
        // until this line was added.
        ThinHand(scn, P1);
        ThinHand(scn, first);
        ThinHand(scn, second);

        int firstDeckBefore = scn.GetDrawDeck(first).size();
        int secondDeckBefore = scn.GetDrawDeck(second).size();
        // "each OPPONENT" excludes the player using the card. ForEachPlayer's
        // own default is every player at the table, so this is the assertion
        // that distinguishes `eachShadow` from `eachPlayer`.
        int ownHandBefore = scn.GetHand(P1).size();
        assertNotEquals("the first opponent's hand must not already be 8",
                8, scn.GetHand(first).size());
        assertNotEquals("the second opponent's hand must not already be 8",
                8, scn.GetHand(second).size());

        scn.PassUntilDecision(P1, "Regroup action");

        // GetCardActionId returns NULL when the action is not on offer, and
        // PlayerDecided(null) passes the phase instead of throwing. Everyone
        // then reconciles to 8 in the ordinary course of the regroup, which
        // satisfies every assertion below about the opponents -- so a run where
        // the card never fired looks exactly like a run where it worked.
        // Measured: this test failed 1 run in 3 that way before this assertion
        // existed.
        String shieldAction = scn.GetCardActionId(P1, scn.GetCardFor(P1, "shield"));
        assertNotNull("the shield's regroup action should be on offer; pending: " + Pending(scn),
                shieldAction);
        scn.PlayerDecided(P1, shieldAction);

        assertEquals("the first opponent redrew to 8; pending: " + Pending(scn),
                8, scn.GetHand(first).size());
        assertEquals("the SECOND opponent redrew to 8 as well; pending: " + Pending(scn),
                8, scn.GetHand(second).size());

        // Their own decks, not one shared pile: each drew from and shuffled into
        // the deck that belongs to them.
        assertNotEquals("the first opponent's deck was touched",
                firstDeckBefore, scn.GetDrawDeck(first).size());
        assertNotEquals("the second opponent's deck was touched",
                secondDeckBefore, scn.GetDrawDeck(second).size());

        assertEquals("the player who used the card is not one of the opponents "
                        + "and did not redraw; pending: " + Pending(scn),
                ownHandBefore, scn.GetHand(P1).size());
        assertNotEquals("...and that only means something because P1 is not at 8",
                8, ownHandBefore);
    }
}
