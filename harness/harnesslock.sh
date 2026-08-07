#!/usr/bin/env bash
# A lock that stops `sync.sh` deleting the served tree out from under a running
# harness.
#
# `sync.sh` opens with `rm -rf "$DST"/*`. Anything served from that directory
# while it runs -- every differential, since they all load the pages from the
# GEMP origin -- fetches ES modules from a directory that is being emptied and
# refilled. The pages die and emit no RESULT line, which `fuzzrun.sh` then
# prints as `CONTROL DID NOT FIRE`. That is an exact impersonation of a real
# regression, and it cost a 15-minute run and a false bug report before anyone
# noticed the RESULT field was empty rather than passing.
#
# This is a SHARED lock, not a mutex: any number of harnesses may hold it at
# once, because running two differentials together is fine and only `sync.sh`
# is dangerous. One file per holder, named by pid, so a holder that dies takes
# its own entry with it and no counter can drift.
#
#   source "$(dirname "$0")/harnesslock.sh"
#   harness_lock fuzzrun
#
# Liveness is `kill -0`, so a killed run leaves a stale file that the next
# reader removes rather than a lock that blocks for ever. `SYNC_FORCE=1` is the
# escape hatch if that ever proves too clever.

HARNESS_LOCKDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.harness-locks"

# Take the lock for this shell, and release it however the shell ends.
harness_lock() {
  mkdir -p "$HARNESS_LOCKDIR"
  printf '%s\t%s\n' "${1:-harness}" "$(date '+%Y-%m-%d %H:%M:%S')" \
    > "$HARNESS_LOCKDIR/$$"
  # INT and TERM as well as EXIT: a Ctrl-C'd run that left its file behind
  # would make the next sync.sh refuse for a reason that no longer exists.
  trap 'rm -f "$HARNESS_LOCKDIR/$$"' EXIT
  trap 'rm -f "$HARNESS_LOCKDIR/$$"; exit 130' INT
  trap 'rm -f "$HARNESS_LOCKDIR/$$"; exit 143' TERM
}

# Print one line per LIVE holder; silently reap the dead ones. Empty output
# means nothing is running.
harness_lock_holders() {
  [ -d "$HARNESS_LOCKDIR" ] || return 0
  local f pid
  for f in "$HARNESS_LOCKDIR"/*; do
    [ -e "$f" ] || continue
    pid="$(basename "$f")"
    case "$pid" in
      ''|*[!0-9]*) rm -f "$f"; continue ;;   # not a pid file; not ours
    esac
    if kill -0 "$pid" 2>/dev/null; then
      printf '  %s (pid %s)\n' "$(cat "$f" 2>/dev/null | tr '\t' ' ')" "$pid"
    else
      rm -f "$f"
    fi
  done
}
