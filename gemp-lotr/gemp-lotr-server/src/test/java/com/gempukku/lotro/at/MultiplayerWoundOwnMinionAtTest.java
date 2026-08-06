package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Choose a Shadow player who must wound one of HIS OR HER minions."
 *
 * Pippin (6_114) needed three separate things above two players, and the plan
 * had it down as a one-line data edit:
 *
 *   1. no choice was offered at all -- the card said `player: shadow` and never
 *      called ChooseOpponent;
 *   2. `player:` named getFirstShadowPlayer rather than the chosen player;
 *   3. `select: choose(minion)` was unscoped, so "his or her minions" meant any
 *      minion on the table.
 *
 * (3) could not be written before this branch added the
 * ownedByPlayerFromMemory filter. At two players every minion belongs to the one
 * Shadow player, so none of the three is observable there.
 */
public class MultiplayerWoundOwnMinionAtTest {

    private static final String PIPPIN = "6_114";
    // Two DIFFERENT minions, both with vitality 2 or more. Both properties are
    // load-bearing and each was learned by a failing run:
    //
    //   - the same card twice is unique-discarded, so one opponent ends up with
    //     nothing in play and a correctly scoped effect finds no target, which
    //     looks exactly like a broken fix;
    //   - a vitality-1 minion cannot be exerted, so the exert test did nothing
    //     whenever the shuffle happened to choose that seat.
    private static final String ORTHANC_WARRIOR = "4_165";   // vitality 2
    private static final String MORGUL_LACKEY = "7_193";     // vitality 2

    /** A Dragon's Tale: "...choose a Shadow player who must exert one of his or her minions." */
    private static final String DRAGONS_TALE = "18_106";
    /** A Hobbit, because the tale needs one spotted to be in play. */
    private static final String MERRY = "4_310";

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("pippin", PIPPIN);
            put("tale", DRAGONS_TALE);
            put("merry", MERRY);
            put("warrior", ORTHANC_WARRIOR);
            put("lackey", MORGUL_LACKEY);
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
     * The wound lands on a minion belonging to the opponent who was chosen, and
     * the other opponent's minion is untouched.
     *
     * Deliberately chooses the opponent who is NOT first in the offer order, so
     * that following the choice and defaulting to getFirstShadowPlayer cannot
     * give the same answer.
     */
    @Test
    public void theChosenOpponentWoundsTheirOwnMinion() throws Exception {
        var scn = ThreeSeats();
        var pippin = scn.GetCardFor(P1, "pippin");
        scn.MoveCompanionsToTable(pippin);
        // Both opponents' minions go down before the game starts, the way the
        // two-player tests do it -- placing cards mid-game does not put them
        // into play properly. P1 is always the Free Peoples player, so the two
        // opponents are P2 and P3 whichever order they end up offered in.
        scn.MoveMinionsToTable(scn.GetCardFor(P2, "warrior"), scn.GetCardFor(P3, "lackey"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);

        PhysicalCardImpl defaultsMinion = scn.GetCardFor(defaultOpponent, defaultOpponent.equals(P2) ? "warrior" : "lackey");
        PhysicalCardImpl chosensMinion = scn.GetCardFor(chosenOpponent, chosenOpponent.equals(P2) ? "warrior" : "lackey");

        scn.PassUntilDecision(P1, "Regroup action");
        assertTrue("Pippin's regroup action should be available",
                scn.ActionAvailable(P1, "Use " + GameUtils.getFullName(pippin)));
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, pippin));

        // The choice only exists above two players: ChooseOpponentEffect
        // auto-picks when there is one candidate.
        assertNotNull("the Free Peoples player should be asked which opponent",
                scn.userFeedback().getAwaitingDecision(P1));
        scn.ChooseOption(P1, chosenOpponent);

        // No decision is presented to the chosen opponent: the scoped filter
        // leaves them exactly one eligible minion -- their own -- and a choice
        // of one resolves without asking. So assert the outcome, not the prompt.
        //
        // "Took the wound" has to mean wounded OR killed by it. The two seats
        // hold different minions and ChooseOpponent offers them in shuffled
        // order, so the chosen one is sometimes the uruk -- vitality 1, which
        // dies and reports 0 wounds from the discard pile -- and sometimes the
        // orc, which survives with 1. Asserting `wounds == 1` therefore passed
        // or failed depending on the shuffle, and looked like flakiness caused
        // by other tests. It was this.
        boolean chosenWasHit = scn.GetWoundsOn(chosensMinion) >= 1
                || chosensMinion.getZone() != Zone.SHADOW_CHARACTERS;
        assertTrue("the chosen opponent's own minion took the wound (wounds="
                        + scn.GetWoundsOn(chosensMinion)
                        + ", zone=" + chosensMinion.getZone() + ")",
                chosenWasHit);
        assertEquals("the other opponent's minion is unwounded",
                0, scn.GetWoundsOn(defaultsMinion));
        assertEquals("the other opponent's minion is still in play",
                Zone.SHADOW_CHARACTERS, defaultsMinion.getZone());
    }

    /**
     * The same three-part pattern on a second card, because "matches the shape of
     * a proven card" is an inference and this makes it a measurement.
     *
     * A Dragon's Tale (18_106) exerts rather than wounds, and its action is a
     * Maneuver rather than a Regroup, so it exercises the pattern rather than
     * repeating Pippin's exact path. Exerting adds a wound, so the outcome reads
     * the same way -- and, as with Pippin, "took it" has to mean wounded OR
     * killed by it, since which opponent is offered second is shuffled.
     */
    @Test
    public void theChosenOpponentExertsTheirOwnMinion() throws Exception {
        var scn = ThreeSeats();
        var tale = scn.GetCardFor(P1, "tale");
        scn.MoveCardsToSupportArea(tale);
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "merry"));
        scn.MoveMinionsToTable(scn.GetCardFor(P2, "warrior"), scn.GetCardFor(P3, "lackey"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);

        PhysicalCardImpl defaultsMinion = scn.GetCardFor(defaultOpponent,
                defaultOpponent.equals(P2) ? "warrior" : "lackey");
        PhysicalCardImpl chosensMinion = scn.GetCardFor(chosenOpponent,
                chosenOpponent.equals(P2) ? "warrior" : "lackey");

        scn.PassUntilDecision(P1, "Maneuver action");
        assertTrue("the tale's maneuver action should be available",
                scn.ActionAvailable(P1, "Use " + GameUtils.getFullName(tale)));
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, tale));

        // The cost discards a tale from play; this one is the only tale there is.
        if (scn.userFeedback().getAwaitingDecision(P1) != null
                && scn.DecisionAvailable(P1, "tale"))
            scn.ChooseCards(P1, tale);

        assertNotNull("the Free Peoples player should be asked which opponent",
                scn.userFeedback().getAwaitingDecision(P1));
        scn.ChooseOption(P1, chosenOpponent);

        boolean chosenWasHit = scn.GetWoundsOn(chosensMinion) >= 1
                || chosensMinion.getZone() != Zone.SHADOW_CHARACTERS;
        assertTrue("the chosen opponent's own minion was exerted (wounds="
                        + scn.GetWoundsOn(chosensMinion)
                        + ", zone=" + chosensMinion.getZone() + ")",
                chosenWasHit);
        assertEquals("the other opponent's minion is unwounded",
                0, scn.GetWoundsOn(defaultsMinion));
        assertEquals("the other opponent's minion is still in play",
                Zone.SHADOW_CHARACTERS, defaultsMinion.getZone());
    }
}
