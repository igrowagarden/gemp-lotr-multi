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
 * A prevention offered to "THE Shadow player" when a specific opponent set the
 * trigger off -- so it belongs to that opponent, not to a fixed seat.
 *
 * Greatest Kingdom of My People, pc errata (51_16):
 *
 *   "The first time YOUR OPPONENT plays an Orc each turn, you may take a
 *    [dwarven] card into hand from your discard pile. THE Shadow player may
 *    discard the bottom 2 cards of their draw deck to prevent this."
 *
 * The trigger names one opponent and the definite article refers back to them,
 * which is the 2_17 / 51_36 shape recorded under the `one` bucket -- that
 * opponent, not the table. So the offer is `ownerFromMemory(playedOrc)`.
 *
 * WHY THIS CARD RATHER THAN THE OTHER TWO TWINS. It shipped HALF converted, and
 * that is a state the errata cross-check could not see: the prevention COST
 * already read `deck: ownerFromMemory(playedOrc)` while the OFFER still read
 * `player: shadow`. Above two players those name different seats, so the wrong
 * opponent was asked whether to spend the right opponent's draw deck. A check
 * that only asks "does the twin still say shadow" grades this identically to
 * the two twins that were untouched, and a check that only asks "does the cost
 * name the right player" passes it outright.
 *
 * It is also the exact inverse of the bug this branch shipped once already on
 * the prevention path -- there the offer was widened and the cost left on a
 * fixed seat; here the cost was fixed and the offer left behind. Both come from
 * changing one half of an offer/cost pair, which is why the cost is now written
 * as `deck: you`: PreventableEffectAppender delegates the cost's context to
 * whoever accepted, so the two cannot drift apart again.
 */
public class MultiplayerTriggeringOpponentPreventsAtTest {

    private static final String GREATEST_KINGDOM = "51_16";

    // race Orc AND culture Orc, twilight 2. Lurker is a skirmish-ordering
    // keyword, so it is inert -- the walk stops in the Shadow phase.
    private static final String ORC_SKULKER = "12_95";

    // A [dwarven] card to sit in the Free Peoples player's discard pile, so the
    // trigger's `chooseCardsFromDiscard` has something to find and the
    // prevention has something to prevent.
    private static final String DWARF_GUARD = "1_7";

    // The prevention costs "discard the bottom 2 cards of your draw deck", so
    // every seat needs a draw deck with at least two cards in it. Without
    // filler, MultiplayerTable's one-copy-per-named-card deck is far too thin
    // and PreventableEffectAppender SKIPS a player who cannot pay -- which
    // looks exactly like the wrong seat being asked.
    private static final String[] FILLER = {
            "1_133", "1_144", "1_151", "1_177", "4_165",
            "7_193", "1_143", "12_157", "1_294", "1_312",
    };

    private VirtualTableScenario ThreeSeats() throws Exception {
        return VirtualTableScenario.MultiplayerTable(3, new HashMap<>() {{
            put("kingdom", GREATEST_KINGDOM);
            put("orc", ORC_SKULKER);
            put("guard", DWARF_GUARD);
            for (int i = 0; i < FILLER.length; i++)
                put("filler" + i, FILLER[i]);
        }});
    }

    /** Opponents counter-clockwise from the Free Peoples player, in Shadow-phase order. */
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

    private static String Board(VirtualTableScenario scn) {
        StringBuilder sb = new StringBuilder();
        for (String p : new String[]{P1, P2, P3})
            sb.append(p).append(" deck=").append(scn.gameState().getDeck(p).size())
              .append(" hand=").append(scn.gameState().getHand(p).size())
              .append(" discard=").append(scn.gameState().getDiscard(p).size())
              .append("; ");
        return sb.toString();
    }

    /**
     * The measurement: the opponent who played the Orc is the one offered the
     * prevention, and the other opponent is never asked.
     */
    @Test
    public void theOpponentWhoPlayedTheOrcIsOfferedThePrevention() throws Exception {
        var scn = ThreeSeats();

        // Placed before the game starts -- putting cards into play mid-game does
        // not register them properly -- and at that point the seating is not yet
        // known, so BOTH opponents are armed with an Orc.
        scn.MoveCardsToSupportArea(scn.GetCardFor(P1, "kingdom"));
        scn.MoveCardsToHand(scn.GetCardFor(P2, "orc"), scn.GetCardFor(P3, "orc"));

        scn.StartMultiplayerGame();
        assertEquals("P1 should take the first turn", P1, scn.FreePeoplesPlayer());

        // The [dwarven] card goes into P1's discard after the start, so the deal
        // cannot move it back.
        scn.MoveCardsToDiscard(scn.GetCardFor(P1, "guard"));

        List<String> opponents = ShadowSeats(scn);
        String firstShadow = opponents.get(0);   // the seat `player: shadow` named
        String actingShadow = opponents.get(1);  // the seat that plays the Orc
        assertNotEquals(firstShadow, actingShadow);
        assertEquals("getFirstShadowPlayer should name the first opponent counter-clockwise",
                firstShadow, GameUtils.getFirstShadowPlayer(scn.game()));

        scn.SetTwilight(10);
        scn.PassUntilDecision(actingShadow, "Shadow action");

        var orc = scn.GetCardFor(actingShadow, "orc");
        String actionId = scn.GetCardActionId(actingShadow, orc);
        // GetCardActionId returns null when the action is not on offer, and
        // PlayerDecided(player, null) passes the phase rather than throwing --
        // so the trigger under test would silently never fire.
        assertNotNull("the Orc should be playable by " + actingShadow
                + " in their own Shadow phase (pending: " + Pending(scn)
                + ", board: " + Board(scn) + ")", actionId);
        scn.PlayerDecided(actingShadow, actionId);

        // Walk the trigger far enough to reach the prevention offer, recording
        // every decision. A trace rather than a fixed sequence, because WHO is
        // asked is the whole question and a rigid walk cannot tell a wrong seat
        // from a trigger that fizzled.
        StringBuilder trace = new StringBuilder();
        String offeredTo = null;
        for (int step = 0; step < 12; step++) {
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
            trace.append("<").append(who).append(" ").append(d.getDecisionType())
                 .append(": ").append(text);
            for (String k : new String[]{"actionId", "cardId", "min", "max"}) {
                String[] v = dp.get(k);
                if (v != null) trace.append(" ").append(k).append("=").append(v.length)
                                    .append(java.util.Arrays.toString(v));
            }
            trace.append("> ");

            // The prevention offer is the thing being measured. Stop the moment
            // it arrives -- who holds it IS the result.
            if (text.toLowerCase().contains("prevent")) {
                offeredTo = who;
                break;
            }

            // Accept the optional trigger, pass everything else, and take a
            // real card when asked to choose one.
            //
            // The trigger arrives as a CARD_ACTION_CHOICE, NOT the ACTION_CHOICE
            // this walk first guessed at -- so it fell through to "" and was
            // PASSED, and the symptom was "nobody is ever offered the
            // prevention", which reads exactly like the card being broken. The
            // decision type and its actionId list are printed into the trace
            // for that reason; one run with them showed the cause where two
            // rounds of reading the card had not.
            String answer;
            switch (d.getDecisionType()) {
                case CARD_ACTION_CHOICE -> {
                    String[] ids = dp.get("actionId");
                    boolean isTheTrigger = text.toLowerCase().contains("optional responses");
                    // Only the trigger is accepted. Taking whatever else is on
                    // offer would let the walk wander into other cards' actions
                    // and measure those instead.
                    answer = (isTheTrigger && ids != null && ids.length > 0) ? ids[0] : "";
                }
                case MULTIPLE_CHOICE -> answer = "0";
                case INTEGER -> answer = dp.containsKey("max") ? dp.get("max")[0] : "0";
                case CARD_SELECTION, ARBITRARY_CARDS -> {
                    // The discard-pile choice is `count: 0-1`, so passing it is
                    // legal and would leave {chosenCard} empty -- the prevention
                    // would then have nothing to prevent.
                    String[] ids = dp.get("cardId");
                    answer = (ids == null || ids.length == 0) ? "" : ids[0];
                }
                default -> answer = "";
            }
            try {
                scn.PlayerDecided(who, answer);
            } catch (Exception e) {
                trace.append("[!! ").append(e.getClass().getSimpleName())
                     .append(" answering ").append(d.getDecisionType())
                     .append(" with '").append(answer).append("'] ");
                break;
            }
        }

        String dump = "acting=" + actingShadow + " first=" + firstShadow
                + " | board: " + Board(scn) + " | trace: " + trace;

        assertNotNull("somebody should have been offered the prevention. " + dump, offeredTo);
        assertEquals("the prevention belongs to the opponent who played the Orc --"
                        + " \"the Shadow player\" refers back to the opponent the"
                        + " trigger already named. " + dump,
                actingShadow, offeredTo);
        assertNotEquals("the other opponent must not be the one asked;"
                        + " getFirstShadowPlayer does not decide this. " + dump,
                firstShadow, offeredTo);
    }
}
