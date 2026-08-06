package com.gempukku.lotro.logic.modifiers;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.game.PhysicalCard;
import com.gempukku.lotro.game.state.LotroGame;

public class ShouldSkipPhaseModifier extends AbstractModifier {
    private final Phase _phase;

    /**
     * Whose phase is skipped, or null for everyone's.
     * <p>
     * This modifier used to ignore the playerId it was handed, so a card reading
     * "skip HIS OR HER next Shadow phase" skipped that phase for every player.
     * Invisible at two players by construction: there is exactly one Shadow
     * player, so "that player skips" and "everyone skips" are the same outcome.
     * <p>
     * Null is kept as the default so the four shared phases -- maneuver, archery,
     * assignment, skirmish -- keep working. Their call sites pass a null playerId
     * because "whose" is meaningless there; only the two per-player phases
     * (fellowship and shadow) pass a real one.
     */
    private final String _playerId;

    public ShouldSkipPhaseModifier(PhysicalCard source, Condition condition, Phase phase) {
        this(source, condition, phase, null);
    }

    public ShouldSkipPhaseModifier(PhysicalCard source, Condition condition, Phase phase, String playerId) {
        super(source, "Skip " + phase.toString() + " phase"
                + (playerId == null ? "" : " for " + playerId), null, condition, ModifierEffect.ACTION_MODIFIER);
        _phase = phase;
        _playerId = playerId;
    }

    @Override
    public boolean shouldSkipPhase(LotroGame game, Phase phase, String playerId) {
        if (phase != _phase)
            return false;
        // Scoped to nobody in particular, or the caller did not say whose phase
        // this is (the shared phases pass null) -- fall back to the old behaviour.
        if (_playerId == null || playerId == null)
            return true;
        return _playerId.equals(playerId);
    }
}
