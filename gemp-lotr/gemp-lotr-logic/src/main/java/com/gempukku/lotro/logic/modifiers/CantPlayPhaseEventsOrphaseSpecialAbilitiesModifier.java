package com.gempukku.lotro.logic.modifiers;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Timeword;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;
import com.gempukku.lotro.logic.timing.Action;

import java.util.Collection;

public class CantPlayPhaseEventsOrphaseSpecialAbilitiesModifier extends AbstractModifier {
    private final Timeword phase;
    /** The players banned, or null for everybody. See CantDiscardFromPlayModifier. */
    private final Collection<String> bannedPlayers;

    public CantPlayPhaseEventsOrphaseSpecialAbilitiesModifier(PhysicalCard source, Condition condition, Phase phase, Collection<String> bannedPlayers) {
        super(source, null, null, condition, ModifierEffect.ACTION_MODIFIER);
        this.phase = Timeword.findByPhase(phase);
        this.bannedPlayers = bannedPlayers;
    }

    @Override
    public boolean canPlayAction(LotroGame game, String performingPlayer, Action action) {
        if ((action.getType() == Action.Type.PLAY_CARD || action.getType() == Action.Type.SPECIAL_ABILITY)
                && action.getActionTimeword() == phase
                && (bannedPlayers == null || bannedPlayers.contains(performingPlayer)))
            return false;
        return true;
    }
}
