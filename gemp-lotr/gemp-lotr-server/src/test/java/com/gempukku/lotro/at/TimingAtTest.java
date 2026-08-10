package com.gempukku.lotro.at;

import com.gempukku.lotro.cards.build.LotroCardBlueprintBuilder;
import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.Token;
import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.game.*;
import com.gempukku.lotro.logic.decisions.AwaitingDecision;
import com.gempukku.lotro.logic.decisions.AwaitingDecisionType;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import com.gempukku.lotro.logic.modifiers.ModifierFlag;
import com.gempukku.lotro.logic.vo.LotroDeck;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TimingAtTest extends AbstractAtTest {
    /**
     * Companion with this blueprint stands in play. Checked by ZONE, not by
     * Filters.countActive -- between the pregame pseudo-turns no player's
     * cards are "affecting the game", so countActive reads 0 even for a
     * companion standing in FREE_CHARACTERS. And not by deck-absence alone:
     * the starting-hand draw runs right after the fellowship, so a SKIPPED
     * companion leaves the deck too -- into the hand.
     */
    private boolean companionInPlay(final String blueprintId) {
        for (PhysicalCard card : _game.getGameState().getDeck(P1))
            if (card.getBlueprintId().equals(blueprintId))
                return false;   // still in the deck: not played
        for (PhysicalCard card : _game.getGameState().getHand(P1))
            if (card.getBlueprintId().equals(blueprintId))
                return false;   // drawn after being skipped: not played
        return true;
    }

    /** Answer a player's open starting-fellowship selection with these blueprints, in order. */
    private void chooseStartingFellowship(String player, String... blueprints) throws DecisionResultInvalidException {
        AwaitingDecision decision = _userFeedback.getAwaitingDecision(player);
        List<String> ids = new ArrayList<>();
        for (String bp : blueprints)
            ids.add(getArbitraryCardId(decision, bp));
        playerDecided(player, String.join(",", ids));
    }

    @Test
    public void startingFellowshipChoicesAreSimultaneous() throws DecisionResultInvalidException {
        // The playtest ruling this flow exists for: every seat picks at once.
        // Both players hold an OPEN starting-fellowship decision before either
        // has answered -- the old chain asked one seat at a time. Both decks
        // need a companion, or the seat without one is (rightly) never asked.
        Map<String, Collection<String>> extraCards = new HashMap<>();
        extraCards.put(P1, Arrays.asList("1_50"));
        extraCards.put(P2, Arrays.asList("1_13"));
        initializeSimplestGame(extraCards);

        AwaitingDecision p1 = _userFeedback.getAwaitingDecision(P1);
        AwaitingDecision p2 = _userFeedback.getAwaitingDecision(P2);
        assertNotNull(p1);
        assertNotNull(p2);
        assertEquals(AwaitingDecisionType.ARBITRARY_CARDS, p1.getDecisionType());
        assertEquals(AwaitingDecisionType.ARBITRARY_CARDS, p2.getDecisionType());
        assertTrue(p1.getText().startsWith("Starting fellowship"));
        assertTrue(p2.getText().startsWith("Starting fellowship"));
    }

    @Test
    public void playStartingFellowshipWithDiscount() throws DecisionResultInvalidException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        extraCards.put(P1, Arrays.asList("7_88", "6_121"));
        initializeSimplestGame(extraCards);

        // One multi-select decision holds the whole build; picks EXECUTE in
        // the order picked, so 6_121 plays after 7_88 and under its discount.
        AwaitingDecision characterDecision = _userFeedback.getAwaitingDecision(P1);
        assertEquals(AwaitingDecisionType.ARBITRARY_CARDS, characterDecision.getDecisionType());
        validateContents(new String[]{"7_88", "6_121"}, ((String[]) characterDecision.getDecisionParameters().get("blueprintId")));

        chooseStartingFellowship(P1, "7_88", "6_121");
        if (_userFeedback.getAwaitingDecision(P2) != null
                && _userFeedback.getAwaitingDecision(P2).getText().startsWith("Starting fellowship"))
            playerDecided(P2, "");

        assertTrue(companionInPlay("7_88"));
        assertTrue(companionInPlay("6_121"));
    }

    @Test
    public void playStartingFellowshipWithDiscountFromCardItself() throws DecisionResultInvalidException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        extraCards.put(P1, Arrays.asList("4_265", "4_267"));
        initializeSimplestGame(extraCards);

        AwaitingDecision characterDecision = _userFeedback.getAwaitingDecision(P1);
        assertEquals(AwaitingDecisionType.ARBITRARY_CARDS, characterDecision.getDecisionType());
        validateContents(new String[]{"4_265", "4_267"}, (characterDecision.getDecisionParameters().get("blueprintId")));

        chooseStartingFellowship(P1, "4_265", "4_267");
        if (_userFeedback.getAwaitingDecision(P2) != null
                && _userFeedback.getAwaitingDecision(P2).getText().startsWith("Starting fellowship"))
            playerDecided(P2, "");

        assertTrue(companionInPlay("4_265"));
        assertTrue(companionInPlay("4_267"));
    }

    @Test
    public void playStartingFellowshipWithSpotRequirement() throws DecisionResultInvalidException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        extraCards.put(P1, Arrays.asList("1_50", "1_48"));
        initializeSimplestGame(extraCards);

        // EVERY companion is shown AND selectable in the simultaneous choice
        // -- 1_48 needs an Elf spotted and none is in play yet, but picking
        // 1_50 first meets it at execution time, so legality cannot be judged
        // here. The executor is the enforcement.
        AwaitingDecision characterDecision = _userFeedback.getAwaitingDecision(P1);
        assertEquals(AwaitingDecisionType.ARBITRARY_CARDS, characterDecision.getDecisionType());
        validateContents(new String[]{"1_50", "1_48"}, ((String[]) characterDecision.getDecisionParameters().get("blueprintId")));
        for (String selectable : (String[]) characterDecision.getDecisionParameters().get("selectable"))
            assertEquals("true", selectable);

        // The decision carries each shown companion's current twilight cost
        // and the budget (base 4 in EVERY format -- the limit is hardcoded in
        // the pregame processes and only card modifiers move it), so a client
        // can grey out live what a growing selection prices out.
        String[] shownCosts = (String[]) characterDecision.getDecisionParameters().get("twilightCost");
        assertEquals(2, shownCosts.length);
        assertEquals("4", ((String[]) characterDecision.getDecisionParameters().get("budgetRemaining"))[0]);

        chooseStartingFellowship(P1, "1_50", "1_48");
        if (_userFeedback.getAwaitingDecision(P2) != null
                && _userFeedback.getAwaitingDecision(P2).getText().startsWith("Starting fellowship"))
            playerDecided(P2, "");

        assertTrue(companionInPlay("1_50"));
        assertTrue(companionInPlay("1_48"));
    }

    @Test
    public void startingFellowshipSpotRequirementRespectsPickOrder() throws DecisionResultInvalidException {
        // Picked the other way round, 1_48 comes up while no Elf is spotted:
        // it is SKIPPED (with a message, never silently) and 1_50 still plays.
        Map<String, Collection<String>> extraCards = new HashMap<>();
        extraCards.put(P1, Arrays.asList("1_50", "1_48"));
        initializeSimplestGame(extraCards);

        chooseStartingFellowship(P1, "1_48", "1_50");
        if (_userFeedback.getAwaitingDecision(P2) != null
                && _userFeedback.getAwaitingDecision(P2).getText().startsWith("Starting fellowship"))
            playerDecided(P2, "");

        assertTrue(companionInPlay("1_50"));
        assertFalse(companionInPlay("1_48"));
    }

    @Test
    public void playMultipleRequiredEffectsInOrder() throws DecisionResultInvalidException, CardNotFoundException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        initializeSimplestGame(extraCards);

        PhysicalCard elrond = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_40");
        PhysicalCard gimli = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_13");
        PhysicalCard dwarvenHeart = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_10");

        _game.getGameState().addCardToZone(_game, gimli, Zone.FREE_CHARACTERS);
        _game.getGameState().addCardToZone(_game, elrond, Zone.SUPPORT);
        _game.getGameState().attachCard(_game, dwarvenHeart, gimli);

        skipMulligans();

        AwaitingDecision requiredActionChoice = _userFeedback.getAwaitingDecision(P1);
        assertEquals(AwaitingDecisionType.ACTION_CHOICE, requiredActionChoice.getDecisionType());
        validateContents(new String[]{"1_40", "1_10"}, (String[]) requiredActionChoice.getDecisionParameters().get("blueprintId"));
        playerDecided(P1, "0");

        assertNotSame(_game.getGameState().getCurrentPhase(), Phase.BETWEEN_TURNS);
    }

    @Test
    public void playMultipleOptionalEffectsInOrder() throws DecisionResultInvalidException, CardNotFoundException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        initializeSimplestGame(extraCards);

        PhysicalCard aragorn = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_365");
        PhysicalCard gandalf = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "2_122");

        _game.getGameState().addCardToZone(_game, aragorn, Zone.FREE_CHARACTERS);
        _game.getGameState().addCardToZone(_game, gandalf, Zone.FREE_CHARACTERS);

        skipMulligans();

        AwaitingDecision firstOptionalActionChoice = _userFeedback.getAwaitingDecision(P1);
        assertEquals(AwaitingDecisionType.CARD_ACTION_CHOICE, firstOptionalActionChoice.getDecisionType());
        assertEquals(2, ((String[]) firstOptionalActionChoice.getDecisionParameters().get("cardId")).length);

        playerDecided(P1, "0");

        AwaitingDecision secondOptionalActionChoice = _userFeedback.getAwaitingDecision(P1);
        assertEquals(AwaitingDecisionType.CARD_ACTION_CHOICE, secondOptionalActionChoice.getDecisionType());
        assertEquals(1, ((String[]) secondOptionalActionChoice.getDecisionParameters().get("cardId")).length);

        playerDecided(P1, "0");

        assertNotSame(_game.getGameState().getCurrentPhase(), Phase.BETWEEN_TURNS);
    }

    @Test
    public void playEffectFromDiscard() throws DecisionResultInvalidException, CardNotFoundException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        initializeSimplestGame(extraCards);

        PhysicalCard gollum = _game.getGameState().createPhysicalCard(P2, _cardLibrary, "7_58");

        _game.getGameState().addCardToZone(_game, gollum, Zone.DISCARD);

        skipMulligans();

        _game.getGameState().addTwilight(10);

        playerDecided(P1, "");

        _userFeedback.getAwaitingDecision(P2);

        AwaitingDecision shadowPhaseAction = _userFeedback.getAwaitingDecision(P2);
        assertEquals(AwaitingDecisionType.CARD_ACTION_CHOICE, shadowPhaseAction.getDecisionType());
        validateContents(new String[]{"7_58"}, (String[]) shadowPhaseAction.getDecisionParameters().get("blueprintId"));

        playerDecided(P2, "0");

        assertEquals(Zone.SHADOW_CHARACTERS, gollum.getZone());
    }

    @Test
    public void playEffectFromStacked() throws DecisionResultInvalidException, CardNotFoundException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        initializeSimplestGame(extraCards);

        PhysicalCard gimli = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_13");
        PhysicalCard letThemCome = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_20");
        PhysicalCard slakedThirsts = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "7_14");

        PhysicalCard gollum = _game.getGameState().createPhysicalCard(P2, _cardLibrary, "7_58");

        _game.getGameState().addCardToZone(_game, gimli, Zone.FREE_CHARACTERS);
        _game.getGameState().addCardToZone(_game, letThemCome, Zone.SUPPORT);
        _game.getGameState().stackCard(_game, slakedThirsts, letThemCome);

        skipMulligans();

        // End fellowship phase
        playerDecided(P1, "");

        _game.getGameState().addCardToZone(_game, gollum, Zone.SHADOW_CHARACTERS);

        // End shadow phase
        playerDecided(P2, "");

        AwaitingDecision maneuverPhaseAction = _userFeedback.getAwaitingDecision(P1);
        assertEquals(AwaitingDecisionType.CARD_ACTION_CHOICE, maneuverPhaseAction.getDecisionType());
        validateContents(new String[]{"Use Slaked Thirsts"}, (String[]) maneuverPhaseAction.getDecisionParameters().get("actionText"));

        playerDecided(P1, "0");

        assertEquals(Zone.DISCARD, slakedThirsts.getZone());
        assertEquals(2, _game.getGameState().getWounds(gollum));
    }

    @Test
    public void stackedCardAffectsGame() throws DecisionResultInvalidException, CardNotFoundException {
        Map<String, Collection<String>> extraCards = new HashMap<>();
        initializeSimplestGame(extraCards);

        PhysicalCard gimli = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_13");
        PhysicalCard letThemCome = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_20");
        PhysicalCard tossMe = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "6_11");

        skipMulligans();

        _game.getGameState().addCardToZone(_game, gimli, Zone.FREE_CHARACTERS);
        _game.getGameState().addCardToZone(_game, letThemCome, Zone.SUPPORT);
        _game.getGameState().stackCard(_game, tossMe, letThemCome);

        assertEquals(7, _game.getModifiersQuerying().getStrength(_game, gimli));
    }

    @Test
    public void movementSiteAffecting() throws Exception {
        initializeSimplestGame();

        skipMulligans();

        LotroCardBlueprintBuilder builder = new LotroCardBlueprintBuilder();
        Map<String, LotroCardBlueprint> cards = LotroCardBlueprintLibrary.loadCardsFromFile(builder, TimingAtTest.class.getResourceAsStream("/siteTest.hjson"));

        PhysicalCardImpl moveFromSite = new PhysicalCardImpl(100, "0_1234", P1, cards.get("0_1234"));
        moveFromSite.setSiteNumber(1);
        PhysicalCardImpl moveToSite = new PhysicalCardImpl(100, "0_1235", P1, cards.get("0_1235"));
        moveToSite.setSiteNumber(2);

        _game.getGameState().removeCardsFromZone(P1, Collections.singleton(_game.getGameState().getCurrentSite()));
        _game.getGameState().addCardToZone(_game, moveFromSite, Zone.ADVENTURE_PATH);
        _game.getGameState().addCardToZone(_game, moveToSite, Zone.ADVENTURE_PATH);

        assertTrue(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.RING_TEXT_INACTIVE));
        assertFalse(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.CANT_PREVENT_WOUNDS));

        // End fellowship phase
        playerDecided(P1, "");

        // End shadow phase
        playerDecided(P2, "");

        assertFalse(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.RING_TEXT_INACTIVE));
        assertTrue(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.CANT_PREVENT_WOUNDS));

        // Pass in Regroup phase
        assertEquals(Phase.REGROUP, _game.getGameState().getCurrentPhase());
        playerDecided(P1, "");
        assertEquals(Phase.REGROUP, _game.getGameState().getCurrentPhase());
        playerDecided(P2, "");

        // Decide not to move
        assertEquals(Phase.REGROUP, _game.getGameState().getCurrentPhase());
        playerDecided(P1, getMultipleDecisionIndex(_userFeedback.getAwaitingDecision(P1), "No"));

        // Fellowship of player2
        assertTrue(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.RING_TEXT_INACTIVE));
        assertFalse(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.CANT_PREVENT_WOUNDS));

        // End fellowship phase
        playerDecided(P2, "");

        // End shadow phase
        playerDecided(P1, "");

        assertFalse(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.RING_TEXT_INACTIVE));
        assertTrue(_game.getModifiersQuerying().hasFlagActive(_game, ModifierFlag.CANT_PREVENT_WOUNDS));
    }

    @Test
    public void extraCostToPlay() throws DecisionResultInvalidException, CardNotFoundException {
        initializeSimplestGame();

        skipMulligans();

        PhysicalCard balinAvenged = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "17_2");
        PhysicalCard prowlingOrc = _game.getGameState().createPhysicalCard(P2, _cardLibrary, "11_136");
        PhysicalCard dwarvenGuard1 = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_7");
        PhysicalCard dwarvenGuard2 = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_7");
        PhysicalCard prowlingOrcInDiscard = _game.getGameState().createPhysicalCard(P2, _cardLibrary, "11_136");

        _game.getGameState().addCardToZone(_game, balinAvenged, Zone.SUPPORT);
        _game.getGameState().addTokens(balinAvenged, Token.DWARVEN, 4);
        _game.getGameState().addCardToZone(_game, dwarvenGuard1, Zone.FREE_CHARACTERS);
        _game.getGameState().addCardToZone(_game, dwarvenGuard2, Zone.FREE_CHARACTERS);
        _game.getGameState().addCardToZone(_game, prowlingOrc, Zone.HAND);
        _game.getGameState().addCardToZone(_game, prowlingOrcInDiscard, Zone.DISCARD);
        _game.getGameState().addTwilight(10);

        // End fellowship phase
        playerDecided(P1, "");

        AwaitingDecision shadowDecision = _userFeedback.getAwaitingDecision(P2);
        assertEquals(AwaitingDecisionType.CARD_ACTION_CHOICE, shadowDecision.getDecisionType());
        validateContents(new String[]{"" + prowlingOrc.getCardId()}, (String[]) shadowDecision.getDecisionParameters().get("cardId"));

        playerDecided(P2, "0");

        assertEquals(Zone.REMOVED, prowlingOrcInDiscard.getZone());
        assertEquals(Zone.SHADOW_CHARACTERS, prowlingOrc.getZone());
    }

    @Test
    public void twoBeforeRequiredEffectsPreventing() throws DecisionResultInvalidException, CardNotFoundException {
        Map<String, LotroDeck> decks = new HashMap<>();
        LotroDeck p1Deck = createSimplestDeck();
        p1Deck.setRingBearer("9_4");
        p1Deck.setRing("4_1");
        decks.put(P1, p1Deck);
        decks.put(P2, createSimplestDeck());

        initializeGameWithDecks(decks);

        skipMulligans();

        PhysicalCard gimlisHelm = _game.getGameState().createPhysicalCard(P1, _cardLibrary, "1_15");
        _game.getGameState().attachCard(_game, gimlisHelm, _game.getGameState().getRingBearer(P1));

        PhysicalCard urukHaiRaidingParty = _game.getGameState().createPhysicalCard(P2, _cardLibrary, "1_158");
        _game.getGameState().addCardToZone(_game, urukHaiRaidingParty, Zone.SHADOW_CHARACTERS);

        // End fellowship phase
        playerDecided(P1, "");

        // End shadow phase
        playerDecided(P2, "");

        // End maneuver phase
        playerDecided(P1, "");
        playerDecided(P2, "");

        // End archery phase
        playerDecided(P1, "");
        playerDecided(P2, "");

        // End assignment phase
        playerDecided(P1, "");
        playerDecided(P2, "");

        playerDecided(P1, _game.getGameState().getRingBearer(P1).getCardId() + " " + urukHaiRaidingParty.getCardId());

        // Choose Gimli's skirmish
        playerDecided(P1, "" + _game.getGameState().getRingBearer(P1).getCardId());

        assertEquals(3, _game.getGameState().getBurdens());
        _game.getGameState().removeBurdens(3);

        AwaitingDecision skirmishDecision = _userFeedback.getAwaitingDecision(P1);
        playerDecided(P1, getCardActionId(skirmishDecision, "Use The One"));
        assertEquals(1, _game.getGameState().getBurdens());

        playerDecided(P2, "");

        AwaitingDecision skirmishSecondDecision = _userFeedback.getAwaitingDecision(P1);
        playerDecided(P1, getCardActionId(skirmishSecondDecision, "Use Gimli's"));
        assertEquals(Zone.DISCARD, gimlisHelm.getZone());

        playerDecided(P2, "");
        playerDecided(P1, "");

        // Prevent both wounds with Helm
        AwaitingDecision beforeRequiredChoice = _userFeedback.getAwaitingDecision(P1);
        playerDecided(P1, getCardActionIdContains(beforeRequiredChoice, "Gimli's Helm"));
        AwaitingDecision beforeRequiredSecondChoice = _userFeedback.getAwaitingDecision(P1);
        playerDecided(P1, getCardActionIdContains(beforeRequiredSecondChoice, "Gimli's Helm"));

        assertEquals(1, _game.getGameState().getBurdens());
    }
}
