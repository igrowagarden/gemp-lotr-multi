package com.gempukku.lotro.cards.build.field.effect.appender;

import com.gempukku.lotro.cards.build.ActionContext;
import com.gempukku.lotro.cards.build.DelegateActionContext;
import com.gempukku.lotro.cards.build.PlayersSource;
import com.gempukku.lotro.cards.build.field.effect.EffectAppender;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.actions.SubAction;
import com.gempukku.lotro.logic.decisions.YesNoDecision;
import com.gempukku.lotro.logic.effects.PlayoutDecisionEffect;
import com.gempukku.lotro.logic.effects.StackActionEffect;
import com.gempukku.lotro.logic.timing.Effect;
import com.gempukku.lotro.logic.timing.UnrespondableEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class PreventableEffectAppender extends DelayedAppender {
    private PlayersSource preventingPlayers;
    private String preventText;
    private Predicate<ActionContext> preventingPredicate;
    private EffectAppender[] costAppenders;
    private EffectAppender[] effectAppenders;
    private EffectAppender[] insteadAppenders;

    public PreventableEffectAppender(PlayersSource preventingPlayers, String preventText, Predicate<ActionContext> preventingPredicate,
                                     EffectAppender[] costAppenders, EffectAppender[] effectAppenders, EffectAppender[] insteadAppenders) {
        this.preventingPlayers = preventingPlayers;
        this.preventText = preventText;
        this.preventingPredicate = preventingPredicate;
        this.costAppenders = costAppenders;
        this.effectAppenders = effectAppenders;
        this.insteadAppenders = insteadAppenders;
    }

    @Override
    protected Effect createEffect(boolean cost, CostToEffectAction action, ActionContext actionContext) {
        String textToUse = GameUtils.substituteText(preventText, actionContext);
        SubAction subAction = new SubAction(action);

        // Everyone the card designates who could actually pay. A player who
        // cannot afford the cost is skipped rather than asked, which is what
        // the single-player version did by declining to offer at all.
        List<String> candidates = new ArrayList<>();
        if (preventingPredicate.test(actionContext)) {
            for (String playerId : preventingPlayers.getPlayers(actionContext)) {
                if (playerId != null && areCostsPlayable(contextFor(actionContext, playerId)))
                    candidates.add(playerId);
            }
        }

        appendOfferTo(candidates, 0, subAction, actionContext, textToUse);
        return new StackActionEffect(subAction);
    }

    private DelegateActionContext contextFor(ActionContext actionContext, String playerId) {
        return new DelegateActionContext(actionContext, playerId, actionContext.getGame(),
                actionContext.getSource(), actionContext.getEffectResult(), actionContext.getEffect());
    }

    /**
     * Offer the prevention to one candidate, and on a refusal move to the next.
     * The effect happens only once everyone has declined, so "any Shadow player
     * may ... to prevent this" stops at the first player who says yes instead of
     * asking whichever seat happened to be first and no one else.
     */
    private void appendOfferTo(List<String> candidates, int index, SubAction subAction,
                               ActionContext actionContext, String textToUse) {
        if (index >= candidates.size()) {
            for (EffectAppender effectAppender : effectAppenders)
                effectAppender.appendEffect(false, subAction, actionContext);
            return;
        }

        String preventingPlayerId = candidates.get(index);
        DelegateActionContext preventCostActionContext = contextFor(actionContext, preventingPlayerId);

        subAction.appendEffect(new PlayoutDecisionEffect(preventingPlayerId, new YesNoDecision(textToUse) {
            @Override
            protected void yes() {
                actionContext.getGame().getGameState().sendMessage(GameUtils.substituteText(preventingPlayerId + " chooses to prevent.", actionContext));
                for (EffectAppender costAppender : costAppenders)
                    costAppender.appendEffect(false, subAction, preventCostActionContext);

                subAction.appendEffect(new UnrespondableEffect() {
                    @Override
                    protected void doPlayEffect(LotroGame game) {
                        // If the prevention was not carried out, need to do the original action anyway
                        if (!subAction.wasCarriedOut()) {
                            game.getGameState().sendMessage(GameUtils.substituteText(preventingPlayerId + " attempted to prevent, but could not carry it out.", actionContext));
                            for (EffectAppender effectAppender : effectAppenders)
                                effectAppender.appendEffect(false, subAction, actionContext);
                        } else {
                            for (EffectAppender insteadAppender : insteadAppenders) {
                                insteadAppender.appendEffect(false, subAction, preventCostActionContext);
                            }
                        }
                    }

                    @Override
                    public boolean wasCarriedOut() {
                        return true;
                    }
                });
            }

            @Override
            protected void no() {
                actionContext.getGame().getGameState().sendMessage(GameUtils.substituteText(preventingPlayerId + " decides not to prevent.", actionContext));
                appendOfferTo(candidates, index + 1, subAction, actionContext, textToUse);
            }
        }));
    }

    private boolean areCostsPlayable(ActionContext actionContext) {
        for (EffectAppender costAppender : costAppenders) {
            if (!costAppender.isPlayableInFull(actionContext)) return false;
        }
        return true;
    }

    @Override
    public boolean isPlayableInFull(ActionContext actionContext) {
        for (EffectAppender effectAppender : effectAppenders) {
            if (!effectAppender.isPlayableInFull(actionContext)) return false;
        }

        return true;
    }
}
