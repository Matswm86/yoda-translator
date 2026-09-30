"""Relay between the Yoda app and Fish Audio's text-to-speech API.

The app posts {"text": "..."} to /tts and gets back an MP3 of Yoda saying it.
The Fish API key lives only on the server (FISH_API_KEY in the environment),
so no phone ever holds it. Limits protect the key's quota:

- each phone (client IP) gets RELAY_PER_IP_HOUR fresh renders per hour,
- the whole relay makes at most RELAY_DAILY_CAP calls to Fish per UTC day,
- a sentence already rendered is served from the disk cache and costs nothing.

Standard library only. Listens on 127.0.0.1; Caddy terminates TLS in front.
"""

import hashlib
import json
import logging
import os
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

FISH_URL = "https://api.fish.audio/v1/tts"
FISH_MODEL = os.environ.get("FISH_MODEL", "s2.1-pro-free")
YODA_VOICE = os.environ.get("FISH_VOICE_ID", "dd61225f53154701ac8a3122a1cd296d")
API_KEY = os.environ["FISH_API_KEY"]

PORT = int(os.environ.get("RELAY_PORT", "8810"))
MAX_CHARS = int(os.environ.get("RELAY_MAX_CHARS", "240"))
PER_IP_HOUR = int(os.environ.get("RELAY_PER_IP_HOUR", "40"))
DAILY_CAP = int(os.environ.get("RELAY_DAILY_CAP", "300"))
CACHE_DIR = Path(os.environ.get("STATE_DIRECTORY", "/tmp/yoda-tts")) / "cache"
CACHE_MAX_FILES = int(os.environ.get("RELAY_CACHE_MAX_FILES", "5000"))

log = logging.getLogger("yoda-tts")
lock = threading.Lock()
per_ip: dict[str, list[float]] = {}
day = {"date": "", "calls": 0}


def cache_path(text: str) -> Path:
    key = f"{FISH_MODEL}|{YODA_VOICE}|{text}".encode()
    return CACHE_DIR / f"{hashlib.sha256(key).hexdigest()}.mp3"


def take_quota(ip: str) -> str | None:
    """Reserve one Fish call for [ip]. Returns the reason when refused."""
    now = time.time()
    today = time.strftime("%Y-%m-%d", time.gmtime(now))
    with lock:
        if day["date"] != today:
            day["date"], day["calls"] = today, 0
        if day["calls"] >= DAILY_CAP:
            return "daily_cap"
        recent = [t for t in per_ip.get(ip, []) if now - t < 3600]
        if len(recent) >= PER_IP_HOUR:
            per_ip[ip] = recent
            return "per_ip_hour"
        recent.append(now)
        per_ip[ip] = recent
        day["calls"] += 1
    return None


def render(text: str) -> bytes:
    body = json.dumps({"text": text, "reference_id": YODA_VOICE, "format": "mp3"}).encode()
    req = urllib.request.Request(
        FISH_URL,
        data=body,
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
            "model": FISH_MODEL,
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=40) as resp:
        return resp.read()


def trim_cache() -> None:
    files = sorted(CACHE_DIR.glob("*.mp3"), key=lambda p: p.stat().st_mtime)
    for old in files[: max(0, len(files) - CACHE_MAX_FILES)]:
        old.unlink(missing_ok=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "yoda-tts"

    def client_ip(self) -> str:
        forwarded = self.headers.get("X-Forwarded-For", "")
        return forwarded.split(",")[0].strip() or self.client_address[0]

    def send(self, status: int, body: bytes, ctype: str = "application/json") -> None:
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def error(self, status: int, reason: str) -> None:
        self.send(status, json.dumps({"error": reason}).encode())

    def do_GET(self) -> None:
        if self.path == "/health":
            self.send(200, json.dumps({"ok": True, "calls_today": day["calls"]}).encode())
        else:
            self.error(404, "not_found")

    def do_POST(self) -> None:
        if self.path != "/tts":
            return self.error(404, "not_found")
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 4096:
                return self.error(400, "bad_length")
            text = str(json.loads(self.rfile.read(length))["text"]).strip()
        except (ValueError, KeyError, TypeError):
            return self.error(400, "bad_json")
        if not text or len(text) > MAX_CHARS:
            return self.error(400, "bad_text")

        cached = cache_path(text)
        if cached.exists():
            cached.touch()
            return self.send(200, cached.read_bytes(), "audio/mpeg")

        refused = take_quota(self.client_ip())
        if refused:
            log.warning("RELAY_LIMIT reason=%s ip=%s", refused, self.client_ip())
            return self.error(429, refused)
        try:
            audio = render(text)
        except urllib.error.HTTPError as e:
            log.error("RELAY_UPSTREAM_HTTP status=%s body=%s", e.code, e.read()[:200])
            return self.error(502, f"upstream_{e.code}")
        except (urllib.error.URLError, TimeoutError) as e:
            log.error("RELAY_UPSTREAM_DOWN %s", e)
            return self.error(502, "upstream_down")
        if not audio:
            log.error("RELAY_UPSTREAM_EMPTY")
            return self.error(502, "upstream_empty")
        cached.write_bytes(audio)
        trim_cache()
        log.info("RELAY_RENDER chars=%d calls_today=%d", len(text), day["calls"])
        self.send(200, audio, "audio/mpeg")

    def log_message(self, fmt: str, *args) -> None:
        pass  # request lines carry user text; only the tagged events above are logged


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    log.info("listening on 127.0.0.1:%d model=%s", PORT, FISH_MODEL)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
