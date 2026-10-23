"""Deterministic 16x16 export of the original imagegen atlas; run from any cwd."""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
TEX = ROOT / 'src/main/resources/assets/frozendawn/textures/block'
MODEL = ROOT / 'src/main/resources/assets/frozendawn/models/block'
PALETTE = ['171c21','22272c','30363d','424950','535c63','687079','939b9f',
           '070e15','0d1b2c','16273a','68452f','986242','bb764b','d18e5a',
           '713604','a65106','ec820a','ffc04e','175363','28d8d1']
COLORS = [tuple(bytes.fromhex(c)) for c in PALETTE]
NAMES = ['induction_furnace_front','induction_furnace_front_on',
         'induction_furnace_side','induction_furnace_top',
         'induction_furnace_back','induction_furnace_bottom']

def main():
    source = Image.open(HERE / 'generated-atlas.png').convert('RGB')
    assert source.size == (1536,1024), source.size
    images = []
    for i in range(6):
        x, y = (i % 3)*512, (i // 3)*512
        tile = source.crop((x,y,x+512,y+512)).resize((16,16),Image.Resampling.NEAREST)
        tile.putdata([min(COLORS,key=lambda c: sum((a-b)**2 for a,b in zip(pixel,c)))
                      for pixel in tile.getdata()])
        images.append(tile)
    # The two LIT variants share the exact same hardware pixels. Only the sealed
    # chamber interior and the single status lamp change with actual work.
    lit = images[0].copy()
    lit.paste(images[1].crop((4,4,12,9)),(4,4))
    lit.putpixel((12,11),tuple(bytes.fromhex('28d8d1')))
    images[0].putpixel((12,11),tuple(bytes.fromhex('175363')))
    images[1] = lit
    manifest = []
    TEX.mkdir(parents=True,exist_ok=True)
    preview = Image.new('RGB',(576,432),'#d7d7d7')
    draw = ImageDraw.Draw(preview)
    for i,(name,tile) in enumerate(zip(NAMES,images)):
        path = TEX / (name+'.png')
        tile.save(path)
        tile.save(HERE / (name+'-16x16.png'))
        x,y = (i%3)*192,(i//3)*216
        preview.paste(tile.resize((192,192),Image.Resampling.NEAREST),(x,y))
        draw.text((x+5,y+197),name.removeprefix('induction_furnace_'),fill='#171c21')
        manifest.append(dict(name=name,path=str(path.relative_to(ROOT)),width=16,
                             height=16,alpha='opaque',sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    for on in [False,True]:
        textures = dict(north='frozendawn:block/induction_furnace_front'+('_on' if on else ''),
                        south='frozendawn:block/induction_furnace_back',
                        east='frozendawn:block/induction_furnace_side',
                        west='frozendawn:block/induction_furnace_side',
                        up='frozendawn:block/induction_furnace_top',
                        down='frozendawn:block/induction_furnace_bottom',
                        particle='frozendawn:block/induction_furnace_side')
        (MODEL/('induction_furnace'+('_on' if on else '')+'.json')).write_text(
            json.dumps(dict(parent='minecraft:block/cube',textures=textures),indent=2)+'\n')
    preview.save(HERE/'native-preview.png')
    (HERE/'manifest.json').write_text(json.dumps(dict(generated='2026-10-14',
        creator='built-in imagegen; deterministic native export by Frozen Dawn',
        source='generated-atlas.png',reference='../01-orsa-steel-copper.png',
        prompt='prompt.txt',export='export_textures.py',palette=PALETTE,
        third_party_pixels=False,textures=manifest),indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__ == '__main__':
    main()
