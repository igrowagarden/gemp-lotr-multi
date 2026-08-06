package com.gempukku.lotro.game;

import com.gempukku.lotro.game.state.GameEvent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Replay metadata is parsed out of the chat messages a game emits, and those
 * messages list every player. The parser used to take exactly two names, so a
 * five-player game left the player list empty and then threw
 * NoSuchElementException the moment the game finished -- the failure surfaced
 * as an HTTP 500 on the last decision of the game.
 *
 * The two-player cases are the control: they must keep behaving exactly as they
 * did, including inferring who went first from the only player who announced.
 */
public class ReplayMetadataTest {

    private static List<GameEvent> messages(String... texts) {
        List<GameEvent> events = new ArrayList<>();
        for (String text : texts)
            events.add(new GameEvent(GameEvent.Type.SEND_MESSAGE).message(text));
        return events;
    }

    // -------- two players: unchanged behaviour --------

    @Test
    public void twoPlayersSeatedInMessageOrder() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages("Players in the game are: asdf, qwer"));

        assertEquals(2, metadata.PlayerIDs.size());
        assertEquals(Integer.valueOf(1), metadata.PlayerIDs.get("asdf"));
        assertEquals(Integer.valueOf(2), metadata.PlayerIDs.get("qwer"));
    }

    /**
     * Only the player who chooses announces; the last one is seated silently.
     * At two players that means "qwer went second" is the sole evidence that
     * asdf went first.
     */
    @Test
    public void twoPlayersInferFirstFromTheOnlyAnnouncement() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: asdf, qwer",
                "qwer has chosen to go second"));

        assertEquals("asdf", metadata.WentFirst);
    }

    @Test
    public void twoPlayersTakeAnExplicitFirstAtItsWord() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: asdf, qwer",
                "qwer has chosen to go first"));

        assertEquals("qwer", metadata.WentFirst);
    }

    @Test
    public void twoPlayersStillHaveAnOpponent() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages("Players in the game are: asdf, qwer"));

        assertEquals("qwer", metadata.GetOpponent("asdf"));
        assertEquals("asdf", metadata.GetOpponent("qwer"));
    }

    // -------- five players --------

    @Test
    public void fivePlayersAreAllSeated() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: carol, dave, qwer, asdf, Librarian"));

        assertEquals(5, metadata.PlayerIDs.size());
        assertEquals(Integer.valueOf(1), metadata.PlayerIDs.get("carol"));
        assertEquals(Integer.valueOf(2), metadata.PlayerIDs.get("dave"));
        assertEquals(Integer.valueOf(3), metadata.PlayerIDs.get("qwer"));
        assertEquals(Integer.valueOf(4), metadata.PlayerIDs.get("asdf"));
        assertEquals(Integer.valueOf(5), metadata.PlayerIDs.get("Librarian"));
    }

    /**
     * The case that used to throw: four players announce a seat, none of them
     * claims the first one, and the fifth is seated in silence.
     */
    @Test
    public void fivePlayersInferFirstFromTheSilentPlayer() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: carol, dave, qwer, asdf, Librarian",
                "dave has chosen to go second",
                "qwer has chosen to go third",
                "asdf has chosen to go fourth",
                "Librarian has chosen to go fifth"));

        assertEquals("carol", metadata.WentFirst);
    }

    @Test
    public void fivePlayersTakeAnExplicitFirstAtItsWord() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: carol, dave, qwer, asdf, Librarian",
                "qwer has chosen to go first",
                "dave has chosen to go second",
                "carol has chosen to go third",
                "asdf has chosen to go fourth"));

        assertEquals("qwer", metadata.WentFirst);
    }

    /**
     * "Second" identified the first player only because there was exactly one
     * other player. At five seats it names one of four, so it must not be used.
     */
    @Test
    public void fivePlayersDoNotGuessFirstFromSecondAlone() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: carol, dave, qwer, asdf, Librarian",
                "dave has chosen to go second"));

        assertNull(metadata.WentFirst);
    }

    @Test
    public void aboveTwoPlayersThereIsNoSingleOpponent() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: carol, dave, qwer, asdf, Librarian"));

        assertNull(metadata.GetOpponent("asdf"));
    }

    /**
     * The exact shape of the crash: a seat announcement arriving with no player
     * list parsed used to call get() on an empty Optional.
     */
    @Test
    public void anUnparsedPlayerListDoesNotThrow() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages("dave has chosen to go second"));

        assertNull(metadata.WentFirst);
        assertNull(metadata.GetOpponent("dave"));
    }

    @Test
    public void bidsAreStillCollected() {
        var metadata = new ReplayMetadata();
        metadata.ParseReplay("asdf", messages(
                "Players in the game are: carol, dave, qwer, asdf, Librarian",
                "carol bid 0",
                "dave bid 3"));

        assertEquals(Integer.valueOf(0), metadata.Bids.get("carol"));
        assertEquals(Integer.valueOf(3), metadata.Bids.get("dave"));
    }
}
