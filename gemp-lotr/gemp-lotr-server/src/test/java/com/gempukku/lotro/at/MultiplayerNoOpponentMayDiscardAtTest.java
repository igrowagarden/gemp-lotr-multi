package com.gempukku.lotro.at;

import com.gempukku.lotro.framework.VirtualTableScenario;
import org.junit.Test;

import java.util.HashMap;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "YOUR OPPONENT may not discard your [shire] tales" -- no opponent may, and
 * you still may.
 *
 * Pippin (1_306) carried `player: shadow` on a `cantBeDiscarded` modifier, so
 * above two players it protected the tales from ONE seat and left every other
 * opponent free to discard them.
 *
 * TWO WRONG ANSWERS ARE BOTH GUARDED HERE, which is the reason this test exists
 * rather than a card-shape match:
 *
 *   1. `player: shadow` -- the shipped bug. Only one opponent is stopped.
 *   2. DELETING the token -- which is what PLAN.md recorded as the fix, and it
 *      over-applies. CantDiscardFromPlayModifier skips its player check
 *      entirely when bannedPlayer is null, so a missing token bans EVERYBODY
 *      including the card's own controller. Pippin would then stop you
 *      discarding your own tales.
 *
 * The second is the one no fixture built around opponents alone would catch,
 * so the owner assertion is not decoration -- it is half the measurement.
 *
 * WHY IT ASKS THE MODIFIER DIRECTLY rather than driving a card that discards.
 * canBeDiscardedFromPlay is the choke point every discard-from-play goes
 * through (ModifiersLogic.java:974), so querying it covers every route at once,
 * the same argument MultiplayerCantRevealAnyHandAtTest makes for
 * canLookOrRevealCardsInHand. Driving one discarding card would measure that
 * card as much as this one.
 *
 * `opponentsOfOwner` rather than `anyShadow` is the other half of the fix, and
 * it matters precisely here: a tale can be discarded on ANY player's turn, and
 * anyShadow is relative to getCurrentPlayerId(). On an opponent's turn it would
 * name Pippin's own controller among the banned and leave the current player
 * free. The third case below pins that by rotating the turn.
 */
public class MultiplayerNoOpponentMayDiscardAtTest {

    private static final String PIPPIN = "1_306";
    // Tale, and a possession so it sits in play. "Bearer must be a Hobbit
    // companion", which Pippin is.
    private static final String THERE_AND_BACK_AGAIN = "1_317";

    // Any card at all, to stand as the SOURCE of the hypothetical discard.
    // CantBeDiscarded's `by:` defaults to `any`, so what it is does not matter
    // -- only that it is not null, since the filter is asked to accept it.
    private static final String WARRIOR = "4_165";

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("pippin", PIPPIN);
            put("tale", THERE_AND_BACK_AGAIN);
            put("warrior", WARRIOR);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
        }});
    }

    /**
     * Every opponent is stopped, and the owner is not.
     */
    @Test
    public void noOpponentMayDiscardTheTaleButTheOwnerMay() throws Exception {
        var scn = ThreeSeats();

        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "pippin"));
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        var pippin = scn.GetCardFor(P1, "pippin");
        var tale = scn.GetCardFor(P1, "tale");
        scn.AttachCardsTo(pippin, tale);
        var source = scn.GetCardFor(P2, "warrior");
        scn.MoveMinionsToTable(source);

        // Guard against a vacuous fixture: if the tale is not actually in play
        // and matching `your,culture(shire),tale`, every assertion below would
        // hold for reasons that have nothing to do with the card.
        assertEquals("fixture: the tale should be attached and in play",
                pippin, tale.getAttachedTo());

        var modifiers = scn.game().getModifiersQuerying();

        for (String opponent : new String[]{P2, P3})
            assertFalse("no opponent may discard the tale -- " + opponent
                            + " was allowed to, so the prohibition is naming"
                            + " one seat rather than all of them",
                    modifiers.canBeDiscardedFromPlay(scn.game(), opponent, tale, source));

        assertTrue("the card's OWN controller may still discard their own tale."
                        + " If this fails the player token has been dropped rather"
                        + " than widened -- a null bannedPlayer bans everybody.",
                modifiers.canBeDiscardedFromPlay(scn.game(), P1, tale, source));
    }

    /**
     * The same card ENTERING PLAY on somebody else's turn.
     *
     * This is what separates `opponentsOfOwner` from `anyShadow`, and getting
     * the fixture to show it took one wrong prediction and one passing control.
     *
     * IN-PLAY MODIFIERS ARE FROZEN WHEN THE CARD ENTERS PLAY.
     * PhysicalCardImpl.startAffectingGame calls getInPlayModifiers ONCE and
     * registers the resulting Modifier objects until the card leaves play
     * (PhysicalCardImpl.java:108-115). The `player:` token is resolved at that
     * moment and never re-read. So merely handing the turn over AFTER the card
     * is down changes nothing at all -- anyShadow keeps whatever list it froze.
     *
     * That is why the first version of this test could not tell the two tokens
     * apart, and the `pippin-any-shadow` control passed 3/3 where it had been
     * predicted to fail. The control was right and the prediction was wrong;
     * the difference is real but only observable if the card enters play while
     * somebody else is the current player.
     *
     * So the turn is handed over FIRST and Pippin put down SECOND. anyShadow
     * then freezes "the opponents of P2" -- which contains the tale's own owner
     * and omits P2 -- and the assertions below invert.
     */
    @Test
    public void theProhibitionIsCorrectWhenTheCardEntersPlayOnAnotherPlayersTurn() throws Exception {
        var scn = ThreeSeats();

        scn.StartMultiplayerGame();

        String owner = P1;
        var source = scn.GetCardFor(P2, "warrior");
        scn.MoveMinionsToTable(source);

        // Hand the turn over BEFORE the card enters play. realTurn: false so
        // the turn counter is untouched; only who is current matters.
        scn.gameState().startPlayerTurn(P2, false);
        String current = scn.gameState().getCurrentPlayerId();
        assertNotEquals("fixture: the turn never left the tale's owner, so this"
                        + " test is identical to the one above and proves nothing"
                        + " extra",
                owner, current);

        // NOW the card enters play, and startAffectingGame resolves `player:`
        // against a game whose current player is NOT the card's owner.
        var pippin = scn.GetCardFor(P1, "pippin");
        var tale = scn.GetCardFor(P1, "tale");
        scn.MoveCompanionsToTable(pippin);
        scn.AttachCardsTo(pippin, tale);

        assertEquals("fixture: the tale must be in play, or every assertion"
                        + " below is vacuous",
                pippin, tale.getAttachedTo());

        var modifiers = scn.game().getModifiersQuerying();
        String dump = " (current player when the card entered play was " + current
                + ", tale owner is " + owner + ")";

        for (String opponent : new String[]{P2, P3})
            assertFalse("no opponent may discard the tale, whoever happened to be"
                            + " the current player when it came down -- " + opponent
                            + " was allowed to." + dump,
                    modifiers.canBeDiscardedFromPlay(scn.game(), opponent, tale, source));

        assertTrue("the owner may still discard their own tale. A turn-relative"
                        + " token freezes the wrong list here and nowhere else."
                        + dump,
                modifiers.canBeDiscardedFromPlay(scn.game(), owner, tale, source));
    }
}
