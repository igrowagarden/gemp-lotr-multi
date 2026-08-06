package com.gempukku.lotro.logic.timing.processes.turn.regroup;

import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.PlayerReconcilesAction;
import com.gempukku.lotro.logic.timing.processes.GameProcess;

public class PlayerReconcilesGameProcess implements GameProcess {
    private final String _playerId;
    private final GameProcess _followingGameProcess;

    public PlayerReconcilesGameProcess(String playerId, GameProcess followingGameProcess) {
        _playerId = playerId;
        _followingGameProcess = followingGameProcess;
    }

    @Override
    public void process(LotroGame game) {
        // ShadowPlayersReconcileGameProcess builds this whole chain up front, one
        // link per shadow player, so a player eliminated later in the phase still
        // has a link waiting for them. Skip it rather than asking someone who has
        // left the game to reconcile their hand.
        if (game.getGameState().getPlayerOrder().isEliminated(_playerId))
            return;
        game.getActionsEnvironment().addActionToStack(new PlayerReconcilesAction(game, _playerId));
    }

    @Override
    public GameProcess getNextProcess() {
        return _followingGameProcess;
    }
}
