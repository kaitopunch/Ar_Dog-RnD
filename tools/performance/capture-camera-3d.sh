#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 --serial SERIAL --scenario S0..S12 [--duration SEC] [--expect-text TEXT]"
}

serial=""
scenario=""
duration_seconds=30
expect_text=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --serial) serial="$2"; shift 2 ;;
    --scenario) scenario="$2"; shift 2 ;;
    --duration) duration_seconds="$2"; shift 2 ;;
    --expect-text) expect_text="$2"; shift 2 ;;
    *) usage; exit 2 ;;
  esac
done

[[ -n "$serial" && "$scenario" =~ ^S([0-9]|1[0-2])$ ]] || { usage; exit 2; }
[[ "$duration_seconds" =~ ^[0-9]+$ && "$duration_seconds" -ge 5 ]] || {
  echo "Duration must be an integer >= 5" >&2
  exit 2
}

project_dir=$(cd "$(dirname "$0")/../.." && pwd)
output_root="$project_dir/build/performance-camera-3d"
run_id="$(date -u +%Y%m%dT%H%M%SZ)-${scenario}-${serial}"
run_dir="$output_root/$run_id"
package_name="com.example.ardogdemo"
activity_name="$package_name/.MainActivity"
mkdir -p "$run_dir"

adb -s "$serial" get-state | grep -qx device
original_peak_rate=$(adb -s "$serial" shell settings get system peak_refresh_rate | tr -d '\r')
original_min_rate=$(adb -s "$serial" shell settings get system min_refresh_rate | tr -d '\r')

restore_setting() {
  local key="$1"
  local value="$2"
  if [[ "$value" == "null" || -z "$value" ]]; then
    adb -s "$serial" shell settings delete system "$key" >/dev/null || true
  else
    adb -s "$serial" shell settings put system "$key" "$value" >/dev/null || true
  fi
}

cleanup() {
  restore_setting peak_refresh_rate "$original_peak_rate"
  restore_setting min_refresh_rate "$original_min_rate"
}
trap cleanup EXIT

{
  echo "peak_refresh_rate=$original_peak_rate"
  echo "min_refresh_rate=$original_min_rate"
} > "$run_dir/display-settings-before.txt"
adb -s "$serial" shell settings put system peak_refresh_rate 60.0
adb -s "$serial" shell settings put system min_refresh_rate 60.0
adb -s "$serial" shell pm grant "$package_name" android.permission.CAMERA
adb -s "$serial" shell am force-stop "$package_name"
adb -s "$serial" shell am start -W -n "$activity_name" \
  --es performance_scenario "$scenario" > "$run_dir/activity-start.txt"
sleep 3
adb -s "$serial" shell dumpsys activity activities > "$run_dir/activity-before.txt"
grep -Eq "(topResumedActivity|mResumedActivity).*${package_name}" "$run_dir/activity-before.txt" || {
  echo "ArDog must be foreground before capture" >&2
  exit 3
}

scenario_marker="PERF:$scenario"
if [[ -z "$expect_text" ]]; then
  expect_text="$scenario_marker"
fi
if [[ -n "$expect_text" ]]; then
  adb -s "$serial" shell uiautomator dump /sdcard/ardog-window.xml >/dev/null
  adb -s "$serial" pull /sdcard/ardog-window.xml "$run_dir/window.xml" >/dev/null
  grep -Fq "$expect_text" "$run_dir/window.xml" || {
    echo "Expected visible scenario marker not found: $expect_text" >&2
    exit 3
  }
fi

adb -s "$serial" shell dumpsys thermalservice > "$run_dir/thermal-before.txt"
thermal_status=$(sed -nE 's/.*Thermal Status: ([0-9]+).*/\1/p' "$run_dir/thermal-before.txt" | head -1)
[[ -n "$thermal_status" ]] || { echo "Missing thermal status" >&2; exit 4; }
[[ "$thermal_status" -le 1 ]] || {
  echo "Thermal start status is $thermal_status; acceptance requires 0-1" >&2
  exit 4
}

adb -s "$serial" shell dumpsys battery > "$run_dir/battery-before.txt"
adb -s "$serial" shell dumpsys display > "$run_dir/display.txt"
active_refresh_rate=$(sed -nE \
  's/.*mActiveSfDisplayMode=.*peakRefreshRate=([0-9.]+).*/\1/p' \
  "$run_dir/display.txt" | head -1)
[[ "$active_refresh_rate" == "60.0" ]] || {
  echo "Active display refresh rate is ${active_refresh_rate:-unknown}; acceptance requires 60.0 Hz" >&2
  exit 5
}
adb -s "$serial" shell dumpsys media.camera > "$run_dir/camera-before.txt"
adb -s "$serial" shell dumpsys meminfo "$package_name" > "$run_dir/meminfo-before.txt"
python3 "$project_dir/tools/performance/write-manifest.py" \
  --project-dir "$project_dir" --run-dir "$run_dir" --serial "$serial" \
  --scenario "$scenario" --duration "$duration_seconds" --thermal "$thermal_status"
sleep 10
adb -s "$serial" shell dumpsys gfxinfo "$package_name" reset >/dev/null
adb -s "$serial" logcat -c
adb -s "$serial" shell am broadcast \
  -a com.example.ardogdemo.diagnostics.RESET_FRAMES \
  -n "$package_name/.diagnostics.RuntimeDiagnosticsReceiver" >/dev/null

duration_ms=$((duration_seconds * 1000))
sed "s/__DURATION_MS__/$duration_ms/" "$project_dir/tools/performance/perfetto-config.pbtx" \
  > "$run_dir/perfetto-config.pbtx"
device_trace="/data/misc/perfetto-traces/ardog-${run_id}.perfetto-trace"
adb -s "$serial" shell perfetto --txt -c - -o "$device_trace" \
  < "$run_dir/perfetto-config.pbtx"
adb -s "$serial" pull "$device_trace" "$run_dir/trace.perfetto-trace" >/dev/null

adb -s "$serial" shell dumpsys activity activities > "$run_dir/activity-after.txt"
adb -s "$serial" shell dumpsys gfxinfo "$package_name" > "$run_dir/gfxinfo.txt"
adb -s "$serial" shell dumpsys meminfo "$package_name" > "$run_dir/meminfo-after.txt"
adb -s "$serial" shell dumpsys SurfaceFlinger --list > "$run_dir/surfaceflinger-layers.txt"
while IFS= read -r layer; do
  if [[ "$layer" == *ardogdemo* || "$layer" == *MainActivity* ]]; then
    echo "LAYER: $layer" >> "$run_dir/surfaceflinger-latency.txt"
    escaped_layer=$(printf '%q' "$layer")
    adb -s "$serial" shell "dumpsys SurfaceFlinger --latency $escaped_layer" \
      >> "$run_dir/surfaceflinger-latency.txt"
  fi
done < "$run_dir/surfaceflinger-layers.txt"
adb -s "$serial" shell dumpsys media.camera > "$run_dir/camera-after.txt"
adb -s "$serial" shell dumpsys thermalservice > "$run_dir/thermal-after.txt"
adb -s "$serial" shell am broadcast \
  -a com.example.ardogdemo.diagnostics.DUMP \
  -n "$package_name/.diagnostics.RuntimeDiagnosticsReceiver" >/dev/null
sleep 1
adb -s "$serial" shell am broadcast \
  -a com.example.ardogdemo.diagnostics.TEARDOWN \
  -n "$package_name/.diagnostics.RuntimeDiagnosticsReceiver" >/dev/null
sleep 3
adb -s "$serial" shell dumpsys meminfo "$package_name" > "$run_dir/meminfo-after-teardown.txt"
adb -s "$serial" shell dumpsys media.camera > "$run_dir/camera-after-teardown.txt"
adb -s "$serial" shell am broadcast \
  -a com.example.ardogdemo.diagnostics.DUMP \
  -n "$package_name/.diagnostics.RuntimeDiagnosticsReceiver" >/dev/null
sleep 1
adb -s "$serial" logcat -d -v epoch > "$run_dir/logcat.txt"

python3 "$project_dir/tools/performance/summarize-run.py" "$run_dir"
echo "$run_dir"
