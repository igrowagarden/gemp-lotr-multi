package com.gempukku.lotro.cards.build.field.effect;

import com.gempukku.lotro.cards.build.ActionContext;
import com.gempukku.lotro.cards.build.ActionSource;
import com.gempukku.lotro.cards.build.PlayerSource;
import com.gempukku.lotro.cards.build.PlayersSource;
import com.gempukku.lotro.cards.build.Requirement;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.actions.CostToEffectAction;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class DefaultActionSource implements ActionSource {
    private final List<Requirement> requirements = new LinkedList<>();

    private final List<EffectAppender> costs = new LinkedList<>();
    private final List<EffectAppender> effects = new LinkedList<>();

    // A GROUP, not a seat. "Each Shadow player may ..." on a trigger could not
    // be written while this was a single PlayerSource: isValid equality-checked
    // it against the candidate performing player, so exactly one seat ever
    // qualified and every other opponent was skipped in silence.
    //
    // Optional triggers need no more than this, because the engine already asks
    // per player -- getOwnOptionalBeforeTriggers/AfterTriggers take a playerId
    // and build the context around it, so widening the check from equality to
    // membership is the whole fix. Same shape as the cantLookOrRevealHand
    // change.
    private PlayersSource playingPlayers;
    private boolean requiresRanger;
    private String text;

    /** Single-player form, unchanged for every caller that already used it. */
    public void setPlayingPlayer(PlayerSource playingPlayer) {
        this.playingPlayers = playingPlayer == null ? null
                : (actionContext) -> Collections.singletonList(playingPlayer.getPlayer(actionContext));
    }

    public void setPlayingPlayers(PlayersSource playingPlayers) {
        this.playingPlayers = playingPlayers;
    }

    public void setRequiresRanger(boolean requiresRanger) {
        this.requiresRanger = requiresRanger;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void addPlayRequirement(Requirement requirement) {
        this.requirements.add(requirement);
    }

    public void addCost(EffectAppender effectAppender) {
        costs.add(effectAppender);
    }

    public void addEffect(EffectAppender effectAppender) {
        effects.add(effectAppender);
    }

    /**
     * The FIRST of the designated players, or null if none was designated.
     *
     * Only the REQUIRED trigger paths use this, and they use it to rebind the
     * action context to a single seat (BuiltLotroCardBlueprint ~830/~884/~1035).
     * Returning the first keeps every existing card byte-identical, because a
     * single-player token wraps to a one-element list.
     *
     * A required trigger designating a GROUP would therefore still fire for
     * only the first of them. No card needs that today -- 13_193 and 4_362, the
     * two that designate a group, are both optional -- and making required
     * triggers fire per player means changing the enumeration itself, not this.
     */
    @Override
    public PlayerSource getPlayer() {
        if (playingPlayers == null)
            return null;
        return (actionContext) -> {
            List<String> players = playingPlayers.getPlayers(actionContext);
            return players.isEmpty() ? null : players.get(0);
        };
    }

    @Override
    public boolean requiresRanger() {
        return requiresRanger;
    }

    @Override
    public boolean isValid(ActionContext actionContext) {
        // Membership, not equality -- see the field comment. A single-player
        // token wraps to a one-element list, so this is the same test it always
        // was for every card that names one seat.
        if (playingPlayers != null
                && !playingPlayers.getPlayers(actionContext).contains(actionContext.getPerformingPlayer()))
            return false;

        for (Requirement requirement : requirements) {
            if (!requirement.accepts(actionContext))
                return false;
        }
        return true;
    }

    @Override
    public void createAction(CostToEffectAction action, ActionContext actionContext) {
        if (text != null)
            action.setText(GameUtils.substituteText(text, actionContext));

        for (EffectAppender cost : costs)
            cost.appendEffect(true, action, actionContext);

        for (EffectAppender actionEffect : effects)
            actionEffect.appendEffect(false, action, actionContext);
    }
}
