package com.gempukku.lotro.cards.build.field.effect.modifier;

import com.gempukku.lotro.cards.build.*;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.EffectAppender;
import com.gempukku.lotro.common.Filterable;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.actions.CostToEffectAction;
import com.gempukku.lotro.logic.actions.PlayEventAction;
import com.gempukku.lotro.logic.modifiers.AbstractExtraPlayCostModifier;
import org.json.simple.JSONObject;

public class ExtraCostToPlay implements ModifierSourceProducer {
    @Override
    public ModifierSource getModifierSource(JSONObject object, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(object, "requires", "cost", "filter", "requiresRanger");

        final JSONObject[] conditionArray = FieldUtils.getObjectArray(object.get("requires"), "requires");
        final String filter = FieldUtils.getString(object.get("filter"), "filter");

        final FilterableSource filterableSource = environment.getFilterFactory().generateFilter(filter, environment);
        final Requirement[] requirements = environment.getRequirementFactory().getRequirements(conditionArray, environment);

        final JSONObject[] effectArray = FieldUtils.getObjectArray(object.get("cost"), "cost");
        final EffectAppender[] effectAppenders = environment.getEffectAppenderFactory().getEffectAppenders(true, effectArray, environment);

        final boolean requiresRanger = FieldUtils.getBoolean(object.get("requiresRanger"), "requiresRanger", false);

        return (actionContext) -> {
            final Filterable filterable = filterableSource.getFilterable(actionContext);

            return new AbstractExtraPlayCostModifier(actionContext.getSource(), "Cost to play is modified", filterable,
                    RequirementCondition.createCondition(requirements, actionContext)) {
                @Override
                public void appendExtraCosts(LotroGame game, CostToEffectAction action, PhysicalCard card) {
                    if (!requiresRanger || (requiresRanger && action instanceof PlayEventAction playEventAction && playEventAction.isRequiresRanger())) {
                        for (EffectAppender effectAppender : effectAppenders)
                            effectAppender.appendEffect(true, action, payerContext(actionContext, card));
                    }
                }

                @Override
                public boolean canPayExtraCostsToPlay(LotroGame game, PhysicalCard card) {
                    for (EffectAppender effectAppender : effectAppenders) {
                        if (!effectAppender.isPlayableInFull(payerContext(actionContext, card)))
                            return false;
                    }

                    return true;
                }
            };
        };
    }

    /**
     * A granted cost is paid by the player playing the card it was granted TO,
     * not by the owner of the card that granted it.
     *
     * The modifier's own context is built by
     * BuiltLotroCardBlueprint.getModifiers as
     * {@code new DefaultActionContext(self.getOwner(), ...)}, where {@code self}
     * is the GRANTING card. Appending the cost against that context makes
     * {@code player: you} and {@code player: owner} both resolve to the granting
     * card's owner, and leaves no token at all that can name the player actually
     * paying -- the context never sees the card being played. Balin Avenged
     * (17_2) grants "To play, remove an [orc] card from YOUR discard pile" to
     * every [orc] Orc, and above two players that "your" is whichever Shadow
     * player is playing the Orc. Measured by MultiplayerGrantedCostAtTest.
     *
     * Delegating rebinds only the performing player: {@code getSource()} stays
     * the granting card, so {@code owner} and any source-relative filter are
     * unchanged. {@code card.getOwner()} is PlayUtils' own idiom for "the player
     * playing this card" -- it is what {@code getValidTargetFilter},
     * {@code canPlayCard} and {@code getPlayEventCardAction} are all handed on
     * the same code path, and what PlayPermanentAction sets as its performing
     * player. Using it in BOTH methods keeps the playability gate and the
     * payment naming the same seat; letting those two drift is the bug this
     * branch already shipped once on the prevention path.
     */
    private static ActionContext payerContext(ActionContext grantingContext, PhysicalCard card) {
        return new DelegateActionContext(grantingContext, card.getOwner());
    }
}
