"""Tiny read-only web server for the release folder (see serve.ps1).

Only answers GET/HEAD for version.json and .apk files in that folder, and only to
addresses on the home network (private ranges), so nothing else on the PC is shared.
"""
import http.server
import ipaddress
import os
import sys
from functools import partial

ALLOWED_SUFFIXES = (".apk", "version.json")


class Handler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".apk": "application/vnd.android.package-archive",
        ".json": "application/json",
    }

    def _allowed(self) -> bool:
        client = ipaddress.ip_address(self.client_address[0])
        if not (client.is_private or client.is_loopback):
            return False
        name = self.path.split("?", 1)[0].lstrip("/")
        return "/" not in name and "\\" not in name and name.endswith(ALLOWED_SUFFIXES)

    def do_GET(self):
        if not self._allowed():
            self.send_error(404)
            return
        super().do_GET()

    def do_HEAD(self):
        if not self._allowed():
            self.send_error(404)
            return
        super().do_HEAD()

    def end_headers(self):
        # version.json must never be cached, or the phone misses new versions.
        if self.path.split("?", 1)[0].endswith(".json"):
            self.send_header("Cache-Control", "no-store")
        super().end_headers()


def main() -> None:
    folder, port = sys.argv[1], int(sys.argv[2])
    os.makedirs(folder, exist_ok=True)
    server = http.server.ThreadingHTTPServer(("0.0.0.0", port), partial(Handler, directory=folder))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
