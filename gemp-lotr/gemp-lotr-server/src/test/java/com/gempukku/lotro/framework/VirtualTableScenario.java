package com.gempukku.lotro.framework;

import com.gempukku.lotro.cards.build.ActionContext;
import com.gempukku.lotro.cards.build.DefaultActionContext;
import com.gempukku.lotro.game.*;
import com.gempukku.lotro.game.PhysicalCardImpl;
import com.gempukku.lotro.game.formats.LotroFormatLibrary;
import com.gempukku.lotro.game.state.GameExtraInfo;
import com.gempukku.lotro.game.state.GameState;
import com.gempukku.lotro.game.state.RTMDGameInfo;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.timing.DefaultLotroGame;
import com.gempukku.lotro.logic.vo.LotroDeck;
import com.gempukku.lotro.packs.PackBox;
import com.gempukku.lotro.packs.ProductLibrary;
import com.gempukku.lotro.packs.ProductLibraryPackOpener;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class VirtualTableScenario implements TestBase, TestConstants, Actions, AdHocEffects, CardProperties, Choices, Decisions,
        GameProcedures, GameProperties, PileProperties, Skirmishes, ZoneManipulation {

    protected static LotroCardBlueprintLibrary _cardLibrary;
    protected static LotroFormatLibrary _formatLibrary;
    protected static ProductLibrary _productLibrary;

    static {
        _cardLibrary = new LotroCardBlueprintLibrary();
        _formatLibrary = new LotroFormatLibrary(new DefaultAdventureLibrary(), _cardLibrary);
        _productLibrary = new ProductLibrary(_cardLibrary);
    }

    public static LotroCardBlueprint FindCard(String bpid) throws CardNotFoundException {
        return _cardLibrary.getLotroCardBlueprint(bpid);
    }

    public static LotroFormat FindFormat(String code) throws CardNotFoundException {
        return _formatLibrary.getFormat(code);
    }

    public static PackBox FindProduct(String name) throws CardNotFoundException {
        return _productLibrary.GetProduct(name);
    }

    // Player key, then name/card
    private Map<String, Map<String, PhysicalCardImpl>> Cards = new HashMap<>();
    private DefaultLotroGame _game;
    private GameState _gameState;
    private DefaultUserFeedback _userFeedback;
    private final ActionContext _freepsFilterContext = new DefaultActionContext(P1, _game, null, null, null);
    private final ActionContext _shadowFilterContext = new DefaultActionContext(P2, _game, null, null, null);

    /*
     * These functions are all used by the various interfaces and not so much in tests or in this class itself.
     */
    public DefaultLotroGame game() { return _game; }
    public GameState gameState() { return _gameState; }
    public DefaultUserFeedback userFeedback() { return _userFeedback; }
    public ActionContext FreepsFilterContext() { return _freepsFilterContext; }
    public ActionContext ShadowFilterContext() { return _shadowFilterContext; }

    public VirtualTableScenario(HashMap<String, String> cardIDs) throws CardNotFoundException, DecisionResultInvalidException {
        this(cardIDs, null, null, null, Multipath, null, null, null);
    }

    /**
     * A table seating more than two players.
     *
     * The two-player constructors cannot express the questions multiplayer
     * raises. "Is every Shadow player offered this, and in what order?" has no
     * meaning when there is one opponent, and any ordering bug is invisible
     * because every order of one element is the same order. Everything below
     * the deck map is already player-count agnostic -- DefaultLotroGame takes a
     * Map of decks -- so this adds seats rather than changing anything.
     *
     * Every seat gets the same deck, so a test can put the same card in any
     * player's hand and address seats by {@link TestConstants#P1} .. P5.
     *
     * @param playerCount how many seats, from 2 to 5
     */
    public static VirtualTableScenario MultiplayerTable(int playerCount, HashMap<String, String> cardIDs)
            throws CardNotFoundException, DecisionResultInvalidException {
        return new VirtualTableScenario(playerCount, cardIDs, null, null, null, Multipath);
    }

    public VirtualTableScenario(int playerCount, HashMap<String, String> cardIDs,
                                HashMap<String, String> siteIDs, String ringBearerID, String ringID,
                                String format) throws CardNotFoundException, DecisionResultInvalidException {
        if (playerCount < 2 || playerCount > 5)
            throw new IllegalArgumentException("A table seats between 2 and 5 players, not " + playerCount);

        if (siteIDs == null) siteIDs = KingSites;
        if (ringBearerID == null) ringBearerID = MDFrodo;
        if (ringID == null) ringID = RulingRing;

        List<String> seats = SeatNames(playerCount);
        Map<String, LotroDeck> decks = new HashMap<>();
        for (String seat : seats) {
            LotroDeck deck = new LotroDeck(seat + "'s deck");
            for (String id : siteIDs.values())
                deck.addSite(id);
            for (String id : cardIDs.values())
                deck.addCard(id);
            deck.setRingBearer(ringBearerID);
            deck.setRing(ringID);
            decks.put(seat, deck);
        }

        InitializeGameWithDecks(decks, format, null, null);
        BidAndSeatPlayers(playerCount);

        for (String seat : seats) {
            Cards.put(seat, new HashMap<>());
            // Undo the opening draw so a test only holds what it puts there.
            for (var card : _gameState.getHand(seat).stream().toList())
                MoveCardsToTopOfDeck((PhysicalCardImpl) card);
            NameCardsInDeck(seat, cardIDs);
            NameCardsInAdventureDeck(seat, cardIDs, siteIDs);
        }
    }

    /** The seat names for a table of this size, in seating order. */
    public static List<String> SeatNames(int playerCount) {
        List<String> all = List.of(P1, P2, P3, P4, P5);
        return all.subList(0, playerCount);
    }

    private void NameCardsInDeck(String seat, HashMap<String, String> cardIDs) {
        if (cardIDs == null)
            return;
        for (var card : _game.getGameState().getDeck(seat)) {
            cardIDs.entrySet().stream()
                    .filter(x -> x.getValue().equals(card.getBlueprintId())
                            && !Cards.get(seat).containsKey(x.getKey()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(name -> Cards.get(seat).put(name, (PhysicalCardImpl) card));
        }
    }

    private void NameCardsInAdventureDeck(String seat, HashMap<String, String> cardIDs,
                                          HashMap<String, String> siteIDs) {
        if (siteIDs == null)
            return;
        for (var card : _game.getGameState().getAdventureDeck(seat)) {
            String name = null;
            if (cardIDs != null) {
                name = cardIDs.entrySet().stream()
                        .filter(x -> x.getValue().equals(card.getBlueprintId())
                                && !Cards.get(seat).containsKey(x.getKey()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
            }
            if (name == null) {
                name = siteIDs.entrySet().stream()
                        .filter(x -> x.getValue().equals(card.getBlueprintId()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
            }
            if (name != null)
                Cards.get(seat).put(name, (PhysicalCardImpl) card);
        }
    }

    public VirtualTableScenario(HashMap<String, String> cardIDs, HashMap<String, String> siteIDs, String ringBearerID, String ringID) throws CardNotFoundException, DecisionResultInvalidException {
        this(cardIDs, siteIDs, ringBearerID, ringID, Multipath, null,null, null);
    }

    public VirtualTableScenario(HashMap<String, String> cardIDs,
            HashMap<String, String> siteIDs,
            String ringBearerID, String ringID,
            String format) throws CardNotFoundException, DecisionResultInvalidException {
        this(cardIDs, siteIDs, ringBearerID, ringID, format, null, null, null);
    }

    public VirtualTableScenario(HashMap<String, String> cardIDs,
            HashMap<String, String> siteIDs,
            String ringBearerID, String ringID,
            String freepsMetaSiteId, String shadowMetaSiteId) throws CardNotFoundException, DecisionResultInvalidException {
        this(cardIDs, siteIDs, ringBearerID, ringID, Multipath, freepsMetaSiteId, shadowMetaSiteId, null);
    }

    public VirtualTableScenario(HashMap<String, String> cardIDs,
            HashMap<String, String> siteIDs,
            String ringBearerID, String ringID,
            String freepsMetaSiteId, String shadowMetaSiteId,
            Consumer<VirtualTableScenario> bidHandler) throws CardNotFoundException, DecisionResultInvalidException {
        this(cardIDs, siteIDs, ringBearerID, ringID, Multipath, freepsMetaSiteId, shadowMetaSiteId, bidHandler);
    }

    public VirtualTableScenario(HashMap<String, String> cardIDs,
            HashMap<String, String> siteIDs,
            String ringBearerID, String ringID,
            String format,
            String freepsMetaSiteId, String shadowMetaSiteId,
            Consumer<VirtualTableScenario> bidHandler) throws CardNotFoundException, DecisionResultInvalidException {

        if(siteIDs == null ) {
            siteIDs = KingSites;
        }

        if(ringBearerID == null ) {
            ringBearerID = MDFrodo;
        }

        if(ringID == null) {
            ringID = RulingRing;
        }

        Map<String, LotroDeck> decks = new HashMap<>();
        decks.put(P1, new LotroDeck(P1 + "'s deck"));
        decks.put(P2, new LotroDeck(P2 + "'s deck"));

        // Strictly speaking, the names don't matter all that much, but in the event that the tester wants to retrieve
        // a specific card from deck by name, then if there are any duplicates the one returned will be random, which
        // can lead to stochastic tests that randomly fail.

        for(String name : siteIDs.keySet()) {
            String id = siteIDs.get(name);
            decks.get(P1).addSite(id);
            decks.get(P2).addSite(id);
        }

        for(String name : cardIDs.keySet()) {
            String id = cardIDs.get(name);
            decks.get(P1).addCard(id);
            decks.get(P2).addCard(id);
        }

        // Now that all the helper parameters have been stuffed into the decklist, we now populate an actual deck for each player.

        decks.get(P1).setRingBearer(ringBearerID);
        decks.get(P2).setRingBearer(ringBearerID);

        decks.get(P1).setRing(ringID);
        decks.get(P2).setRing(ringID);

        InitializeGameWithDecks(decks, format, freepsMetaSiteId, shadowMetaSiteId);

        //We want to handle this at this time before the game start process because certain game properties aren't
        // available until the player order has been determined.
        if (bidHandler != null) {
            bidHandler.accept(this);
        } else {
            BidAndSeatPlayers();
        }

        Cards.put(P1, new HashMap<>());
        Cards.put(P2, new HashMap<>());

        // Now that the game has been initialized, we reset any automatic drawing that was performed as part of startup
        for(var card : _gameState.getHand(P2).stream().toList()) {
            MoveCardsToTopOfDeck((PhysicalCardImpl)card);
        }

        // Next we associate all the physically-instantiated cards with the human-readable names they were given by the
        // tester.  This will now permit us to nab the exact card from anywhere without searching or collisions.
        if(cardIDs != null) {
            for(var card : _game.getGameState().getHand(P1).stream().toList()) {
                MoveCardsToTopOfDeck((PhysicalCardImpl)card);
            }

            for (var card : _game.getGameState().getDeck(P1)) {
                String name = cardIDs.entrySet()
                        .stream()
                        .filter(x -> x.getValue().equals(card.getBlueprintId()) && !Cards.get(P1).containsKey(x.getKey()))
                        .map(Map.Entry::getKey)
                        .findFirst().get();

                Cards.get(P1).put(name, (PhysicalCardImpl) card);
            }

            for(var card : _game.getGameState().getHand(P2).stream().toList()) {
                MoveCardsToTopOfDeck((PhysicalCardImpl)card);
            }

            for (var card : _game.getGameState().getDeck(P2)) {
                String name = cardIDs.entrySet()
                        .stream()
                        .filter(x -> x.getValue().equals(card.getBlueprintId()) && !Cards.get(P2).containsKey(x.getKey()))
                        .map(Map.Entry::getKey)
                        .findFirst().get();

                Cards.get(P2).put(name, (PhysicalCardImpl) card);
            }
        }

        if(siteIDs != null) {
            for (var card : _game.getGameState().getAdventureDeck(P1)) {
                String name = null;

                if(cardIDs != null) {
                    name = cardIDs.entrySet()
                            .stream()
                            .filter(x -> x.getValue().equals(card.getBlueprintId()) && !Cards.get(P1).containsKey(x.getKey()))
                            .map(Map.Entry::getKey)
                            .findFirst().orElse(null);
                }

                if(name == null) {
                    name = siteIDs.entrySet()
                            .stream()
                            .filter(x -> x.getValue().equals(card.getBlueprintId()))
                            .map(Map.Entry::getKey)
                            .findFirst().get();
                }

                Cards.get(P1).put(name, (PhysicalCardImpl) card);
            }

            for (var card : _game.getGameState().getAdventureDeck(P2)) {
                String name = null;

                if(cardIDs != null) {
                    name = cardIDs.entrySet()
                            .stream()
                            .filter(x -> x.getValue().equals(card.getBlueprintId()) && !Cards.get(P2).containsKey(x.getKey()))
                            .map(Map.Entry::getKey)
                            .findFirst().orElse(null);
                }

                if(name == null) {
                    name = siteIDs.entrySet()
                            .stream()
                            .filter(x -> x.getValue().equals(card.getBlueprintId()))
                            .map(Map.Entry::getKey)
                            .findFirst().get();
                }

                Cards.get(P2).put(name, (PhysicalCardImpl) card);
            }
        }

        // Create and place meta-site modifiers in SUPPORT zone if provided
        if (freepsMetaSiteId != null) {
            var freepsMod = _gameState.getMetaSites(P1).getFirst();
            Cards.get(P1).put("mod", (PhysicalCardImpl)freepsMod);
        }
        if (shadowMetaSiteId != null) {
            var shadowMod = _gameState.getMetaSites(P2).getFirst();
            Cards.get(P2).put("mod", (PhysicalCardImpl)shadowMod);
        }
    }

    /**
     * Returns a card from the Free Peoples player's deck by its human-readable test alias.
     * @param cardName The human-readable name assigned at the top of each test class.
     * @return The physical card that was instantiated for the game.
     */
    public PhysicalCardImpl GetFreepsCard(String cardName) {
        var card =  Cards.get(P1).get(cardName);
        if(card == null) {
            throw new NullPointerException("Card '" + cardName + "' does not exist for Free Peoples.");
        }
        return card;
    }

    /**
     * A named card belonging to a specific seat.
     *
     * At two players the Free Peoples player is always P1, so GetFreepsCard is
     * enough. Above two, seating is decided by a shuffle inside
     * ChooseSeatingOrderGameProcess -- equal bids keep their shuffled order --
     * so which seat holds the Free Peoples role varies between runs and a test
     * has to ask rather than assume.
     */
    public PhysicalCardImpl GetCardFor(String playerId, String cardName) {
        var seat = Cards.get(playerId);
        if (seat == null)
            throw new NullPointerException("No seat named '" + playerId + "' at this table.");
        var card = seat.get(cardName);
        if (card == null)
            throw new NullPointerException("Card '" + cardName + "' does not exist for " + playerId + ".");
        return card;
    }

    /** Whoever holds the Free Peoples role right now. */
    public String FreePeoplesPlayer() {
        return _gameState.getCurrentPlayerId();
    }
    /**
     * Returns a card from the Shadow player's deck by its human-readable test alias.
     * @param cardName The human-readable name assigned at the top of each test class.
     * @return The physical card that was instantiated for the game.
     */
    public PhysicalCardImpl GetShadowCard(String cardName) {
        var card =  Cards.get(P2).get(cardName);
        if(card == null) {
            throw new NullPointerException("Card '" + cardName + "' does not exist for Shadow.");
        }
        return card;
    }
    /**
     * Returns a given player's card by its human-readable test alias.
     * @param player The player to look up a card for.
     * @param cardName The human-readable name assigned at the top of each test class.
     * @return The physical card that was instantiated for the game.
     */
    public PhysicalCardImpl GetCard(String player, String cardName) { return Cards.get(player).get(cardName); }
    public PhysicalCardImpl GetFreepsCardByID(String id) { return GetCardByID(P1, Integer.parseInt(id)); }
    public PhysicalCardImpl GetFreepsCardByID(int id) { return GetCardByID(P1, id); }
    public PhysicalCardImpl GetShadowCardByID(String id) { return GetCardByID(P2, Integer.parseInt(id)); }
    public PhysicalCardImpl GetShadowCardByID(int id) { return GetCardByID(P2, id); }
    public PhysicalCardImpl GetCardByID(String player, int id) {
        return Cards.get(player).values().stream()
                .filter(x -> x.getCardId() == id)
                .findFirst().orElse(null);
    }

    /**
     * Starts up a game of LOTR-TCG with the given decks and format.  This is used internally but may have use in certain
     * complicated test scenarios.  The vast majority of the time you do not need this.
     * @param decks A map of decks for each player in the game; key is the player name.
     * @param formatName Name of the format this table should be following.
     * @param freepsMetaSiteId Blueprint ID of the Race to Mount Doom meta-sites used by player 1, or else null
     * @param shadowMetaSiteId Blueprint ID of the Race to Mount Doom meta-sites used by player 2, or else null
     */
    public void InitializeGameWithDecks(Map<String, LotroDeck> decks, String formatName, String freepsMetaSiteId, String shadowMetaSiteId) {
        _userFeedback = new DefaultUserFeedback();

        var format = _formatLibrary.getFormat(formatName);

        GameExtraInfo info = null;
        if(freepsMetaSiteId != null || shadowMetaSiteId != null) {
            var playerMetaSites = new HashMap<String, List<RTMDGameInfo.MetaSitePair>>();
            if(freepsMetaSiteId != null) {
                playerMetaSites.put(P1, Collections.singletonList(new RTMDGameInfo.MetaSitePair("90_1", freepsMetaSiteId)));
            }
            if(shadowMetaSiteId != null) {
                playerMetaSites.put(P2, Collections.singletonList(new RTMDGameInfo.MetaSitePair("90_1", shadowMetaSiteId)));
            }
            info = new RTMDGameInfo(playerMetaSites);
        }

        _game = new DefaultLotroGame(format, decks, _userFeedback, _cardLibrary, info);
        _userFeedback.setGame(_game);
        _game.startGame();
        _game.setPackOpener(new ProductLibraryPackOpener(_productLibrary));
        _gameState = _game.getGameState();
    }

    /**
     * When testing the live mid-game booster pack opening, we obviously want to be able to have deterministic
     * booster pack results, which requires that we override the default pack opener with a custom one.
     */
    public void UseTestBoosters() {
        _game.setPackOpener(new TestPackOpener(_productLibrary));
    }

    private final List<PhysicalCardImpl> _createdCards = new java.util.ArrayList<>();

    /**
     * Registers a callback on the game state that captures any cards created dynamically
     * mid-game (e.g. from booster pack opening). Call this before the effect that creates cards.
     * Retrieved cards can be accessed via {@link #GetCreatedCards()} or {@link #GetLastCreatedCard()}.
     */
    public void StartCardCapture() {
        _createdCards.clear();
        _gameState.setCardCreationCallback(card -> _createdCards.add((PhysicalCardImpl) card));
    }

    /**
     * Stops capturing dynamically created cards and clears the callback.
     */
    public void StopCardCapture() {
        _gameState.setCardCreationCallback(null);
    }

    /**
     * Returns all cards captured since the last {@link #StartCardCapture()} call.
     */
    public List<PhysicalCardImpl> GetCreatedCards() {
        return java.util.Collections.unmodifiableList(_createdCards);
    }

    /**
     * Returns the most recently captured card, or null if none were captured.
     */
    public PhysicalCardImpl GetLastCreatedCard() {
        return _createdCards.isEmpty() ? null : _createdCards.getLast();
    }

    /**
     * Handles the bidding and seating placement of both players, then removes the burdens that were bid for consistency.
     */
    public void BidAndSeatPlayers() {
        FreepsDecided("1");
        ShadowDecided( "0");

        // Seating choice
        FreepsDecided("0");
    }

    /**
     * Bidding and seating for a table of any size.
     *
     * Driven off whoever actually has a pending decision rather than a fixed
     * script. Bidding is simultaneous but seating is not: N-1 players choose in
     * bid order and the last is placed silently, so the sequence depends on the
     * bids and cannot be written down in advance for an arbitrary table.
     *
     * P1 bids one burden and everyone else bids nothing, which is what the
     * two-player version does and is what makes the result deterministic: the
     * highest bidder chooses first, takes the first seat, and is therefore the
     * Free Peoples player on turn one. With every player bidding the same, the
     * tie is broken arbitrarily and the table seats in a different order from
     * run to run -- which showed up as a test that passed alone and failed in
     * the suite.
     */
    public void BidAndSeatPlayers(int playerCount) {
        if (playerCount == 2) {
            BidAndSeatPlayers();
            return;
        }

        // N bids, then N-1 seating choices; the last player is seated silently.
        // At two players that is the three answers the two-player version gives,
        // so this is the same contract generalised rather than a new one.
        int answersExpected = (playerCount * 2) - 1;
        for (int answered = 0; answered < answersExpected; answered++) {
            var pending = new java.util.ArrayList<>(_userFeedback.getUsersPendingDecision());
            if (pending.isEmpty())
                break;
            // Deterministic order, so a failing test fails the same way twice.
            pending.sort(java.util.Comparator.comparingInt(SeatNames(playerCount)::indexOf));
            String playerId = pending.get(0);
            var decision = _userFeedback.getAwaitingDecision(playerId);
            if (decision == null)
                break;

            String answer;
            if (decision.getDecisionType() == com.gempukku.lotro.logic.decisions.AwaitingDecisionType.INTEGER
                    && playerId.equals(SeatNames(playerCount).get(0))) {
                answer = "1";   // outbid the table, so this seat chooses first
            } else {
                answer = LowestValidAnswer(decision);
            }
            PlayerDecided(playerId, answer);
        }
    }

    /**
     * The least eventful answer to a pregame decision: bid nothing, take the
     * first seat offered. Answered by decision type rather than by position in a
     * script, because the seating questions depend on what the bids were.
     */
    private static String LowestValidAnswer(com.gempukku.lotro.logic.decisions.AwaitingDecision decision) {
        var params = decision.getDecisionParameters();
        switch (decision.getDecisionType()) {
            case INTEGER:
                return params.containsKey("min") ? params.get("min")[0] : "0";
            case MULTIPLE_CHOICE:
            case ACTION_CHOICE:
                return "0";
            default:
                return "";
        }
    }

    /**
     * Passes through certain setup steps at the start of the game so our test may begin at the Free Peoples player's
     * Fellowship phase.  Resets the hand so that the only cards in hand are those the tester defines manually
     * before calling this function.
     */
    public void StartGame() {
        StartGame(true);
    }

    /**
     * Passes through certain setup steps at the start of the game so our test may begin at the Free Peoples player's
     * Fellowship phase.  Auto-selects the provided site as the first site 1; this is only useful when using the
     * Shadow path.  Will reset the hand back to replace the initial drawn hand.
     * @param site1 The site that Free Peoples should start on.
     */
    public void StartGame(PhysicalCardImpl site1) {
        FreepsChooseCardBPFromSelection(site1);
        StartGame(true);
    }

    /**
     * Passes through certain setup steps at the start of the game so our test may begin at the Free Peoples player's
     * Fellowship phase.
     * @param resetHand If true, any cards drawn at the start of the game will be placed back on top of the draw deck,
     *                  ensuring that each player only has the cards in their hand that the tester manually places
     *                  before calling StartGame.  This ensures that there are no confounding variables.
     *                  If false, the default drawn hand will remain untouched.
     */
    public void StartGame(boolean resetHand) {
        StartGame(resetHand, true);
    }

    /**
     * Passes through certain setup steps at the start of the game so our test may begin at the Free Peoples player's
     * Fellowship phase.
     * @param resetHand If true, any cards drawn at the start of the game will be placed back on top of the draw deck.
     * @param skipStartingFellowships If true, auto-skips the starting fellowship selection for both players.
     *                                 If false, the test must handle starting fellowship selection manually before
     *                                 this method proceeds to mulligans.
     */
    public void StartGame(boolean resetHand, boolean skipStartingFellowships) {
        var freepsHand = GetFreepsHand().stream().toList();
        var shadowHand = GetShadowHand().stream().toList();

        if (skipStartingFellowships) {
            SkipStartingFellowships();
        }

        // As a convenience, we want the tester to be able to stack their hand and other piles before the game begins.
        // However, since a new hand will be drawn, this tramples over the careful stacking, so we will reset the
        // state of the deck + hand to what they were before the card draw.
        if(resetHand) {
            for(var card : GetFreepsHand().stream().toList().reversed()) {
                if(!freepsHand.contains(card)) {
                    MoveCardsToTopOfDeck((PhysicalCardImpl) card);
                }
            }

            for(var card : GetShadowHand().stream().toList().reversed()) {
                if(!shadowHand.contains(card)) {
                    MoveCardsToTopOfDeck((PhysicalCardImpl) card);
                }
            }
        }

        SkipMulligans();
        //Get rid of the bidded burden so that tests don't have to remember it exists all the time
        RemoveBurdens(1);
    }

    /**
     * When both players are asked to select a starting fellowship, this can be used to skip past the prompt and choose
     * 0 companions to go down (since you can cheat in any companions you may need for the test scenario anyway).
     */
    public void SkipStartingFellowships() {
        if(FreepsDecisionAvailable("Starting fellowship")) {
            FreepsChoose("");
        }
        if(ShadowDecisionAvailable("Starting fellowship")) {
            ShadowChoose("");
        }
    }

    /**
     * When both players are prompted to mulligan their hand, this can be used to choose "no" for both players.
     */
    public void SkipMulligans() {
        if(FreepsDecisionAvailable("Do you wish to mulligan")) {
            FreepsChooseNo();
        }
        if(ShadowDecisionAvailable("Do you wish to mulligan")) {
            ShadowChooseNo();
        }
    }

    /**
     * Low-level function used by the rest of the test rig to return a decision result back to the server.  This is the
     * beating heart of what is essentially a headless client.  You do not need to call this manually during tests.
     * @param player The player making the decision
     * @param answer What decision is being returned to the server
     */
    public void PlayerDecided(String player, String answer) {
        var decision = userFeedback().getAwaitingDecision(player);
        userFeedback().participantDecided(player);
        try {
            decision.decisionMade(answer);
        } catch (DecisionResultInvalidException exp) {
            userFeedback().sendAwaitingDecision(player, decision);
            throw new RuntimeException(exp);
        }
        game().carryOutPendingActionsUntilDecisionNeeded();
    }




}
