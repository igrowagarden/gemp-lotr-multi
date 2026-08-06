package com.gempukku.lotro.logic.timing.processes.turn;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.PlayOrder;
import com.gempukku.lotro.logic.timing.processes.GameProcess;
import com.gempukku.lotro.logic.timing.processes.turn.general.EndOfPhaseGameProcess;
import com.gempukku.lotro.logic.timing.processes.turn.general.PlayerPlaysPhaseActionsUntilPassesGameProcess;
import com.gempukku.lotro.logic.timing.processes.turn.general.StartOfPhaseGameProcess;

public class ShadowPhaseOfPlayerGameProcess implements GameProcess {
    private final PlayOrder _playOrder;
    private final String _shadowPlayer;

    private GameProcess _followingGameProcess;

    public ShadowPhaseOfPlayerGameProcess(PlayOrder playOrder, String shadowPlayer) {
        _playOrder = playOrder;
        _shadowPlayer = shadowPlayer;
    }

    @Override
    public void process(LotroGame game) {
        String nextPlayer = _playOrder.getNextPlayer();
        GameProcess afterGameProcess;
        if (nextPlayer == null)
            afterGameProcess = new ManeuverGameProcess();
        else
            afterGameProcess = new ShadowPhaseOfPlayerGameProcess(_playOrder, nextPlayer);

        // _shadowPlayer is bound when this link of the chain is built, one per
        // shadow player at the start of the Shadow phases. Skip the whole phase
        // for anyone eliminated since then, rather than running start-of-phase and
        // end-of-phase around a player who has left the game.
        if (game.getGameState().getPlayerOrder().isEliminated(_shadowPlayer))
            _followingGameProcess = afterGameProcess;
        else if (game.getModifiersQuerying().shouldSkipPhase(game, Phase.SHADOW, _shadowPlayer))
            _followingGameProcess = afterGameProcess;
        else
            _followingGameProcess = new StartOfPhaseGameProcess(Phase.SHADOW, _shadowPlayer,
                    new PlayerPlaysPhaseActionsUntilPassesGameProcess(_shadowPlayer,
                            new EndOfPhaseGameProcess(Phase.SHADOW,
                                    afterGameProcess)));
    }

    @Override
    public GameProcess getNextProcess() {
        return _followingGameProcess;
    }
}
