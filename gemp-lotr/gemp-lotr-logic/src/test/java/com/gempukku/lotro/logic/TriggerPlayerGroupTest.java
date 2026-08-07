package com.gempukku.lotro.logic;

import com.gempukku.lotro.cards.build.DefaultActionContext;
import com.gempukku.lotro.cards.build.field.effect.DefaultActionSource;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A trigger's `player:` may name a GROUP, and the check is membership.
 *
 * `player:` on a trigger is neither a target nor a temporal gate: it designates
 * who may PERFORM the trigger's action. `DefaultActionSource.isValid` compares
 * that designation against the candidate performing player, and while the
 * designation was a single `PlayerSource` the comparison was `.equals` — so
 * exactly one seat could ever qualify and "each Shadow player may …" was
 * inexpressible. 13_193 Isenwash and 4_362 Orthanc Library both say it.
 *
 * Optional triggers needed nothing more than this, because the engine already
 * asks per player: `getOwnOptionalBeforeTriggers` and its After twin take a
 * playerId and build the action context around it, then call `isValid`. So the
 * loop already existed and only the test inside it was too narrow.
 *
 * This is a unit test rather than a game one on purpose. Both cards that
 * designate a group are SITES, and putting a chosen site under the fellowship
 * at three seats is an unbuilt fixture — the same one that blocks Denethor
 * (`10_28`) in todo.md. Rather than leave the engine change resting on "5,951
 * other tests still pass", which is regression evidence and not evidence the new
 * capability works, this drives the changed line directly.
 */
public class TriggerPlayerGroupTest {

    /** isValid only reads the performing player and the requirement list. */
    private static DefaultActionContext contextFor(String performingPlayer) {
        return new DefaultActionContext(performingPlayer, null, null, null, null);
    }

    @Test
    public void aGroupAdmitsEveryPlayerInIt() {
        DefaultActionSource source = new DefaultActionSource();
        source.setPlayingPlayers((ctx) -> Arrays.asList("shadowA", "shadowB"));

        assertTrue("the first designated player may perform it",
                source.isValid(contextFor("shadowA")));
        // The whole point. Before the change this was false, and the second
        // Shadow player was skipped without a word.
        assertTrue("the SECOND designated player may perform it too",
                source.isValid(contextFor("shadowB")));
    }

    @Test
    public void aGroupStillExcludesPlayersOutsideIt() {
        DefaultActionSource source = new DefaultActionSource();
        source.setPlayingPlayers((ctx) -> Arrays.asList("shadowA", "shadowB"));

        // Guards against the fix being "everyone may do everything", which
        // would satisfy the test above just as well.
        assertFalse("a player not in the group may not perform it",
                source.isValid(contextFor("freeps")));
    }

    /**
     * The single-player form is unchanged, which is what keeps every existing
     * card byte-identical: a one-player token wraps to a one-element list, so
     * membership and equality agree.
     */
    @Test
    public void aSinglePlayerIsStillExclusive() {
        DefaultActionSource source = new DefaultActionSource();
        source.setPlayingPlayer((ctx) -> "shadowA");

        assertTrue(source.isValid(contextFor("shadowA")));
        assertFalse("naming one seat must still exclude every other",
                source.isValid(contextFor("shadowB")));
    }

    @Test
    public void noDesignationAdmitsAnybody() {
        DefaultActionSource source = new DefaultActionSource();

        assertTrue("a trigger with no player: is not restricted",
                source.isValid(contextFor("anyone")));
    }

    /**
     * An empty group admits nobody rather than everybody.
     *
     * Not hypothetical: `anyShadow` resolves to an empty list in a solo game
     * (PlayerResolver returns Collections.emptyList() when game.isSolo()), and
     * "restricted to nobody" must not degrade into "unrestricted".
     */
    @Test
    public void anEmptyGroupAdmitsNobody() {
        DefaultActionSource source = new DefaultActionSource();
        source.setPlayingPlayers((ctx) -> Collections.<String>emptyList());

        assertFalse(source.isValid(contextFor("shadowA")));
    }

    /**
     * getPlayer() reports the first of the group.
     *
     * The REQUIRED trigger paths use it to rebind the action context to one
     * seat (BuiltLotroCardBlueprint ~830/~884/~1035), so it must keep returning
     * a single player. A required trigger naming a group therefore still fires
     * only for the first of them — no card needs otherwise today, since both
     * group-naming cards are optional, and fixing it means changing the
     * enumeration rather than this.
     */
    @Test
    public void getPlayerReportsTheFirstOfTheGroup() {
        DefaultActionSource source = new DefaultActionSource();
        source.setPlayingPlayers((ctx) -> Arrays.asList("shadowA", "shadowB"));

        List<String> seen = Collections.singletonList(
                source.getPlayer().getPlayer(contextFor("irrelevant")));
        assertTrue("required-trigger rebinding still gets one seat",
                seen.contains("shadowA"));
    }
}
