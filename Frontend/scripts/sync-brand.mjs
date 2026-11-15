import { copyFile, mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const kit = new URL('../../branding/', import.meta.url);
const publicBrand = new URL('../public/brand/', import.meta.url);
await mkdir(publicBrand, { recursive: true });
for (const file of await readdir(new URL('web/', kit))) {
  if (/\.(png|svg|ico|webmanifest)$/.test(file)) {
    await copyFile(new URL(`web/${file}`, kit), new URL(file, publicBrand));
  }
}
for (const file of await readdir(new URL('assets/', kit))) {
  if (file.endsWith('.svg')) await copyFile(new URL(`assets/${file}`, kit), new URL(file, publicBrand));
}
await copyFile(new URL('web/brand.css', kit), new URL('../src/theme/brand.css', import.meta.url));
const manifestPath = new URL('site.webmanifest', publicBrand);
const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
manifest.description = 'Events and workspaces for student associations.';
await writeFile(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
console.log(`Brand assets synchronized to ${fileURLToPath(publicBrand)}`);
