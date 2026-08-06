package com.gempukku.lotro.cards.build.field.effect.modifier;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.logic.modifiers.Modifier;
import com.gempukku.lotro.logic.modifiers.ShouldSkipPhaseModifier;
import org.json.simple.JSONObject;

public class SkipPhase implements ModifierSourceProducer {
    @Override
    public ModifierSource getModifierSource(JSONObject object, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(object, "requires", "phase", "player");

        final JSONObject[] conditionArray = FieldUtils.getObjectArray(object.get("requires"), "requires");
        final Phase phase = FieldUtils.getEnum(Phase.class, object.get("phase"), "phase");
        // Optional. Omitted means "everyone", which is what every existing card
        // relied on and what the shared phases need.
        final String player = FieldUtils.getString(object.get("player"), "player");
        final PlayerSource playerSource = player != null ? PlayerResolver.resolvePlayer(player) : null;

        if (phase == null)
            throw new InvalidCardDefinitionException("'phase' is required for SkipPhase modifier.");

        final Requirement[] requirements = environment.getRequirementFactory().getRequirements(conditionArray, environment);

        return new ModifierSource() {
            @Override
            public Modifier getModifier(ActionContext actionContext) {
                return new ShouldSkipPhaseModifier(actionContext.getSource(),
                        RequirementCondition.createCondition(requirements, actionContext), phase,
                        playerSource == null ? null : playerSource.getPlayer(actionContext));
            }
        };
    }
}
