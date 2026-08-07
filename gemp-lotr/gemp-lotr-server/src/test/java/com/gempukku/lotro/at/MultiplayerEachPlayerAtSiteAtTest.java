package com.gempukku.lotro.at;

import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.PhysicalCardImpl;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Each player …s each of his or her characters" -- on a SITE.
 *
 * Doorway to Doom (18_134) and Mithlond (18_136) both say "each player", and
 * both were written as a Free Peoples half plus a Shadow half:
 *
 *     { wound, player: free people, select: choose(side(free people),character) }
 *     { wound, player: shadow,      select: choose(side(shadow),character) }
 *
 * which is the same sentence only while there are exactly two players. Above
 * two, the Shadow half fired once for whichever seat getFirstShadowPlayer named
 * and every other opponent was skipped -- and `side(shadow)` is not scoping, so
 * the one seat that did fire chose from every opponent's characters at once.
 *
 * Both are now `forEachPlayer player: eachPlayer` with `choose(your,character)`.
 *
 * THE FIXTURE IS THE POINT OF THIS FILE. Nothing on this branch had ever put a
 * named site under the fellowship at more than two seats, which is what blocked
 * these two cards and Denethor. The recipe, measured rather than reasoned:
 *
 *   1. Put the site in the SITES map at some slot, not the cards map. Every
 *      seat's adventure deck then holds a copy and it gets a test alias.
 *   2. MoveCardToAdventurePath() it after the game starts -- that sets its site
 *      number to current+1 and drops it on the path.
 *   3. PassUntilSite() to walk there. DefaultAdventure.appendNextSiteAction
 *      only plays a site when getSite(n) is null, so a promoted site is simply
 *      used.
 *
 * Step 2 is not optional and step 1 alone does not work: these are Shadows-block
 * sites with no printed site number, so PlaySiteEffect's matcher finds nothing
 * for slot 2, plays nothing, and the game walks on to a site number with no site
 * at it -- which surfaces as a NullPointerException out of startAffectingGame.
 */
public class MultiplayerEachPlayerAtSiteAtTest {

    private static final String DOORWAY = "18_134";     // wounds two each
    private static final String MITHLOND = "18_136";    // heals each

    private static final String GANDALF = "6_30";       // vitality 4
    private static final String KNIGHT = "5_35";        // vitality 2
    // A different minion per seat: the same unique card on two seats is
    // discarded down to one, and then a seat has nothing of its own to hit.
    private static final String WARRIOR = "4_165";      // vitality 2
    private static final String TROOP = "1_143";        // vitality 4
    private static final String LACKEY = "7_193";       // vitality 2
    private static final String URUK_TROOP = "12_157";  // vitality 4

    private static String[] minionKeys(String seat) {
        return seat.equals(P2) ? new String[]{"warrior", "troop"}
                               : new String[]{"lackey", "urukTroop"};
    }

    private static HashMap<String, String> SitesWith(String siteId) {
        var sites = new HashMap<>(VirtualTableScenario.KingSites);
        sites.put("site2", siteId);
        return sites;
    }

    /** Three seats, characters on the table, and `siteId` waiting as site 2. */
    private VirtualTableScenario TableWalkingOnto(String siteId) throws Exception {
        var scn = new VirtualTableScenario(3, new HashMap<>() {{
            put("gandalf", GANDALF);
            put("knight", KNIGHT);
            put("warrior", WARRIOR);
            put("troop", TROOP);
            put("lackey", LACKEY);
            put("urukTroop", URUK_TROOP);
        }}, SitesWith(siteId), null, null, VirtualTableScenario.Multipath);

        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "gandalf"), scn.GetCardFor(P1, "knight"));
        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        for (String seat : new String[]{P2, P3})
            for (String key : minionKeys(seat))
                scn.MoveMinionsToTable(scn.GetCardFor(seat, key));

        scn.MoveCardToAdventurePath(scn.GetCardFor(P1, "site2"));
        assertEquals("the promoted card should be sitting at site 2", siteId,
                scn.game().getGameState().getSite(2).getBlueprintId());
        return scn;
    }

    private static String Pending(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            sb.append(p).append("=").append(d == null ? "(null)" : d.getText()).append("  ");
        }
        return sb.length() == 0 ? "(nobody)" : sb.toString();
    }

    /**
     * Every seat wounds two of its OWN characters.
     *
     * The Free Peoples player is the only seat that gets a prompt, and that is
     * not a weakness of the fixture -- each opponent holds exactly two eligible
     * characters, so a correctly scoped selection resolves without asking. The
     * prompt the FP player DOES get is what proves the scoping; the opponents'
     * wounds are what prove the loop ran for all of them.
     */
    @Test
    public void everySeatWoundsTwoOfItsOwnCharacters() throws Exception {
        var scn = TableWalkingOnto(DOORWAY);
        var gandalf = scn.GetCardFor(P1, "gandalf");
        var knight = scn.GetCardFor(P1, "knight");

        scn.PassUntilSite(2);

        assertTrue("the site's trigger should be asking the Free Peoples player;"
                        + " pending: " + Pending(scn),
                scn.DecisionAvailable(P1, "Choose cards to wound"));

        // Kills the unscoped control: "two of HIS OR HER characters".
        assertTrue("the Free Peoples player should be offered their own companion",
                scn.HasCardChoiceAvailable(P1, knight));
        for (String seat : new String[]{P2, P3})
            for (String key : minionKeys(seat))
                assertFalse("the Free Peoples player must NOT be offered " + seat
                                + "'s " + key,
                        scn.HasCardChoiceAvailable(P1, scn.GetCardFor(seat, key)));

        int gandalfBefore = scn.GetWoundsOn(gandalf);
        int knightBefore = scn.GetWoundsOn(knight);
        scn.ChooseCards(P1, gandalf, knight);

        assertEquals("the Free Peoples player wounded their own two",
                gandalfBefore + 1, scn.GetWoundsOn(gandalf));
        assertEquals(knightBefore + 1, scn.GetWoundsOn(knight));

        // Kills the two-halves control: the SECOND opponent is reached too.
        for (String seat : new String[]{P2, P3})
            for (String key : minionKeys(seat))
                assertEquals(seat + "'s " + key + " should have taken exactly one"
                                + " wound from the site", 1,
                        scn.GetWoundsOn(scn.GetCardFor(seat, key)));
    }

    /**
     * The mirror, on the other card: every seat heals its own.
     *
     * THIS TEST PINS BEHAVIOUR; IT DOES NOT PROVE A FIX, and the control sweep
     * is what said so -- it stayed green under BOTH controls while the wound
     * test went red under both. That is the "a control that passes is telling
     * you the assertion is blind" rule, and here the assertion is blind for a
     * real reason rather than a fixable one:
     *
     *   heal player: shadow, select: all(side(shadow),character)
     *
     * `side(shadow)` is not scoping. At three seats it matches EVERY opponent's
     * characters, so the single seat the old Shadow half fired for healed all of
     * them anyway, and the FP half did the same to every Free Peoples character
     * on the table. The two forms therefore produce an identical board.
     *
     * The conversion is still right -- "each player heals each of HIS OR HER
     * characters" is a loop, and `player:` decides who is credited with the heal,
     * which matters to anything that triggers off it -- but the difference is not
     * observable in wounds, which is all this fixture can see. Wounds are applied
     * first so that "healed" is at least a change rather than a state that was
     * already true.
     *
     * 18_134 above does not have this problem: its `choose(...)` has to pick two
     * from the candidate set, so the scoping shows up in the option list.
     */
    @Test
    public void everySeatHealsItsOwnCharacters() throws Exception {
        var scn = TableWalkingOnto(MITHLOND);

        // Wound BEFORE the walk, and do not try to be cleverer than that.
        // PassUntilPhase(REGROUP) looks like the tighter place to do it and is
        // not: it lands at site TWO, because the move happens inside regroup, so
        // the trigger has already fired and the wounds go on afterwards. That is
        // the phase-overshoot trap the harness notes warn about, and it reads
        // exactly like the heal doing nothing.
        //
        // Gandalf is deliberately NOT in this set. Reaching site 2 traverses a
        // whole turn and a strength-10 companion gets pulled into a skirmish, so
        // he arrives re-wounded whatever the site did. Measured, not assumed:
        // with him included the assertion failed on him alone while every other
        // character healed.
        var wounded = new ArrayList<PhysicalCardImpl>();
        wounded.add(scn.GetCardFor(P1, "knight"));
        for (String seat : new String[]{P2, P3})
            for (String key : minionKeys(seat))
                wounded.add(scn.GetCardFor(seat, key));
        for (PhysicalCardImpl card : wounded)
            scn.AddWoundsToChar(card, 1);
        for (PhysicalCardImpl card : wounded)
            assertEquals("fixture: " + card.getBlueprint().getTitle()
                    + " starts wounded", 1, scn.GetWoundsOn(card));

        scn.PassUntilSite(2);

        // "each of his or her characters" -- no choice anywhere, so this is
        // purely an outcome assertion, and the opponents are the point.
        for (PhysicalCardImpl card : wounded)
            assertEquals(card.getBlueprint().getTitle() + " (" + card.getOwner()
                            + ") should have been healed by the site; pending: "
                            + Pending(scn),
                    0, scn.GetWoundsOn(card));
    }
}
