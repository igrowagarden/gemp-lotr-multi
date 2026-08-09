/**
 * blueprintId -> card image URL.
 *
 * Mirrors Card.getImageUrl in the reference client. A blueprint is `set_card`,
 * and the image is `LOTR<setNo padded to 2><cardNo padded to 3>.jpg` on the
 * community CDN:
 *
 *   "1_320"  -> https://i.lotrtcgpc.net/decipher/LOTR01320.jpg
 *
 * Masterworks cards are the exception: above a per-set threshold the number
 * restarts with an `O0` prefix, so set 12 card 195 is LOTR12O01, not LOTR12195.
 *
 * The per-set override tables and the errata list live in `card-images.json`
 * (1,308 images, 64 errata) -- extracted from the reference client's `set40.js`,
 * `hobbit.js` and `PC_Cards.js` by evaluating them, not by parsing, because
 * set40 builds its URLs by concatenation. They are DATA, so they are loaded at
 * runtime rather than transcribed into a module.
 *
 * `loadImageTable()` is optional: until it resolves, `imageUrl` falls back to
 * the derivation, which is right for every card that has no override. And a
 * card whose image fails to load drops the <img> for a tinted placeholder, so a
 * missing entry is a blank card rather than a broken board.
 */

const CDN = "https://i.lotrtcgpc.net/decipher/";

/** The handful of specials from the reference client's CardImages.js. */
export const OVERRIDES = Object.freeze({
  "-1_1": CDN + "LOTR00000.jpg",                     // card back
  "15_204": CDN + "LOTR15060D.jpg",
  "15_205": CDN + "LOTR15060E.jpg",
  "15_206": CDN + "LOTR15060G.jpg",
  "15_207": CDN + "LOTR15029H.jpg",                  // holiday Gandalf
  "gl_theOneRing": "/gemp-lotr/images/cards/gl_theOneRing.png"
});

export const CARD_BACK = OVERRIDES["-1_1"];

/**
 * Meta-site modifiers live in sets 90-93 (Card.isMetaSiteModifier). Such a card
 * is not drawn as itself: it is drawn as the SITE it modifies, with its own art
 * as a strip across the bottom, so the board shows where the fellowship is
 * standing and what has been done to it at once.
 */
export const isMetaSiteModifier = (blueprintId) => {
  const set = parseInt(String(blueprintId ?? "").split("_")[0], 10);
  return set >= 90 && set <= 93;
};

/**
 * Errata cards live in sets 50-89 (Card.hasErrata). The reference marks them
 * with a stripe down the edge, because an errata card's printed text is not the
 * text being played -- which matters most to the player who does not own it.
 */
export const hasErrata = (blueprintId) => {
  const set = parseInt(String(blueprintId ?? "").split("_")[0], 10);
  return set >= 50 && set <= 89;
};

/** How much of the card the modifier's own art takes, bottom-anchored. */
export const META_OVERLAY_PERCENT = 27;

/**
 * `PRE_GAME_SETUP.metaSites` -> { modifierBlueprint: visualBlueprint }.
 *
 * Format is `player:visual|modifier;visual|modifier,player:...`. Note the map
 * is keyed by the MODIFIER's blueprint, not by a card id -- the reference
 * client reads it back with `metaSiteOverlays[card.bareBlueprint]`, and keying
 * it by card id would find nothing.
 */
export function parseMetaSites(raw) {
  const out = {};
  if (!raw) return out;
  for (const entry of String(raw).split(",")) {
    const colon = entry.indexOf(":");
    if (colon < 0) continue;
    for (const pair of entry.slice(colon + 1).split(";")) {
      const [visual, modifier] = pair.split("|");
      if (visual && modifier) out[modifier] = visual;
    }
  }
  return out;
}

const MASTERWORKS_OFFSET = { 17: 148, 18: 140 };

function isMasterworks(setNo, cardNo) {
  switch (setNo) {
    case 12: case 13: return cardNo > 194;
    case 15: return cardNo > 194 && cardNo < 204;
    case 17: return cardNo > 148;
    case 18: return cardNo > 140;
    default: return false;
  }
}

const pad = (n, width) => String(n).padStart(width, "0");

/**
 * @param blueprintId e.g. "1_320"
 * @returns an absolute URL, or null if the id is not of the expected shape --
 *          callers should draw a placeholder rather than an empty <img>.
 */
export function imageUrl(blueprintId) {
  if (!blueprintId) return null;
  if (OVERRIDES[blueprintId]) return OVERRIDES[blueprintId];
  if (TABLE.images[blueprintId]) return TABLE.images[blueprintId];

  const cut = blueprintId.indexOf("_");
  if (cut < 0) return null;
  const setNo = parseInt(blueprintId.slice(0, cut), 10);
  const cardNo = parseInt(blueprintId.slice(cut + 1), 10);
  if (Number.isNaN(setNo) || Number.isNaN(cardNo)) return null;

  const set = pad(setNo, 2);
  const code = isMasterworks(setNo, cardNo)
    ? set + "O0" + (cardNo - (MASTERWORKS_OFFSET[setNo] ?? 194))
    : set + pad(cardNo, 3);

  // A remade card is served from the GEMP origin, not the CDN, and supersedes
  // the printed image.
  if (TABLE.errata[String(setNo)]?.includes(cardNo)) {
    return `/gemp-lotr/images/erratas/LOTR${code}.jpg`;
  }
  return `${CDN}LOTR${code}.jpg`;
}

let TABLE = { images: {}, errata: {} };

/** Optional. Until it resolves, every card falls back to the derivation. */
export async function loadImageTable(url = new URL("./card-images.json", import.meta.url)) {
  try {
    const res = await fetch(url);
    if (!res.ok) return { loaded: 0, errata: 0 };
    const data = await res.json();
    TABLE = { images: data.images ?? {}, errata: data.errata ?? {} };
    return {
      loaded: Object.keys(TABLE.images).length,
      errata: Object.values(TABLE.errata).reduce((n, list) => n + list.length, 0)
    };
  } catch {
    // Missing or unreachable is not fatal: derivation covers the classic sets.
    return { loaded: 0, errata: 0 };
  }
}

/** For tests: install a table without fetching. */
export function setImageTable(table) {
  TABLE = { images: table.images ?? {}, errata: table.errata ?? {} };
}
