"""Sensor emulator (data generator) for the ZAI 26Z measurement backend.

Every reading is POSTed to <backend>/api/measurements with the sensor's X-API-Key header;
the backend decides the series from that key. The key is never stored in this file: pass it
with --api-key or the SENSOR_API_KEY environment variable.

Two ways of running:

  live (default)  a new reading every --interval seconds, timestamped "now", until stopped
                  or until --count readings were sent. Also serves the readings locally:
                      GET /latest   - most recent reading (JSON)
                      GET /history  - recent readings, newest last (JSON); ?limit=N
                      GET /stream   - Server-Sent Events stream, one event per new reading

  --backfill      sends --count readings at once with timestamps in the past, spaced by
                  --interval and ending now, then exits. Used to prepare sample data.

How values are generated (--mode), always within [--min, --max]:
  sine      smooth wave over --period seconds, plus a little noise (default)
  random    uniformly random values
  walk      random walk that stays inside the range
  constant  always --value (e.g. a value outside the series range, to show a rejection)

Examples:
  python sensor_server.py --backend-url http://localhost:8080 --api-key KEY
  python sensor_server.py --backend-url http://localhost:8080 --api-key KEY \\
         --backfill --count 48 --interval 1800 --mode walk --min 30 --max 70
  python sensor_server.py --backend-url http://localhost:8080 --api-key KEY \\
         --mode constant --value 999 --count 1 --no-server
"""

import argparse
import json
import math
import os
import random
import sys
import threading
import urllib.error
import urllib.request
from collections import deque
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HISTORY_SIZE = 500
PUSH_TIMEOUT_SECONDS = 5.0
MODES = ("sine", "random", "walk", "constant")


class ValueGenerator:
    """Produces values for given timestamps according to the chosen mode."""

    def __init__(self, mode, min_value, max_value, period_seconds, constant=None):
        self.mode = mode
        self.min = min_value
        self.max = max_value
        self.period = period_seconds
        self.constant = constant
        self._walk = (min_value + max_value) / 2

    def value_at(self, timestamp):
        span = self.max - self.min
        if self.mode == "constant":
            return self.constant
        if self.mode == "random":
            value = random.uniform(self.min, self.max)
        elif self.mode == "walk":
            self._walk += random.gauss(0, span * 0.04)
            # Reflect at the bounds so the walk never leaves the range.
            if self._walk < self.min:
                self._walk = 2 * self.min - self._walk
            if self._walk > self.max:
                self._walk = 2 * self.max - self._walk
            value = self._walk
        else:  # sine
            phase = 2 * math.pi * timestamp.timestamp() / self.period
            value = (self.min + self.max) / 2 + 0.45 * span * math.sin(phase) + random.gauss(0, span * 0.015)
        return round(min(max(value, self.min), self.max), 2)


class BackendPusher:
    """Sends readings to the backend and turns its answers into readable messages."""

    def __init__(self, backend_url, api_key):
        self.url = backend_url.rstrip("/") + "/api/measurements"
        self.api_key = api_key

    def push(self, value, timestamp):
        """Returns (status, message); status is 'accepted', 'rejected' or 'error'."""
        body = json.dumps({"value": value, "timestamp": timestamp.isoformat()}).encode()
        request = urllib.request.Request(
            self.url,
            data=body,
            method="POST",
            headers={"Content-Type": "application/json", "X-API-Key": self.api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=PUSH_TIMEOUT_SECONDS) as response:
                created = json.loads(response.read() or b"{}")
                return "accepted", f"accepted as measurement {created.get('id')} in series {created.get('seriesId')}"
        except urllib.error.HTTPError as e:
            return "rejected", f"REJECTED (HTTP {e.code}): {self._reason(e)}"
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            return "error", f"not sent, backend unreachable: {getattr(e, 'reason', e)}"

    @staticmethod
    def _reason(error):
        raw = error.read().decode(errors="replace")
        try:
            problem = json.loads(raw)
        except ValueError:
            return raw or error.reason
        detail = problem.get("detail") or problem.get("title") or raw
        if error.code == 401:
            detail += " - check the API key; the sensor may have been unregistered"
        for field in problem.get("errors") or []:
            detail += f"; {field.get('field')}: {field.get('message')}"
        return detail


class ReadingStore:
    """Thread-safe history of readings with a condition for waiting on new ones."""

    def __init__(self, maxlen=HISTORY_SIZE):
        self._readings = deque(maxlen=maxlen)
        self._cond = threading.Condition()

    def add(self, reading):
        with self._cond:
            self._readings.append(reading)
            self._cond.notify_all()

    def latest(self):
        with self._cond:
            return self._readings[-1] if self._readings else None

    def history(self, limit=None):
        with self._cond:
            items = list(self._readings)
        return items[-limit:] if limit else items

    def wait_for_newer(self, seq, timeout):
        """Block until a reading with seq > `seq` exists, or timeout. Returns it or None."""
        with self._cond:
            self._cond.wait_for(
                lambda: self._readings and self._readings[-1]["seq"] > seq, timeout
            )
            last = self._readings[-1] if self._readings else None
            return last if last and last["seq"] > seq else None


def report(timestamp, value, unit, result):
    status, message = result if result else (None, "not sent (no backend URL / API key)")
    print(f"[{timestamp.isoformat()}] {value} {unit}  {message}", flush=True)
    if status == "rejected":
        print(f"  >>> value {value} was rejected by the server", file=sys.stderr, flush=True)


def run_backfill(args, generator, pusher):
    """Sends `count` past readings ending now; returns the process exit code."""
    now = datetime.now(timezone.utc).replace(microsecond=0)
    start = now - timedelta(seconds=args.interval * (args.count - 1))
    print(f"Backfilling {args.count} readings from {start.isoformat()} to {now.isoformat()} -> {pusher.url}")
    counts = {"accepted": 0, "rejected": 0, "error": 0}
    for i in range(args.count):
        timestamp = start + timedelta(seconds=args.interval * i)
        value = generator.value_at(timestamp)
        result = pusher.push(value, timestamp)
        counts[result[0]] += 1
        report(timestamp, value, args.unit, result)
        if result[0] == "rejected" and "HTTP 401" in result[1]:
            print("Stopping: the API key is not accepted.", file=sys.stderr)
            break
    print(f"Done: {counts['accepted']} accepted, {counts['rejected']} rejected, {counts['error']} not sent.")
    return 0 if counts["accepted"] == args.count else 1


def run_live(args, generator, pusher, store, stop_event, on_finished):
    seq = 0
    while not stop_event.is_set():
        timestamp = datetime.now(timezone.utc)
        value = generator.value_at(timestamp)
        seq += 1
        store.add({"sensor": args.name, "seq": seq, "timestamp": timestamp.isoformat(),
                   "value": value, "unit": args.unit})
        report(timestamp, value, args.unit, pusher.push(value, timestamp) if pusher else None)
        if args.count and seq >= args.count:
            break
        stop_event.wait(args.interval)
    on_finished()


def make_handler(store):
    class Handler(BaseHTTPRequestHandler):
        def _send_json(self, status, payload):
            body = json.dumps(payload).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            url = urlparse(self.path)
            if url.path == "/latest":
                reading = store.latest()
                if reading is None:
                    self._send_json(503, {"error": "no data yet"})
                else:
                    self._send_json(200, reading)
            elif url.path == "/history":
                try:
                    limit = int(parse_qs(url.query).get("limit", ["0"])[0]) or None
                except ValueError:
                    self._send_json(400, {"error": "limit must be an integer"})
                    return
                self._send_json(200, store.history(limit))
            elif url.path == "/stream":
                self._stream()
            elif url.path == "/":
                self._send_json(200, {"endpoints": ["/latest", "/history?limit=N", "/stream"]})
            else:
                self._send_json(404, {"error": "not found"})

        def _stream(self):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            last = store.latest()
            seq = last["seq"] - 1 if last else 0
            try:
                while True:
                    reading = store.wait_for_newer(seq, timeout=15)
                    if reading is None:
                        self.wfile.write(b": keep-alive\n\n")  # SSE comment
                    else:
                        seq = reading["seq"]
                        self.wfile.write(f"data: {json.dumps(reading)}\n\n".encode())
                    self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                pass  # client disconnected

        def log_message(self, fmt, *args):
            pass  # keep console output to sensor readings

    return Handler


def env_float(name, default):
    value = os.environ.get(name)
    return float(value) if value not in (None, "") else default


def parse_args():
    parser = argparse.ArgumentParser(
        description="Sensor emulator: generates readings and sends them to POST /api/measurements.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    target = parser.add_argument_group("backend")
    target.add_argument("--backend-url", default=os.environ.get("BACKEND_URL"),
                        help="API address, e.g. http://localhost:8080 (env BACKEND_URL)")
    target.add_argument("--api-key", default=os.environ.get("SENSOR_API_KEY"),
                        help="sensor API key from POST /api/sensors (env SENSOR_API_KEY)")

    timing = parser.add_argument_group("timing")
    timing.add_argument("--interval", type=float, default=env_float("SENSOR_INTERVAL", 5.0),
                        help="seconds between readings (env SENSOR_INTERVAL)")
    timing.add_argument("--count", type=int, default=None,
                        help="number of readings; live mode runs forever without it, --backfill requires it")
    timing.add_argument("--backfill", action="store_true",
                        help="send --count readings with past timestamps ending now, then exit")

    values = parser.add_argument_group("values")
    values.add_argument("--mode", choices=MODES, default=os.environ.get("SENSOR_MODE", "sine"),
                        help="how values are generated (env SENSOR_MODE)")
    values.add_argument("--min", type=float, default=env_float("SENSOR_MIN", 18.0),
                        help="lowest generated value (env SENSOR_MIN)")
    values.add_argument("--max", type=float, default=env_float("SENSOR_MAX", 24.0),
                        help="highest generated value (env SENSOR_MAX)")
    values.add_argument("--period", type=float, default=env_float("SENSOR_PERIOD", 86400.0),
                        help="sine mode: length of one full wave in seconds (env SENSOR_PERIOD)")
    values.add_argument("--value", type=float, default=None, help="constant mode: the value to send")
    values.add_argument("--unit", default=os.environ.get("SENSOR_UNIT", "C"),
                        help="unit shown in the output, informational only")
    values.add_argument("--name", default=os.environ.get("SENSOR_NAME", "sensor-emulator"),
                        help="name shown in the local /latest, /history, /stream readings")

    server = parser.add_argument_group("local server (live mode)")
    server.add_argument("--host", default="127.0.0.1")
    server.add_argument("--port", type=int, default=8000)
    server.add_argument("--no-server", action="store_true", help="do not serve readings locally")

    args = parser.parse_args()
    if args.interval <= 0:
        parser.error("--interval must be greater than 0")
    if args.count is not None and args.count < 1:
        parser.error("--count must be at least 1")
    if args.mode == "constant":
        if args.value is None:
            parser.error("--mode constant requires --value")
    elif args.min >= args.max:
        parser.error("--min must be less than --max")
    if args.backfill:
        if args.count is None:
            parser.error("--backfill requires --count")
        if not (args.backend_url and args.api_key):
            parser.error("--backfill requires --backend-url and --api-key (or BACKEND_URL / SENSOR_API_KEY)")
    return args


def main():
    # Units like "°C" must not crash the output on consoles that cannot encode them.
    for stream in (sys.stdout, sys.stderr):
        stream.reconfigure(errors="replace")
    args = parse_args()
    generator = ValueGenerator(args.mode, args.min, args.max, args.period, args.value)

    pusher = None
    if args.backend_url and args.api_key:
        pusher = BackendPusher(args.backend_url, args.api_key)
    elif args.backend_url:
        print("Backend URL set but no API key; readings will not be pushed")

    if args.backfill:
        sys.exit(run_backfill(args, generator, pusher))

    if pusher:
        print(f"Pushing readings to {pusher.url}")
    mode_info = f"value {args.value}" if args.mode == "constant" else f"range [{args.min}, {args.max}]"
    print(f"Live mode: {args.mode}, {mode_info}, every {args.interval:g}s"
          + (f", {args.count} readings" if args.count else ""))

    store = ReadingStore()
    stop_event = threading.Event()
    server = None
    if not args.no_server:
        server = ThreadingHTTPServer((args.host, args.port), make_handler(store))
        server.daemon_threads = True
        print(f"Serving readings on http://{args.host}:{args.port}")

    finished = threading.Event()

    def on_finished():
        finished.set()
        if server:
            server.shutdown()

    sensor_thread = threading.Thread(
        target=run_live, args=(args, generator, pusher, store, stop_event, on_finished), daemon=True
    )
    sensor_thread.start()
    try:
        if server:
            server.serve_forever()
        else:
            while not finished.wait(0.5):
                pass
    except KeyboardInterrupt:
        print("\nShutting down")
    finally:
        stop_event.set()
        if server:
            server.server_close()


if __name__ == "__main__":
    main()
