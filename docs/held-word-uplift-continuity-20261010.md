# Held final-word uplift continuity

NativeWordMotion previously completed the main 72% rise within 650ms, then waited until 180ms before the word end for the remaining 28%. A word longer than that interval had a stationary plateau followed by resumed movement, independent of rendering frame rate.

Start the continuation no later than 160ms before the main rise finishes, extending it through the existing end + 240ms endpoint. Both phases retain smoothstep easing, monotonic bounded displacement, overlapping adjacent words and the existing uplift amplitude. TTML return, row scroll spring, highlight and layout are unchanged.

Regression covers 900/1200/2000/5000ms words, movement through the former plateau, frame increments and final position. Device bitmap tests validate renderer regressions; they do not measure subjective playback smoothness.

Validation completed: 108 local tests, 9 connected-device renderer tests and Release build passed; Release installed via ADB.
