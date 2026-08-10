package com.gempukku.lotro.cards.unofficial.rtmd.set_91_ttt;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import org.junit.Test;

import java.util.HashMap;

import static com.gempukku.lotro.framework.Assertions.assertInPlay;
import static com.gempukku.lotro.framework.Assertions.assertNotInZone;
import static org.junit.Assert.*;

public class Card_91_001_Tests
{
	private final HashMap<String, String> companionDeck =  new HashMap<>()
	{{
		// Gimli: twilight 2
		put("gimli", "1_13");
		// Boromir: twilight 3
		put("boromir", "1_97");
		// Legolas: twilight 2
		put("legolas", "1_50");
		// Aragorn, Elessar Telcontar: twilight 5
		put("elessar", "10_25");
		// Merry: twilight 1
		put("merry", "1_302");
	}};
	protected VirtualTableScenario GetFreepsScenario() throws CardNotFoundException, DecisionResultInvalidException {
		return new VirtualTableScenario(
				companionDeck,
				VirtualTableScenario.FellowshipSites,
				VirtualTableScenario.FOTRFrodo,
				VirtualTableScenario.RulingRing,
				"91_1", null
		);
	}

	protected VirtualTableScenario GetShadowScenario() throws CardNotFoundException, DecisionResultInvalidException {
		return new VirtualTableScenario(
				companionDeck,
				VirtualTableScenario.FellowshipSites,
				VirtualTableScenario.FOTRFrodo,
				VirtualTableScenario.RulingRing,
				null, "91_1"
		);
	}

	protected VirtualTableScenario GetNoModScenario() throws CardNotFoundException, DecisionResultInvalidException {
		return new VirtualTableScenario(
				companionDeck,
				VirtualTableScenario.FellowshipSites,
				VirtualTableScenario.FOTRFrodo,
				VirtualTableScenario.RulingRing
		);
	}

	@Test
	public void StartingFellowshipCostStatsAreCorrect() throws DecisionResultInvalidException, CardNotFoundException {
		/**
		 * Set: RTMD 91
		 * Name: Race Text 91_1
		 * Type: MetaSite
		 * Game Text: Your starting companions may have a total twilight cost of 6 instead of 4.
		 */

		var scn = GetFreepsScenario();

		var card = scn.GetFreepsCard("mod");

		assertEquals("Race Text 91_1", card.getBlueprint().getTitle());
		assertEquals(CardType.METASITE, card.getBlueprint().getCardType());
	}

	@Test
	public void CanPlay5TwilightWorthWithModifier() throws DecisionResultInvalidException, CardNotFoundException {
		// 91_1: "Your starting companions may have a total twilight cost of 6 instead of 4."
		// The choice is ONE simultaneous multi-select now; the executor plays
		// the picks in order and enforces the budget. Gimli (2) + Boromir (3)
		// + Merry (1) = 6 fits the raised limit.

		var scn = GetFreepsScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var boromir = scn.GetFreepsCard("boromir");
		var merry = scn.GetFreepsCard("merry");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, boromir, merry);
		if (scn.ShadowDecisionAvailable("Starting fellowship"))
			scn.ShadowChoose("");

		scn.StartGame(true, false);
		assertInPlay(gimli, boromir, merry);
	}

	@Test
	public void CannotExceed6TwilightWithModifier() throws DecisionResultInvalidException, CardNotFoundException {
		// 91_1: Gimli (2) + Legolas (2) + Boromir (3) = 7 exceeds 6. The
		// executor plays the first two and SKIPS Boromir with a message.

		var scn = GetFreepsScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var legolas = scn.GetFreepsCard("legolas");
		var boromir = scn.GetFreepsCard("boromir");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, legolas, boromir);
		if (scn.ShadowDecisionAvailable("Starting fellowship"))
			scn.ShadowChoose("");

		assertInPlay(gimli, legolas);
		assertNotInZone(Zone.FREE_CHARACTERS, boromir);
	}

	@Test
	public void CannotPlay5TwilightWorthWithoutModifier() throws DecisionResultInvalidException, CardNotFoundException {
		// Without the modifier, Gimli (2) + Boromir (3) = 5 exceeds the normal
		// limit of 4: Boromir is skipped at execution.

		var scn = GetNoModScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var boromir = scn.GetFreepsCard("boromir");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, boromir);
		if (scn.ShadowDecisionAvailable("Starting fellowship"))
			scn.ShadowChoose("");

		assertInPlay(gimli);
		assertNotInZone(Zone.FREE_CHARACTERS, boromir);
	}

	@Test
	public void ShadowCopyDoesNotAffectFreeps() throws DecisionResultInvalidException, CardNotFoundException {
		// 91_1: "Your starting companions" -- Shadow's copy must not raise the
		// FP player's limit: Gimli (2) + Boromir (3) = 5 > 4, Boromir skipped.

		var scn = GetShadowScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var boromir = scn.GetFreepsCard("boromir");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, boromir);
		if (scn.ShadowDecisionAvailable("Starting fellowship"))
			scn.ShadowChoose("");

		assertInPlay(gimli);
		assertNotInZone(Zone.FREE_CHARACTERS, boromir);
	}

	@Test
	public void ShadowCopyWorks() throws DecisionResultInvalidException, CardNotFoundException {
		// 91_1 on the Shadow side: their own starting companions get the raised
		// limit of 6. Both selections are open SIMULTANEOUSLY.

		var scn = GetShadowScenario();

		var gimli = scn.GetShadowCard("gimli");
		var boromir = scn.GetShadowCard("boromir");
		var merry = scn.GetShadowCard("merry");

		assertTrue(scn.ShadowDecisionAvailable("Starting fellowship"));
		scn.FreepsChoose("");
		scn.ShadowChooseCards(gimli, boromir, merry);

		scn.StartGame(true, false);
		assertInPlay(gimli, boromir, merry);
	}
}
