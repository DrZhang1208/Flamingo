# Exterior lyric accent and quicker return

User screenshot showed outline contours inside dim/unplayed characters. The previous uniform-white interior test was insufficient. The accent now uses a transparent unplayed endpoint, is rendered to a bounded layer, and has the opaque filled glyph silhouette removed with DstOut. Ordinary gradient fill is painted last at its original opacity. This prevents the accent from drawing inside the filled letter shape or brightening unplayed text. Broad text shadow was removed from this pass.

The independent lift pulse now ends 120ms after the word instead of 420ms; smooth endpoints and horizontal geometry remain unchanged.

107 local tests passed. Nine bitmap renderer tests passed on the connected HONOR, including the new dim-tail regression and existing fill, endpoint cleanup, wrapped-text and Latin-edge tests. Subjective playback motion is not established by bitmap tests.
