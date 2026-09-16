#!/usr/bin/env python3
import argparse
import hashlib
import json
import pathlib
import re
import subprocess


def adb(serial: str, *args: str) -> str:
    return subprocess.check_output(["adb", "-s", serial, *args], text=True).strip()


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def field(contents: str, name: str):
    prefix = f"{name}:"
    for line in contents.splitlines():
        stripped = line.strip()
        if stripped.startswith(prefix):
            return stripped.removeprefix(prefix).strip()
    return None


def active_refresh_rate(contents: str):
    match = re.search(
        r"mActiveSfDisplayMode=.*?peakRefreshRate=([0-9.]+)", contents
    )
    return float(match.group(1)) if match else None


parser = argparse.ArgumentParser()
parser.add_argument("--project-dir", required=True)
parser.add_argument("--run-dir", required=True)
parser.add_argument("--serial", required=True)
parser.add_argument("--scenario", required=True)
parser.add_argument("--duration", required=True, type=int)
parser.add_argument("--thermal", required=True, type=int)
args = parser.parse_args()

project = pathlib.Path(args.project_dir)
run_dir = pathlib.Path(args.run_dir)
apk = project / "app/build/outputs/apk/debug/app-debug.apk"
if not apk.is_file():
    raise SystemExit(f"Missing APK: {apk}. Run assembleDebug first.")

assets = sorted((project / "app/src/main/assets").glob("**/*.glb"))
if not assets:
    raise SystemExit("No packaged GLB files found")

battery_text = (run_dir / "battery-before.txt").read_text(encoding="utf-8")
display_path = run_dir / "display.txt"
display_text = display_path.read_text(encoding="utf-8", errors="replace")
installed_path = adb(args.serial, "shell", "pm", "path", "com.example.ardogdemo")
if not installed_path.startswith("package:"):
    raise SystemExit("Unable to resolve installed ArDog APK")
installed_path = installed_path.splitlines()[0].removeprefix("package:")
installed_sha = adb(args.serial, "shell", "sha256sum", installed_path).split()[0]
local_sha = sha256(apk)
if installed_sha != local_sha:
    raise SystemExit("Installed APK hash does not match local debug APK")

manifest = {
    "scenario": args.scenario,
    "durationSeconds": args.duration,
    "serial": args.serial,
    "model": adb(args.serial, "shell", "getprop", "ro.product.model"),
    "buildFingerprint": adb(args.serial, "shell", "getprop", "ro.build.fingerprint"),
    "displaySize": adb(args.serial, "shell", "wm", "size"),
    "displayDensity": adb(args.serial, "shell", "wm", "density"),
    "peakRefreshRate": adb(args.serial, "shell", "settings", "get", "system", "peak_refresh_rate"),
    "minRefreshRate": adb(args.serial, "shell", "settings", "get", "system", "min_refresh_rate"),
    "activeRefreshRate": active_refresh_rate(display_text),
    "displayDumpSha256": sha256(display_path),
    "brightness": adb(args.serial, "shell", "settings", "get", "system", "screen_brightness"),
    "battery": {
        "level": field(battery_text, "level"),
        "status": field(battery_text, "status"),
        "acPowered": field(battery_text, "AC powered"),
        "usbPowered": field(battery_text, "USB powered"),
        "wirelessPowered": field(battery_text, "Wireless powered"),
    },
    "thermalStartStatus": args.thermal,
    "apk": {
        "path": str(apk.relative_to(project)),
        "installedPath": installed_path,
        "sha256": local_sha,
    },
    "assets": {
        str(path.relative_to(project)): sha256(path)
        for path in assets
    },
}
(run_dir / "manifest.json").write_text(
    json.dumps(manifest, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
