package com.gempukku.lotro.framework;

import com.gempukku.lotro.common.Phase;
import com.gempukku.lotro.common.SitesBlock;
import com.gempukku.lotro.game.PhysicalCardImpl;
import org.junit.Assert;

/**
 * These functions are for progressing through the game itself.  For instance, if your test is really about battle
 * attrition and the phases before that point are just in the way, here you will find functions to skip past phases
 * so that your test can remain mostly clean of irrelevant procedure.
 *
 * Do be warned that these functions assume a best-case scenario that can be safely passed through; if a card has a
 * required decision that does not have an obvious "decline" option, then these functions will fail and you will have
 * to do it manually.  If you actually do need such a pestering card on the table, it is advised that you only place
 * it at the last possible second rather than putting it down early and requiring you to do all the manual procedure.
 */
public interface GameProcedures extends Actions, GameProperties, PileProperties {

	default void SkipToArcheryWounds() {
		SkipToPhase(Phase.ARCHERY);
		PassCurrentPhaseActions();
	}


	default void SkipToAssignments() {
		SkipToPhase(Phase.ASSIGNMENT);
		PassCurrentPhaseActions();
	}

	default void FreepsResolveSkirmish(PhysicalCardImpl comp) { FreepsChooseCard(comp); }
	default void ShadowResolveSkirmish(PhysicalCardImpl comp) { ShadowChooseCard(comp); }

	default void SkipToMovementDecision() {
		SkipToPhase(Phase.REGROUP);
		PassCurrentPhaseActions();
		if(ShadowDecisionAvailable("reconcile")) {
			ShadowDeclineReconciliation();
		}
		while(ShadowDecisionAvailable("discard down")) {
			ShadowChooseCard((PhysicalCardImpl) GetShadowHand().getFirst());
		}
	}


	/**
	 * Causes both players to pass during the Maneuver phase.
	 */
	default void PassManeuverActions() { PassCurrentPhaseActions(); }
	/**
	 * Causes both players to pass during the Archery phase.
	 */
	default void PassArcheryActions() { PassCurrentPhaseActions(); }
	/**
	 * Causes both players to pass during the Assignment phase.
	 */
	default void PassAssignmentActions() { PassCurrentPhaseActions(); }
	/**
	 * Causes both players to pass during the Fierce Assignment phase.
	 */
	default void PassFierceAssignmentActions() { PassCurrentPhaseActions(); }
	/**
	 * Causes both players to pass during a Skirmish phase.
	 */
	default void PassSkirmishActions() { PassCurrentPhaseActions(); }
	/**
	 * Causes both players to pass during a fierce Skirmish phase.
	 */
	default void PassFierceSkirmishActions() { PassCurrentPhaseActions(); }
	/**
	 * Causes both players to pass during the Regroup phase.
	 */
	default void PassRegroupActions() { PassCurrentPhaseActions(); }


	default void FreepsChooseToMove() { PlayerDecided(P1, "0"); }
	default void FreepsChooseToStay() { PlayerDecided(P1, "1"); }

	//These two are only used in situations where the play is inverted, such as when skipping to a site.
	default void ShadowChooseToMove() { PlayerDecided(P2, "0"); }
	default void ShadowChooseToStay() { PlayerDecided(P2, "1"); }

	default void FreepsDeclineReconciliation() { FreepsPassCurrentPhaseAction(); }
	default void ShadowDeclineReconciliation() { ShadowPassCurrentPhaseAction(); }

	/**
	 * Causes both players to pass, first the player with a current decision and then the other.
	 */
	default void PassResponses() {
		var decider = GetDecidingPlayer();
		var offPlayer = GetNextDecider();

		PlayerPass(decider);

		if(AnyDecisionsAvailable(offPlayer)) {
			PlayerPass(offPlayer);
		}
	}

	/**
	 * Causes both players to pass any decisions that contain the provided text.  First the current decider will pass,
	 * and then the other.
	 * @param text Text which must be contained inside the decision
	 */
	default void PassResponses(String text) {
		var decider = GetDecidingPlayer();
		var offPlayer = GetNextDecider();

		if(DecisionAvailable(decider, text)) {
			PlayerPass(decider);

			if(DecisionAvailable(offPlayer, text)) {
				PlayerPass(offPlayer);
			}
		}
	}

	/**
	 * Causes both players to pass, first the player with a current decision and then the other. Both will check
	 * to ensure that they have a currently available decision to be passing first.
	 */
	default void PassCurrentPhaseActions() {
		var decider = GetDecidingPlayer();
		var offPlayer = GetNextDecider();

		PlayerPass(decider);

		if(AnyDecisionsAvailable(offPlayer)) {
			PlayerPass(offPlayer);
		}
	}

	default void FreepsPassCurrentPhaseAction() {
		if(userFeedback().getAwaitingDecision(P1) != null) {
			PlayerDecided(P1, "");
		}
	}

	default void ShadowPassCurrentPhaseAction() {
		if(userFeedback().getAwaitingDecision(P2) != null) {
			PlayerDecided(P2, "");
		}
	}

	/**
	 * Causes both players to pass. Both will check to ensure that they have a currently available decision to be passing first.
	 */
	default void BothPass() {
		var currentPlayer = GetCurrentPlayer();
		var offPlayer = GetOffPlayer();

		if(AnyDecisionsAvailable(currentPlayer)) {
			PlayerDecided(currentPlayer, "");
		}

		if(AnyDecisionsAvailable(offPlayer)) {
			PlayerDecided(offPlayer, "");
		}
	}

	/**
	 * Causes both players to pass any decisions that contain the provided text.
	 * @param text
	 */
	default void BothPass(String text) {
		var currentPlayer = GetCurrentPlayer();
		var offPlayer = GetOffPlayer();
		if(DecisionAvailable(currentPlayer, text)) {
			PlayerDecided(currentPlayer, "");
		}

		if(DecisionAvailable(offPlayer, text)) {
			PlayerDecided(offPlayer, "");
		}
	}

	/**
	 * Causes both players to pass, but makes the opponent pass before the current player. Both will check
	 * to ensure that they have a currently available decision to be passing first.
	 */
	default void BothPassInverted() {
		var currentPlayer = GetCurrentPlayer();
		var offPlayer = GetOffPlayer();

		if(AnyDecisionsAvailable(offPlayer)) {
			PlayerDecided(offPlayer, "");
		}

		if(AnyDecisionsAvailable(currentPlayer)) {
			PlayerDecided(currentPlayer, "");
		}
	}

	/**
	 * Causes both players to pass any decisions that contain the provided text.  First the off-player will pass, and
	 * then the current player.
	 * @param text
	 */
	default void BothPassInverted(String text) {
		var currentPlayer = GetCurrentPlayer();
		var offPlayer = GetOffPlayer();

		if(DecisionAvailable(offPlayer, text)) {
			PlayerDecided(offPlayer, "");
		}

		if(DecisionAvailable(currentPlayer, text)) {
			PlayerDecided(currentPlayer, "");
		}
	}



	/**
	 * Causes players to spam pass until the provided target phase is current.  This process attempts to choose the
	 * first option of any required triggers, but may be brittle if there are any reacts that interrupt the pass-fest.
	 * Only 20 rounds of passing will be attempted to avoid infinite loops.
	 * @param target The phase the tester actually wants to be in
	 */
    default void SkipToPhase(Phase target) {
		Phase current = gameState().getCurrentPhase();
		try {
			for (int attempts = 1; attempts <= 20; attempts++) {
				current = gameState().getCurrentPhase();
				if (current == target)
					break;

				if (current == Phase.FELLOWSHIP) {
					FreepsPassCurrentPhaseAction();
					if (game().getFormat().getSiteBlock() == SitesBlock.SHADOWS) {
						ShadowChooseAnyCard();
					}
				} else if (current == Phase.SHADOW) {
					ShadowPassCurrentPhaseAction();
				} else {
					var freeps = FreepsGetAwaitingDecision();
					var shadow = ShadowGetAwaitingDecision();
					if (freeps != null && freeps.getText().toLowerCase().contains("required")) {
						FreepsChooseAction("0");
					} else if (shadow != null && shadow.getText().toLowerCase().contains("required")) {
						ShadowChooseAction("0");
					} else {
						PassCurrentPhaseActions();
					}
				}

				if (attempts == 20) {
					if (current == Phase.ARCHERY)
						throw new RuntimeException(
								"You left an archer in the scenario; can't reach '" + target + "' after 20 attempts because of the archery assignment.");

					throw new RuntimeException("Could not arrive at target '" + target + "' after 20 attempts!");
				}
			}
		}
		catch(RuntimeException ex) {
			throw new RuntimeException("Attempting to skip to phase " + target + ", halted in phase " + current, ex);
		}
    }

	/**
	 * Advance to a phase at any number of seats, by answering whoever is
	 * actually waiting.
	 *
	 * {@link #SkipToPhase} is written as P1 and P2, which is exact at two
	 * players and stalls above them: each Shadow player gets their own Shadow
	 * phase, so at five seats three of the four are never answered and the loop
	 * runs out of attempts. This asks the game who it is waiting on instead of
	 * assuming, and gives the least eventful answer each time.
	 *
	 * Deliberately a separate method rather than a rewrite of SkipToPhase --
	 * roughly six thousand tests depend on the two-player one behaving exactly
	 * as it does.
	 */
	default void PassUntilPhase(Phase target) {
		// Generous: five seats can need a pass per player per phase for several
		// phases, but still bounded so a stall fails rather than hangs.
		for (int attempts = 1; attempts <= 300; attempts++) {
			if (gameState().getCurrentPhase() == target)
				return;

			var pending = new java.util.ArrayList<>(userFeedback().getUsersPendingDecision());
			if (pending.isEmpty())
				throw new RuntimeException("Nobody has a decision, and the phase is "
						+ gameState().getCurrentPhase() + " rather than " + target);

			java.util.Collections.sort(pending);
			String playerId = pending.get(0);
			var decision = userFeedback().getAwaitingDecision(playerId);
			if (decision == null)
				continue;

			String answer;
			switch (decision.getDecisionType()) {
				case INTEGER -> {
					var params = decision.getDecisionParameters();
					answer = params.containsKey("min") ? params.get("min")[0] : "0";
				}
				// A required choice has no decline, so take the first option.
				case ACTION_CHOICE -> answer = "0";
				case MULTIPLE_CHOICE -> answer = "0";
				// Everything else declines: pass the phase action, discard
				// nothing, assign nothing.
				default -> answer = "";
			}
			PlayerDecided(playerId, answer);
		}
		throw new RuntimeException("Could not reach " + target + "; stalled in "
				+ gameState().getCurrentPhase());
	}

	/**
	 * Walk the fellowship to a site, at any number of seats.
	 *
	 * {@link #SkipToSite} goes through SkipCurrentSite, which is written as P1
	 * and P2 throughout -- reconcile, discard-down, and a "swap" for the shadow
	 * player's turn -- so it stalls above two seats the way SkipToPhase did.
	 *
	 * The one answer that cannot be the least eventful one is the move question:
	 * the fellowship only reaches the next site if it moves, so "another move"
	 * is answered Yes and everything else declines. Selected by option text
	 * rather than by index, because the options are not in a fixed order --
	 * mulligan is {No,Yes} while move-again is {Yes,No}, so a positional answer
	 * accepts one and declines the other.
	 */
	default void PassUntilSite(int siteNumber) {
		for (int attempts = 1; attempts <= 600; attempts++) {
			var site = GetCurrentSite();
			if (site != null && site.getSiteNumber() >= siteNumber)
				return;

			var pending = new java.util.ArrayList<>(userFeedback().getUsersPendingDecision());
			if (pending.isEmpty())
				throw new RuntimeException("Nobody has a decision, and the fellowship is at site "
						+ (site == null ? "(none)" : site.getSiteNumber()) + " rather than " + siteNumber);

			java.util.Collections.sort(pending);
			String playerId = pending.get(0);
			var decision = userFeedback().getAwaitingDecision(playerId);
			if (decision == null)
				continue;

			PlayerDecided(playerId, AnswerThatKeepsMoving(decision));
		}
		throw new RuntimeException("Could not reach site " + siteNumber
				+ "; the fellowship stopped at "
				+ (GetCurrentSite() == null ? "(none)" : GetCurrentSite().getSiteNumber()));
	}

	/**
	 * Advance until a named player is asked something matching `textFragment`.
	 *
	 * Phase-based waiting is not precise enough for a trigger that fires at the
	 * start of a phase: PassUntilSite walks through several regroup phases to
	 * get where it is going, and answers the trigger's decision on the way past.
	 * Waiting for the decision itself cannot overshoot it.
	 */
	default void PassUntilDecision(String playerId, String textFragment) {
		for (int attempts = 1; attempts <= 600; attempts++) {
			var wanted = userFeedback().getAwaitingDecision(playerId);
			if (wanted != null && wanted.getText() != null
					&& wanted.getText().toLowerCase().contains(textFragment.toLowerCase()))
				return;

			var pending = new java.util.ArrayList<>(userFeedback().getUsersPendingDecision());
			if (pending.isEmpty())
				throw new RuntimeException("Nobody has a decision, and " + playerId
						+ " was never asked anything matching '" + textFragment + "'");

			java.util.Collections.sort(pending);
			String next = pending.get(0);
			var decision = userFeedback().getAwaitingDecision(next);
			if (decision == null)
				continue;
			PlayerDecided(next, AnswerThatKeepsMoving(decision));
		}
		throw new RuntimeException(playerId + " was never asked anything matching '"
				+ textFragment + "'");
	}

	/**
	 * Put one companion in a skirmish against one minion, at any number of seats,
	 * and stop when that skirmish is the one being resolved.
	 *
	 * {@link #SkipToAssignments} plus {@link Skirmishes#FreepsAssignAndResolve}
	 * is written as P1 and P2 -- it declines assignments for P2 alone -- so a
	 * third seat's unanswered assignment stalls it.
	 *
	 * The shape above two seats was measured rather than guessed, and it is not
	 * the one the two-player helpers imply. The Free Peoples player is asked
	 * first and is offered EVERY opponent's minions at once; each Shadow player
	 * is then asked separately about whichever of their own are still
	 * unassigned. So the assignment this method wants is always the Free Peoples
	 * player's, whoever owns the minion. That matches archery, where the Free
	 * Peoples player assigns the wounds given by minions regardless of who
	 * controls them (docs/GEMP_INTERNALS.md section 6).
	 */
	default void PassUntilSkirmishBetween(PhysicalCardImpl comp, PhysicalCardImpl minion) {
		String target = String.valueOf(minion.getCardId());
		for (int attempts = 1; attempts <= 300; attempts++) {
			if (gameState().getCurrentPhase() == Phase.SKIRMISH
					&& gameState().getSkirmish() != null
					&& gameState().getSkirmish().getShadowCharacters().contains(minion))
				return;

			var pending = new java.util.ArrayList<>(userFeedback().getUsersPendingDecision());
			if (pending.isEmpty())
				throw new RuntimeException("Nobody has a decision, and the phase is "
						+ gameState().getCurrentPhase() + " with no skirmish against "
						+ minion.getBlueprint().getTitle());

			java.util.Collections.sort(pending);
			String playerId = pending.get(0);
			var decision = userFeedback().getAwaitingDecision(playerId);
			if (decision == null)
				continue;

			var params = decision.getDecisionParameters();
			String[] minions = params.get("minions");
			if (minions != null && java.util.Arrays.asList(minions).contains(target)) {
				PlayerDecided(playerId, comp.getCardId() + " " + target);
				continue;
			}
			PlayerDecided(playerId, AnswerThatStartsNoSkirmish(decision));
		}
		throw new RuntimeException("Never reached a skirmish against "
				+ minion.getBlueprint().getTitle() + "; stalled in "
				+ gameState().getCurrentPhase());
	}

	/**
	 * Decline everything, but answer the questions that have no decline.
	 *
	 * "Choose next skirmish to resolve" is a card selection with min=1: passing
	 * it is rejected outright, which is how a walk that declines everything dies
	 * one step short of the skirmish it was walking to.
	 */
	private static String AnswerThatStartsNoSkirmish(
			com.gempukku.lotro.logic.decisions.AwaitingDecision decision) {
		var params = decision.getDecisionParameters();
		switch (decision.getDecisionType()) {
			case INTEGER:
				return params.containsKey("min") ? params.get("min")[0] : "0";
			case ACTION_CHOICE:
			case MULTIPLE_CHOICE:
				return "0";
			case CARD_SELECTION: {
				boolean mustPick = params.containsKey("min") && !params.get("min")[0].equals("0");
				String[] cardIds = params.get("cardId");
				if (mustPick && cardIds != null && cardIds.length > 0)
					return cardIds[0];
				return "";
			}
			default:
				return "";
		}
	}

	/** Decline everything except the invitation to move on. */
	private static String AnswerThatKeepsMoving(
			com.gempukku.lotro.logic.decisions.AwaitingDecision decision) {
		var params = decision.getDecisionParameters();
		String text = decision.getText() == null ? "" : decision.getText().toLowerCase();
		switch (decision.getDecisionType()) {
			case INTEGER:
				return params.containsKey("min") ? params.get("min")[0] : "0";
			case MULTIPLE_CHOICE: {
				String[] results = params.get("results");
				if (results == null || results.length == 0)
					return "0";
				String wanted = text.contains("another move") ? "yes" : "no";
				for (int i = 0; i < results.length; i++)
					if (results[i].trim().equalsIgnoreCase(wanted))
						return String.valueOf(i);
				return "0";
			}
			case ACTION_CHOICE:
				return "0";
			default:
				return "";
		}
	}

	default void SkipToPhaseInverted(Phase target) {
		for(int attempts = 1; attempts <= 20; attempts++)
		{
			Phase current = gameState().getCurrentPhase();
			if(current == target)
				break;

			if(current == Phase.FELLOWSHIP) {
				ShadowPassCurrentPhaseAction();
			}
			else if(current == Phase.SHADOW) {
				FreepsPassCurrentPhaseAction();
			}
			else {
				PassCurrentPhaseActions();
			}

			if(attempts == 20)
			{
				throw new RuntimeException("Could not arrive at target '" + target + "' after 20 attempts!");
			}
		}
	}

	default void SkipToSite(int siteNum) {
		for(int i = GetCurrentSite().getSiteNumber(); i < siteNum; i = GetCurrentSite().getSiteNumber())
		{
			SkipCurrentSite();
		}
	}

	default void SkipCurrentSite() {
		SkipToPhase(Phase.REGROUP);
		PhysicalCardImpl site = GetCurrentSite();
		if(site.getSiteNumber() == 9)
			return; // Game finished
		PassCurrentPhaseActions();
		if(ShadowDecisionAvailable("reconcile"))
		{
			ShadowDeclineReconciliation();
		}
		while(ShadowDecisionAvailable("discard down"))
		{
			ShadowChooseCard((PhysicalCardImpl) GetShadowHand().getFirst());
		}
		if(FreepsDecisionAvailable("another move"))
		{
			FreepsChooseToStay();
		}
		if(FreepsDecisionAvailable("reconcile"))
		{
			FreepsDeclineReconciliation();
		}
		if(FreepsDecisionAvailable("discard down"))
		{
			FreepsChooseCard((PhysicalCardImpl) GetFreepsHand().getFirst());
		}

		//Shadow player
		SkipToPhaseInverted(Phase.REGROUP);
		ShadowPassCurrentPhaseAction(); // actually freeps with the swap
		FreepsPassCurrentPhaseAction(); // actually shadow with the swap
		if(FreepsDecisionAvailable("reconcile"))
		{
			FreepsDeclineReconciliation();
		}
		if(FreepsDecisionAvailable("discard down"))
		{
			FreepsChooseCard((PhysicalCardImpl) GetFreepsHand().getFirst());
		}
		if(ShadowDecisionAvailable("another move"))
		{
			ShadowChoose("1"); // Choose to stay
		}
		if(ShadowDecisionAvailable("reconcile"))
		{
			ShadowDeclineReconciliation();
		}
		if(ShadowDecisionAvailable("discard down"))
		{
			ShadowChooseCard((PhysicalCardImpl) GetShadowHand().getFirst());
		}

		Assert.assertTrue(GetCurrentPhase() == Phase.BETWEEN_TURNS
				|| GetCurrentPhase() == Phase.FELLOWSHIP);
	}




	default void FreepsDismissRevealedCards() { FreepsPassCurrentPhaseAction(); }
	default void ShadowDismissRevealedCards() { ShadowPassCurrentPhaseAction(); }
	default void DismissRevealedCards() {
		FreepsDismissRevealedCards();
		ShadowDismissRevealedCards();
	}

}
