/**
 * How many ranks a row of cards needs, and how big those cards end up.
 *
 * Total card area is conserved however you arrange them, so ranks never create
 * space -- they stop you wasting it. A single row wastes nothing until the cards
 * are forced narrower than the band is tall; past that every card is
 * width-constrained and the leftover height is dead.
 *
 * Let M be how many full-height cards one row holds. Then rank k earns its place
 * at N > k(k-1)M, and k ranks are fully efficient at N = k^2 M.
 *
 *   1 rank : N <= 2M      2 ranks : 2M < N <= 6M      3 ranks : 6M < N <= 12M
 *
 * The existing client already implements exactly this test, arrived at
 * independently -- CardGroup.layoutInRowsIfPossible takes the one-row height only
 * `if (oneRowHeight * 2 + padding > this.height)`, which is N <= 2M. Worth
 * keeping rather than reinventing.
 */

/** Portrait playing card. Sites are laid out sideways and pass 7/5. */
export const CARD_ASPECT = 5 / 7;

/** Full-height cards that fit in one row of a boxWidth x boxHeight band. */
export function capacity(boxWidth, boxHeight, aspect = CARD_ASPECT) {
  if (boxHeight <= 0 || aspect <= 0) return 0;
  return boxWidth / (aspect * boxHeight);
}

/**
 * Ranks for `count` cards. Depends only on the band's aspect ratio, not its
 * pixel size, so the answer does not change with resolution -- only with the
 * shape of the band.
 */
export function ranks(count, boxWidth, boxHeight, aspect = CARD_ASPECT) {
  if (count <= 1) return 1;
  const M = capacity(boxWidth, boxHeight, aspect);
  if (M <= 0) return 1;
  let k = 1;
  while (k * (k + 1) * M < count && k < 12) k++;
  return k;
}

/** Card height once the cards are split across k ranks. */
export function cardHeight(count, boxWidth, boxHeight, k = 1, aspect = CARD_ASPECT) {
  if (count <= 0) return boxHeight;
  const perRank = Math.ceil(count / k);
  return Math.min(boxHeight / k, boxWidth / (perRank * aspect));
}

/** The arrangement a band should use: ranks, card height, cards per rank. */
export function plan(count, boxWidth, boxHeight, aspect = CARD_ASPECT) {
  const k = ranks(count, boxWidth, boxHeight, aspect);
  return {
    ranks: k,
    perRank: Math.ceil(count / Math.max(k, 1)),
    cardHeight: cardHeight(count, boxWidth, boxHeight, k, aspect),
    capacity: capacity(boxWidth, boxHeight, aspect)
  };
}
