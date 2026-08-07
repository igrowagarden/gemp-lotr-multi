package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.framework.VirtualTableScenario;
import org.junit.Test;

import java.util.HashMap;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Remove his player marker and all of his cards from play (and discard any
 * opponent's cards that were on them)."
 *
 * RULED, from the multiplayer rules text. Before this, an eliminated player
 * left the rotation and kept watching while everything they owned stayed on the
 * table -- minions still assignable, conditions still imposing their effects,
 * and companions still satisfying other players' spot requirements, because
 * spotting in GEMP is global rather than per-fellowship.
 *
 * THE TWO VERBS ARE DIFFERENT AND THE RULE MEANS THEM DIFFERENTLY, which is
 * most of what this test pins:
 *
 *   his own cards            -> REMOVED. He is out; nothing should be able to
 *                              retrieve them from a discard pile afterwards.
 *   an opponent's card ON one -> DISCARD, into that owner's own pile, where
 *                              their remaining cards can still reach it.
 *
 * ONLY WITH AT LEAST TWO OTHERS LEFT. With one other player the game is already
 * over by the last-player-standing rule, and playerLost takes the game-won
 * branch instead -- so none of this is reachable at two seats, which is why no
 * existing two-player card test can be affected by it.
 */
public class MultiplayerEliminationCleanupAtTest {

    // A companion for the player who will be eliminated, and a possession an
    // OPPONENT will own but which sits on that companion.
    private static final String GIMLI = "0_62";
    private static final String DWARF_GUARD = "1_7";
    // Shadow-side support-area condition, to prove support cards go too and not
    // just characters.
    private static final String CONDITION = "1_133";
    private static final String WARRIOR = "4_165";

    private static final String[] FILLER = {
            "1_144", "1_151", "1_177", "1_294", "1_312",
            "1_157", "1_159", "1_173", "1_195", "1_198",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("gimli", GIMLI);
            put("guard", DWARF_GUARD);
            put("condition", CONDITION);
            put("warrior", WARRIOR);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
        }});
    }

    /**
     * Everything the losing player had in play leaves; an opponent's card that
     * was sitting on one of them goes to that opponent's discard; and the other
     * players' own cards are untouched.
     */
    @Test
    public void theLosersCardsAreRemovedAndOpponentsCardsOnThemAreDiscarded() throws Exception {
        var scn = ThreeSeats();

        var gimli = scn.GetCardFor(P1, "gimli");
        scn.MoveCompanionsToTable(gimli);
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        // P1's own support-area condition.
        var p1Condition = scn.GetCardFor(P1, "condition");
        scn.MoveCardsToSupportArea(p1Condition);

        // An OPPONENT's possession, sitting on P1's companion. This is the
        // "opponent's cards that were on them" case.
        var opponentsGuard = scn.GetCardFor(P2, "guard");
        scn.AttachCardsTo(gimli, opponentsGuard);

        // A card belonging to an uninvolved opponent, to prove the sweep is
        // scoped rather than clearing the table.
        var bystander = scn.GetCardFor(P3, "warrior");
        scn.MoveMinionsToTable(bystander);

        // Fixture guards: if any of this is not actually in play, every
        // assertion below would hold for reasons that have nothing to do with
        // the rule.
        assertTrue("fixture: Gimli should be in play", gimli.getZone().isInPlay());
        assertTrue("fixture: P1's condition should be in play", p1Condition.getZone().isInPlay());
        assertEquals("fixture: the opponent's guard should be attached to Gimli",
                gimli, opponentsGuard.getAttachedTo());
        assertTrue("fixture: the bystander's minion should be in play",
                bystander.getZone().isInPlay());

        scn.game().playerLost(P1, "test-forced elimination");

        String dump = " | gimli=" + gimli.getZone()
                + " p1Condition=" + p1Condition.getZone()
                + " opponentsGuard=" + opponentsGuard.getZone()
                + " bystander=" + bystander.getZone();

        assertEquals("the loser's companion is REMOVED, not discarded -- he is out"
                        + " of the game and nothing should retrieve it later." + dump,
                Zone.REMOVED, gimli.getZone());
        assertEquals("the loser's support-area condition goes too, not just his"
                        + " characters." + dump,
                Zone.REMOVED, p1Condition.getZone());

        assertEquals("an opponent's card that was ON one of his is DISCARDED, not"
                        + " removed -- the rule says discard, and it belongs to"
                        + " somebody still playing." + dump,
                Zone.DISCARD, opponentsGuard.getZone());
        assertTrue("and it lands in ITS OWN owner's discard pile." + dump,
                scn.gameState().getDiscard(P2).contains(opponentsGuard));

        assertTrue("a card belonging to an uninvolved opponent is untouched --"
                        + " this removes one player's cards, not the table." + dump,
                bystander.getZone().isInPlay());
    }

    /**
     * The guard on the whole feature: with only one other player left the game
     * is over, and none of the cleanup should run.
     *
     * Without this, a sweep that fired at two seats would go unnoticed until it
     * broke a card test for reasons nobody could trace.
     */
    @Test
    public void nothingIsRemovedWhenTheLossEndsTheGame() throws Exception {
        var scn = ThreeSeats();

        var gimli = scn.GetCardFor(P1, "gimli");
        scn.MoveCompanionsToTable(gimli);
        scn.StartMultiplayerGame();

        // Two of the three are out, so the third has won and the game ends.
        scn.game().playerLost(P2, "test-forced elimination");
        scn.game().playerLost(P3, "test-forced elimination");

        assertTrue("P1's card should still be in play -- the game ended on the"
                        + " second loss and the cleanup must not have run."
                        + " gimli=" + gimli.getZone(),
                gimli.getZone().isInPlay());
    }
}
