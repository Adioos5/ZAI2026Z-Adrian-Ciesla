"""Simulated temperature sensor served over HTTP.

A background thread produces a new reading every 5 seconds. Endpoints:
    GET /latest   - most recent reading (JSON)
    GET /history  - recent readings, newest last (JSON); ?limit=N
    GET /stream   - Server-Sent Events stream, one event per new reading

If a backend URL and sensor API key are given, every reading is also POSTed
to <backend>/api/measurements. The key comes from registering the sensor via
POST /api/sensors on the backend.

Run:  python sensor_server.py [--host 0.0.0.0] [--port 8000]
                              [--backend-url http://localhost:8080] [--api-key KEY]
      (or set BACKEND_URL / SENSOR_API_KEY)
"""

import argparse
import json
import math
import os
import random
import threading
import time
import urllib.error
import urllib.request
from collections import deque
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

INTERVAL_SECONDS = 5.0
HISTORY_SIZE = 500
PUSH_TIMEOUT_SECONDS = 3.0


class TemperatureSensor:
    """Generates realistic-looking readings: slow daily cycle + drift + noise."""

    def __init__(self, sensor_id="temp-sensor-01", base_temp=21.0):
        self.sensor_id = sensor_id
        self.base_temp = base_temp
        self._drift = 0.0
        self._seq = 0

    def read(self):
        now = datetime.now(timezone.utc)
        # Daily sinusoidal cycle, amplitude 3 °C, peak around 15:00 local-ish.
        hours = now.hour + now.minute / 60 + now.second / 3600
        daily = 3.0 * math.sin((hours - 9) / 24 * 2 * math.pi)
        # Random walk drift, pulled back toward 0 so it stays bounded.
        self._drift += random.gauss(0, 0.05) - 0.02 * self._drift
        noise = random.gauss(0, 0.1)
        self._seq += 1
        return {
            "sensor_id": self.sensor_id,
            "seq": self._seq,
            "timestamp": now.isoformat(),
            "temperature": round(self.base_temp + daily + self._drift + noise, 2),
            "unit": "C",
        }


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


class BackendPusher:
    """Sends readings to the backend's measurement endpoint, authenticated by the sensor's API key."""

    def __init__(self, backend_url, api_key):
        self.url = backend_url.rstrip("/") + "/api/measurements"
        self.api_key = api_key

    def push(self, reading):
        body = json.dumps({"value": reading["temperature"], "timestamp": reading["timestamp"]}).encode()
        request = urllib.request.Request(
            self.url,
            data=body,
            method="POST",
            headers={"Content-Type": "application/json", "X-API-Key": self.api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=PUSH_TIMEOUT_SECONDS):
                pass
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            print(f"  push failed: HTTP {e.code} {detail}", flush=True)
        except (urllib.error.URLError, TimeoutError, OSError) as e:
            # Backend not up yet or unreachable; the next reading will try again.
            print(f"  push failed: {getattr(e, 'reason', e)}", flush=True)


def run_sensor(sensor, store, stop_event, pusher=None):
    while not stop_event.is_set():
        reading = sensor.read()
        store.add(reading)
        print(f"[{reading['timestamp']}] {reading['temperature']:.2f} °C", flush=True)
        if pusher:
            pusher.push(reading)
        stop_event.wait(INTERVAL_SECONDS)


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


def main():
    parser = argparse.ArgumentParser(description="Simulated temperature sensor server")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--backend-url", default=os.environ.get("BACKEND_URL"))
    parser.add_argument("--api-key", default=os.environ.get("SENSOR_API_KEY"))
    args = parser.parse_args()

    pusher = None
    if args.backend_url and args.api_key:
        pusher = BackendPusher(args.backend_url, args.api_key)
        print(f"Pushing readings to {pusher.url}")
    elif args.backend_url:
        print("Backend URL set but no API key; readings will not be pushed")

    store = ReadingStore()
    stop_event = threading.Event()
    sensor_thread = threading.Thread(
        target=run_sensor, args=(TemperatureSensor(), store, stop_event, pusher), daemon=True
    )
    sensor_thread.start()

    server = ThreadingHTTPServer((args.host, args.port), make_handler(store))
    server.daemon_threads = True
    print(f"Sensor server on http://{args.host}:{args.port} (new reading every {INTERVAL_SECONDS:g}s)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down")
    finally:
        stop_event.set()
        server.server_close()


if __name__ == "__main__":
    main()
