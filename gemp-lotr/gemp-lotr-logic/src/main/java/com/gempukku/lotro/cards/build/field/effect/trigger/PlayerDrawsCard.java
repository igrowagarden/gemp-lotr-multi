package com.gempukku.lotro.cards.build.field.effect.trigger;

import com.gempukku.lotro.cards.build.ActionContext;
import com.gempukku.lotro.cards.build.CardGenerationEnvironment;
import com.gempukku.lotro.cards.build.InvalidCardDefinitionException;
import com.gempukku.lotro.cards.build.PlayersSource;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import com.gempukku.lotro.logic.timing.EffectResult;
import com.gempukku.lotro.logic.timing.results.DrawCardOrPutIntoHandResult;
import org.json.simple.JSONObject;

public class PlayerDrawsCard implements TriggerCheckerProducer {
    @Override
    public TriggerChecker getTriggerChecker(JSONObject value, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(value, "player");

        final String player = FieldUtils.getString(value.get("player"), "player");

        // `player:` here is a GATE -- whose draw sets the trigger off -- and it
        // may name a group. Betrayal of Isengard (3_29, 12_25) says "Each time
        // AN OPPONENT draws a card during the Shadow phase", and above two
        // players a single token watched one seat while every other opponent
        // drew freely.
        //
        // resolvePlayers wraps single tokens as one-element lists, so the four
        // existing users (two `fp`, two `shadow`) keep their exact behaviour.
        PlayersSource playerSource = PlayerResolver.resolvePlayers(player);
        return new TriggerChecker() {
            @Override
            public boolean accepts(ActionContext actionContext) {
                EffectResult effectResult = actionContext.getEffectResult();
                if (effectResult.getType() == EffectResult.Type.DRAW_CARD_OR_PUT_INTO_HAND) {
                    DrawCardOrPutIntoHandResult drawResult = (DrawCardOrPutIntoHandResult) effectResult;
                    return playerSource.getPlayers(actionContext).contains(drawResult.getPlayerId());
                }
                return false;
            }

            @Override
            public boolean isBefore() {
                return false;
            }
        };
    }
}
