package com.gempukku.lotro.hall;

import com.gempukku.lotro.db.vo.CollectionType;
import com.gempukku.lotro.db.vo.League;
import com.gempukku.lotro.game.LotroFormat;
import com.gempukku.lotro.league.LeagueSerieInfo;

public record GameSettings(CollectionType collectionType, LotroFormat format, String tournamentId, League league, LeagueSerieInfo leagueSerie,
                           boolean competitive, boolean privateGame, boolean isInviteOnly, boolean hiddenGame,
                           GameTimer timeSettings, String userDescription, boolean isSolo,
                           int seatCount
) {
    /** Seats at a table that does not say otherwise. */
    public static final int DEFAULT_SEAT_COUNT = 2;

    /**
     * Hard ceiling. {@code ChooseSeatingOrderGameProcess._choices} holds exactly
     * five labels ("first".."fifth") and indexes them per seat, so a sixth player
     * throws AIOOBE there. Raising this means extending that array too.
     */
    public static final int MAX_SEAT_COUNT = 5;

    /** Backwards-compatible constructor for the callers that always mean two. */
    public GameSettings(CollectionType collectionType, LotroFormat format, String tournamentId, League league, LeagueSerieInfo leagueSerie,
                        boolean competitive, boolean privateGame, boolean isInviteOnly, boolean hiddenGame,
                        GameTimer timeSettings, String userDescription, boolean isSolo) {
        this(collectionType, format, tournamentId, league, leagueSerie, competitive, privateGame,
                isInviteOnly, hiddenGame, timeSettings, userDescription, isSolo, DEFAULT_SEAT_COUNT);
    }
}
