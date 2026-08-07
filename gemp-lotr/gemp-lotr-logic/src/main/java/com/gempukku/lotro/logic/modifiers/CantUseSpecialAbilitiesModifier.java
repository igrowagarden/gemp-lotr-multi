package com.gempukku.lotro.logic.modifiers;

import com.gempukku.lotro.common.Filterable;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Timeword;
import com.gempukku.lotro.filters.Filter;
import com.gempukku.lotro.filters.Filters;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.timing.Action;

import java.util.Collection;

public class CantUseSpecialAbilitiesModifier extends AbstractModifier {
    private final Timeword phase;
    /** The players banned, or null for everybody. See CantDiscardFromPlayModifier. */
    private final Collection<String> bannedPlayers;
    private final Filter sourceFilters;

    public CantUseSpecialAbilitiesModifier(PhysicalCard source, Condition condition, Phase phase, Collection<String> bannedPlayers, Filterable... sourceFilters) {
        super(source, null, null, condition, ModifierEffect.ACTION_MODIFIER);
        this.phase = Timeword.findByPhase(phase);
        this.bannedPlayers = bannedPlayers;
        this.sourceFilters = Filters.and(sourceFilters);
    }

    @Override
    public boolean canPlayAction(LotroGame game, String performingPlayer, Action action) {
        if (action.getType() == Action.Type.SPECIAL_ABILITY
                && (phase == null || action.getActionTimeword() == phase)
                && (bannedPlayers == null || bannedPlayers.contains(performingPlayer))
                && sourceFilters.accepts(game, action.getActionSource()))
            return false;
        return true;
    }
}
