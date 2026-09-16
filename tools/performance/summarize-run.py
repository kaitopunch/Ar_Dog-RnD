#!/usr/bin/env python3
import json
import pathlib
import re
import sys


if len(sys.argv) != 2:
    raise SystemExit("Usage: summarize-run.py RUN_DIR")

run_dir = pathlib.Path(sys.argv[1])
required = [
    "manifest.json",
    "meminfo-before.txt",
    "meminfo-after.txt",
    "meminfo-after-teardown.txt",
    "thermal-before.txt",
    "thermal-after.txt",
    "activity-after.txt",
    "gfxinfo.txt",
    "camera-after.txt",
    "logcat.txt",
    "trace.perfetto-trace",
]
missing = [name for name in required if not (run_dir / name).is_file()]
if missing:
    raise SystemExit(f"Missing capture artifacts: {', '.join(missing)}")


def text(name: str) -> str:
    return (run_dir / name).read_text(encoding="utf-8", errors="replace")


def total_pss_kb(contents: str):
    match = re.search(r"TOTAL\s+(\d+)", contents)
    return int(match.group(1)) if match else None


def thermal_status(contents: str):
    match = re.search(r"Thermal Status:\s*(\d+)", contents)
    return int(match.group(1)) if match else None


runtime_lines = [
    line.partition("ArDogRuntime: ")[2]
    for line in text("logcat.txt").splitlines()
    if "ArDogRuntime: " in line
]
runtime_snapshots = []
for line in runtime_lines:
    try:
        runtime_snapshots.append(json.loads(line))
    except json.JSONDecodeError:
        runtime_snapshots.append({"parseError": line})

fatal_patterns = re.compile(
    r"FATAL EXCEPTION|ANR in com\.example\.ardogdemo|Fatal signal|OutOfMemoryError",
    re.IGNORECASE,
)
latest_runtime_snapshot = runtime_snapshots[-1] if runtime_snapshots else None
camera_error_count = 0
if isinstance(latest_runtime_snapshot, dict):
    camera_error_count = (
        latest_runtime_snapshot.get("counters", {}).get("CameraError", 0) or 0
    )
foreground_at_end = bool(
    re.search(
        r"(?:topResumedActivity|mResumedActivity).*com\.example\.ardogdemo",
        text("activity-after.txt"),
    )
)
summary = {
    "manifest": json.loads(text("manifest.json")),
    "memory": {
        "totalPssBeforeKb": total_pss_kb(text("meminfo-before.txt")),
        "totalPssAfterKb": total_pss_kb(text("meminfo-after.txt")),
        "totalPssAfterTeardownKb": total_pss_kb(text("meminfo-after-teardown.txt")),
    },
    "thermal": {
        "startStatus": thermal_status(text("thermal-before.txt")),
        "endStatus": thermal_status(text("thermal-after.txt")),
    },
    "runtimeDiagnostics": latest_runtime_snapshot,
    "runtimeSnapshotCount": len(runtime_snapshots),
    "foregroundAtEnd": foreground_at_end,
    "stabilityFindingCount": (
        len(fatal_patterns.findall(text("logcat.txt")))
        + int(camera_error_count > 0)
        + int(not foreground_at_end)
    ),
    "traceBytes": (run_dir / "trace.perfetto-trace").stat().st_size,
}
(run_dir / "summary.json").write_text(
    json.dumps(summary, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print(run_dir / "summary.json")
