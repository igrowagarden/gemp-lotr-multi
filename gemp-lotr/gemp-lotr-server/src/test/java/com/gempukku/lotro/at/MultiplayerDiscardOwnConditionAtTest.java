package com.gempukku.lotro.at;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Choose an opponent who must discard one of HIS OR HER conditions."
 *
 * Introspection (12_29) said {discard, player: shadow,
 * select: choose(side(shadow),condition)}. `side(shadow)` looks like scoping and
 * is not: at five players every opponent's conditions are shadow-side, so it
 * means any of them.
 *
 * This card is worth its own test rather than trusting the pattern, because
 * fixing it changed control flow rather than field values. Its discard sat
 * inside a `choice` whose texts map to effects positionally, so the discard had
 * to move out to a HasMemory-guarded block after the choice -- the structure
 * Crashed Gate (8_119) uses. Crashed Gate is also the card that shipped
 * half-wired for years, memorising an opponent and then ignoring the answer, so
 * "it matches 8_119" is not evidence of anything on its own.
 *
 * Fixture rules that apply to every test in this group, each learned from a
 * failing run that looked exactly like a broken scoping filter: give each seat a
 * DIFFERENT card, and assert the OUTCOME rather than a prompt, since a scoped
 * selection with one candidate resolves without asking. The offer order is
 * shuffled, so assertions must hold whichever opponent is picked.
 */
public class MultiplayerDiscardOwnConditionAtTest {

    private static final String INTROSPECTION = "12_29";
    private static final String GANDALF = "1_72";           // [gandalf] Wizard
    // Two different Shadow conditions, one per opponent. The same card on both
    // seats would be unique-discarded down to one, leaving an opponent with
    // nothing to discard and a correct effect finding no target.
    private static final String URUK_BLOODLUST = "1_144";
    private static final String SARUMANS_AMBITION = "1_133";

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("introspection", INTROSPECTION);
            put("gandalf", GANDALF);
            put("bloodlust", URUK_BLOODLUST);
            put("ambition", SARUMANS_AMBITION);
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

    @Test
    public void theChosenOpponentDiscardsTheirOwnCondition() throws Exception {
        var scn = ThreeSeats();
        var introspection = scn.GetCardFor(P1, "introspection");
        scn.MoveCardsToHand(introspection);
        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "gandalf"));
        scn.MoveCardsToSupportArea(scn.GetCardFor(P2, "bloodlust"),
                scn.GetCardFor(P3, "ambition"));

        scn.StartMultiplayerGame();
        List<String> opponents = ShadowSeats(scn);
        String defaultOpponent = opponents.get(0);
        String chosenOpponent = opponents.get(1);

        PhysicalCardImpl defaultsCondition = scn.GetCardFor(defaultOpponent,
                defaultOpponent.equals(P2) ? "bloodlust" : "ambition");
        PhysicalCardImpl chosensCondition = scn.GetCardFor(chosenOpponent,
                chosenOpponent.equals(P2) ? "bloodlust" : "ambition");

        scn.PassUntilDecision(P1, "Fellowship action");
        scn.PlayerDecided(P1, scn.GetCardActionId(P1, introspection));

        // The card's "Choose one" never appears here: its other half needs a
        // battleground site, so with only one option playable the choice
        // resolves itself and we land straight on "Choose an opponent".
        assertTrue("the Free Peoples player should be asked which opponent",
                scn.DecisionAvailable(P1, "Choose an opponent"));
        scn.ChooseOption(P1, chosenOpponent);

        assertEquals("the chosen opponent's own condition was discarded",
                Zone.DISCARD, chosensCondition.getZone());
        assertEquals("the other opponent's condition is untouched",
                Zone.SUPPORT, defaultsCondition.getZone());
    }
}
