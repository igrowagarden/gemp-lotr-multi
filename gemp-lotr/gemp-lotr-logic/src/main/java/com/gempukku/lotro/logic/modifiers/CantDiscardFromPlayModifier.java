package com.gempukku.lotro.logic.modifiers;

import com.gempukku.lotro.common.Filterable;
import com.gempukku.lotro.filters.Filter;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;

import java.util.Collection;

public class CantDiscardFromPlayModifier extends AbstractModifier {
    /**
     * The players the prohibition applies to, or null for everybody.
     *
     * This was a single String, which could not say "no opponent may" -- only
     * "this one seat may not". Above two players those differ, and the cards
     * that want the former say so in print: Pippin (1_306) "your opponent may
     * not discard your [shire] tales", Haleth (15_128) "those mounts cannot be
     * discarded by a Shadow player".
     *
     * NULL STILL MEANS EVERYONE, which is what RingBearerRule relies on, and a
     * single-player token wraps to a one-element list, so every existing user
     * is unchanged by construction.
     */
    private final Collection<String> bannedPlayers;
    private final Filter _sourceFilter;

    public CantDiscardFromPlayModifier(PhysicalCard source, String text, Condition condition, Collection<String> bannedPlayers, Filterable affectFilter, Filterable sourceFilter) {
        super(source, text, affectFilter, condition, ModifierEffect.DISCARD_FROM_PLAY_MODIFIER);
        this.bannedPlayers = bannedPlayers;
        _sourceFilter = Filters.changeToFilter(sourceFilter);
    }

    @Override
    public boolean canBeDiscardedFromPlay(LotroGame game, String performingPlayer, PhysicalCard card, PhysicalCard source) {

        if (bannedPlayers != null && !bannedPlayers.contains(performingPlayer))
            return true;

        if (_sourceFilter.accepts(game, source))
            return false;

        return true;
    }
}
