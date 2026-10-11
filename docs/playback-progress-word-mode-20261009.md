# Smooth playback progress and source-only word mode

The player progress previously copied playback position only when the integer
second changed, inside a 700 ms polling loop. It now samples position/duration
at 80 ms intervals and uses the native presentation clock between samples at
screen-frame cadence. The progress Canvas reads position during drawing, so
frame updates do not recompose or remeasure the player controls. Time labels
still update only when seconds/duration change. The existing periodic controls
callback remains at 700 ms.

The frame coroutine stops outside the resumed lifecycle or when the expanded
player is not visible. The clock follows actual playback state and speed,
resets on track changes, protects pending drag seeks from stale reports, and
restores actual playback position when dragging is cancelled. Progress drawing
uses rounded rectangles for the track and fill.

The lyric settings now expose Word-by-word lyrics, backed by the existing
`EnableWordByWordLyric` preference (default true). When enabled, source tokens
with word timing are retained; untimed lyrics remain line-timed. When disabled,
each whole line uses its source line start and has no word duration. No synthetic
word timing is generated. Mode changes also reset row opacity presentation.

Validation: 75 offline unit tests passed, including mode selection/fallback and
per-frame position advancement with sparse player samples. Device rendering and
Release installation results follow below. Simulated clock checks do not prove
that a full app scene achieves 120 FPS on the phone.

All seven device tests passed, including pixel equality for whole-line highlighting
across wrapped visual lines and brightness stability across forward/backward lyric
seeks. Build and test logs: /tmp/flamingo-progress-verified-build.log and
/tmp/flamingo-progress-device.log.

Release 2.3.2 (code 8) installed successfully at device time 2026-10-09 15:35:17.
Package flags confirm a non-debuggable build; temporary USB keep-awake was restored.

## Progress cap follow-up

A nonzero progress fill now maps its width between the circular end caps, starting
as a full-height dot and growing continuously. This avoids both the narrow vertical
sliver at song start and a minimum-width plateau. Zero progress remains empty and
full progress fills the entire track. An Android bitmap regression covers 7/10 px
track heights and zero, early and full progress.

The updated build and 75 offline tests passed. After allowing USB installation,
the new Android bitmap regression passed on the connected HONOR, covering seven
progress positions at both 7 and 10 px heights. It verifies a round starting fill,
continued early growth, no fill at zero and full-width fill at completion.
Device log: /tmp/flamingo-progress-cap-device.log.

The progress-cap Release installed successfully at device time 2026-10-09 16:06:35.
The installed package is non-debuggable; temporary USB keep-awake was restored.
