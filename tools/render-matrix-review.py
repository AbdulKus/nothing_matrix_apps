#!/usr/bin/env python3
"""Show the actual Kotlin renderer output; run after testDebugUnitTest.

HTML is self contained. Optional PNG contact sheets require Pillow.
"""
import json
import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'app/build/reports/matrix-review'
data = json.loads((OUTPUT / 'frames.json').read_text())
# Store only lit LEDs: the 36-second review remains compact and lossless.
def encode(frame):
    return base64.b64encode(bytes(v for i, level in enumerate(frame) if level for v in (i, level))).decode('ascii')

packed = {group: [dict(name=item['name'], frames=[encode(frame) for frame in item['frames']])
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
#matrix-review .review-controls {display:flex;align-items:center;flex-wrap:wrap;gap:12px}
#matrix-review figcaption {font-size:12px}
</style>
<div id="matrix-review">
<div class="review-controls"><button id="pause-matrix" type="button">Пауза</button>
<label>Момент <input id="matrix-time" type="range" min="0" max="359" value="0" aria-label="Момент анимации"></label>
<output id="matrix-seconds">0.0 с</output></div>
<div id="matrix-sections"></div>
</div>
<script>
const matrixData = __DATA__;
const rowWidths = [5,9,11,11,13,13,13,13,13,11,11,9,5];
const samples=[];
for (const [group, title] of [['weather','Погода · 13×13, 137 диодов'],['clocks','Шрифты · 12:34 / 20:58 / 09:16'],['layouts','Оформление · 12:34'],['transitions','Переходы из часов'],['meteors','Падающие звёзды'],['moons','Луна']]) {
  if(!matrixData[group]?.length) continue;
  const section=document.getElementById('matrix-sections');
  const heading=document.createElement('h3');heading.textContent=title;
  const parent=document.createElement('div');parent.className='samples';section.append(heading,parent);
  for(const item of matrixData[group]) {
    const fig=document.createElement('figure');
    const canvas=document.createElement('canvas'); canvas.width=156;canvas.height=156;
    canvas.setAttribute('role','img');canvas.setAttribute('aria-label',item.name);
    const caption=document.createElement('figcaption');caption.textContent=item.name;
    fig.append(canvas,caption);parent.append(fig);
    const frames=item.frames.map(encoded=>{
      const frame=new Uint8Array(169), bytes=atob(encoded);
      for(let i=0;i<bytes.length;i+=2)frame[bytes.charCodeAt(i)]=bytes.charCodeAt(i+1);
      return frame;
    });
    samples.push({canvas,group,frames});
  }
}
let paused=matchMedia('(prefers-reduced-motion: reduce)').matches, tick=0;
const button=document.getElementById('pause-matrix');
const timeline=document.getElementById('matrix-time'), seconds=document.getElementById('matrix-seconds');
const cycleTicks=Math.max(1,...samples.map(s=>s.frames.length*(['weather','transitions','meteors','moons'].includes(s.group)?1:25)));
timeline.max=String(cycleTicks-1);
function label(){button.textContent=paused?'Продолжить':'Пауза';button.setAttribute('aria-pressed',String(paused));}
button.onclick=()=>{paused=!paused;label();};label();
timeline.oninput=()=>{paused=true;tick=Number(timeline.value);label();draw();};
function draw(){
 timeline.value=String(tick%cycleTicks);seconds.textContent=`${((tick%cycleTicks)/10).toFixed(1)} с`;
 for(const {canvas,group,frames} of samples){
  const f=frames[(group==='weather'||group==='transitions'||group==='meteors'||group==='moons'?tick:Math.floor(tick/25))%frames.length];
  const ctx=canvas.getContext('2d');ctx.clearRect(0,0,156,156);
  for(let y=0;y<13;y++)for(let x=0;x<13;x++){
   if(Math.abs(x-6)>(rowWidths[y]-1)/2)continue;
   const value=f[y*13+x];
   const level=Math.round(20+value*0.88);ctx.fillStyle=`rgb(${level},${level},${level})`;
   ctx.beginPath();ctx.arc((x+.5)*12,(y+.5)*12,3.48,0,Math.PI*2);ctx.fill();
  }
 }
}
draw();setInterval(()=>{if(!paused){tick++;draw();}},100);
</script></body></html>'''
(OUTPUT / 'matrix-review.html').write_text(markup.replace('__DATA__', json.dumps(packed, ensure_ascii=False, separators=(',', ':'))))
(OUTPUT / 'weather-review.html').write_text(markup.replace('__DATA__', json.dumps({'weather':packed['weather']}, ensure_ascii=False, separators=(',', ':'))))
if 'moons' in packed:
    ordinary = [dict(name=item['name'].split(' · ')[0], frames=item['frames']) for item in packed['moons'] if item['name'].endswith('обычная')]
    (OUTPUT / 'moon-scenes.html').write_text(markup.replace('__DATA__', json.dumps({'moons':ordinary}, ensure_ascii=False, separators=(',', ':'))))
    (OUTPUT / 'moon-review.html').write_text(markup.replace('__DATA__', json.dumps({'moons':packed['moons']}, ensure_ascii=False, separators=(',', ':'))))
if 'meteors' in packed:
    (OUTPUT / 'night-review.html').write_text(markup.replace('__DATA__', json.dumps({'meteors':packed['meteors']}, ensure_ascii=False, separators=(',', ':'))))
if 'transitions' in packed:
    (OUTPUT / 'weather-and-transitions-review.html').write_text(markup.replace('__DATA__', json.dumps({'weather':packed['weather'],'transitions':packed['transitions']}, ensure_ascii=False, separators=(',', ':'))))
    (OUTPUT / 'transitions-review.html').write_text(markup.replace('__DATA__', json.dumps({'transitions':packed['transitions']}, ensure_ascii=False, separators=(',', ':'))))
if 'tilt' in packed:
    tilt_page = markup.replace('__DATA__', json.dumps({'weather':packed['tilt']}, ensure_ascii=False, separators=(',', ':')))
    tilt_page = tilt_page.replace('minmax(112px,1fr)', 'minmax(200px,1fr)').replace('</style>', '#matrix-review .review-controls {display:none}</style>')
    (OUTPUT / 'weather-tilt-review.html').write_text(tilt_page)
try:
    from PIL import Image, ImageDraw, ImageFont
    font_paths = (
        '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
        '/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf',
    )
    font_path = next((path for path in font_paths if Path(path).is_file()), None)
    if not font_path:
        raise ImportError('No font for optional PNG contact sheets')
    font = ImageFont.truetype(font_path, 16)
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
