package com.gempukku.lotro.logic.timing.processes.pregame;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.PlayerOrder;
import com.gempukku.lotro.logic.decisions.MultipleChoiceAwaitingDecision;
import com.gempukku.lotro.logic.timing.PlayerOrderFeedback;
import com.gempukku.lotro.logic.timing.processes.GameProcess;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ChooseSeatingOrderGameProcess implements GameProcess {
    private final String[] _choices = new String[]{"first", "second", "third", "fourth", "fifth"};
    private final Map<String, Integer> _bids;
    private final PlayerOrderFeedback _playerOrderFeedback;

    private final Iterator<String> _biddingOrderPlayers;
    private final String[] _orderedPlayers;
    private boolean _sentBids;

    public ChooseSeatingOrderGameProcess(Map<String, Integer> bids, PlayerOrderFeedback playerOrderFeedback) {
        _bids = bids;
        _playerOrderFeedback = playerOrderFeedback;

        ArrayList<String> participantList = new ArrayList<>(bids.keySet());
        Collections.shuffle(participantList, ThreadLocalRandom.current());

        participantList.sort(new Comparator<>() {
            @Override
            public int compare(String o1, String o2) {
                return _bids.get(o2) - _bids.get(o1);
            }
        });

        _biddingOrderPlayers = participantList.iterator();
        _orderedPlayers = new String[participantList.size()];
    }

    @Override
    public void process(LotroGame game) {
        if (!_sentBids) {
            _sentBids = true;
            for (Map.Entry<String, Integer> playerBid : _bids.entrySet())
                game.getGameState().sendMessage(playerBid.getKey() + " bid " + playerBid.getValue());
        }
        checkForNextSeating(game);
    }

    private int getLastEmptySeat() {
        boolean found = false;
        int emptySeatIndex = -1;
        for (int i = 0; i < _orderedPlayers.length; i++) {
            if (_orderedPlayers[i] == null) {
                if (found)
                    return -1;
                found = true;
                emptySeatIndex = i;
            }
        }
        return emptySeatIndex;
    }

    private void checkForNextSeating(LotroGame game) {
        int lastEmptySeat = getLastEmptySeat();
        if (lastEmptySeat == -1)
            askNextPlayerToChoosePlace(game);
        else {
            _orderedPlayers[lastEmptySeat] = _biddingOrderPlayers.next();
            _playerOrderFeedback.setPlayerOrder(new PlayerOrder(Arrays.asList(_orderedPlayers)), _orderedPlayers[0]);
        }
    }

    /**
     * The seats still open, as ABSOLUTE indexes into {@link #_orderedPlayers}.
     *
     * The labels handed to the player are a compacted list -- with seat 0 taken,
     * three seats become {"Go second", "Go third"} at choice indexes 0 and 1 --
     * so the choice index is NOT the seat index. Callers must map through this
     * array. At two players the two can never diverge, because the only time a
     * choice is offered every seat is still empty.
     */
    private int[] getEmptySeats() {
        int count = 0;
        for (String orderedPlayer : _orderedPlayers)
            if (orderedPlayer == null)
                count++;
        int[] seats = new int[count];
        int next = 0;
        for (int i = 0; i < _orderedPlayers.length; i++)
            if (_orderedPlayers[i] == null)
                seats[next++] = i;
        return seats;
    }

    private String[] getEmptySeatNumbers() {
        int[] emptySeats = getEmptySeats();
        String[] result = new String[emptySeats.length];
        for (int i = 0; i < emptySeats.length; i++)
            result[i] = "Go " + _choices[emptySeats[i]];
        return result;
    }

    private void participantHasChosenSeat(LotroGame game, String participant, int placeIndex) {
        _orderedPlayers[placeIndex] = participant;

        checkForNextSeating(game);
    }

    private void askNextPlayerToChoosePlace(final LotroGame game) {
        final String playerId = _biddingOrderPlayers.next();
        // Captured alongside the labels so the choice index can be mapped back to
        // the seat it actually names.
        final int[] emptySeats = getEmptySeats();
        game.getUserFeedback().sendAwaitingDecision(playerId,
                new MultipleChoiceAwaitingDecision(1, "Choose one", getEmptySeatNumbers()) {
                    @Override
                    protected void validDecisionMade(int index, String result) {
                        int seat = emptySeats[index];
                        game.getGameState().sendMessage(playerId + " has chosen to go " + _choices[seat]);
                        participantHasChosenSeat(game, playerId, seat);
                    }
                }
        );
    }

    @Override
    public GameProcess getNextProcess() {
        return new FirstPlayerPlaysSiteGameProcess(_bids, _orderedPlayers[0]);
    }
}
