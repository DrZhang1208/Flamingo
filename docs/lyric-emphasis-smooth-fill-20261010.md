# Emphasis fill and motion

Reference reviewed: AMLL `packages/core/src/lyric-player/dom/animation/emphasize/index.ts`, commit `86200dead453bb067e554e989110cbadca8d4756`. Its highlight and longer additive float run independently. This change independently implements that separation for the native Compose renderer; it does not copy its DOM implementation or claim Apple source parity.

The native renderer paints filled text first, then adds an outline using the same colour field at full paint alpha. Outline width supplies the accent envelope; a translucent stroke paint is no longer shared with the fill. Normal word highlight colour remains governed by the continuous sweep.

Glow/scale follow a full-note sine-squared pulse, replacing the fixed 260/380ms rise/return and static plateau. Additional lift uses a separate smooth pulse from 180ms before the word to 420ms after it ends. The 3dp extra lift, 2.5% vertical emphasis and fixed horizontal geometry remain bounded. Playback time directly determines every phase, including seek and pause.

Validation: 107 local tests passed; Debug, test APK and Release built. Device bitmap regression covers filled interiors at early/middle/late phases, fixed horizontal bounds, restored cached text paint, wrapped lines and Latin word edges. Initial full device run had a transient ordinary-highlight monotonic-coverage failure; isolated rerun and two subsequent full runs (8 tests each) passed. Actual subjective motion quality requires playback observation.
