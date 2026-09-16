# ArDog Demo

Native Android/Kotlin research demo for reconstructing the camera-character
experience of the supplied Trending AR Soundboard APK.

## Current Target

- Exact girl wearing the penguin costume shown in the supplied screenshot.
- Four-direction joystick movement on the camera plane.
- Vertical slider for model scale.
- Drag and rotate gestures.
- Complete accessory list and selector UI from the sample.
- Punch, dance/jump, and howl animations; howl includes synchronized sound.
- Two missions: `Kill 3 Roaches` and `Defeat Bobrito Bandito`, using exact
  APK-derived enemy assets and combat animations/audio.

Mission combat keeps joystick movement active, requires range and facing for
player hits, gives each enemy independent HP, and provides victory/defeat with
replay and exit while preserving character transform and accessories.

## Workflow

Work proceeds through Reverse -> Documentation -> Planning -> Implementation ->
Testing. The exact target character, accessories, actions, enemies, and mission
rules are mapped and implemented. Current work focuses on architecture,
lifecycle, and camera/scene/model performance without changing those contracts.

Start with docs/README.md and
plans/260915-ardog-clean-architecture-performance/plan.md.

## Research Assets

The APK and extracted resources are used locally for non-commercial research.
Do not publish or redistribute competitor resources, and do not bypass licensing
or integrity controls.
