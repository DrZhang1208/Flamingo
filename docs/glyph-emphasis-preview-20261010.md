# Per-grapheme emphasis recovery and clear browsing

Source tokens may contain several characters. Their old emphasis shared source start/end times, so even independently lifted characters recovered as one block. Store a grapheme delay at paragraph measurement time: 65ms per character with a maximum 260ms wave across a token. Evaluate the independent emphasis lift/scale with that phase and keep phase differences out of draw grouping. Highlight sweep timing is unchanged. Whitespace follows the neighbouring phase; grapheme segmentation preserves combining marks.

Manual scrolling and preview idle disable blur immediately for foreground and background vocals. The normal blur target remains zero during browsing, then resumes its usual animated value only after preview exits. This avoids a delayed blur fade while reading.

Validation: 111 local tests passed; 11 connected-device renderer/preview-idle tests passed. Release built and installed via ADB. Blur suppression was checked in the UI wiring; the idle device test verifies preview state, not a visual blur screenshot.
