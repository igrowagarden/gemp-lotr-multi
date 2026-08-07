package com.gempukku.lotro.cards.build.field.effect;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.EffectProcessor;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.logic.timing.EffectResult;
import com.gempukku.lotro.logic.timing.results.DiscardCardsFromPlayResult;
import com.gempukku.lotro.logic.timing.results.HinderedResult;
import org.json.simple.JSONObject;

public class SelfRemovedFromPlayTriggerEffectProcessor implements EffectProcessor {

    /**
     * The card that did the removing, or null if this is not a removal result.
     *
     * Both result types carry it under getSource() but share no interface that
     * exposes it, so this switches rather than casts blindly.
     */
    private static PhysicalCard removerOf(EffectResult result) {
        if (result instanceof HinderedResult hindered)
            return hindered.getSource();
        if (result instanceof DiscardCardsFromPlayResult discarded)
            return discarded.getSource();
        return null;
    }
    @Override
    public void processEffect(JSONObject value, BuiltLotroCardBlueprint blueprint, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(value, "optional", "source", "method", "requires", "cost", "effect");

        final boolean optional = FieldUtils.getBoolean(value.get("optional"), "optional", false);
        final String source = FieldUtils.getString(value.get("source"), "source", "any");
        final String method = FieldUtils.getString(value.get("method"), "method", "");

        final FilterableSource bySource = source != null ? environment.getFilterFactory().generateFilter(source, environment) : null;

        DefaultActionSource triggerActionSource = new DefaultActionSource();
        EffectUtils.processRequirementsCostsAndEffects(value, environment, triggerActionSource);

        // `source:` filters on WHAT REMOVED the card, which is no longer the
        // context's source: the context is now built around this card so that
        // `you` and `owner` mean its controller (see BuiltLotroCardBlueprint's
        // four self-removed getters). The remover is read off the effect result
        // instead, which is passed in for exactly this.
        triggerActionSource.addPlayRequirement(
                actionContext -> {
                    if(bySource == null)
                        return true;

                    PhysicalCard remover = removerOf(actionContext.getEffectResult());
                    if(remover == null)
                        return false;

                    return Filters.accepts(actionContext.getGame(), remover, bySource.getFilterable(actionContext));
				});

        if(method.toLowerCase().contains("discard") || method.isEmpty()) {
            if (optional) {
                blueprint.setDiscardedFromPlayOptionalTriggerAction(triggerActionSource);
            }
            else {
                blueprint.setDiscardedFromPlayRequiredTriggerAction(triggerActionSource);
            }
        }

        if(method.toLowerCase().contains("hinder") || method.isEmpty()) {
            if (optional) {
                blueprint.setHinderedFromPlayOptionalTriggerAction(triggerActionSource);
            }
            else {
                blueprint.setHinderedFromPlayRequiredTriggerAction(triggerActionSource);
            }
        }



    }
}
