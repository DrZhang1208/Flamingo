# Album position, shadow and hidden-volume layout

The portrait album slot transfers 12dp from bottom padding to top padding (20/33 -> 32/21). Total padding and cover constraints stay equal: the cover moves down 12dp without changing its size. Pause scaling remains centred on the shared visible artwork.

Hidden-volume spacing has been restored to the original layout at the user’s request: portrait transport top/bottom gaps are 25/35dp, footer gap 16dp and panel bottom inset 15dp. No additional panel offset applies when volume is hidden. Compact-window spacing is unchanged.

The lyric viewport fades over the last 48dp, ending 8dp above the controls. This mask sits outside lyric translation/scale in fixed player coordinates, so the album-to-lyrics transition cannot expose a hard clipping edge. The instrumented clipping test now checks intermediate fade opacity as well as absence of lyrics below the controls.

Shadow belongs to the single visible artwork canvas, outside its image clip. Replace nested rectangular shells with a cached 416px software Gaussian mask: a 256px cover, 80px transparent padding, ambient blur 18px with 14px downward offset and alpha 50/255, contact blur 7px with 8px offset and alpha 24/255. The existing half-opacity multiplier remains. Scale the texture proportionally with the artwork, including compact covers; frame drawing performs no repeated blur work.

Reference: user screenshots. Local tests/builds and compiled instrumentation do not establish phone visual quality; device verification remains separate.
