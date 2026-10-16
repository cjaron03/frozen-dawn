"""Export original ORSA monitor face and state colors into opaque native textures."""
from pathlib import Path
import json,hashlib
from PIL import Image
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
PALETTE=['171c21','283039','414950','667077','976344','c18054','07141c','153843','2dd4d2','1c7584','e5a064','e58b83','72a8e0']
COLORS=[tuple(bytes.fromhex(c)) for c in PALETTE]
source=Image.open(HERE/'generated-front.png').convert('RGB').resize((16,16),Image.Resampling.NEAREST)
source.putdata([min(COLORS,key=lambda c:sum((a-b)**2 for a,b in zip(source.getpixel((x,y)),c))) for y in range(16) for x in range(16)])
mask=[(x,y) for y in range(16) for x in range(16) if source.getpixel((x,y)) in [COLORS[8],COLORS[9]]]
manifest=[]
for status,color in enumerate(['414950','2dd4d2','72a8e0','e5a064','e58b83']):
 im=source.copy()
 for p in mask:im.putpixel(p,tuple(bytes.fromhex(color)))
 im.putpixel((12,14),tuple(bytes.fromhex(color)))
 name=f'power_monitor_{status}'
 out=ROOT/f'src/main/resources/assets/frozendawn/textures/block/{name}.png';im.save(out);im.save(HERE/f'{name}-16x16.png')
 manifest.append(dict(path=str(out.relative_to(ROOT)),width=16,height=16,alpha='opaque',sha256=hashlib.sha256(out.read_bytes()).hexdigest()))
 faces={direction:dict(texture='#front' if direction=='north' else '#case',uv=[0,0,16,16]) for direction in ['north','south','east','west','up','down']}
 model=dict(parent='minecraft:block/block',textures=dict(front=f'frozendawn:block/{name}',case='frozendawn:block/induction_furnace_bottom',particle='frozendawn:block/induction_furnace_bottom'),elements=[dict(**{'from':[2,3,13],'to':[14,13,16]},faces=faces)])
 (ROOT/f'src/main/resources/assets/frozendawn/models/block/{name}.json').write_text(json.dumps(model,indent=2)+'\n')
variants={f'facing={face},status={status}':dict(model=f'frozendawn:block/power_monitor_{status}',y=rotation) for face,rotation in [('north',0),('east',90),('south',180),('west',270)] for status in range(5)}
(ROOT/'src/main/resources/assets/frozendawn/blockstates/power_monitor.json').write_text(json.dumps(dict(variants=variants),indent=2)+'\n')
(ROOT/'src/main/resources/assets/frozendawn/models/item/power_monitor.json').write_text('{"parent":"frozendawn:block/power_monitor_1"}\n')
(HERE/'manifest.json').write_text(json.dumps(dict(created='2026-10-14',creator='built-in imagegen; Frozen Dawn native export',source='generated-front.png',prompt='prompt.txt',script='export_textures.py',palette=PALETTE,third_party_pixels=False,textures=manifest),indent=2)+'\n')
print('Five 16x16 opaque monitor states exported')
