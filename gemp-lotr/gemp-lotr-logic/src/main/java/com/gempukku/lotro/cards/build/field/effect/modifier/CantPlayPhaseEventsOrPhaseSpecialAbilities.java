package com.gempukku.lotro.cards.build.field.effect.modifier;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.logic.modifiers.CantPlayPhaseEventsOrphaseSpecialAbilitiesModifier;
import com.gempukku.lotro.logic.modifiers.Modifier;
import org.json.simple.JSONObject;

public class CantPlayPhaseEventsOrPhaseSpecialAbilities implements ModifierSourceProducer {
    @Override
    public ModifierSource getModifierSource(JSONObject object, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(object, "player", "phase", "requires");

        String player = FieldUtils.getString(object.get("player"), "player");
        final Phase phase = FieldUtils.getEnum(Phase.class, object.get("phase"), "phase");
        if (phase == null)
            throw new InvalidCardDefinitionException("Has to have a phase defined");
        final JSONObject[] conditionArray = FieldUtils.getObjectArray(object.get("requires"), "requires");

        final Requirement[] requirements = environment.getRequirementFactory().getRequirements(conditionArray, environment);
        PlayersSource playerSource = player != null ? PlayerResolver.resolvePlayers(player) : null;

        return new ModifierSource() {
            @Override
            public Modifier getModifier(ActionContext actionContext) {
                java.util.Collection<String> bannedPlayer = playerSource != null ? playerSource.getPlayers(actionContext) : null;
                return new CantPlayPhaseEventsOrphaseSpecialAbilitiesModifier(actionContext.getSource(),
                        RequirementCondition.createCondition(requirements, actionContext), phase, bannedPlayer);
            }
        };
    }
}
