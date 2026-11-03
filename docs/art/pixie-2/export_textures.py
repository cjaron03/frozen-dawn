from pathlib import Path
from PIL import Image
import json,hashlib
root=Path(__file__).resolve().parents[3];art=Path(__file__).resolve().parent
# Export original generated pixel art to native16x16; no third-party pixels.
palette=['11191e','20292e','343e46','505c67','78838d','a6afb5','653d29','965b35','c37d4d','efa069','07505c','0d8290','25bfc7','83eef0']
colors=[tuple(bytes.fromhex(p)) for p in palette]
source=Image.open(art/'generated-front.png').convert('RGB').resize((16,16),Image.Resampling.BOX)
out=Image.new('RGB',(16,16));out.putdata([min(colors,key=lambda c:sum((c[i]-p[i])**2 for i in range(3))) for p in source.getdata()])
assets=root/'src/main/resources/assets/frozendawn/textures/block';assets.mkdir(parents=True,exist_ok=True)
rows=[]
for active in (False,True):
 image=out.copy()
 # The small lamp only indicates processing, never fictitious water percentage.
 for y in range(4,6):
  for x in range(12,14):image.putpixel((x,y),tuple(bytes.fromhex('83eef0' if active else '505c67')))
 name='pixie_front'+('_on' if active else '')+'.png';p=assets/name;image.save(p);image.save(art/(p.stem+'-16x16.png'));rows.append({'path':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'width':16,'height':16,'alpha':'opaque'})
(art/'manifest.json').write_text(json.dumps({'created':'2026-10-14','creator':'Built-in imagegen for Frozen Dawn','source':'generated-front.png','prompt':'prompt.txt','script':'export_textures.py','palette':palette,'third_party_pixels':False,'textures':rows},indent=2)+'\n')
