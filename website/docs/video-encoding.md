# Video Encoding for the Website

Hero and demo videos on the landing page use `<video autoplay muted loop playsinline>` instead of GIFs. This keeps file sizes under 1-2 MB for a 22-second clip versus 20-50 MB as a GIF.

## Recording

Record with Kdenlive (or any screen recorder). Project settings don't matter much since we re-encode everything.

### Kdenlive export

1. **Project > Render** (or the Render button)
2. Choose **MP4 - H.264** preset
3. Export at your native recording resolution (we downscale in the next step)
4. Audio can be left in -- it gets stripped during encoding

## Encoding for web

We produce two formats from each source video:

| Format | Codec | Why |
|---|---|---|
| **MP4** | H.264 | Universal browser support, fallback |
| **WebM** | VP9 | Better compression on Chrome/Firefox |

### Commands

From the repo root, with the source video at `~/Videos/yourfile.mp4`:

```bash
# MP4 (H.264) -- universal fallback
ffmpeg -y -i ~/Videos/yourfile.mp4 \
  -vcodec libx264 -crf 28 -preset slow \
  -vf "scale=1280:-2,fps=30" \
  -an -movflags +faststart \
  website/src/assets/images/hero-demo.mp4

# WebM (VP9) -- smaller on Chrome/Firefox
ffmpeg -y -i ~/Videos/yourfile.mp4 \
  -c:v libvpx-vp9 -crf 35 -b:v 0 \
  -vf "scale=1280:-2,fps=30" \
  -an -row-mt 1 \
  website/src/assets/images/hero-demo.webm
```

### What the flags do

| Flag | Purpose |
|---|---|
| `-crf 28` (MP4) / `-crf 35` (WebM) | Quality level. Higher = smaller file, lower quality. 28/35 is the sweet spot for screen recordings. |
| `-preset slow` | Better compression at the cost of encode time. |
| `-vf "scale=1280:-2,fps=30"` | Downscale to 1280px wide (auto height), 30 fps. The hero container is max ~768px, so 1280 covers retina. |
| `-an` | Strip audio -- not needed for autoplay muted videos. |
| `-movflags +faststart` | MP4 only. Moves metadata to the front so playback starts before the full download. |
| `-b:v 0` | WebM only. Tells VP9 to use pure quality-based (CRF) mode. |
| `-row-mt 1` | WebM only. Multi-threaded row encoding for faster VP9 encodes. |

### Target sizes

For a ~22 second screen recording, expect:
- MP4: ~900 KB - 1.5 MB
- WebM: ~1 - 1.5 MB

If either exceeds 3 MB, increase the CRF value (e.g., 30 for MP4, 38 for WebM).

## HTML usage

```html
<video autoplay muted loop playsinline class="w-full rounded-xl border border-slate-200 dark:border-slate-700">
  <source src="assets/images/hero-demo.webm" type="video/webm" />
  <source src="assets/images/hero-demo.mp4" type="video/mp4" />
</video>
```

The browser picks the first format it supports. WebM is listed first (preferred), MP4 is the universal fallback.

Key attributes:
- `autoplay` + `muted` -- browsers require muted for autoplay
- `loop` -- continuous replay like a GIF
- `playsinline` -- prevents fullscreen takeover on iOS
- No `controls` -- keeps it clean

Optional: add `poster="assets/images/hero-poster.jpg"` for a static frame while loading.
