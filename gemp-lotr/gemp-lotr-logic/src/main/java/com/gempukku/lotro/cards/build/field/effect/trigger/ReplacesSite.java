package com.gempukku.lotro.cards.build.field.effect.trigger;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.ValueResolver;
import com.gempukku.lotro.logic.timing.EffectResult;
import com.gempukku.lotro.logic.timing.results.ReplaceSiteResult;
import org.json.simple.JSONObject;

public class ReplacesSite implements TriggerCheckerProducer {
    @Override
    public TriggerChecker getTriggerChecker(JSONObject value, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(value, "player", "number");

        String player = FieldUtils.getString(value.get("player"), "player");
        ValueSource numberResolver = ValueResolver.resolveEvaluator(value.get("number"), 0, environment);

        // A GATE naming a group -- Riddermark Tactician (13_133) says "Each time
        // A SHADOW PLAYER replaces the fellowship's current site". It is the
        // only card using this trigger's player field, and a single token
        // watched one seat.
        PlayersSource playerSource = (player != null) ? PlayerResolver.resolvePlayers(player) : null;

        return new TriggerChecker() {
            @Override
            public boolean accepts(ActionContext actionContext) {
                EffectResult effectResult = actionContext.getEffectResult();
                if (effectResult.getType() != EffectResult.Type.REPLACE_SITE)
                    return false;

                ReplaceSiteResult replaceSiteResult = (ReplaceSiteResult) effectResult;
                int number = numberResolver.getEvaluator(actionContext).evaluateExpression(actionContext.getGame(), null);
                if (number != 0 && replaceSiteResult.getSiteNumber() != number)
                    return false;

                if (playerSource != null
                        && !playerSource.getPlayers(actionContext).contains(replaceSiteResult.getPlayerId()))
                    return false;

                return true;
            }

            @Override
            public boolean isBefore() {
                return false;
            }
        };
    }
}
