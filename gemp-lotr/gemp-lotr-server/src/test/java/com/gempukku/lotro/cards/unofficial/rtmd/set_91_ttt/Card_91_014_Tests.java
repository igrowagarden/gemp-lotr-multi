package com.gempukku.lotro.cards.unofficial.rtmd.set_91_ttt;

import com.gempukku.lotro.common.CardType;
import com.gempukku.lotro.common.Zone;
import com.gempukku.lotro.framework.Assertions;
import com.gempukku.lotro.framework.VirtualTableScenario;
import com.gempukku.lotro.game.CardNotFoundException;
import com.gempukku.lotro.logic.decisions.DecisionResultInvalidException;
import org.junit.Test;

import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Starting fellowships are now chosen in ONE simultaneous multi-select per
 * player (SimultaneousStartingFellowshipChoiceGameProcess), so the budget is
 * no longer expressed by shrinking the selectable set between picks -- every
 * companion is selectable, the client greys advisorily, and the EXECUTOR is
 * the enforcement: picks the budget cannot cover are skipped with a message.
 * These tests therefore assert what actually PLAYED.
 */
public class Card_91_014_Tests
{
	private final HashMap<String, String> companionDeck = new HashMap<>()
	{{
		// Gimli: twilight 2
		put("gimli", "1_13");
		// Boromir: twilight 3
		put("boromir", "1_97");
		// Legolas: twilight 2
		put("legolas", "1_50");
		// Merry: twilight 1
		put("merry", "1_302");
		// Aragorn: twilight 4
		put("aragorn", "1_89");
	}};

	protected VirtualTableScenario GetFreepsScenario() throws CardNotFoundException, DecisionResultInvalidException {
		return new VirtualTableScenario(companionDeck,
				VirtualTableScenario.FellowshipSites,
				VirtualTableScenario.FOTRFrodo,
				VirtualTableScenario.RulingRing,
				"91_14", null
		);
	}

	protected VirtualTableScenario GetShadowScenario() throws CardNotFoundException, DecisionResultInvalidException {
		return new VirtualTableScenario(companionDeck,
				VirtualTableScenario.FellowshipSites,
				VirtualTableScenario.FOTRFrodo,
				VirtualTableScenario.RulingRing,
				null, "91_14"
		);
	}

	protected VirtualTableScenario GetNoModScenario() throws CardNotFoundException, DecisionResultInvalidException {
		return new VirtualTableScenario(companionDeck,
				VirtualTableScenario.FellowshipSites,
				VirtualTableScenario.FOTRFrodo,
				VirtualTableScenario.RulingRing
		);
	}

	private void finishShadowChoice(VirtualTableScenario scn) throws DecisionResultInvalidException {
		// The choice is SIMULTANEOUS now: the Shadow seat's selection is open
		// at the same time and execution waits for both.
		if (scn.ShadowDecisionAvailable("Starting fellowship"))
			scn.ShadowChoose("");
	}

	@Test
	public void StartingFellowshipCostStatsAreCorrect() throws DecisionResultInvalidException, CardNotFoundException {
		/**
		 * Set: RTMD 91
		 * Name: Race Text 91_14
		 * Type: MetaSite
		 * Game Text: Your starting companions must have a total twilight cost of 3 or less.
		 */

		var scn = GetFreepsScenario();

		var card = scn.GetFreepsCard("mod");

		assertEquals("Race Text 91_14", card.getBlueprint().getTitle());
		assertEquals(CardType.METASITE, card.getBlueprint().getCardType());
	}

	@Test
	public void CannotPlay4TwilightWorthWithModifier() throws DecisionResultInvalidException, CardNotFoundException {
		// 91_14: "Your starting companions must have a total twilight cost of 3 or less."
		// Gimli (2) + Legolas (2) = 4: the executor plays Gimli and must SKIP
		// Legolas -- with a message, never silently.

		var scn = GetFreepsScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var legolas = scn.GetFreepsCard("legolas");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, legolas);
		finishShadowChoice(scn);

		Assertions.assertInZone(Zone.FREE_CHARACTERS, gimli);
		Assertions.assertNotInZone(Zone.FREE_CHARACTERS, legolas);
	}

	@Test
	public void CanPlay3TwilightWorthWithModifier() throws DecisionResultInvalidException, CardNotFoundException {
		// 91_14: Gimli (2) + Merry (1) = 3 fits the reduced budget.

		var scn = GetFreepsScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var merry = scn.GetFreepsCard("merry");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, merry);
		finishShadowChoice(scn);

		Assertions.assertInZone(Zone.FREE_CHARACTERS, gimli, merry);
	}

	@Test
	public void CanPlay4TwilightWorthWithoutModifier() throws DecisionResultInvalidException, CardNotFoundException {
		// Without the modifier, Gimli (2) + Legolas (2) = 4 fits the normal limit.

		var scn = GetNoModScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var legolas = scn.GetFreepsCard("legolas");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, legolas);
		finishShadowChoice(scn);

		Assertions.assertInZone(Zone.FREE_CHARACTERS, gimli, legolas);
	}

	@Test
	public void ShadowCopyDoesNotAffectFreeps() throws DecisionResultInvalidException, CardNotFoundException {
		// 91_14: Shadow's copy must not reduce FP's starting fellowship limit.

		var scn = GetShadowScenario();

		var gimli = scn.GetFreepsCard("gimli");
		var legolas = scn.GetFreepsCard("legolas");

		assertTrue(scn.FreepsDecisionAvailable("Starting fellowship"));
		scn.FreepsChooseCards(gimli, legolas);
		finishShadowChoice(scn);

		Assertions.assertInZone(Zone.FREE_CHARACTERS, gimli, legolas);
	}
}
