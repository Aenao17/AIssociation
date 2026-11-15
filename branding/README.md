# AIssociation brand kit - v1.0

Created 16 November 2026 from the approved interlocking-band logo.

Open `brand-guide.html` for the visual guide or `brand-guide.pdf` for the printable edition.

## Package contents

- `assets/`: transparent SVG outlines and PNG exports of the full logo, standalone symbol and wordmark, in ink and white. SVGs contain real paths, not embedded raster images. The custom lettering is not a font.
- `web/`: adaptive SVG favicon, multi-resolution ICO (16/32/48), PNG icons, Apple touch icon (180), app icons (192/512), maskable app icon (512), web manifest, social preview (1200x630), CSS tokens and HTML metadata.
- `source/approved-logo.png`: original approved image, preserved for reference.
- `tools/`: reproducible export scripts. Vector outlines were traced from the approved image; they are a production reconstruction, not an original font or manually drawn master.

The guide defines a proposed website system around the approved logo. The terracotta accent, system typography and “Connect. Learn. Build together.” line are proposed brand extensions; the line is optional, not a required tagline.

## Website handoff (Ionic / React / Vite)

1. Copy `web/` into `Frontend/public/brand/`, except this HTML snippet if not needed. Copy the SVG assets into `Frontend/public/brand/` as well.
2. In `Frontend/index.html`, replace the existing `/favicon.png` shortcut and `/manifest.json` links with `web/head-snippet.html`. Do not leave duplicate manifest tags. Replace `https://YOUR-DOMAIN` with the deployed public origin in social metadata. The provided manifest assumes the app is served from the domain root; update `id`, `start_url`, `scope` and icon paths for a subdirectory deployment. Merge existing manifest behavior if your app already depends on it.
3. Import `brand.css` after Ionic base CSS and before application overrides. Check Ionic mode-specific styles. The semantic dark-theme tokens are opt-in and do not automatically theme every Ionic component.
4. Add the full logo with `<img src="/brand/logo-ink.svg" alt="AIssociation" width="280" height="31" />`. If the logo is inside a home link, use an accessible name such as `AIssociation home` on the link.
5. Prefer the full lockup at 240-320 CSS px in desktop headers. At narrow widths use the standalone symbol at 36-44 px with an accessible label. Do not squeeze the full lockup below 220 px.
6. Check the favicon at 16/32 px, a pinned home-screen icon, keyboard focus, mobile header, dark surfaces and social preview after deployment. A manifest supplies install metadata; it does not add offline support.

## Product integration

The application imports the copied tokens from `Frontend/src/theme/brand.css`,
uses `BrandHeader` for responsive SVG logo rendering, and serves icon/manifest/
social assets from `Frontend/public/brand/`. The light palette is explicit;
automatic Ionic dark mode is disabled until all dark surfaces are designed.

After updating the source kit, run from `Frontend/`:

```sh
npm run brand:sync
```

Commit the updated kit and synchronized frontend files together. Deployments use
the checked-in copies and do not need access to files above the Frontend root.
Do not edit copied assets independently or redraw the logo.

The original zip is kept local; the expanded source kit is tracked in Git.

## Build / verification

Vector conversion: Python 3 + Pillow + Potrace. Raster exports: Node.js + Sharp. The Node utilities resolve Sharp and Playwright through normal local package resolution; no developer-specific runtime path is required. The PDF renderer uses Playwright Chromium by default, or an explicitly configured CHROMIUM_EXECUTABLE_PATH. Guide PDF: Chromium print from the HTML. Font stack uses Arial/Helvetica/sans-serif and Georgia for editorial accents; no third-party font downloads are required.

Do not regenerate the identity with image generation for routine exports. Use the supplied vector masters and preserve their aspect ratio. Keep the approved source for design review.

## Optional regeneration tools

Routine application builds do not regenerate brand assets. To run the optional
Node export tools, install `sharp` and `playwright` in an environment resolvable
from this folder (for example, from `branding/`, run
`npm install --no-save --package-lock=false sharp playwright`). Install Playwright
Chromium before using `tools/render-guide.mjs`, or configure
`CHROMIUM_EXECUTABLE_PATH`. Python tools additionally need Pillow and Potrace.
The supplied SVGs/exports are ready for normal development without these tools.
