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
 * "Spot a FREE PEOPLES PLAYER'S site on the adventure path" -- the site spotted
 * (and then stolen: "You now control the spotted site") belongs to the Free
 * Peoples player.
 *
 * Uruk Cavern Striker (15_174) shipped with `choose(not(your),site,...)`. At
 * two players everything not yours is the Free Peoples player's; at three-plus
 * seats it also matches the OTHER Shadow players' path sites, so the striker
 * could steal a fellow Shadow player's site. The fix scopes the spot to
 * ownedByFreePeoplesPlayer.
 *
 * The result is read from the offer itself: the which-site decision's card list
 * must contain only sites the current Free Peoples player owns.
 */
public class MultiplayerCavernStrikerAtTest {

    private static final String STRIKER = "15_174";
    private static final String GANDALF = "1_72";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195", "1_178",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("striker", STRIKER);
            put("gandalf", GANDALF);
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
    public void onlyFreePeoplesSitesCanBeSpotted() throws Exception {
        var scn = ThreeSeats();
        var gandalf = scn.GetCardFor(P1, "gandalf");
        scn.MoveCompanionsToTable(gandalf);
        scn.StartMultiplayerGame();
        assertEquals(P1, scn.FreePeoplesPlayer());

        List<String> opponents = ShadowSeats(scn);
        String actor = opponents.get(0);
        String other = opponents.get(1);

        var striker = scn.GetCardFor(actor, "striker");
        scn.MoveMinionsToTable(striker);

        // The actor controls one site (the exchange needs one). The OTHER
        // Shadow seat's site is placed ON the path -- the card the shipped
        // select could steal -- and a second Free Peoples site joins it so the
        // scoped spot still has two candidates and must ask rather than
        // auto-resolve.
        var actorSite = scn.GetSite(actor, 2);
        var otherSite = scn.GetSite(other, 3);
        var extraFpSite = scn.GetSite(P1, 5);
        assertNotNull("fixture: the actor's site 2 exists", actorSite);
        assertNotNull("fixture: the other seat's site 3 exists", otherSite);
        assertNotNull("fixture: P1's site 5 exists", extraFpSite);
        actorSite.setCardController(actor);
        scn.MoveCardToAdventurePath(otherSite);
        otherSite.setCardController(other);
        scn.MoveCardToAdventurePath(extraFpSite);

        scn.PassUntilSkirmishBetween(gandalf, striker);

        // The Free Peoples player's skirmish window comes first.
        scn.PassUntilDecision(actor, "action");
        String actionId = scn.GetCardActionId(actor, striker);
        assertNotNull("the striker's skirmish action should be available", actionId);
        scn.PlayerDecided(actor, actionId);

        // The which-site offer is the result: every candidate must be a site
        // the Free Peoples player owns.
        StringBuilder pending = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            pending.append(p).append("=")
                   .append(d == null ? "(null)" : d.getText()).append("  ");
        }
        assertNotNull("the actor should hold the which-site decision; pending: "
                + pending, scn.GetAwaitingDecision(actor));
        String[] offered = scn.GetADParam(actor, "cardId");
        assertNotNull("the actor should be choosing among sites", offered);
        assertTrue("at least one Free Peoples site should be offered",
                offered.length > 0);
        for (String cardId : offered) {
            var site = scn.game().getGameState().findCardById(Integer.parseInt(cardId));
            assertNotNull("offered card should exist: " + cardId, site);
            assertEquals("every spottable site belongs to the Free Peoples"
                            + " player -- reaching " + site.getBlueprint().getTitle()
                            + " owned by " + site.getOwner() + " was the shipped bug",
                    P1, site.getOwner());
        }
    }
}
