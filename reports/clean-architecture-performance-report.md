# Clean Architecture And Performance Report

Date: 2026-09-15  
Device: Samsung SM-A165F (`RF8Y60B9NCZ`), Android 16, 60 Hz

## Implementation

- Extracted Android-free character and mission domain packages.
- Kept MVI state/intent/reducer/ViewModel under presentation.
- CameraX remembers one Preview, uses PERFORMANCE mode, and unbinds only its
  owned use case.
- SceneView uses the performance preset. Mission preview allocates no enemy;
  three roaches share one Filament model through instanced loading.
- Replaced seven eager MediaPlayers with lifecycle-released SoundPool wrappers.
- Added deterministic GLB audit/optimization/LOD tooling. Packaged GLBs changed
  from about 31 MB to 14 MB; Bobrito uses the validated LOD1.

## Build And Tests

- `./gradlew clean testDebugUnitTest lintDebug assembleDebug`: PASS.
- `./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest`: PASS,
  one connected test on SM-A165F.
- Final post-review `testDebugUnitTest lintDebug assembleDebug`: PASS.
- Domain Android/Compose import scan: no findings.

## Device Behavior

- Base optimized Gugu model: renders with correct established framing.
- Pipe swap: accessory remains attached; transform is retained.
- Held joystick after pipe swap: model moves, turns, and displays Walk pose.
- Bobrito LOD1: renders, animates, pursues, and damages player.
- Roach mission: all three exact roach instances render and pursue.
- Final gltfio log: no unsupported skin-weight warning and no crash.

## Performance

Comparable 60-second idle captures are in `build/performance-baseline/` and
`build/performance-final/`.

| Metric | Before | After |
|---|---:|---:|
| frame p50 | 27 ms | 18 ms |
| frame p90 | 32 ms | 22 ms |
| frame p95 | 34 ms | 23 ms |
| frame p99 | 40 ms | 27 ms |
| modern jank | 0.07% | 0.00% |
| legacy jank | 85.33% | 62.16% |
| 60 s total PSS | 512,166 KB | 401,532 KB |
| 60 s graphics | 287,557 KB | 202,592 KB |

The p95 frame time is below 33.3 ms, satisfying the 30 FPS target in this idle
camera/scene sample.

Ten Home/foreground cycles recorded 10 camera opens and 10 closes. PSS briefly
rose while surfaces were recreated, then settled to 408,751 KB. Five full
Activity destroy/recreate cycles also recorded five camera opens/closes; PSS
settled to 416,689 KB after deferred graphics cleanup, with no monotonic growth
shown by this bounded test.

## Thermal Limitation

The device began the final sample already at skin thermal status 2, so this is
not an absolute cool-start comparison with the Unity sample. Over the measured
60 seconds, the higher reported AP/BAT/PA/SKIN sensors changed approximately
`+0.2/+0.2/+0.1/+0.3 C` and status stayed 2. A matched cool-start long soak is
still required before claiming thermal parity.

## Rejected Candidate

Quantizing all vertex attributes reduced size further but produced Filament's
`Cannot normalize weights, unsupported attribute type` warning on device. The
candidate was discarded; the final pipeline leaves skin weights as float.
