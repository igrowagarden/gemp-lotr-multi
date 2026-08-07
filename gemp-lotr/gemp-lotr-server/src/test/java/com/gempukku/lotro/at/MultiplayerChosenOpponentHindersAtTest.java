package com.gempukku.lotro.at;

import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayOrder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.gempukku.lotro.framework.TestConstants.*;
import static org.junit.Assert.*;

/**
 * "Make AN OPPONENT hinder X Shadow cards" -- the chosen opponent does the
 * hindering, not the seat getFirstShadowPlayer names.
 *
 * Blood Runs Chill, pc errata (58_3):
 *
 *   "If the fellowship moves, spot a Dwarf who is damage +X and exert that
 *    Dwarf to make an opponent hinder X Shadow cards."
 *
 * The original 8_3 says the same sentence with `discard` in place of `hinder`
 * and got the choose-an-opponent treatment; its errata twin kept `player:
 * shadow` in a different file, which is how the twins keep getting missed.
 *
 * WHY THIS ONE IS WORTH DRIVING RATHER THAN MATCHING ON SHAPE. Hinder is an
 * appender nothing on this branch had measured, and HinderCardsInPlay's
 * `player:` does DOUBLE duty (HinderCardsInPlay.java:41,47): it is both the
 * player asked to choose the cards and the player credited with the hinder.
 * A token that named the wrong seat would therefore move the prompt as well as
 * the effect -- and this branch has already been caught out once by assuming
 * one appender behaves like another (103_38, whose trigger has neither the
 * controller as performing player nor the card as getSource().getOwner()).
 *
 * The selection is deliberately UNSCOPED. The printed text has no owner word,
 * so the chosen opponent may hinder any Shadow card in play including another
 * opponent's -- exactly the reading recorded on the original. That is why the
 * assertion here is about WHO IS ASKED rather than about what got hindered:
 * scoping is not what this card claims.
 */
public class MultiplayerChosenOpponentHindersAtTest {

    private static final String BLOOD_RUNS_CHILL = "58_3";

    // The cost is `exert choose(dwarf,damage,CanExert,MinVitality(2))` and the
    // count is one per `damage` keyword on the exerted Dwarf. Gimli is Damage+1
    // with vitality 3, so he qualifies and X is 1.
    private static final String GIMLI = "0_62";

    // Two hinderable Shadow cards per opponent, and a DIFFERENT pair per seat.
    // Two is the floor, not a luxury: with one apiece the hinder selection has
    // a single candidate, resolves without asking anybody, and a wrong-seat
    // control would pass in silence -- the most recurrent mistake in this
    // codebase.
    private static final String WARRIOR = "4_165";
    private static final String TROOP = "1_143";
    private static final String LACKEY = "7_193";
    private static final String URUK_TROOP = "12_157";

    private static String[] minionKeys(String seat) {
        return seat.equals(P2) ? new String[]{"warrior", "troop"}
                               : new String[]{"lackey", "urukTroop"};
    }

    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "1_294",
            "1_312", "1_157", "1_159", "1_173", "1_195",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("blood", BLOOD_RUNS_CHILL);
            put("gimli", GIMLI);
            put("warrior", WARRIOR);
            put("troop", TROOP);
            put("lackey", LACKEY);
            put("urukTroop", URUK_TROOP);
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

    private static String Pending(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new ArrayList<>(scn.userFeedback().getUsersPendingDecision())) {
            var d = scn.userFeedback().getAwaitingDecision(p);
            sb.append(p).append("=").append(d == null ? "(null)" : d.getText()).append("  ");
        }
        return sb.length() == 0 ? "(nobody)" : sb.toString();
    }

    /**
     * The chosen opponent is the one asked to hinder.
     *
     * The Free Peoples player deliberately picks the opponent that
     * getFirstShadowPlayer does NOT name, so "the token followed the choice"
     * and "the token fell back to the first seat" cannot produce the same
     * board.
     */
    @Test
    public void theChosenOpponentIsTheOneWhoHinders() throws Exception {
        var scn = ThreeSeats();

        scn.MoveCompanionsToTable(scn.GetCardFor(P1, "gimli"));
        scn.MoveCardsToHand(scn.GetCardFor(P1, "blood"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        for (String seat : new String[]{P2, P3})
            for (String key : minionKeys(seat))
                scn.MoveMinionsToTable(scn.GetCardFor(seat, key));

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);
        String chosenOpponent = opponents.get(1);
        assertNotEquals(firstShadow, chosenOpponent);
        assertEquals("getFirstShadowPlayer should name the first opponent counter-clockwise",
                firstShadow, GameUtils.getFirstShadowPlayer(scn.game()));

        scn.SetTwilight(10);

        // Blood Runs Chill is a RESPONSE to the fellowship moving, and the move
        // happens INSIDE regroup -- so PassUntilPhase(REGROUP) is already too
        // late, which is the phase-overshoot trap the harness notes warn about.
        // Rather than guess at the window's decision text, the walk simply asks
        // at every step whether the card has become playable, and plays it the
        // moment it has.
        var blood = scn.GetCardFor(P1, "blood");
        StringBuilder trace = new StringBuilder();
        String hinderAskedOf = null;
        boolean played = false;
        boolean sawOpponentChoice = false;

        for (int step = 0; step < 60; step++) {
            var waiting = new ArrayList<>(scn.userFeedback().getUsersPendingDecision());
            if (waiting.isEmpty()) {
                trace.append("[nobody waiting] ");
                break;
            }
            java.util.Collections.sort(waiting);
            String who = waiting.get(0);
            var d = scn.userFeedback().getAwaitingDecision(who);
            if (d == null) continue;
            String text = d.getText() == null ? "(null)" : d.getText();
            var dp = d.getDecisionParameters();

            // Log EVERY decision, with its type and parameter keys. The first
            // version of this walk logged only the steps it recognised, and the
            // trace then showed no opponent choice at all -- which could equally
            // have meant "the choice auto-resolved" or "the branch meant to
            // answer it never fired and something else did". One run with this
            // in place answered that; reasoning about it twice had not.
            trace.append("<").append(who).append(" ").append(d.getDecisionType())
                 .append(" keys=").append(new java.util.TreeSet<>(dp.keySet()))
                 .append(": ").append(text).append("> ");

            // The measurement. Stop the instant it arrives -- who holds this
            // decision IS the result.
            if (text.toLowerCase().contains("hinder")) {
                hinderAskedOf = who;
                trace.append("<").append(who).append(" HINDER: ").append(text).append("> ");
                break;
            }

            if (!played && who.equals(P1)) {
                String actionId = scn.GetCardActionId(P1, blood);
                if (actionId != null) {
                    trace.append("[playing blood at step ").append(step)
                         .append(" during '").append(text).append("'] ");
                    scn.PlayerDecided(P1, actionId);
                    played = true;
                    continue;
                }
            }

            // The opponent choice belongs to the Free Peoples player and is
            // answered by NAME, so the test controls which seat it picks.
            //
            // The options live under "results", NOT "choice". This branch was
            // written against "choice", never fired, and the walk answered the
            // opponent choice with "0" instead -- so the seat under test was
            // whichever one happened to be listed first, and the test passed
            // three times running on that coincidence. The sawOpponentChoice
            // guard below is what exposed it; without that guard this would
            // have shipped as a measurement.
            if (d.getDecisionType() == com.gempukku.lotro.logic.decisions.AwaitingDecisionType.MULTIPLE_CHOICE
                    && dp.get("results") != null
                    && java.util.Arrays.asList(dp.get("results")).contains(chosenOpponent)) {
                trace.append("[CHOOSE-OPPONENT -> ").append(chosenOpponent).append("] ");
                sawOpponentChoice = true;
                scn.ChooseOption(who, chosenOpponent);
                continue;
            }

            String answer;
            switch (d.getDecisionType()) {
                case MULTIPLE_CHOICE -> answer = "0";
                case INTEGER -> answer = dp.containsKey("min") ? dp.get("min")[0] : "0";
                default -> answer = "";
            }
            try {
                scn.PlayerDecided(who, answer);
            } catch (Exception e) {
                trace.append("[!! ").append(e.getClass().getSimpleName())
                     .append(" answering ").append(d.getDecisionType())
                     .append(" with '").append(answer).append("' for ").append(who)
                     .append(": ").append(text).append("] ");
                break;
            }
        }

        String dump = "chosen=" + chosenOpponent + " first=" + firstShadow
                + " played=" + played + " | pending: " + Pending(scn)
                + " | trace: " + trace;

        assertTrue("Blood Runs Chill never became playable, so nothing was measured."
                + " " + dump, played);
        // Guards the assertion against being satisfied by an opponent choice
        // this walk made arbitrarily. If the choice was never offered, then
        // `chosenOpponent` is the test's opinion rather than the game's, and
        // "the chosen opponent hinders" is vacuous.
        assertTrue("the Free Peoples player was never actually asked which opponent,"
                + " so nothing chose anything and the assertion below is blind."
                + " " + dump, sawOpponentChoice);
        assertNotNull("somebody should have been asked to hinder. " + dump, hinderAskedOf);
        assertEquals("the opponent the Free Peoples player CHOSE is the one who hinders."
                        + " " + dump,
                chosenOpponent, hinderAskedOf);
        assertNotEquals("the first Shadow seat must not be the one asked. " + dump,
                firstShadow, hinderAskedOf);
    }
}
