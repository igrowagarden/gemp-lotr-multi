#!/usr/bin/env bash
# Copy the client into our container's web directory, cache-busted.
#
# The client has to be served from the GEMP origin: the session cookie is bound
# to it. That directory is bind-mounted into the container, so this is a copy and
# a reload -- no rebuild, no restart.
#
# GEMP serves JavaScript with an `etag` and no `Cache-Control`, so browsers cache
# ES modules aggressively and may not revalidate. That cost real confusion: a fix
# was live on the server while the browser kept running the old module, so the
# feature "still didn't work" and the CSS for it was missing too. Telling someone
# to hard-reload is not a fix, so every relative import and stylesheet href gets
# a ?v=<stamp> stamped in at deploy time. Nested imports are rewritten as well,
# which is the part a query on the entry page alone would miss.
set -eu

SRC="$(cd "$(dirname "$0")/../src" && pwd)"
DST="/c/Users/emers/gemp2/gemp-lotr/gemp-lotr-async/src/main/web/newclient"
STAMP="$(date +%s)"

mkdir -p "$DST"
rm -rf "${DST:?}"/*
cp -r "$SRC"/* "$DST"/
# `wirecheck` fetches this, so removing it makes that ONE suite stall at
# "RUNNING" for ever when served over HTTP -- no error, no failure, just a page
# that never finishes. Run wirecheck from disk, or copy the capture across for
# the run and delete it again. It is excluded on purpose: it is a real captured
# game and has no business being served to anyone who loads the client.
rm -f "$DST/dev/live_capture.xml"

python - "$DST" "$STAMP" <<'PY'
import os, re, sys
dst, stamp = sys.argv[1], sys.argv[2]

# from "./x.js"  /  import("./x.js")  /  href="./x.css"  /  new URL("./x.json", ...)
patterns = [
    re.compile(r'(from\s+["\'])(\.{1,2}/[^"\']+?\.js)(["\'])'),
    re.compile(r'(import\(\s*["\'])(\.{1,2}/[^"\']+?\.js)(["\'])'),
    re.compile(r'(href=")(\.{1,2}/[^"]+?\.css)(")'),
    re.compile(r'(new URL\(\s*["\'])(\.{1,2}/[^"\']+?\.json)(["\'])'),
]

touched = 0
for root, _, files in os.walk(dst):
    for name in files:
        if not name.endswith((".js", ".html")):
            continue
        path = os.path.join(root, name)
        text = open(path, encoding="utf-8").read()
        original = text
        for pat in patterns:
            text = pat.sub(lambda m: f"{m.group(1)}{m.group(2)}?v={stamp}{m.group(3)}", text)
        if text != original:
            open(path, "w", encoding="utf-8").write(text)
            touched += 1
print(f"cache-busted {touched} file(s) with v={stamp}")
PY

echo "synced $(find "$DST" -type f | wc -l) files to $DST"
echo
echo "  board  http://localhost:17002/gemp-lotr/newclient/live.html?gameId=NN&participantId=watcher&login=watcher&password=qwer"
echo "  hall   http://localhost:17002/gemp-lotr/newclient/hall.html?participantId=watcher&login=watcher&password=qwer"
