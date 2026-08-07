#!/usr/bin/env python3
"""Prove the auto-pass cookie changes what the ENGINE asks, not just what the
server parses.

`src/dev/autopasscheck.html` already measures that the cookie ARRIVES and is
PARSED -- a poisoned value comes back 500, so `Phase.valueOf` demonstrably ran
on our string. What that cannot show is the thing the feature is actually for:
that a *valid* set changes which decisions reach the player. This does.

The observable is a **CARD_ACTION_CHOICE offering no cards at all**. That is
exactly what `playableActions.isEmpty()` produces at
PlayerPlaysPhaseActionsUntilPassesGameProcess.java:34 and its two siblings, so
it is the decision auto-pass suppresses and the only one it suppresses.

    run A   autoPass=false          nothing auto-passes  -> expect MANY
    run B   all seven phases        everything does      -> expect ZERO
    run C   no cookie               _autoPassDefault     -> expect SHADOW only

**Run A is the control.** Without it, a zero in run B is indistinguishable from
a measurement that cannot see these decisions at all -- which is this project's
most-repeated failure and the reason the comparison is built as a pair.

Run C is the sharpest of the three and the one worth keeping. It is what every
client gets today, and the default set omits SHADOW and SKIRMISH -- so the
no-action decisions should survive in SHADOW and nowhere else. Measured:

    A  FELLOWSHIP 7, REGROUP 21, SHADOW 4     (32)
    C  SHADOW 4                               ( 4)   <- default suppresses the rest
    B  none                                   ( 0)

Three points on one curve, matching GameRequestHandler.java:50-54 exactly, and
C-against-B is the user-facing claim: tick Shadow and the Shadow prompts stop.

Two games are two different games, so this is deliberately NOT a count-vs-count
comparison: it is "many" against "none", which survives the shuffle. The counts
are printed per phase anyway, because a per-phase breakdown catches the case
where only some phases respond and a total would average it away.

  python autopassmeasure.py --game 7 --subject asdf --others qwer,Librarian \
                            --seconds 120

The subject is driven by this script WITH the cookie; the other seats are driven
by play_bots' MINIMAL policy without one, so they carry the game forward.
"""
import argparse
import collections
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

sys.path.insert(0, __file__.rsplit("/", 1)[0] if "/" in __file__ else ".")
import play_bots  # noqa: E402  -- reuse its login, its policy and its Seat

BASE = play_bots.BASE

# Phase names as the COOKIE spells them (the enum), which is not how the wire
# spells them -- GAME_PHASE_CHANGE carries Phase.getHumanReadable(), so the
# stream says "Regroup" where the cookie says "REGROUP". Normalised on read; see
# src/model/autopass.js, which mirrors Phase.findPhase for the same reason.
ALL_PHASES = ["FELLOWSHIP", "SHADOW", "MANEUVER", "ARCHERY",
              "ASSIGNMENT", "SKIRMISH", "REGROUP"]


def phase_name(wire):
    """Phase.findPhase (Phase.java:37-39), which is the mapping that matters."""
    if wire is None:
        return None
    return wire.upper().strip().replace(" ", "_").replace("-", "_")


class Subject(threading.Thread):
    """One seat, driven with an auto-pass cookie, counting what it is asked."""

    def __init__(self, user, session, game, deadline, cookie_extra, log):
        super().__init__(daemon=True)
        self.user, self.session, self.game = user, session, game
        self.deadline, self.log = deadline, log
        self.cookie_extra = cookie_extra
        self.channel = 0
        self.phase = None
        # The whole point of the run: zero-card CARD_ACTION_CHOICEs, by phase.
        self.noaction = collections.Counter()
        self.decisions = collections.Counter()
        self.phases_seen = collections.Counter()
        self.error = None
        self.http_errors = collections.Counter()

    def _cookie(self):
        parts = ["loggedUser=" + self.session]
        parts.extend(self.cookie_extra)
        return "; ".join(parts)

    def _request(self, path, data=None):
        body = urllib.parse.urlencode(data).encode() if data is not None else None
        req = urllib.request.Request(BASE + path, data=body)
        if body is not None:
            req.add_header("Content-Type", "application/x-www-form-urlencoded")
        req.add_header("Cookie", self._cookie())
        with urllib.request.urlopen(req, timeout=40) as r:
            return r.read().decode("utf-8", "replace")

    def handle(self, xml):
        try:
            root = ET.fromstring(xml)
        except ET.ParseError:
            return None
        cn = root.get("cn")
        if cn is not None:
            self.channel = int(cn)

        pending = None
        # Order matters: a GPC earlier in the same batch as a decision sets the
        # phase that decision belongs to. Walking the batch in document order
        # is what keeps the attribution right.
        for ge in root.iter("ge"):
            if ge.get("type") == "GPC":
                nxt = phase_name(ge.get("phase"))
                if nxt:
                    self.phase = nxt
                    self.phases_seen[nxt] += 1
            elif ge.get("type") == "D" and ge.get("participantId") == self.user:
                kind = ge.get("decisionType")
                self.decisions[kind] += 1
                params = play_bots.params_of(ge)
                if kind == "CARD_ACTION_CHOICE" and not params.get("cardId"):
                    self.noaction[self.phase or "?"] += 1
                pending = (ge.get("id"), play_bots.answer(ge))
        return pending

    def run(self):
        try:
            pending = self.handle(self._request(
                "/game/%s?participantId=%s" % (self.game, self.user)))
            while time.time() < self.deadline:
                data = {"participantId": self.user, "channelNumber": self.channel}
                if pending:
                    data["decisionId"], data["decisionValue"] = pending
                try:
                    xml = self._request("/game/%s" % self.game, data)
                except urllib.error.HTTPError as e:
                    self.http_errors[e.code] += 1
                    if e.code == 410:
                        pending = self.handle(self._request(
                            "/game/%s?participantId=%s" % (self.game, self.user)))
                        continue
                    # A 500 here is the empty-cookie defect and must not be
                    # swallowed as a timeout -- it would read as a quiet game.
                    if e.code >= 500:
                        raise
                    continue
                except Exception:
                    continue                       # long-poll timeout: poll again
                pending = self.handle(xml)
        except Exception as exp:                   # noqa: BLE001 - reported
            self.error = exp


def drive_others(users, game, deadline, log):
    """The other seats, MINIMAL policy, no auto-pass cookie of their own."""
    seats = []
    for u in users:
        session = play_bots.login(u, play_bots.PASSWORDS.get(u, "qwer"))
        s = play_bots.Seat(u, session, game, deadline, log)
        s.start()
        seats.append(s)
    return seats


def run_once(label, cookie_extra, args, log):
    deadline = time.time() + args.seconds
    session = play_bots.login(args.subject, play_bots.PASSWORDS.get(args.subject, "qwer"))
    subject = Subject(args.subject, session, args.game, deadline, cookie_extra, log)
    others = drive_others(args.others.split(","), args.game, deadline, log)
    subject.start()
    subject.join()
    for s in others:
        s.join(timeout=5)
    print("\n--- %s   cookie: %s" % (label, "; ".join(cookie_extra) or "(none)"))
    if subject.error:
        print("    ERROR: %r" % (subject.error,))
    if subject.http_errors:
        print("    http:  %s" % dict(subject.http_errors))
    print("    decisions received: %s" % dict(subject.decisions))
    print("    phases seen:        %s" % dict(subject.phases_seen))
    print("    NO-ACTION CARD_ACTION_CHOICE by phase: %s"
          % (dict(subject.noaction) or "{} (none)"))
    return subject


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--game", required=True)
    ap.add_argument("--subject", default="asdf")
    ap.add_argument("--others", required=True)
    ap.add_argument("--seconds", type=int, default=120)
    ap.add_argument("--only", choices=["A", "B", "C"], default=None,
                    help="run one third; each needs its own fresh game")
    args = ap.parse_args()

    def log(msg):
        pass                                        # the bots' chatter is noise here

    if args.only in (None, "A"):
        a = run_once("RUN A (control): nothing auto-passes",
                     ["autoPass=false"], args, log)
    if args.only in (None, "B"):
        b = run_once("RUN B: all seven phases auto-pass",
                     ["autoPassPhases=" + "0".join(ALL_PHASES)], args, log)
    if args.only == "C":
        # No cookie at all -- _autoPassDefault. The sharpest of the three,
        # because it is what every client gets today and it should suppress
        # FELLOWSHIP and REGROUP while leaving SHADOW asking. That difference,
        # inside one run, is the user-facing claim: tick Shadow and the Shadow
        # prompts stop.
        run_once("RUN C: no cookie -- the server's own default set", [], args, log)
        return 0

    if args.only is None:
        print("\n=== VERDICT")
        seen_a, seen_b = sum(a.noaction.values()), sum(b.noaction.values())
        if seen_a == 0:
            print("  INCONCLUSIVE — the control saw no no-action decisions at all.")
            print("  Nothing is proven: a zero in run B would mean the same thing.")
            print("  Give it longer, or drive the other seats with --play.")
            return 2
        if seen_b == 0:
            print("  PASS — control saw %d, with auto-pass on it saw 0." % seen_a)
            return 0
        print("  FAIL — auto-pass on, yet %d no-action decision(s) still arrived: %s"
              % (seen_b, dict(b.noaction)))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
