package com.gempukku.lotro.cards.build.field.effect.modifier;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.modifiers.AbstractModifier;
import com.gempukku.lotro.logic.modifiers.ModifierEffect;
import org.json.simple.JSONObject;

public class CantLookOrRevealHand implements ModifierSourceProducer {
    @Override
    public ModifierSource getModifierSource(JSONObject object, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(object, "player", "hand", "requires");

        final String player = FieldUtils.getString(object.get("player"), "player");
        final String hand = FieldUtils.getString(object.get("hand"), "hand");

        PlayerSource playerSource = PlayerResolver.resolvePlayer(player);
        // `hand:` names a GROUP, not a seat. No Business of Ours (2_44) and its
        // errata (52_44) both read "The Free Peoples player may not look at or
        // reveal cards in ANY Shadow player's hand", and `hand: shadow`
        // protected exactly one of them -- at five seats the other three were
        // as readable as before.
        //
        // resolvePlayers wraps every single-player token as a one-element list,
        // so `hand: shadow` and `hand: fp` keep working unchanged; only
        // `anyShadow`/`eachShadow`/`eachPlayer` become newly expressible. Same
        // move ForEachPlayer and PreventableAppenderProducer already made.
        //
        // `player:` stays a single PlayerSource: these cards restrict ONE
        // player from looking, and there is only ever one Free Peoples player.
        PlayersSource handSource = PlayerResolver.resolvePlayers(hand);

        final JSONObject[] conditionArray = FieldUtils.getObjectArray(object.get("requires"), "requires");
        final Requirement[] requirements = environment.getRequirementFactory().getRequirements(conditionArray, environment);

        return actionContext -> new AbstractModifier(actionContext.getSource(), "Player may not look at or reveal cards in another player hand",
                null, RequirementCondition.createCondition(requirements, actionContext), ModifierEffect.LOOK_OR_REVEAL_MODIFIER) {
            @Override
            public boolean canLookOrRevealCardsInHand(LotroGame game, String revealingPlayerId, String actingPlayerId) {
                if (playerSource.getPlayer(actionContext).equals(actingPlayerId)
                        && handSource.getPlayers(actionContext).contains(revealingPlayerId))
                    return false;
                return true;
            }
        };
    }
}
