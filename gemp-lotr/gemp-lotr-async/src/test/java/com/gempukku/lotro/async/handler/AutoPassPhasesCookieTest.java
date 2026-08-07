package com.gempukku.lotro.async.handler;

import com.gempukku.lotro.common.Phase;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * The `autoPassPhases` cookie must not be able to take a game down.
 *
 * It is user-controlled input that outlives the code that wrote it: it is set
 * with a one-year expiry, it survives client upgrades, and it can be edited by
 * hand from the browser console. Before this, parsing it was
 *
 *     for (String phase : value.split("0")) result.add(Phase.valueOf(phase));
 *
 * which throws on anything it does not recognise -- and the throw escapes into
 * the request handler, so the response is HTTP 500 on the GET handshake AND on
 * every subsequent poll, for as long as the cookie survives. A player in that
 * state cannot load any game until they clear it, and the client gives them no
 * way to do so.
 *
 * THE EMPTY VALUE IS NOT HYPOTHETICAL. The settings panel builds the value by
 * concatenation and only strips the leading separator when something was
 * ticked, so unticking all seven checkboxes writes exactly "". That path was
 * unreachable only because a second defect stopped the cookie ever reaching the
 * server -- fixing that one alone would have turned a silent no-op into a hard
 * failure for every player who turned auto-pass off.
 */
public class AutoPassPhasesCookieTest {

    /**
     * The case that 500s a live game: every checkbox unticked.
     *
     * An empty result is the CORRECT answer rather than a fallback -- the set is
     * the phases to auto-pass, so no entries means auto-pass nothing, which is
     * what the player just asked for. Returning the server default here would
     * silently switch the setting back on.
     */
    @Test
    public void anEmptyCookieMeansAutoPassNothing() {
        Set<Phase> phases = GameRequestHandler.parseAutoPassPhases("");
        assertNotNull(phases);
        assertTrue("an empty cookie must yield an empty set, not throw and not"
                + " fall back to a default", phases.isEmpty());
    }

    /** The ordinary case, so the tolerance above cannot be hiding a parser that stopped working. */
    @Test
    public void aNormalCookieParsesEveryPhase() {
        Set<Phase> phases = GameRequestHandler.parseAutoPassPhases(
                "FELLOWSHIP0MANEUVER0ARCHERY0ASSIGNMENT0REGROUP");
        assertEquals(5, phases.size());
        assertTrue(phases.contains(Phase.FELLOWSHIP));
        assertTrue(phases.contains(Phase.MANEUVER));
        assertTrue(phases.contains(Phase.ARCHERY));
        assertTrue(phases.contains(Phase.ASSIGNMENT));
        assertTrue(phases.contains(Phase.REGROUP));
        assertFalse("a phase that was not in the cookie must not appear",
                phases.contains(Phase.SHADOW));
    }

    /**
     * A cookie written by a different build of the client.
     *
     * Skipped rather than rejected, so the phases both versions understand still
     * work. Rejecting the whole cookie would be defensible; throwing is not.
     */
    @Test
    public void anUnknownPhaseNameIsIgnoredAndTheRestSurvives() {
        Set<Phase> phases = GameRequestHandler.parseAutoPassPhases(
                "FELLOWSHIP0NOTAPHASE0REGROUP");
        assertEquals(2, phases.size());
        assertTrue(phases.contains(Phase.FELLOWSHIP));
        assertTrue(phases.contains(Phase.REGROUP));
    }

    /**
     * Stray separators, which hand-editing produces easily -- and which the old
     * code turned into Phase.valueOf("") just as surely as the empty cookie did.
     */
    @Test
    public void straySeparatorsAreIgnored() {
        assertEquals(Set.of(Phase.FELLOWSHIP, Phase.REGROUP),
                GameRequestHandler.parseAutoPassPhases("0FELLOWSHIP00REGROUP0"));
        assertTrue(GameRequestHandler.parseAutoPassPhases("000").isEmpty());
    }

    /** Nothing here may throw, whatever it is handed. */
    @Test
    public void nothingThrowsOnAnyInput() {
        for (String value : new String[]{"", "0", "000", "NOTAPHASE", "0NOTAPHASE0",
                "fellowship", "FELLOWSHIP0", " ", "!@#$%", null}) {
            try {
                assertNotNull(GameRequestHandler.parseAutoPassPhases(value));
            } catch (RuntimeException e) {
                fail("parsing " + (value == null ? "null" : "\"" + value + "\"")
                        + " threw " + e.getClass().getSimpleName()
                        + " -- this reaches the player as HTTP 500 on every game request");
            }
        }
    }

    /**
     * Lower case does NOT parse, and that is recorded rather than fixed.
     *
     * Phase.valueOf is case-sensitive and the client only ever writes upper
     * case, so accepting lower case would invent a format nothing produces. It
     * is pinned here so the behaviour is a decision rather than an accident: a
     * lower-case cookie now degrades to "auto-pass nothing" instead of 500ing.
     */
    @Test
    public void lowerCaseIsNotAccepted() {
        assertTrue(GameRequestHandler.parseAutoPassPhases("fellowship").isEmpty());
    }
}
