import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const sharp=require('sharp');
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const svg=await fs.readFile(`${root}/assets/symbol-ink.svg`,'utf8');
const [,w,h]=svg.match(/viewBox="0 0 (\d+) (\d+)"/);
const body=svg.slice(svg.indexOf('<g '),svg.lastIndexOf('</svg>'));
function icon(padding=.14,bg='#FAF5EB') {const size=1000,iw=size*(1-2*padding),ih=iw*Number(h)/Number(w); return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000"><rect width="1000" height="1000" fill="${bg}"/><svg x="${size*padding}" y="${(size-ih)/2}" width="${iw}" height="${ih}" viewBox="0 0 ${w} ${h}">${body}</svg></svg>`;}
for(const name of ['logo','symbol','wordmark'])for(const color of ['ink','white']){
const source=await fs.readFile(`${root}/assets/${name}-${color}.svg`);
await sharp(source).resize({width:name==='symbol'?1024:2400}).png().toFile(`${root}/assets/${name}-${color}.png`);
}
for(const size of [16,32,48,180,192,512])await sharp(Buffer.from(icon())).resize(size,size).png().toFile(`${root}/web/${size===180?'apple-touch-icon':`icon-${size}`}.png`);
await sharp(Buffer.from(icon(.22))).resize(512,512).png().toFile(`${root}/web/icon-maskable-512.png`);
await fs.copyFile(`${root}/web/icon-32.png`,`${root}/web/favicon.png`);
// Multi-resolution Windows ICO with PNG-compressed images.
const sizes=[16,32,48],buffers=await Promise.all(sizes.map(n=>fs.readFile(`${root}/web/icon-${n}.png`)));
let offset=6+16*sizes.length;const head=Buffer.alloc(offset);head.writeUInt16LE(1,2);head.writeUInt16LE(sizes.length,4);
sizes.forEach((n,i)=>{const o=6+16*i;head[o]=n;head[o+1]=n;head.writeUInt16LE(1,o+4);head.writeUInt16LE(32,o+6);head.writeUInt32LE(buffers[i].length,o+8);head.writeUInt32LE(offset,o+12);offset+=buffers[i].length;});
await fs.writeFile(`${root}/web/favicon.ico`,Buffer.concat([head,...buffers]));
const adaptive=icon().replace('<rect','<style>rect{fill:#FAF5EB}g{fill:#171714}@media(prefers-color-scheme:dark){rect{fill:#171714}g{fill:#FAF5EB}}</style><rect');
await fs.writeFile(`${root}/web/favicon.svg`,adaptive);
await fs.writeFile(`${root}/web/site.webmanifest`,JSON.stringify({name:'AIssociation',short_name:'AIssociation',description:'Connect. Learn. Build together.',id:'/',start_url:'/',scope:'/',display:'standalone',background_color:'#FAF5EB',theme_color:'#FAF5EB',icons:[{src:'/brand/icon-192.png',sizes:'192x192',type:'image/png',purpose:'any'},{src:'/brand/icon-512.png',sizes:'512x512',type:'image/png',purpose:'any'},{src:'/brand/icon-maskable-512.png',sizes:'512x512',type:'image/png',purpose:'maskable'}]},null,2));
const logo=await fs.readFile(`${root}/assets/logo-ink.svg`,'utf8');
const social=`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630" viewBox="0 0 1200 630"><rect width="1200" height="630" fill="#FAF5EB"/><path d="M0 606h1200v24H0z" fill="#A94530"/><svg x="90" y="235" width="1020" height="114" viewBox="0 0 1853 206">${logo.slice(logo.indexOf('<g '),logo.lastIndexOf('</svg>'))}</svg><text x="90" y="425" font-family="Arial,sans-serif" font-size="28" fill="#5E5E56">Connect. Learn. Build together.</text></svg>`;
await sharp(Buffer.from(social)).png().toFile(`${root}/web/social-card.png`);
console.log('Exported transparent PNGs, favicon suite, manifest and social card.');
