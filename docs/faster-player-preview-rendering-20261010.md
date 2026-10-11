# Lyric settings preview and player transition cost

The lyric settings preview uses an opaque white card, black main text and grey translation in both themes. It contains one stanza, with natural content height instead of a nested scroll viewport or top/bottom gradient overlays. Font size and weight controls still update the preview.

Speed up mini-player click opening: critically damped spring stiffness 180 -> 320. Page header motion: 220 -> 360; shared artwork page transition: 300 -> 420. Lyric/queue fade and scale: 280 -> 210ms; controls expand/collapse: 260 -> 200ms. Preserve gesture velocity, interruption, pause centring, and the lyric sentence-scroll spring.

Consolidate the two large lyric saveLayer/mask passes into one fixed-coordinate cached mask. Measure the fixed spacer height above the lyric viewport (not globally transformed bounds), fade its first 32dp, and fade the last 48dp ending 8dp above the controls. When controls are hidden the bottom is unmasked. Gradient and paint are cached until geometry changes. Remove the inner viewport mask, its per-frame controls-progress rebuilds and its redundant panel-height measurement state.

Cache conservative settled-time boundaries with the shaped lyric paragraph. Future and finished paragraphs use one filled draw call with the same colour and settled vertical offset, instead of per-word/visual-row clipping and repeated paragraph drawing. Active words retain their continuous sweep, overlap and TTML outline rendering.

Regression coverage includes both gradient edges under translated content and pixel equality of settled wrapped paragraphs. Device tests require hardware/emulator; compiling Android test APKs does not execute these pixel checks. Local builds cannot prove phone frame rate or resource savings; hardware profiling remains pending when no device is connected.

Follow-up: initial paused opening could retain global lyric bounds from the cover-page translation. The mask now uses the spacer height measured in layout coordinates, independent of artwork/header transforms and playback ticks. A paused-first-entry instrumentation regression advances only the page animation and checks visible lyrics plus both fade boundaries.

Interaction isolation: fading or transparent lyrics remain composed for uninterrupted page transitions, but only the foreground full lyrics page accepts input. Pass an explicit interaction flag to empty-state/background click targets and every normal/background lyric row; disable scrolling too. Guard seek/long-press callbacks using the latest flag to reject in-flight events after a page switch. A touch regression checks active, outgoing and fully transparent rows, including re-enabling input on return.
