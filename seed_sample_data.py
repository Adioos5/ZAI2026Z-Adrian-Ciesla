"""Prepares the sample data required by F11: 3 series with at least 15 measurements each.

As administrator it creates the series (unless a series with that name already exists) and
registers one sensor per series. The measurements themselves are then sent by the sensor
emulator (sensor_server.py --backfill) through POST /api/measurements with each sensor's key,
never written to the database directly.

Run:  python seed_sample_data.py --backend-url https://your-api.example.com
      (admin credentials from --username / --password, ADMIN_USERNAME / ADMIN_PASSWORD,
       or asked for interactively)
"""

import argparse
import getpass
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

GENERATOR = Path(__file__).with_name("sensor_server.py")

# name, unit, color, icon, allowed range, generator mode and the range it generates in.
SAMPLE_SERIES = [
    {"name": "Temperatura - salon", "unit": "°C", "color": "#d94f30", "icon": "thermometer",
     "minValue": -20, "maxValue": 50, "mode": "sine", "gen_min": 18, "gen_max": 25},
    {"name": "Wilgotność - salon", "unit": "%", "color": "#2b7bb9", "icon": "droplet",
     "minValue": 0, "maxValue": 100, "mode": "walk", "gen_min": 35, "gen_max": 65},
    {"name": "Ciśnienie atmosferyczne", "unit": "hPa", "color": "#3a9a5b", "icon": "gauge",
     "minValue": 900, "maxValue": 1100, "mode": "walk", "gen_min": 1000, "gen_max": 1025},
]


class Api:
    def __init__(self, base_url):
        self.base = base_url.rstrip("/")
        self.token = None

    def call(self, method, path, body=None):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            detail = e.read().decode(errors="replace")
            sys.exit(f"{method} {path} failed: HTTP {e.code} {detail}")
        except urllib.error.URLError as e:
            sys.exit(f"Cannot reach {self.base}: {e.reason}")


def main():
    sys.stdout.reconfigure(errors="replace")
    parser =argparse.ArgumentParser(description="Create F11 sample data through the API.")
    parser.add_argument("--backend-url", default=os.environ.get("BACKEND_URL", "http://localhost:8080"))
    parser.add_argument("--username", default=os.environ.get("ADMIN_USERNAME"))
    parser.add_argument("--password", default=os.environ.get("ADMIN_PASSWORD"))
    parser.add_argument("--count", type=int, default=48, help="measurements per series")
    parser.add_argument("--interval", type=float, default=1800, help="seconds between measurements")
    args = parser.parse_args()
    if args.count < 15:
        parser.error("F11 needs at least 15 measurements per series")

    username = args.username or input("Admin username: ")
    password = args.password or getpass.getpass("Admin password: ")

    api = Api(args.backend_url)
    api.token = api.call("POST", "/api/auth/login", {"username": username, "password": password})["accessToken"]
    existing = {series["name"]: series for series in api.call("GET", "/api/series")}

    failures = 0
    for spec in SAMPLE_SERIES:
        series = existing.get(spec["name"])
        if series:
            print(f"\nSeries '{spec['name']}' already exists (id {series['id']}), adding measurements to it")
        else:
            series = api.call("POST", "/api/series", {
                key: spec[key] for key in ("name", "minValue", "maxValue", "color", "icon", "unit")})
            print(f"\nCreated series '{spec['name']}' (id {series['id']})")

        sensor = api.call("POST", "/api/sensors", {"name": f"Generator - {spec['name']}", "seriesId": series["id"]})
        print(f"Registered sensor {sensor['id']} for it")

        # The key goes to the generator through the environment, not the command line.
        env = dict(os.environ, SENSOR_API_KEY=sensor["apiKey"])
        result = subprocess.run([
            sys.executable, str(GENERATOR), "--backfill",
            "--backend-url", args.backend_url,
            "--count", str(args.count), "--interval", str(args.interval),
            "--mode", spec["mode"], "--min", str(spec["gen_min"]), "--max", str(spec["gen_max"]),
            "--unit", spec["unit"],
        ], env=env)
        failures += result.returncode != 0

    api.call("POST", "/api/auth/logout")
    if failures:
        sys.exit(f"\n{failures} series did not receive all measurements, see the output above.")
    print(f"\nDone: {len(SAMPLE_SERIES)} series with {args.count} measurements each.")


if __name__ == "__main__":
    main()
