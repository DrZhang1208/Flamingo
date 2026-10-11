# Native lyric edges and artwork transitions — 2026-10-09

The pre-fix implementation was committed and pushed to `origin/master` as
`e3b0a2cd10f2275f2ede885f6e567ce55ab1809b`, as requested before changes.

## Changes

- Latin words retain one uplift envelope even when their timing arrives as
  multiple source tokens. Continuous highlighting still uses the original timing.
  Chinese glyphs retain their overlapping stagger.
- Whitespace is partitioned at its centre, moving each half with the adjacent
  word. Paragraph shaping is retained; clips no longer cut Latin word-edge ink.
- Interlude dots share one breathing phase and radius. Per-dot progress brightness
  and the existing entrance/exit scale and opacity remain.
- Album, lyric header and queue header use one image request and its resolved
  painter. AsyncImagePainter itself is not drawn at multiple endpoint sizes.
- Lyrics and queue share one compact header composition. Artwork transitions
  track only the album/compact Boolean, preventing a third shared-cover participant
  during rapid interrupted switching.

## Validation

73 offline unit tests passed. On the connected HONOR BVL-AN00, eight lyric/countdown/
artwork-motion device tests passed; the final cover-continuity test separately passed
all 56 sampled frames across seven rapid switches and confirmed one image request.
The English test compares
intermediate italic word motion pixel-for-pixel with the original shaped paragraph.
The artwork test uses a deliberately delayed image request and checks cover opacity
on successive animation frames, including rapid reversals, and checks request count.
Window screenshots wait for the test Activity entrance to finish, then sample
without Compose's idle wait, which can stall under a manually paused animation clock.
Earlier attempts were interrupted by the phone installer UI or blocked by that wait;
they are not counted as successful runs. The final cover run is recorded in
`/tmp/flamingo-window-cover-retest.log`.
These isolated rendering tests do not establish full-app 120 Hz performance.

Release 2.3.2 (code 8) was installed successfully at device time 2026-10-09 15:01:31.
Package flags confirm the installed app is not debuggable. The temporary USB
keep-awake setting was restored to its original value (0).
