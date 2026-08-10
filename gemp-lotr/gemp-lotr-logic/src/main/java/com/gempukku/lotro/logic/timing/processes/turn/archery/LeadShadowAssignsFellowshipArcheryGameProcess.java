package com.gempukku.lotro.logic.timing.processes.turn.archery;

import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.PlayOrder;
import com.gempukku.lotro.logic.timing.processes.GameProcess;

/**
 * The fellowship's archery total is assigned by the LEAD Shadow player -- the
 * first in clockwise order after the Free Peoples player -- automatically.
 *
 * RULED in the five-player playtest (2026-08-09), matching the Council text
 * "archery wounds can be doled out by the primary shadow player to other
 * minions". Upstream's free-for-all code (this class's previous life as
 * FellowshipPlayerChoosesShadowPlayerToAssignDamageToGameProcess) had the
 * Free Peoples player CHOOSE which Shadow player assigns, which both added a
 * prompt and put the casualty decision in the wrong hands -- the playtest hit
 * it immediately ("I chose wrong; in a normal game my one archery wound would
 * have killed the goblin runner"). At two players the lead Shadow player is
 * the only opponent, so nothing changes there. The assignment itself is
 * mandatory and crosses seats; see ShadowPlayerAssignsArcheryDamageGameProcess.
 */
public class LeadShadowAssignsFellowshipArcheryGameProcess implements GameProcess {
    private final int _woundsToAssign;
    private final GameProcess _followingGameProcess;

    private GameProcess _nextProcess;

    public LeadShadowAssignsFellowshipArcheryGameProcess(int woundsToAssign, GameProcess followingGameProcess) {
        _woundsToAssign = woundsToAssign;
        _followingGameProcess = followingGameProcess;
    }

    @Override
    public void process(LotroGame game) {
        _nextProcess = _followingGameProcess;
        if (_woundsToAssign > 0) {
            GameState gameState = game.getGameState();
            PlayOrder playOrder = gameState.getPlayerOrder().getClockwisePlayOrder(gameState.getCurrentPlayerId(), false);
            playOrder.getNextPlayer();               // the Free Peoples player himself
            String leadShadow = playOrder.getNextPlayer();
            if (leadShadow != null)
                _nextProcess = new ShadowPlayerAssignsArcheryDamageGameProcess(leadShadow, _woundsToAssign, _followingGameProcess);
        }
    }

    @Override
    public GameProcess getNextProcess() {
        return _nextProcess;
    }
}
