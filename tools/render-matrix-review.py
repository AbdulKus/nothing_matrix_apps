#!/usr/bin/env python3
"""Show the actual Kotlin renderer output; run after testDebugUnitTest.

HTML is self contained. Optional PNG contact sheets require Pillow.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app/build/reports/matrix-review'
data = json.loads((OUTPUT / 'frames.json').read_text())
# Hex encodes each LED exactly in two chars, so the report stays small.
packed = {group: [dict(name=item['name'], frames=[''.join(f'{v:02x}' for v in frame) for frame in item['frames']])
                  for item in items] for group, items in data.items()}
markup = '''<!doctype html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>
<style>
#matrix-review {color:var(--foreground);font-family:var(--font-sans,system-ui)}
#matrix-review .samples {display:grid;grid-template-columns:repeat(auto-fit,minmax(112px,1fr));gap:16px;margin:16px 0 24px}
#matrix-review figure {margin:0;text-align:center}
#matrix-review canvas {display:block;width:100%;max-width:150px;aspect-ratio:1;margin:0 auto 6px;border-radius:50%;background:#000}
#matrix-review h3 {margin:12px 0;font-size:inherit;font-weight:500}
#matrix-review button {font:inherit;color:var(--foreground);background:var(--secondary);border:1px solid var(--border);border-radius:var(--radius);padding:8px 12px}
#matrix-review figcaption {font-size:12px}
</style>
<div id="matrix-review">
<button id="pause-matrix" type="button">Пауза</button>
<h3>Погода · 13×13, 137 диодов</h3><div class="samples" id="weather-samples"></div>
<h3>Шрифты · 12:34 / 20:58 / 09:16</h3><div class="samples" id="clock-samples"></div>
<h3>Оформление · 12:34</h3><div class="samples" id="layout-samples"></div>
</div>
<script>
const matrixData = __DATA__;
const rowWidths = [5,9,11,11,13,13,13,13,13,11,11,9,5];
const samples=[];
for (const [group, target] of [['weather','weather-samples'],['clocks','clock-samples'],['layouts','layout-samples']]) {
  const parent=document.getElementById(target);
  for(const item of matrixData[group]) {
    const fig=document.createElement('figure');
    const canvas=document.createElement('canvas'); canvas.width=156;canvas.height=156;
    canvas.setAttribute('role','img');canvas.setAttribute('aria-label',item.name);
    const caption=document.createElement('figcaption');caption.textContent=item.name;
    fig.append(canvas,caption);parent.append(fig);
    samples.push({canvas,group,frames:item.frames});
  }
}
let paused=matchMedia('(prefers-reduced-motion: reduce)').matches, tick=0;
const button=document.getElementById('pause-matrix');
function label(){button.textContent=paused?'Продолжить':'Пауза';button.setAttribute('aria-pressed',String(paused));}
button.onclick=()=>{paused=!paused;label();};label();
function draw(){
 for(const {canvas,group,frames} of samples){
  const f=frames[(group==='weather'?tick:Math.floor(tick/25))%frames.length];
  const ctx=canvas.getContext('2d');ctx.clearRect(0,0,156,156);
  for(let y=0;y<13;y++)for(let x=0;x<13;x++){
   if(Math.abs(x-6)>(rowWidths[y]-1)/2)continue;
   const value=parseInt(f.slice((y*13+x)*2,(y*13+x+1)*2),16);
   const level=Math.round(20+value*0.88);ctx.fillStyle=`rgb(${level},${level},${level})`;
   ctx.beginPath();ctx.arc((x+.5)*12,(y+.5)*12,3.48,0,Math.PI*2);ctx.fill();
  }
 }
}
draw();setInterval(()=>{if(!paused){tick++;draw();}},100);
</script></body></html>'''.replace('__DATA__', json.dumps(packed, ensure_ascii=False, separators=(',', ':')))
(OUTPUT / 'matrix-review.html').write_text(markup)
try:
    from PIL import Image, ImageDraw, ImageFont
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16)
    def matrix(draw, frame, ox, oy, step):
        for y, width in enumerate([5,9,11,11,13,13,13,13,13,11,11,9,5]):
            for x in range((13-width)//2, (13+width)//2):
                v=round(20+frame[y*13+x]*.88)
                cx,cy=ox+(x+.5)*step,oy+(y+.5)*step
                r=step*.29
                draw.ellipse((cx-r,cy-r,cx+r,cy+r),fill=(v,v,v))
    for group in ['weather','clocks','layouts']:
        items=data[group]; columns=3; cellw=270; cellh=260
        img=Image.new('RGB',(columns*cellw,((len(items)+columns-1)//columns)*cellh),(5,5,5));d=ImageDraw.Draw(img)
        for i,item in enumerate(items):
            x,y=(i%columns)*cellw,(i//columns)*cellh
            frame=item['frames'][13 if group=='weather' else 0]
            matrix(d,frame,x+31,y+10,16)
            d.text((x+10,y+229),item['name'],font=font,fill=(230,230,230))
        img.save(OUTPUT / f'{group}-contact.png')
except ImportError:
    pass
print(OUTPUT / 'matrix-review.html')
