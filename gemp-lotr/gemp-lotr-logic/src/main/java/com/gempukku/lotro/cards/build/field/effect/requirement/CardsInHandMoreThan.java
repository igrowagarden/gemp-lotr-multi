package com.gempukku.lotro.cards.build.field.effect.requirement;

import com.gempukku.lotro.cards.build.CardGenerationEnvironment;
import com.gempukku.lotro.cards.build.InvalidCardDefinitionException;
import com.gempukku.lotro.cards.build.PlayersSource;
import com.gempukku.lotro.cards.build.Requirement;
import com.gempukku.lotro.cards.build.field.FieldUtils;
import com.gempukku.lotro.cards.build.field.effect.appender.resolver.PlayerResolver;
import org.json.simple.JSONObject;

public class CardsInHandMoreThan implements RequirementProducer {
    @Override
    public Requirement getPlayRequirement(JSONObject object, CardGenerationEnvironment environment) throws InvalidCardDefinitionException {
        FieldUtils.validateAllowedFields(object, "count", "player");

        final int count = FieldUtils.getInteger(object.get("count"), "count");
        final String player = FieldUtils.getString(object.get("player"), "player", "you");

        // A PlayersSource, so `player:` may name a GROUP -- and when it does the
        // requirement is satisfied if ANY of them is over the count.
        //
        // The Mirror of Galadriel (1_55) reads "If AN OPPONENT has at least 7
        // cards in hand", which above two players is "if any of them has", not
        // "if the seat getFirstShadowPlayer names has". Any rather than every,
        // because the card then picks one -- an `every` reading would make the
        // action unavailable whenever a single opponent had a small hand.
        //
        // Both existing users name one player (1_55, and 11_x which omits the
        // field entirely and so defaults to `you`), and a single token wraps to
        // a one-element list, so both are unchanged.
        final PlayersSource playerSource = PlayerResolver.resolvePlayers(player);

        return (actionContext) -> {
            for (String playerId : playerSource.getPlayers(actionContext))
                if (actionContext.getGame().getGameState().getHand(playerId).size() > count)
                    return true;
            return false;
        };
    }
}
