from pathlib import Path
from PIL import Image
import subprocess, re
root=Path(__file__).resolve().parents[1]
im=Image.open(root/'source/approved-logo.png').convert('L')
mask=im.point(lambda v:0 if v<128 else 255,mode='1')
# Production format conversion from the approved raster; no generative redesign.
boxes={'logo':(175,245,2055,480),'symbol':(175,245,565,480),'wordmark':(665,245,2055,480)}
for name,box in boxes.items():
    cut=mask.crop(box); cut=cut.crop(Image.eval(cut.convert('L'),lambda x:255-x).getbbox())
    w,h=cut.size
    pbm=root/'source'/f'{name}.pbm';cut.save(pbm)
    raw=subprocess.check_output(['potrace',str(pbm),'-s','-o','-','--flat','--turdsize','2','--alphamax','1','--opttolerance','0.2']).decode()
    body=raw[raw.index('<g '):raw.rindex('</svg>')]
    for color,suffix in [('#171714','ink'),('#FFFFFF','white')]:
        art=body.replace('#000000',color)
        svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" role="img" aria-label="AIssociation{ " symbol" if name=="symbol" else ""}"><title>AIssociation</title>{art}</svg>'
        (root/'assets'/f'{name}-{suffix}.svg').write_text(svg)
    pbm.unlink()
print('Traced approved logo, symbol and wordmark to SVG outlines.')
