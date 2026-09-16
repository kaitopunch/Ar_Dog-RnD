#!/usr/bin/env python3
import argparse
import hashlib
import json
import pathlib
import statistics


SCENARIOS = tuple(f"S{index}" for index in range(7))
MIN_RUNS = 5


def sha256(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def frame_gate(frames: dict) -> bool:
    count = frames.get("count", 0)
    return (
        count > 0
        and frames.get("p95Ms", float("inf")) <= 33.3
        and frames.get("p99Ms", float("inf")) <= 50.0
        and frames.get("maxMs", float("inf")) <= 700.0
        and frames.get("within30", 0) / count >= 0.95
    )


def resource_gate(counters: dict) -> bool:
    return (
        counters.get("CameraBindRequested") == 1
        and counters.get("CameraBound") == 1
        and counters.get("CameraStreaming") == 1
        and counters.get("CameraClosed") == 1
        and counters.get("CameraError") == 0
        and counters.get("ModelAssetCreated") == counters.get("ModelAssetDestroyed")
        and counters.get("ModelInstanceCreated") == counters.get("ModelInstanceDestroyed")
    )


parser = argparse.ArgumentParser()
parser.add_argument("--project-dir", default=".")
parser.add_argument("--input-root", default="build/performance-camera-3d")
parser.add_argument("--output")
args = parser.parse_args()

project = pathlib.Path(args.project_dir).resolve()
input_root = (project / args.input_root).resolve()
apk = project / "app/build/outputs/apk/debug/app-debug.apk"
if not apk.is_file():
    raise SystemExit(f"Missing APK: {apk}")
apk_sha = sha256(apk)

runs = {scenario: [] for scenario in SCENARIOS}
for summary_path in sorted(input_root.glob("*/summary.json")):
    data = json.loads(summary_path.read_text(encoding="utf-8"))
    manifest = data.get("manifest", {})
    scenario = manifest.get("scenario")
    if scenario not in runs or manifest.get("apk", {}).get("sha256") != apk_sha:
        continue
    diagnostics = data.get("runtimeDiagnostics") or {}
    frames = diagnostics.get("frames") or {}
    counters = diagnostics.get("counters") or {}
    valid_conditions = (
        manifest.get("durationSeconds") == 30
        and manifest.get("activeRefreshRate") == 60.0
        and data.get("thermal", {}).get("startStatus") in (0, 1)
    )
    passed = (
        valid_conditions
        and data.get("stabilityFindingCount") == 0
        and resource_gate(counters)
        and (scenario == "S0" or frame_gate(frames))
    )
    count = frames.get("count", 0)
    runs[scenario].append({
        "path": str(summary_path.relative_to(project)),
        "passed": passed,
        "thermalStart": data.get("thermal", {}).get("startStatus"),
        "thermalEnd": data.get("thermal", {}).get("endStatus"),
        "p95Ms": frames.get("p95Ms"),
        "p99Ms": frames.get("p99Ms"),
        "maxMs": frames.get("maxMs"),
        "withinThirtyFpsRatio": frames.get("within30", 0) / count if count else None,
        "totalPssAfterKb": data.get("memory", {}).get("totalPssAfterKb"),
        "totalPssAfterTeardownKb": data.get("memory", {}).get("totalPssAfterTeardownKb"),
    })

scenario_summaries = {}
for scenario, scenario_runs in runs.items():
    passed_runs = [run for run in scenario_runs if run["passed"]]
    frame_runs = [run for run in passed_runs if run["p95Ms"] is not None and scenario != "S0"]
    scenario_summaries[scenario] = {
        "validRunCount": len(passed_runs),
        "complete": len(passed_runs) >= MIN_RUNS,
        "medianP95Ms": statistics.median(run["p95Ms"] for run in frame_runs) if frame_runs else None,
        "worstP95Ms": max((run["p95Ms"] for run in frame_runs), default=None),
        "worstP99Ms": max((run["p99Ms"] for run in frame_runs), default=None),
        "worstMaxMs": max((run["maxMs"] for run in frame_runs), default=None),
        "runs": scenario_runs,
    }

result = {
    "apkSha256": apk_sha,
    "requiredRunsPerScenario": MIN_RUNS,
    "complete": all(item["complete"] for item in scenario_summaries.values()),
    "scenarios": scenario_summaries,
}
encoded = json.dumps(result, indent=2, sort_keys=True) + "\n"
if args.output:
    output_path = (project / args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(encoded, encoding="utf-8")
    print(output_path)
else:
    print(encoded, end="")
