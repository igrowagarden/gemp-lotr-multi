package com.gempukku.lotro.logic.timing.processes.pregame;

import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayUtils;
import com.gempukku.lotro.logic.timing.Action;
import com.gempukku.lotro.logic.timing.processes.GameProcess;
import com.gempukku.lotro.logic.timing.results.FinishedPlayingFellowshipResult;

import java.util.Deque;

/**
 * Plays one player's PRE-CHOSEN starting fellowship inside their pseudo-turn,
 * one card per process step, with no decisions at all -- the choosing already
 * happened, simultaneously for every seat, in
 * {@link SimultaneousStartingFellowshipChoiceGameProcess}.
 *
 * Each card still goes through the real play action, so costs are paid from
 * the player's own pool, discounts and play requirements are re-checked at
 * the moment of play, and triggered responses fire as they always did. A
 * pick that is NOT playable when its turn comes -- the budget ran out under
 * a cost that rose, or a requirement failed -- is SKIPPED WITH A MESSAGE,
 * never silently: the playtest's rule is that nothing may quietly drop.
 * (The choice decision already refused any selection whose summed shown
 * costs exceed the budget, so a skip here is rare and means the state moved
 * between choosing and playing.)
 */
public class PlayerPlaysStartingFellowshipGameProcess implements GameProcess {
    private final String _playerId;
    private final Deque<PhysicalCard> _remaining;

    private final GameProcess _followingGameProcess;
    private GameProcess _nextProcess;

    public PlayerPlaysStartingFellowshipGameProcess(String playerId, Deque<PhysicalCard> remaining,
                                                    GameProcess followingGameProcess) {
        _playerId = playerId;
        _remaining = remaining;
        _followingGameProcess = followingGameProcess;
    }

    @Override
    public void process(LotroGame game) {
        while (!_remaining.isEmpty()) {
            PhysicalCard card = _remaining.removeFirst();
            if (playable(game, card)) {
                Action playCardAction = PlayUtils.getPlayCardAction(game, card, 0, Filters.any, false);
                game.getActionsEnvironment().addActionToStack(playCardAction);
                _nextProcess = new PlayerPlaysStartingFellowshipGameProcess(_playerId, _remaining, _followingGameProcess);
                return;
            }
            game.getGameState().sendMessage(_playerId + " could not play " + GameUtils.getFullName(card)
                    + " - the starting fellowship budget no longer covers it, so it was skipped");
        }
        game.getActionsEnvironment().emitEffectResult(new FinishedPlayingFellowshipResult(_playerId));
        _nextProcess = _followingGameProcess;
    }

    private boolean playable(LotroGame game, PhysicalCard card) {
        int twilightCost = game.getModifiersQuerying().getTwilightCostToPlay(game, card, null, 0, false);
        int startingFellowshipLimit = 4 + game.getModifiersQuerying().getStartingFellowshipCostModifier(game, _playerId);
        return game.getGameState().getTwilightPool() + twilightCost <= startingFellowshipLimit
                && PlayUtils.checkPlayRequirements(game, card, Filters.any, 0, 0, false, false, true);
    }

    @Override
    public GameProcess getNextProcess() {
        return _nextProcess;
    }
}
