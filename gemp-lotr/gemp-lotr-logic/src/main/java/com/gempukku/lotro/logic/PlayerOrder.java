package com.gempukku.lotro.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PlayerOrder {
    /**
     * Every seat, in seating order, for the whole game. Deliberately NOT mutated
     * when a player is eliminated: {@link #getClockwisePlayOrder} and its
     * counter-clockwise twin locate their starting player with
     * {@code indexOf(startingPlayerId)}, and callers such as
     * {@code BetweenTurnsProcess} pass the CURRENT player -- who, when the
     * Ring-bearer dies, is usually the player who just lost. Removing them would
     * make {@code indexOf} return -1 and the next {@code get()} throw.
     * <p>
     * The seated list is also handed in as {@code Arrays.asList(...)} by
     * {@code ChooseSeatingOrderGameProcess}, which is fixed-size, so it is copied.
     */
    private final List<String> _turnOrder;

    /** Players who have lost but whose game continues around them. */
    private final Set<String> _eliminated = new LinkedHashSet<>();

    public PlayerOrder(List<String> turnOrder) {
        _turnOrder = new ArrayList<>(turnOrder);
    }

    public String getFirstPlayer() {
        return _turnOrder.get(0);
    }

    /** Players still in the game. */
    public List<String> getAllPlayers() {
        if (_eliminated.isEmpty())
            return Collections.unmodifiableList(_turnOrder);
        List<String> survivors = new ArrayList<>(_turnOrder);
        survivors.removeAll(_eliminated);
        return Collections.unmodifiableList(survivors);
    }

    /** Every seat, including eliminated players. For display and bookkeeping. */
    public List<String> getAllSeatedPlayers() {
        return Collections.unmodifiableList(_turnOrder);
    }

    /**
     * Take a player out of the rotation without disturbing anyone's seat.
     *
     * @return true if this player was seated and had not already been eliminated
     */
    public boolean eliminatePlayer(String playerId) {
        if (!_turnOrder.contains(playerId))
            return false;
        return _eliminated.add(playerId);
    }

    public boolean isEliminated(String playerId) {
        return _eliminated.contains(playerId);
    }

    public PlayOrder getCounterClockwisePlayOrder(String startingPlayerId, boolean looped) {
        return buildOrder(startingPlayerId, looped, -1);
    }

    public PlayOrder getClockwisePlayOrder(String startingPlayerId, boolean looped) {
        return buildOrder(startingPlayerId, looped, +1);
    }

    /**
     * Walks the FULL seated list so the starting index is always valid, but emits
     * only players still in the game -- except the starting player, who is always
     * emitted first even if eliminated.
     * <p>
     * That exception is deliberate and load-bearing: callers such as
     * {@code BetweenTurnsProcess}, {@code ShadowPhasesGameProcess} and
     * {@code GameUtils.getFirstShadowPlayer} all discard the first entry to mean
     * "skip the player I started from". Dropping an eliminated starting player
     * here would silently shift every one of those by a seat.
     */
    private PlayOrder buildOrder(String startingPlayerId, boolean looped, int step) {
        int currentPlayerIndex = _turnOrder.indexOf(startingPlayerId);
        List<String> playOrder = new ArrayList<>();
        int nextIndex = currentPlayerIndex;
        do {
            String player = _turnOrder.get(nextIndex);
            if (nextIndex == currentPlayerIndex || !_eliminated.contains(player))
                playOrder.add(player);
            nextIndex += step;
            if (nextIndex < 0)
                nextIndex = _turnOrder.size() - 1;
            else if (nextIndex == _turnOrder.size())
                nextIndex = 0;
        } while (currentPlayerIndex != nextIndex);
        return new PlayOrder(playOrder, looped);
    }

    /** Players still in the game. Pass-loops terminate against this. */
    public int getPlayerCount() {
        return _turnOrder.size() - _eliminated.size();
    }
}
