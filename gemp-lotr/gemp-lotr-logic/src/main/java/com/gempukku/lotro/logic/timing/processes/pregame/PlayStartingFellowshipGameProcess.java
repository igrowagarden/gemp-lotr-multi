package com.gempukku.lotro.logic.timing.processes.pregame;

import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.PlayOrder;
import com.gempukku.lotro.logic.timing.processes.GameProcess;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The pseudo-turn chain that PLAYS each starting fellowship, one player at a
 * time. The choosing no longer happens here: every player already picked
 * simultaneously in {@link SimultaneousStartingFellowshipChoiceGameProcess},
 * and this chain executes the stored picks through the real action machinery
 * inside each player's pseudo-turn -- costs, discounts and triggered
 * responses run exactly as they always did, in seating order.
 *
 * WINDOW BRACKETING IS LOAD-BEARING and deliberately reproduces the old
 * per-player flow exactly: the FIRST player's pseudo-turn window was opened
 * by the choice process (where the old flow had it open across the choosing
 * decision), so the first entry here must NOT open it again -- an unbalanced
 * start orphans a card's modifier hooks in the modifiers environment and
 * every number it touches is off by one extra application for the rest of
 * the game. Measured the hard way, twice, in both directions.
 */
public class PlayStartingFellowshipGameProcess implements GameProcess {
    private PlayOrder _playOrder;
    private final String _firstPlayer;
    private final Map<String, List<PhysicalCard>> _selections;
    private final boolean _firstWindowAlreadyOpen;

    private GameProcess _nextProcess;

    public PlayStartingFellowshipGameProcess(PlayOrder playOrder, String firstPlayer,
                                             Map<String, List<PhysicalCard>> selections) {
        this(playOrder, firstPlayer, selections, false);
    }

    public PlayStartingFellowshipGameProcess(PlayOrder playOrder, String firstPlayer,
                                             Map<String, List<PhysicalCard>> selections,
                                             boolean firstWindowAlreadyOpen) {
        _playOrder = playOrder;
        _firstPlayer = firstPlayer;
        _selections = selections;
        _firstWindowAlreadyOpen = firstWindowAlreadyOpen;
    }

    @Override
    public void process(LotroGame game) {
        // The choice process consumed the order it was given, so this chain
        // builds its own on first entry rather than sharing a spent one.
        if (_playOrder == null)
            _playOrder = game.getGameState().getPlayerOrder().getClockwisePlayOrder(_firstPlayer, false);
        String nextPlayer = _playOrder.getNextPlayer();

        if (nextPlayer != null) {
            if (_firstWindowAlreadyOpen && nextPlayer.equals(_firstPlayer)) {
                // The choice process opened this player's pseudo-turn window
                // and it is still open -- entering it again would double-start
                // every affecting card. Just play the picks.
            } else {
                String currentPlayer = game.getGameState().getCurrentPlayerId();
                if (currentPlayer != null && currentPlayer.equals(_playOrder.getFirstPlayer()))
                    game.getGameState().stopAffectingCardsForCurrentPlayer();
                game.getGameState().startPlayerTurn(nextPlayer, false);
                game.getGameState().startAffectingCardsForCurrentPlayer(game);
            }

            List<PhysicalCard> picks = _selections == null ? null : _selections.get(nextPlayer);
            _nextProcess = new PlayerPlaysStartingFellowshipGameProcess(nextPlayer,
                    picks == null ? new LinkedList<>() : new LinkedList<>(picks),
                    new PlayStartingFellowshipGameProcess(_playOrder, _firstPlayer, _selections, false));
        } else {
            game.getGameState().stopAffectingCardsForCurrentPlayer();
            _nextProcess = new PlayersDrawStartingHandGameProcess(_firstPlayer);
        }
    }

    @Override
    public GameProcess getNextProcess() {
        return _nextProcess;
    }
}
