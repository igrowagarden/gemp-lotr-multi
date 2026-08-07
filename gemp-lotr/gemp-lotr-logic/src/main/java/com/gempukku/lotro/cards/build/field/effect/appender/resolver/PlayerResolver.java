package com.gempukku.lotro.cards.build.field.effect.appender.resolver;

import com.gempukku.lotro.cards.build.ActionContext;
import com.gempukku.lotro.cards.build.CardGenerationEnvironment;
import com.gempukku.lotro.cards.build.InvalidCardDefinitionException;
import com.gempukku.lotro.cards.build.PlayerSource;
import com.gempukku.lotro.cards.build.PlayersSource;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.GameUtils;
import com.gempukku.lotro.logic.PlayOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlayerResolver {

    /**
     * A designation that may name several players.
     *
     * `anyShadow` is every Shadow player, in the order the Shadow phases
     * themselves use: counter-clockwise from the Free Peoples player. Turns
     * rotate clockwise but every response window rotates counter-clockwise, and
     * the two are indistinguishable at two players -- so taking the order from
     * GameUtils.getShadowPlayers, which returns seating order, would look
     * correct in every existing test and be wrong at the table.
     *
     * Every other token still names exactly one player and is wrapped, so this
     * is a superset of resolvePlayer rather than a separate vocabulary.
     */
    public static PlayersSource resolvePlayers(String type) throws InvalidCardDefinitionException {
        if (type.equalsIgnoreCase("eachPlayer") || type.equalsIgnoreCase("allPlayers")) {
            // Everyone at the table including the Free Peoples player, in
            // seating order. This is what ForEachPlayer did before it took a
            // player field at all, so it stays that appender's default.
            return (actionContext) ->
                    Arrays.asList(GameUtils.getAllPlayers(actionContext.getGame()));
        }
        if (type.equalsIgnoreCase("anyShadow") || type.equalsIgnoreCase("eachShadow")) {
            return (actionContext) -> {
                final LotroGame game = actionContext.getGame();
                if (game.isSolo())
                    return Collections.emptyList();
                final String fpPlayer = game.getGameState().getCurrentPlayerId();
                final PlayOrder order = game.getGameState().getPlayerOrder()
                        .getCounterClockwisePlayOrder(fpPlayer, false);
                order.getNextPlayer();   // skip the Free Peoples player
                List<String> shadowPlayers = new ArrayList<>();
                String next;
                while ((next = order.getNextPlayer()) != null && !next.equals(fpPlayer))
                    shadowPlayers.add(next);
                return shadowPlayers;
            };
        }
        final PlayerSource single = resolvePlayer(type);
        return (actionContext) -> Collections.singletonList(single.getPlayer(actionContext));
    }

    public static PlayerSource resolvePlayer(String type) throws InvalidCardDefinitionException {
        if (type.equalsIgnoreCase("you"))
            return ActionContext::getPerformingPlayer;
        if (type.equalsIgnoreCase("owner"))
            return (actionContext) -> actionContext.getSource().getOwner();
        else if (type.equalsIgnoreCase("shadowPlayer") || type.equalsIgnoreCase("shadow")
                || type.equalsIgnoreCase("s"))
            return (actionContext) -> GameUtils.getFirstShadowPlayer(actionContext.getGame());
        else if (type.equalsIgnoreCase("fp") || type.equalsIgnoreCase("freeps")
                || type.equalsIgnoreCase("free peoples") || type.equalsIgnoreCase("free people"))
            return ((actionContext) -> actionContext.getGame().getGameState().getCurrentPlayerId());
        else if (type.toLowerCase(Locale.ROOT).startsWith("ownerfrommemory(") && type.endsWith(")")) {
            String memory = type.substring(type.indexOf("(") + 1, type.lastIndexOf(")"));
            return (actionContext) -> {
                final PhysicalCard cardFromMemory = actionContext.getCardFromMemory(memory);
                if (cardFromMemory != null)
                    return cardFromMemory.getOwner();
                else
                    // Sensible default
                    return actionContext.getPerformingPlayer();
            };
        }
        else if (type.toLowerCase().startsWith("frommemory(") && type.endsWith(")")) {
            String memory = type.substring(type.indexOf("(") + 1, type.lastIndexOf(")"));
            return new PlayerSource() {
                @Override
                public String getPlayer(ActionContext actionContext) {
                    return actionContext.getValueFromMemory(memory);
                }

                @Override
                public boolean canPreEvaluate() {
                    return false;
                }
            };
        }
        throw new InvalidCardDefinitionException("Unable to resolve player resolver of type: " + type);
    }
}
