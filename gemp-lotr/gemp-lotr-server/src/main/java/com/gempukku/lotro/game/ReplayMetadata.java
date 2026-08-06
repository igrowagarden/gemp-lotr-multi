package com.gempukku.lotro.game;

import com.gempukku.lotro.common.DBDefs;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.game.state.GameEvent;
import com.gempukku.lotro.logic.vo.LotroDeck;
import com.mysql.cj.util.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

public class ReplayMetadata {

    public class DeckMetadata {
        public String Owner;
        public String TargetFormat;
        public String DeckName;
        public List<String> AdventureDeck;
        public List<String> DrawDeck;
        public String RingBearer;
        public String Ring;
        public List<String> StartingFellowship = new ArrayList<>();
    }

    // This field looks like it's unused, but it gets serialized.
    //Version 1: First tracked version
    //Version 2: Adding the highest achieved sites by player, game IDs, and game timer length information
    //Version 3: Fixed a bug where attached cards were never added to the PlayedCards collection
    public Integer MetadataVersion = 3;

    public DBDefs.GameHistory GameReplayInfo;

    public Map<String, DeckMetadata> Decks = new HashMap<>();
    public Map<String, Integer> PlayerIDs = new HashMap<>();
    public Map<String, Integer> Bids = new HashMap<>();
    public String WentFirst;
    public boolean GameStarted = false;
    public boolean Conceded = false;
    public boolean Canceled = false;

    public Map<String, String> AllCards = new HashMap<>();

    public Set<Integer> SeenCards = new HashSet<>();

    public HashSet<Integer> PlayedCards = new HashSet<>();

    public ReplayMetadata() {

    }
    public ReplayMetadata(DBDefs.GameHistory game, Map<String, LotroDeck> decks) {
        GameReplayInfo = game;

        for(var pair : decks.entrySet()) {
            String player = pair.getKey();
            var deck = pair.getValue();
            var metadata = new DeckMetadata() {{
                Owner = player;
                TargetFormat = deck.getTargetFormat();
                DeckName = deck.getDeckName();
                AdventureDeck = deck.getSites();
                DrawDeck = deck.getDrawDeckCards();
                RingBearer = deck.getRingBearer();
                Ring = deck.getRing();
            }};

            Decks.put(player, metadata);
        }

        if(GameReplayInfo.lose_reason.contains("cancelled") || GameReplayInfo.win_reason.contains("cancelled")) {
            Canceled = true;
        }

        if(GameReplayInfo.lose_reason.contains("Concession") || GameReplayInfo.win_reason.contains("Concession")) {
            Conceded = true;
        }
    }

    /**
     * The other player, where there is exactly one -- that is, at two seats.
     *
     * Above two seats "the opponent" is not a thing, so this returns null rather
     * than an arbitrary player. It also no longer throws when the player list is
     * empty: it used to call get() on an empty Optional, which turned an
     * unparsed player list into a NoSuchElementException at the moment the game
     * finished.
     */
    public String GetOpponent(String player) {
        if (PlayerIDs.size() != 2)
            return null;
        return PlayerIDs.keySet().stream().filter(x -> !x.equals(player)).findFirst().orElse(null);
    }

    // Every player in the game, not the first two. The old pattern took exactly
    // two names and used matches(), so at more seats it failed to match at all
    // and left PlayerIDs empty -- which is what made the replay of a finished
    // five-player game throw.
    private final Pattern gameStartPattern = Pattern.compile("Players in the game are: ([\\w~-]+(?:, [\\w~-]+)*)");
    private final Pattern orderPattern = Pattern.compile("([\\w~-]+) has chosen to go (.*)");
    private final Pattern bidPattern = Pattern.compile("([\\w~-]+) bid (\\d+)");
    public void ParseReplay(String player, List<GameEvent> events) {
        GameStarted = false;
        // Who announced a seat. Kept local rather than as a field because every
        // field of this class is serialised into the stored replay metadata.
        Set<String> announcedSeat = new HashSet<>();

        for(var event : events) {
            if(event.getType() == GameEvent.Type.SEND_MESSAGE) {
                var message = event.getMessage();
                if(StringUtils.isNullOrEmpty(message))
                    continue;

                var regex = gameStartPattern.matcher(message);
                if(regex.matches()) {
                    int seat = 1;
                    for(String name : regex.group(1).split(", ")) {
                        PlayerIDs.put(name, seat++);
                    }
                    continue;
                }

                regex = orderPattern.matcher(message);
                if(regex.matches()) {
                    String bidder = regex.group(1);
                    String order = regex.group(2);
                    announcedSeat.add(bidder);
                    if(order.equals("first")) {
                        WentFirst = bidder;
                    }
                    // "second" no longer implies who went first. It did at two
                    // seats, where the only other player must have gone first;
                    // at five it names one of four, so it is left to the
                    // by-elimination pass below.
                    continue;
                }

                regex = bidPattern.matcher(message);
                if(regex.matches()) {
                    String bidder = regex.group(1);
                    String bid = regex.group(2);
                    Bids.put(bidder, Integer.valueOf(bid));
                    continue;
                }
            }
            else if(!GameStarted && event.getType() == GameEvent.Type.GAME_PHASE_CHANGE) {
                var phase = Phase.findPhase(event.getPhase());
                if (phase == Phase.BETWEEN_TURNS)
                {
                    GameStarted = true;
                }
            }

            else if(event.getType() == GameEvent.Type.PUT_CARD_INTO_PLAY) {

                var bpID = event.getBlueprintId();
                var cardID = event.getCardId();
                var participantID = event.getParticipantId();
                var zone = event.getZone();
                var targetCardID = event.getTargetCardId();

                if (bpID != null && cardID != null && participantID != null && participantID.equals(player)) {
                    if (!GameStarted && (zone.equals(Zone.FREE_CHARACTERS)) || zone.equals(Zone.ATTACHED)) {
                        Decks.get(player).StartingFellowship.add(bpID);
                    }

                    switch (zone) {
                        case ATTACHED, FREE_CHARACTERS, SUPPORT, SHADOW_CHARACTERS, ADVENTURE_PATH, VOID_FROM_HAND, VOID -> {
                            AllCards.put(cardID.toString(), bpID);
                            SeenCards.add(cardID);
                            PlayedCards.add(cardID);
                        }
                        case HAND, STACKED, DEAD, DISCARD, ADVENTURE_DECK, DECK, REMOVED -> {
                            AllCards.put(cardID.toString(), bpID);
                            SeenCards.add(cardID);
                        }
                    }
                }
            }
        }

        // Seating announces one message per player who *chose* a seat; the last
        // player is seated silently and says nothing. So if nobody claimed the
        // first seat, it belongs to the one player who never announced one.
        //
        // At two seats this is exactly the old inference -- one player chooses,
        // "X has chosen to go second" leaves only the other -- and it holds at
        // any number of seats for the same reason. Only assigned when the silent
        // player is unique, so a replay whose messages did not parse leaves
        // WentFirst null rather than naming someone at random.
        if(WentFirst == null) {
            List<String> silent = new ArrayList<>();
            for(String candidate : PlayerIDs.keySet()) {
                if(!announcedSeat.contains(candidate))
                    silent.add(candidate);
            }
            if(silent.size() == 1)
                WentFirst = silent.get(0);
        }
    }
}
