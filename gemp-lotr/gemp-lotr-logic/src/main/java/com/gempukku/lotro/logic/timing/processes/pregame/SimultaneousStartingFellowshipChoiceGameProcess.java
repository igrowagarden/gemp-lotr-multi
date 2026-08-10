package com.gempukku.lotro.logic.timing.processes.pregame;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.PlayOrder;
import com.gempukku.lotro.logic.decisions.ArbitraryCardsSelectionDecision;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.timing.processes.GameProcess;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Every player picks their whole starting fellowship AT ONCE.
 *
 * The playtest ruling: "we should pick our starting fellowships
 * simultaneously since it affects nothing" -- and rules-wise that is right:
 * each player's build is priced against their OWN budget in their own
 * pseudo-turn, and nothing a player picks changes what another may pick.
 * The old chain still made seats two through five WAIT, because each build
 * ran inside its player's pseudo-turn one at a time.
 *
 * So the CHOICE is lifted out of the pseudo-turns and collected here,
 * simultaneously -- one multi-select decision per player, all sent in one
 * process() exactly the way {@link BiddingGameProcess} already sends bids.
 * The EXECUTION stays sequential and unchanged: the pseudo-turn chain in
 * {@link PlayStartingFellowshipGameProcess} plays each stored pick through
 * the real action machinery IN THE ORDER PICKED, so costs, discounts, spot
 * requirements and triggered responses resolve exactly as they did when the
 * picks were made one at a time.
 *
 * EVERY companion is selectable here, deliberately. Affordability cannot be
 * judged before the plays happen: a companion may be over budget on its
 * printed cost yet legal after an earlier pick discounts it, and a spot
 * requirement may only be met by a companion picked before it (both covered
 * in TimingAtTest). The decision carries twilightCost and budgetRemaining
 * so the client can grey the OBVIOUS over-picks live, and the executor is
 * the enforcement: a pick that cannot legally play when its turn comes is
 * skipped WITH A MESSAGE -- never silently, per the playtest's other rule.
 */
public class SimultaneousStartingFellowshipChoiceGameProcess implements GameProcess {
    private final PlayOrder _playOrder;
    private final String _firstPlayer;
    private final Map<String, List<PhysicalCard>> _selections = new HashMap<>();

    public SimultaneousStartingFellowshipChoiceGameProcess(PlayOrder playOrder, String firstPlayer) {
        _playOrder = playOrder;
        _firstPlayer = firstPlayer;
    }

    @Override
    public void process(LotroGame game) {
        // The game PAUSES here while everyone chooses, and the old flow
        // paused inside the FIRST player's pseudo-turn -- everything
        // current-player-relative (threat bookkeeping, cards added to the
        // table by test setups, cost queries, and the addCardToZone
        // affecting registrations those additions perform) is written
        // against that context. Open the same context before the pause, or
        // all of it lands on whichever player the corruption check happened
        // to end on. The executor KNOWS this window is open (the
        // firstWindowAlreadyOpen flag below) and must not open it twice --
        // the bracketing has to match the old flow exactly, or modifier
        // hooks orphan and duplicate.
        game.getGameState().startPlayerTurn(_firstPlayer, false);
        game.getGameState().startAffectingCardsForCurrentPlayer(game);
        while (true) {
            String player = _playOrder.getNextPlayer();
            if (player == null)
                break;
            sendChoiceDecision(game, player);
        }
    }

    private void sendChoiceDecision(final LotroGame game, final String playerId) {
        final List<PhysicalCard> shown = new LinkedList<>(
                Filters.filter(game, game.getGameState().getDeck(playerId), CardType.COMPANION));
        if (shown.isEmpty()) {
            _selections.put(playerId, new LinkedList<>());
            return;
        }
        final int budget = 4 + game.getModifiersQuerying().getStartingFellowshipCostModifier(game, playerId);
        final String[] costs = new String[shown.size()];
        int costIndex = 0;
        for (PhysicalCard card : shown)
            costs[costIndex++] = String.valueOf(
                    game.getModifiersQuerying().getTwilightCostToPlay(game, card, null, 0, false));

        game.getUserFeedback().sendAwaitingDecision(playerId,
                new ArbitraryCardsSelectionDecision(1, "Starting fellowship - Choose the companions to play with your Ring-bearer, in the order to play them",
                        shown, new LinkedList<>(shown), 0, shown.size()) {
                    {
                        setParam("twilightCost", costs);
                        setParam("budgetRemaining", String.valueOf(budget));
                    }
                    @Override
                    public void decisionMade(String result) throws DecisionResultInvalidException {
                        _selections.put(playerId, getSelectedCardsByResponse(result));
                    }
                });
    }

    @Override
    public GameProcess getNextProcess() {
        return new PlayStartingFellowshipGameProcess(null, _firstPlayer, _selections, true);
    }
}
