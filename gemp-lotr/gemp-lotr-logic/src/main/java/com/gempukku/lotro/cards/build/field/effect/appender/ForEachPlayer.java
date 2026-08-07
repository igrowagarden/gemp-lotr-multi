package com.gempukku.lotro.cards.build.field.effect.appender;

import com.gempukku.lotro.cards.build.ActionContext;
import com.gempukku.lotro.cards.build.CardGenerationEnvironment;
import com.gempukku.lotro.cards.build.DelegateActionContext;
import com.gempukku.lotro.cards.build.InvalidCardDefinitionException;
import com.gempukku.lotro.cards.build.PlayersSource;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.EffectAppender;
import com.gempukku.lotro.cards.build.field.effect.EffectAppenderProducer;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.actions.SubAction;
import com.gempukku.lotro.logic.effects.StackActionEffect;
import com.gempukku.lotro.logic.timing.Effect;
import org.json.simple.JSONObject;

/**
 * Run a set of effects once per player, each with that player as the acting one.
 *
 * The `player` field says WHICH players. It defaults to `eachPlayer`, which is
 * everyone at the table including the Free Peoples player -- what this appender
 * did before the field existed, and what its two existing users mean ("Each
 * player may draw a card"). `eachShadow` narrows it to the opponents, in the
 * counter-clockwise order every response window in this game uses.
 *
 * Inside the nested effects, `you` is the player of the current iteration:
 * each one is appended against a DelegateActionContext built around them. That
 * is what makes "each Shadow player may draw a card" expressible at all --
 * `player: shadow` in there would name one fixed seat every time round.
 */
public class ForEachPlayer implements EffectAppenderProducer {
    @Override
    public EffectAppender createEffectAppender(boolean cost, JSONObject effectObject, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(effectObject, "player", "effect");

        final String player = FieldUtils.getString(effectObject.get("player"), "player", "eachPlayer");
        final JSONObject[] effectArray = FieldUtils.getObjectArray(effectObject.get("effect"), "effect");

        if (effectArray.length == 0)
            throw new InvalidCardDefinitionException("Effect is required for a ForEachPlayer effect.");

        final PlayersSource playersSource = PlayerResolver.resolvePlayers(player);
        final EffectAppender[] effectAppenders = environment.getEffectAppenderFactory().getEffectAppenders(cost, effectArray, environment);

        return new DelayedAppender() {
            @Override
            protected Effect createEffect(boolean cost, CostToEffectAction action, ActionContext actionContext) {
                SubAction subAction = new SubAction(action);
                for (String playerId : playersSource.getPlayers(actionContext)) {
                    if (playerId == null)
                        continue;
                    for (EffectAppender effectAppender : effectAppenders) {
                        DelegateActionContext playerActionContext = new DelegateActionContext(actionContext, playerId,
                                actionContext.getGame(), actionContext.getSource(), actionContext.getEffectResult(),
                                actionContext.getEffect());
                        effectAppender.appendEffect(cost, action, playerActionContext);
                    }
                }
                return new StackActionEffect(subAction);
            }

            /**
             * Every named player must be able to play it in full.
             *
             * Left as it was, deliberately. It is the wrong rule for "each
             * Shadow player MAY do X" -- one player with an empty draw deck
             * would stop everybody -- but it is only consulted when something
             * nesting this appender checks playability, and changing it is a
             * separate change with its own control. See HANDOFF.md.
             */
            @Override
            public boolean isPlayableInFull(ActionContext actionContext) {
                for (String playerId : playersSource.getPlayers(actionContext)) {
                    if (playerId == null)
                        continue;
                    for (EffectAppender effectAppender : effectAppenders) {
                        DelegateActionContext playerActionContext = new DelegateActionContext(actionContext, playerId,
                                actionContext.getGame(), actionContext.getSource(), actionContext.getEffectResult(),
                                actionContext.getEffect());
                        if (!effectAppender.isPlayableInFull(playerActionContext))
                            return false;
                    }
                }

                return true;
            }
        };
    }
}
