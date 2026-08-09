"""Static server for the standalone suite. No GEMP, no Docker.

Serves the src/ tree so the check pages' relative ES module imports resolve.
Exists instead of `python -m http.server` for ONE reason: on Windows the stock
server takes .js MIME from the registry, which some machines map to
text/plain -- and a module served as text/plain is refused by the browser
with an error only visible in the console. The pages then print nothing,
which reads as a dead harness. Pinning the map here removes the machine as a
variable.

Usage: python devserver.py PORT DIRECTORY
"""
import sys
import http.server
import socketserver


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        ".html": "text/html",
        ".js": "text/javascript",
        ".mjs": "text/javascript",
        ".css": "text/css",
        ".json": "application/json",
        ".xml": "application/xml",
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".svg": "image/svg+xml",
        "": "application/octet-stream",
    }

    def log_message(self, *args):  # quiet: the runner reads RESULT lines, not access logs
        pass


if __name__ == "__main__":
    port, directory = int(sys.argv[1]), sys.argv[2]

    class Rooted(Handler):
        def __init__(self, *a, **kw):
            super().__init__(*a, directory=directory, **kw)

    socketserver.ThreadingTCPServer.allow_reuse_address = True
    with socketserver.ThreadingTCPServer(("127.0.0.1", port), Rooted) as httpd:
        print(f"serving {directory} on http://127.0.0.1:{port}", flush=True)
        httpd.serve_forever()
