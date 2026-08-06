package com.gempukku.lotro.cards.build;

import java.util.List;

/**
 * A designation that may name more than one player.
 *
 * {@link PlayerSource} returns a single name, which is all "the Shadow player"
 * ever needed when there was exactly one. Cards that say "Any Shadow player may
 * ... to prevent this" designate a group, and at more than two players that
 * group has to be offered the choice in turn rather than collapsed to whichever
 * seat happens to be first.
 */
public interface PlayersSource {
    List<String> getPlayers(ActionContext actionContext);
}
