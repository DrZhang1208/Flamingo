# AMLL-inspired native artwork background

Reference: AMLL mesh renderer and background documentation, inspected 2026-10-09:
- https://github.com/amll-dev/applemusic-like-lyrics/tree/main/packages/core/src/bg-render/mesh-renderer
- https://amll.dev/en/guides/component/background.html

AMLL softens album images and deforms a textured gradient mesh. This change is an
original Android AGSL implementation of the visual approach, not a source port,
not Apple's implementation, and not an exact reproduction of AMLL's Hermite mesh.
It uses continuous domain deformation of a softened artwork texture instead.
No web runtime or additional dependency is introduced.

## Rendering

- Prepare an opaque 96 x 96, saturated, twice-blurred cover texture off the UI thread.
- Keep a single RuntimeShader program alive across track/page changes. Independent
  low-frequency waves deform the sampling domain without ping-pong reversals.
- Mix old/new textures over 900ms in the shader. On interruption, snapshot the
  current colour mixture into another small texture; deformation phase continues.
- Keep the current texture while new artwork loads; missing artwork fades to a
  neutral opaque texture. Cap the cover-asset cache at six entries.
- Advance the background at about 30Hz using the display clock. Phase is read in
  drawing only; background frames do not recompose the player controls or lyrics.
- Pause motion when playback pauses, player is minimised, or lifecycle is not
  resumed. Resume without jumping elapsed time. Cover transitions still work
  while music is paused.
- Android 13+ uses AGSL when the existing dynamic background setting is enabled.
  Older systems and the disabled setting retain the pre-blurred static path.
- UI descriptions updated in English, Simplified/Traditional Chinese and Japanese.

## Validation

Final validation on 2026-10-09:
- 75 local unit tests passed, with no failures/errors/skips. Debug, test APK and
  Release built successfully offline; git diff --check passed.
- Eight device tests passed on HONOR BVL-AN00. Three new cases exercise texture
  opacity and interrupted colour mixing; actual GPU motion and pause/minimise
  freezing; rapid cover transitions and convergence to the newest cover. Five
  existing artwork/page-transition/progress cases also passed.
- A real cover was visually inspected in the full player. The existing dynamic
  background switch was already enabled on the device and was preserved.
- Release 2.3.2 (8) installed at 17:22:33. Package has no DEBUGGABLE flag, and
  cold launch returned Status: ok. USB keep-awake restored to zero.
- Logs: /tmp/flamingo-flow-shader-reuse-build.log and
  /tmp/flamingo-flow-shader-reuse-device.log.

This does not establish a 120Hz frame-time or battery-life benchmark; the slow
background has a separate update cadence from the player controls and lyrics.
Android below API 33 was checked by code/build only, not on an older device.
