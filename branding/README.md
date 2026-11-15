# AIssociation brand kit - v1.0

Created 16 November 2026 from the approved interlocking-band logo.

Open `brand-guide.html` for the visual guide or `brand-guide.pdf` for the printable edition.

## Package contents

- `assets/`: transparent SVG outlines and PNG exports of the full logo, standalone symbol and wordmark, in ink and white. SVGs contain real paths, not embedded raster images. The custom lettering is not a font. Vector outlines were traced from the approved image; they are a production reconstruction, not an original font or manually drawn master.
- `web/`: adaptive SVG favicon, multi-resolution ICO (16/32/48), PNG icons, Apple touch icon (180), app icons (192/512), maskable app icon (512), web manifest, social preview (1200x630), CSS tokens and HTML metadata.
- `source/approved-logo.png`: original approved image, preserved for reference.

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

## Asset usage

The supplied SVGs, raster exports and guide are ready for normal development.
No export or PDF-generation tooling is required by the product build.
Use Arial/Helvetica/sans-serif for interface text and Georgia for editorial
headings; no third-party font downloads are required.

Preserve the supplied vector artwork and aspect ratio. Keep the approved source
for design review. The frontend asset-sync script copies existing files; it does
not regenerate the logo or guide.

## Product design direction

Keep layouts restrained and consistent: generous spacing, clear type hierarchy,
subtle separators and one primary action per context. Public entry pages can use
open layouts; forms and administration use purposeful surfaces. Avoid decorative
panels, heavy shadows, unnecessary badges and invented navigation. Retain the
approved logo, palette and typography. Preserve visible keyboard focus, readable
contrast, minimum 44px targets, responsive spacing and reduced-motion support.
