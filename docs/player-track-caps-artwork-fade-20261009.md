# Zero-position caps, volume track and artwork fades

Playback and volume use the same rounded track renderer. A zero fraction draws a
full-height circular cap. The fill grows between the end-cap centres, preserving
rounded ends at small values and full width at maximum. Dragging changes thickness
from 7 to 10 dp without squeezing the fill.

The player retains one shared resolved artwork surface across album/lyrics/queue.
A new image fades in over the current image after loading, over 300 ms. Loading
retains the previous visible cover. Interrupted fades freeze the visible blend and
continue toward the latest loaded cover; completed fades discard the old layers.
The painter crops each source independently and handles unspecified intrinsic sizes.
Each transition owns a separate animation clock so an interrupted painter cannot
redraw at zero before the replacement surface is composed.

Validation on 2026-10-09:
- 75 local unit tests passed; Debug, instrumentation APK and Release built offline.
- 5 targeted tests passed on HONOR BVL-AN00: zero/early/full progress at both
  thicknesses, opaque colour interpolation, interruption continuity, asynchronous
  image loading during track changes, and rapid page switching without reloading
  or losing the cover.
- The asynchronous test caught a shared-clock reset flash; separate per-transition
  clocks fixed it, and the full targeted suite passed afterward.
- Release 2.3.2 (8) installed successfully at 16:53:27; package has no DEBUGGABLE
  flag and a cold activity launch returned Status: ok. Temporary USB keep-awake
  was restored to its original value of zero.
- Logs: /tmp/flamingo-artwork-fade-clock-build.log and
  /tmp/flamingo-artwork-fade-clock-device.log.
These are targeted rendering and interaction checks, not a full performance audit.
