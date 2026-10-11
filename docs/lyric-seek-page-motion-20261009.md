# Artwork page bounds and lyric navigation motion

## Changes

- Artwork shared bounds now measure the current playback inset directly. Removed
  the page-keyed layout-progress snapshot and its compensating scale transform
  from the album endpoint, so switching pages cannot rebase the outgoing cover
  before the shared transition samples it. Pause/resume keeps one spring above
  page content. Remeasurement occurs during that short spring, not playback ticks.
- Countdown dots still breathe together; cycle duration increased from 1800ms to
  2800ms. Boundary scale/fade and dot dimensions are unchanged.
- A newly loaded lyric document enters with a 280ms fade and 8dp upward settling.
  This is a layer transform; normal word/highlight frames do not restart it.
- The scroll driver now retains a moving virtual viewport. Virtualization samples
  the origin/intermediate viewport as well as destination rows instead of dropping
  the old viewport immediately after a seek.
- Newly visible rows inherit the active transition's spring origin, velocity and
  elapsed time. Their first layer read seeds motion even before registration
  effects commit, preventing a frame at the final position. Settled rows do not
  replay old seeks. Rapid retargeting preserves the sampled position and velocity.
- Seeking before the first lyric follows the first document row rather than
  leaving the previous playback row in place.

## Validation

81 offline unit tests passed on the final source. Six added
cases cover forward/backward seek entry, first layer reads before effect commits,
rapid retargeting, no replay after settling, and virtualization along a long seek.
Instrumentation cases updated for shared artwork bounds and the slower breathing
cycle; compiled only, because the user disconnected the phone.
Release 2.3.2 (8) and the instrumentation APK built successfully offline. Final
build log: /tmp/flamingo-lyric-navigation-release-build.log. git diff --check passed.
The Release output directory was opened with the current APK selected. No phone
installation or device execution was attempted; appearance/performance is left
for the user's installation and testing.

## Line timestamp emphasis follow-up

Line mode (including songs without word timing) now selects solid paragraph colour
from playback time directly. It lights the entire wrapped paragraph at the exact
line start and dims it at the next valid start, independently of scroll/focus and
opacity springs. Word-timed mode retains its existing continuous sweep. Static
introduction text and countdown rendering are unchanged. Cached row-time bounds
still reduce repainting during long gaps but now preserve the next-line boundary.

Final validation: 85 offline unit tests passed; instrumentation APK and Release
built successfully. Updated bitmap regression checks whole wrapped-line colour at
the exact start and next-line boundary; it was compiled, not device-executed.
Build log: /tmp/flamingo-line-highlight-final-build.log. Release folder opened
with the current package selected; no phone install attempted.
