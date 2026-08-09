#!/usr/bin/env python
"""Give every player a genuinely random, format-legal deck.

WHAT THIS USED TO DO, AND WHY IT WAS WEAK: the first version sampled from the
seeded `starter` deck's own card list -- 28 distinct cards -- and kept its
ring-bearer, ring and nine sites fixed. Every "random" deck was the same cards in
different proportions, on the same adventure path, with the same ring-bearer. It
varied which actions became playable and when, which is worth something, but it
never exercised a different site, a different ring-bearer, an errata card
(blueprint sets 50-89) or a meta-site modifier (90-93), and it left the rest of
the block untouched.

The real pool comes from the card definitions. `lotrFormats.hjson` says
Fellowship Block is **sets 1, 2 and 3**: 609 cards, of which 57 are sites
covering every site number, and 5 can start with the ring.

LEGALITY, which the server checks at TABLE CREATION rather than on save -- so an
illegal deck surfaces as "could not seat a table" and reads like an
infrastructure fault:

  * equal numbers of Shadow and Free Peoples cards
  * exactly nine sites, one for each site number 1-9
  * a ring-bearer that may start with the ring, and a ring
  * at most 4 copies of any one card

Formats differ in two ways that matter, both read from `lotrFormats.hjson`
rather than hardcoded: which SETS are legal, and which site BLOCK the adventure
path must come from (FELLOWSHIP / TWO_TOWERS / KING / SHADOWS). Site cards carry
a matching `block:` field.

MULTIPATH and SHADOWS formats are skipped. Their sites carry no site number at
all -- a Shadows-block site has `block: Shadows` and no `site:` field -- so the
nine-site path this builds does not apply to them. Modelling their path properly
is a separate job.

Not every format in the file is playable either: the hall offers 23, and the
rest answer "This format is not supported". `--formats` marks what is both
buildable and offered.

    python randomdeck.py <deckName> <seed> <format> user1 user2 ...
    python randomdeck.py --formats            # list what can be generated
"""
import collections
import glob
import io
import os
import random
import re
import sys
import urllib.parse
import urllib.request

BASE = "http://localhost:17002/gemp-lotr-server"
CARDS = r"C:\Users\emers\gemp2\gemp-lotr\gemp-lotr-cards\src\main\resources\cards\official"
FORMATS_FILE = os.path.join(os.path.dirname(CARDS), os.pardir, "lotrFormats.hjson")
# `sites:` in a format maps to the `block:` printed on a site card.
SITE_BLOCK = {"FELLOWSHIP": "Fellowship", "TWO_TOWERS": "Towers",
              "KING": "King", "SHADOWS": "Shadows"}
PASSWORDS = {"asdf": "asdf"}
DECK_SIZE = 60
MAX_COPIES = 4


def post(path, data, cookie=None):
    req = urllib.request.Request(BASE + path, data=urllib.parse.urlencode(data).encode())
    if cookie:
        req.add_header("Cookie", "loggedUser=" + cookie)
    return urllib.request.urlopen(req, timeout=30)


def login(user):
    res = post("/login", {"login": user, "password": PASSWORDS.get(user, "qwer")})
    for key, value in res.getheaders():
        if key.lower() == "set-cookie" and "loggedUser=" in value:
            return value.split("loggedUser=")[1].split(";")[0]
    raise SystemExit("login failed for " + user)


def formats():
    """{code: (name, set numbers, site block)} for every format we can build.

    Scanned line by line rather than with a multi-line regex: the pattern needs
    a literal newline between `name:` and `code:`, and this file has been
    rewritten by script more than once -- an escape that survives one pass and
    not the next turns into a raw line break inside the pattern.
    """
    lines = io.open(os.path.normpath(FORMATS_FILE), encoding="utf-8").read().splitlines()
    out, name = {}, None
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("name:"):
            name = stripped[5:].strip()
        elif stripped.startswith("code:") and name:
            code = stripped[5:].strip().strip('"')
            # The format's own fields follow its code, before the next entry.
            tail = "\n".join(lines[i:i + 30])
            kind = ""
            for t in tail.splitlines():
                if t.strip().startswith("sites:"):
                    kind = t.strip()[6:].strip()
                    break
            block = re.search(r"sets:\s*\[(.*?)\]", tail, re.S)
            sets = {int(n) for n in re.findall(r"\d+", block.group(1))} if block else set()
            # Formats ban cards outright and restrict others to a single copy.
            # Ignoring these gets "Card is X-listed: Galadriel, Lady of Light"
            # from the server -- again only at table creation.
            wide = "\n".join(lines[i:i + 400])
            grab = lambda k: set(re.findall(r"\d+_\d+",
                        (re.search(k + r":\s*\[(.*?)\]", wide, re.S) or [None, ""])[1]))
            if kind in SITE_BLOCK and sets:
                out[code] = (name, sets, SITE_BLOCK[kind], grab("banned"), grab("restricted"))
            name = None
    return out


def top_level(body, key, value):
    """Is `key: value` a field of the card itself, not of a nested effect?

    Card fields sit at two tabs; anything deeper belongs to an effect. Matching
    the key anywhere in the card's text instead treated cards whose ABILITY
    mentions the ring as ring-bearers, and the server refused the deck with
    "Card assigned as Ring-bearer cannot bear the ring" -- at table creation,
    long after the save returned 200.
    """
    want = key + ":"
    for line in body.splitlines():
        depth = len(line) - len(line.lstrip("\t"))
        if depth == 2 and line.strip().startswith(want):
            return line.strip()[len(want):].strip() == value
    return False


def pools(legal_sets, site_block):
    """Parse the card definitions into the pools a deck is built from."""
    free, shadow, ring_bearers, rings = [], [], [], []
    sites = collections.defaultdict(list)

    for folder in sorted(os.listdir(CARDS)):
        m = re.match(r"set(\d+)$", folder)
        if not m or int(m.group(1)) not in legal_sets:
            continue
        for path in glob.glob(os.path.join(CARDS, folder, "*.hjson")):
            text = io.open(path, encoding="utf-8").read()
            for m in re.finditer(r"\n\t(\d+_\d+):\s*\{", text):
                cid, body = m.group(1), text[m.end():m.end() + 1500]
                field = lambda k: (re.search(k + r":\s*([^\n]+)", body) or [None, ""])[1].strip()
                ctype, side = field("type"), field("side").lower()

                if ctype == "Site":
                    number = field("site")
                    # Only sites from the block this format walks.
                    if number.isdigit() and field("block") == site_block:
                        sites[int(number)].append(cid)
                elif ctype == "The One Ring":
                    rings.append(cid)
                else:
                    # A TOP-LEVEL field, not a substring. Matching anywhere in
                    # the card's text also caught nested effects, and the server
                    # answered "Card assigned as Ring-bearer cannot bear the
                    # ring" -- at table creation, as ever.
                    # A ring-bearer must be a COMPANION. Some allies carry
                    # `canStartWithRing: true` as well -- Farmer Maggot and
                    # Filibert Bolger both do -- and the server refuses them
                    # with "Card assigned as Ring-bearer cannot bear the ring".
                    if ctype == "Companion" and top_level(body, "canStartWithRing", "true"):
                        ring_bearers.append(cid)
                    # A ring-bearer is also an ordinary companion, so it stays in
                    # the draw pool too -- only the chosen one is lifted out.
                    if side == "free peoples":
                        free.append(cid)
                    elif side == "shadow":
                        shadow.append(cid)
    return free, shadow, ring_bearers, rings, sites


def sample(rng, pool, count):
    """`count` cards from `pool`, at most MAX_COPIES of any one."""
    counts, picked = {}, []
    while len(picked) < count:
        card = rng.choice(pool)
        if counts.get(card, 0) >= MAX_COPIES:
            continue
        counts[card] = counts.get(card, 0) + 1
        picked.append(card)
    return picked


def build(rng, free, shadow, ring_bearers, rings, sites, banned=(), restricted=()):
    drop = set(banned)
    free = [c for c in free if c not in drop]
    shadow = [c for c in shadow if c not in drop]
    ring_bearers = [c for c in ring_bearers if c not in drop] or ring_bearers
    rings = [c for c in rings if c not in drop] or rings
    sites = {n: ([c for c in v if c not in drop] or v) for n, v in sites.items()}

    rb = rng.choice(ring_bearers)
    ring = rng.choice(rings)
    # One site per number, so the path is complete and in order.
    path = [rng.choice(sites[n]) for n in range(1, 10)]

    half = DECK_SIZE // 2
    # The ring-bearer plays from outside the deck; do not draw it as well.
    # A restricted card is legal but capped at one copy, so it is simplest to
    # keep it out of the random draw entirely.
    keep = lambda pool: [c for c in pool if c not in set(restricted)] or pool
    cards = (sample(rng, keep([c for c in free if c != rb]), half)
             + sample(rng, keep(shadow), half))
    rng.shuffle(cards)
    return "|".join([rb, ring, ",".join(path), ",".join(cards)])


def main():
    if sys.argv[1] == "--formats":
        for code, (name, sets, block, banned, restricted) in sorted(formats().items()):
            free, shadow, rbs, rings, sites = pools(sets, block)
            ok = free and shadow and rbs and rings and all(sites[n] for n in range(1, 10))
            print(f"{'OK ' if ok else 'no '} {code:18} {name:34} "
                  f"{len(free)}f/{len(shadow)}s {sum(len(v) for v in sites.values())} sites")
        return

    name, seed, fmt, users = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4:]
    rng = random.Random(seed)
    known = formats()
    if fmt not in known:
        raise SystemExit(f"unknown or unsupported format {fmt}; try --formats")
    _, legal_sets, site_block, banned, restricted = known[fmt]
    free, shadow, ring_bearers, rings, sites = pools(legal_sets, site_block)
    if not (free and shadow and ring_bearers and rings and all(sites[n] for n in range(1, 10))):
        raise SystemExit(f"card pool incomplete for {fmt}")

    for user in users:
        cookie = login(user)
        res = post("/deck", {
            "participantId": user,
            "deckName": name,
            "targetFormat": fmt,
            "notes": "generated for differential testing",
            "deckContents": build(rng, free, shadow, ring_bearers, rings, sites,
                                  banned, restricted),
        }, cookie)
        if res.status != 200:
            raise SystemExit(f"deck save failed for {user}: HTTP {res.status}")

    print(f"{name} [{fmt}]: {len(free)}f/{len(shadow)}s, "
          f"{sum(len(v) for v in sites.values())} sites, "
          f"{len(ring_bearers)} ring-bearers, seed {seed}")


if __name__ == "__main__":
    main()
