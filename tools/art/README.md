# Chess Tutor art

Makes the pictures in `app/src/main/res/drawable-nodpi/art_*.webp` (decision D56). Every scene is
rendered twice: on pure black for dark themes and on pure white for light themes. The app blends
them into the theme's own colours (`design/CtArt.kt`).

Needs Node and Microsoft Edge on Windows. Everything else runs inside the browser.

1. Download the Poly Haven `chess_set` 4k glTF into `gltf4k/` and the HDRI `studio_small_09` 2k into
   `hdr/`. Both are CC0. Download `rt_ldr.tza` from the npm package `denoiser` into `oidn/`.
2. Render, one shot at a time: `GPU=1 node render.js "pt.html?only=king_night" 1300,1300`
3. Denoise: `GPU=1 node render.js "denoise.html?names=king_night" 700,900`
4. Export WebP: `node render.js "export.html?names=king_night" 700,900`

Scenes are data in `ptshots.js`. Pictures land in `out/`. Check each one by eye: a long render
sometimes loses its lights or goes black. Rendering again usually fixes it, with fewer samples
(`&spp=70`) if not.
