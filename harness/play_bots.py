#!/usr/bin/env python3
"""Drive every seat at a GEMP table over HTTP until the board has cards on it.

DumpGameTrace already plays GEMP headlessly, but it bypasses the server
entirely. This does the same job through the real HTTP protocol, so a browser
can be pointed at the same game and asked to draw it. The answer policy is
deliberately the same MINIMAL policy DumpGameTrace uses -- pass, decline, take
the lowest number -- so the game advances without any of the seats trying to
win.

  python play_bots.py --players asdf,qwer,Librarian,carol,dave --game 1 --seconds 90

Prints one line per decision answered, so a stall is visible rather than silent.
"""
import argparse
import random
import re
import sys
import threading
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

BASE = "http://localhost:17002/gemp-lotr-server"
DELAY = 0.0   # seconds before each answer; keeps a game watchable
PLAY = False       # play cards instead of passing everything
PLAY_CHANCE = 0.35 # how often to take an action when one is offered
DELAY = 0.0   # seconds to pause before each answer, so a game can be watched

PASSWORDS = {"asdf": "asdf"}   # everyone else uses qwer


def post(path, data, cookie=None, timeout=40):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(BASE + path, data=body)
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    if cookie:
        req.add_header("Cookie", "loggedUser=" + cookie)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", "replace")


def get(path, cookie=None, timeout=40):
    req = urllib.request.Request(BASE + path)
    if cookie:
        req.add_header("Cookie", "loggedUser=" + cookie)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read().decode("utf-8", "replace")


def login(user, password):
    body = urllib.parse.urlencode({"login": user, "password": password}).encode()
    req = urllib.request.Request(BASE + "/login", data=body)
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    with urllib.request.urlopen(req, timeout=20) as r:
        for k, v in r.getheaders():
            if k.lower() == "set-cookie" and "loggedUser=" in v:
                return re.search(r"loggedUser=([^;\s]+)", v).group(1)
    raise RuntimeError("no cookie for " + user)


def params_of(decision):
    """<parameter> children, grouped by name -- a name may repeat."""
    out = {}
    for p in decision.findall("parameter"):
        out.setdefault(p.get("name"), []).append(p.get("value"))
    return out


def answer(decision):
    """The MINIMAL policy, matching harness/java/DumpGameTrace.java."""
    kind = decision.get("decisionType")
    p = params_of(decision)

    if kind == "CARD_ACTION_CHOICE":
        # A CARD_ACTION_CHOICE carries parallel cardId/actionId/actionText
        # arrays and is answered with an ACTION ID. Passing is the empty string,
        # which is what MINIMAL does -- and why nothing is ever played, so the
        # board stays at five ring-bearers all game.
        if PLAY:
            actions = p.get("actionId", [])
            # Always taking actions[0] loops forever on a repeatable action --
            # the bot replays it, the phase never advances, and twilight climbs
            # without bound. Choose at random and pass most of the time, which
            # both terminates and produces a varied board.
            if actions and random.random() < PLAY_CHANCE:
                return random.choice(actions)
        return ""
    if kind == "ACTION_CHOICE":
        return "0"                                   # mandatory: take the first
    if kind == "INTEGER":
        return p.get("min", ["0"])[0]
    if kind == "MULTIPLE_CHOICE":
        results = p.get("results", [])
        if not results:
            return "0"
        # Semantic, not positional: mulligan is {No,Yes} but move-again is
        # {Yes,No}, so index 0 would accept one and decline the other.
        for i, r in enumerate(results):
            if r.strip().lower() == "no":
                return str(i)
        return "0"
    if kind in ("ARBITRARY_CARDS", "CARD_SELECTION"):
        try:
            lo = int(p.get("min", ["0"])[0])
        except ValueError:
            lo = 0
        if lo <= 0:
            return ""                                # decline
        ids = p.get("cardId", [])
        if not ids:
            return ""
        picked = [("temp%d" % i) if kind == "ARBITRARY_CARDS" else ids[i]
                  for i in range(min(lo, len(ids)))]
        return ",".join(picked)
    if kind == "ASSIGN_MINIONS":
        return ""                                    # assign nothing
    return ""


class Seat(threading.Thread):
    def __init__(self, user, cookie, game, deadline, log):
        super().__init__(daemon=True)
        self.user, self.cookie, self.game = user, cookie, game
        self.deadline, self.log = deadline, log
        self.channel = 0
        self.answered = 0
        self.error = None

    def handle(self, xml):
        """Answer at most one decision addressed to this seat."""
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            return None
        cn = root.get("cn")
        if cn is not None:
            self.channel = int(cn)
        for ge in root.iter("ge"):
            if ge.get("type") == "D" and ge.get("participantId") == self.user:
                return ge.get("id"), answer(ge), ge.get("decisionType"), ge.get("text")
        return None

    def run(self):
        try:
            pending = self.handle(get("/game/%s?participantId=%s" % (self.game, self.user),
                                      self.cookie))
            while time.time() < self.deadline:
                data = {"participantId": self.user, "channelNumber": self.channel}
                if pending:
                    if DELAY:
                        time.sleep(DELAY)
                    did, val, kind, text = pending
                    data["decisionId"] = did
                    data["decisionValue"] = val
                    self.answered += 1
                    self.log("%-10s %-18s %-8r %s" % (self.user, kind, val,
                                                      (text or "")[:60]))
                try:
                    xml = post("/game/%s" % self.game, data, self.cookie)
                except urllib.error.HTTPError as e:
                    if e.code == 410:                # channel expired: resync
                        pending = self.handle(get("/game/%s?participantId=%s"
                                                  % (self.game, self.user), self.cookie))
                        continue
                    raise
                except Exception:
                    continue                          # long-poll timeout: poll again
                pending = self.handle(xml)
        except Exception as exp:                      # noqa: BLE001 - reported, not raised
            self.error = exp


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--players", required=True)
    ap.add_argument("--game", required=True)
    ap.add_argument("--seconds", type=int, default=90)
    ap.add_argument("--play", action="store_true",
                    help="actually play cards instead of passing everything")
    ap.add_argument("--delay", type=float, default=0.0,
                    help="pause before each answer, so a game can be watched")
    ap.add_argument("--except-player", default=None,
                    help="seat left alone, so a browser can drive it")
    args = ap.parse_args()
    globals()['DELAY'] = args.delay
    globals()['PLAY'] = args.play

    users = args.players.split(",")
    lock = threading.Lock()

    def log(msg):
        with lock:
            print(msg, flush=True)

    cookies = {}
    for u in users:
        cookies[u] = login(u, PASSWORDS.get(u, "qwer"))
        print("login %-10s %s" % (u, cookies[u]))

    deadline = time.time() + args.seconds
    seats = [Seat(u, cookies[u], args.game, deadline, log)
             for u in users if u != args.except_player]
    for s in seats:
        s.start()
    for s in seats:
        s.join(args.seconds + 15)

    print("--- summary ---")
    for s in seats:
        print("%-10s answered=%-4d %s" % (s.user, s.answered, s.error or ""))
    print("cookies: " + " ".join("%s=%s" % (u, c) for u, c in cookies.items()))


if __name__ == "__main__":
    sys.exit(main())
