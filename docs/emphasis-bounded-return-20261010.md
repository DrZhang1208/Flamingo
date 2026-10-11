# Bounded emphasis return

Reducing the post-note extension alone did not address the slow descent: sine-squared lift still stretched its return over half of every long note. Lift now peaks at half the note duration capped at 800ms, then returns over a fixed 320ms smoothstep. Vertical emphasis scale uses this same motion envelope, so size and position restore together. Highlight colour and outline retain their independent note-time envelope; ordinary word uplift and row scroll are unchanged.

Regression verifies peak, halfway return and restored position for 900/2000/5000ms words, plus bounded frame-to-frame velocity changes.

User correction: complete the entire note-timed rise before returning. Peak now occurs at word end, followed immediately by the smooth 320ms return; remove the half-duration / 800ms early peak. Size and extra lift remain synchronized. Regression explicitly checks monotonically rising through word end and descending only after it.
