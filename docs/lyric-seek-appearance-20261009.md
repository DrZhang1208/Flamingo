# Remove smart word timing and prevent skipped-row flashes

The Smart Word-by-Word Lyrics setting, initialization, translated labels and
synthetic display-token generator have been removed. Rendering uses the source
lyric tokens; source-provided word timestamps remain supported. Existing persisted
values of the deleted setting are ignored.

A lyric click advances a presentation revision alongside the immediate clock seek.
Each row resets its emphasis animation on this revision, using its new past/future
state immediately. This keeps the new highlight field and opacity consistent;
normal playback still uses the existing emphasis spring. Paragraph geometry and
scroll motion are preserved.

Validation: 73 offline unit tests passed and Release built successfully. Six device
tests passed on the connected HONOR: five native renderer regressions and a new
appearance test which seeks forward/backward four times, checking brightness at
16, 80, 280 and 780 ms after each seek. The test checks white text intensity on an
opaque black background, rather than the window's always-opaque alpha channel.
Logs: /tmp/flamingo-seek-build.log and /tmp/flamingo-seek-device.log.

Release 2.3.2 (code 8) installed successfully at device time 2026-10-09 15:22:22.
The installed package is not debuggable; the temporary keep-awake setting was restored.
