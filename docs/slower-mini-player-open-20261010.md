# Slower mini-player expansion

Mini-player click expansion now uses a critically damped spring (stiffness 180, damping ratio 1, threshold 0.001) rather than stiffness 400. It retains the same nonlinear spring family used by the cover/lyrics shared artwork transition (stiffness 300), with a slower response for full-screen travel. Upward drag release uses the same expansion spec and preserves release velocity. Direct dragging and closing behaviour are unchanged.

The sheet, shared artwork, background and controls continue consuming the same sheet progress; no extra animation clock or second easing is applied to the cover. This is a timing-only change. Release compilation validates integration; visual timing remains a playback-device check.

Gesture correction: the previous change slowed upward release while downward release retained the default fast spring. Both release directions now explicitly use stiffness 300 / damping ratio 1 / threshold 0.001 and preserve release velocity. Clicking still uses stiffness 180; direct dragging remains snap-to-finger.
