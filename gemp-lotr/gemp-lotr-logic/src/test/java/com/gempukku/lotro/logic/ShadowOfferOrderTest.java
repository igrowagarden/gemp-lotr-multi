package com.gempukku.lotro.logic;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Pins the direction Shadow players are offered things in.
 *
 * Turns rotate clockwise -- "the next player to his left" -- but the action
 * procedure and every response window rotate counter-clockwise, "the player on
 * your right". At two players the two orders are identical, so a card offered
 * round the table in the wrong direction passes every existing test and is only
 * wrong at a real multiplayer table.
 *
 * `player: anyShadow` on a preventable offers the prevention to each Shadow
 * player in turn, and must use the same order the Shadow phases themselves use:
 * counter-clockwise from the Free Peoples player, skipping them.
 * GameUtils.getShadowPlayers returns *seating* order, which is a different
 * sequence and the easy mistake to make.
 */
public class ShadowOfferOrderTest {

    private static List<String> counterClockwiseShadowPlayers(PlayerOrder order, String fp) {
        // Deliberately the same two lines ShadowPhasesGameProcess uses.
        PlayOrder playOrder = order.getCounterClockwisePlayOrder(fp, false);
        playOrder.getNextPlayer();
        List<String> result = new ArrayList<>();
        String next;
        while ((next = playOrder.getNextPlayer()) != null && !next.equals(fp))
            result.add(next);
        return result;
    }

    @Test
    public void fiveSeatsAreOfferedCounterClockwiseFromTheFreePeoplesPlayer() {
        PlayerOrder order = new PlayerOrder(Arrays.asList("p1", "p2", "p3", "p4", "p5"));
        assertEquals(Arrays.asList("p5", "p4", "p3", "p2"),
                counterClockwiseShadowPlayers(order, "p1"));
    }

    @Test
    public void theOrderFollowsWhoeverIsTheFreePeoplesPlayer() {
        PlayerOrder order = new PlayerOrder(Arrays.asList("p1", "p2", "p3", "p4", "p5"));
        assertEquals(Arrays.asList("p2", "p1", "p5", "p4"),
                counterClockwiseShadowPlayers(order, "p3"));
    }

    /**
     * Seating order is what GameUtils.getShadowPlayers returns, and it is not
     * the offer order. If these ever coincide at five seats, this test is no
     * longer protecting anything.
     */
    @Test
    public void seatingOrderIsNotTheOfferOrder() {
        PlayerOrder order = new PlayerOrder(Arrays.asList("p1", "p2", "p3", "p4", "p5"));
        List<String> seating = new ArrayList<>(order.getAllPlayers());
        seating.remove("p1");
        assertEquals(Arrays.asList("p2", "p3", "p4", "p5"), seating);
        assertEquals(Arrays.asList("p5", "p4", "p3", "p2"),
                counterClockwiseShadowPlayers(order, "p1"));
    }

    /**
     * The two-player control: with one opponent the direction cannot be
     * observed, which is exactly why the bug is invisible until five seats.
     */
    @Test
    public void twoSeatsHaveOneShadowPlayerEitherWay() {
        PlayerOrder order = new PlayerOrder(Arrays.asList("p1", "p2"));
        assertEquals(Arrays.asList("p2"), counterClockwiseShadowPlayers(order, "p1"));

        List<String> seating = new ArrayList<>(order.getAllPlayers());
        seating.remove("p1");
        assertEquals(seating, counterClockwiseShadowPlayers(order, "p1"));
    }
}
