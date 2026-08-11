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
 * "Exert a [sauron] minion" is a COST, and a cost cannot be paid with another
 * player's card (ruled).
 *
 * Speak No More to Me (10_100) shipped with `exert choose(culture(sauron),
 * minion)` unscoped, so at three-plus seats the acting Shadow player could pay
 * by exerting a minion a DIFFERENT Shadow player controls. At two players every
 * sauron minion belongs to the one Shadow player, so the scoping is invisible
 * there -- which is exactly why it shipped.
 *
 * With one sauron minion per Shadow seat in play, the scoped cost has exactly
 * one candidate (the actor's own) and auto-resolves onto it; the shipped card
 * had two candidates, so it asked -- and could exert the other player's. The
 * wound counts after payment are the whole result.
 */
public class MultiplayerSpeakNoMoreAtTest {

    private static final String EVENT = "10_100";
    private static final String HUNTER = "1_256";   // Morgul Hunter, culture Sauron
    private static final String BIRD = "17_23";     // any FP condition, for the effect half

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("event", EVENT);
            put("hunter", HUNTER);
            put("bird", BIRD);
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

    @Test
    public void payingExertsTheActorsOwnMinion() throws Exception {
        var scn = ThreeSeats();
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String actor = opponents.get(0);
        String other = opponents.get(1);

        var own = scn.GetCardFor(actor, "hunter");
        var theirs = scn.GetCardFor(other, "hunter");
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "bird"));
        scn.MoveMinionsToTable(own, theirs);
        scn.MoveCardsToHand(scn.GetCardFor(actor, "event"));
        scn.SetTwilight(5);

        scn.PassUntilDecision(actor, "action");
        String actionId = scn.GetCardActionId(actor, scn.GetCardFor(actor, "event"));
        assertNotNull("the event should be playable with an own minion in play",
                actionId);
        scn.PlayerDecided(actor, actionId);

        assertEquals("the actor's own minion pays the exertion",
                1, scn.GetWoundsOn(own));
        assertEquals("the other player's minion must not pay",
                0, scn.GetWoundsOn(theirs));
    }
}
