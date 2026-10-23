#!/usr/bin/env python3
"""Original RH-5 steel cabinet and exposed copper elements, built from native cuboids.
Reuses owned induction casing and vanilla metal textures; no external art/dependencies.
"""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[1]/'src/main/resources/assets/frozendawn'
FACES=['north','south','east','west','up','down']
def box(a,b,texture):return {'from':a,'to':b,'faces':{f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in FACES}}
def model(lit):
 body=box([0,0,0],[16,16,16],'casing');body['faces']['up']['texture']='#top';body['faces']['north']['texture']='#recess'
 elements=[body]
 for x in (0,14):elements.append(box([x,0,-.25],[x+2,16,0],'rim'))
 for y in (0,14):elements.append(box([2,y,-.25],[14,y+2,0],'rim'))
 for y in (3,6,9,12):
  coil=box([3,y,-.35],[13,y+1,-.05],'coil')
  if lit:coil.update({'shade':False,'neoforge_data':{'color':'ffff972d','block_light':15,'sky_light':15,'ambient_occlusion':False}})
  elements.append(coil)
 for x in (4,11):elements.append(box([x,2,-.65],[x+1,14,-.40],'rim'))
 return {'parent':'minecraft:block/block','textures':{'particle':'frozendawn:block/induction_furnace_side','casing':'frozendawn:block/induction_furnace_side','top':'frozendawn:block/induction_furnace_top','rim':'minecraft:block/iron_block','coil':'minecraft:block/copper_block','recess':'minecraft:block/black_concrete'},'elements':elements}
def main():
 variants={}
 for lit in (False,True):
  name='resistive_heater'+('_on' if lit else '')
  (R/f'models/block/{name}.json').write_text(json.dumps(model(lit),indent=2)+'\n')
  for face,y in [('north',0),('east',90),('south',180),('west',270)]:variants[f'facing={face},lit={str(lit).lower()}']={'model':'frozendawn:block/'+name,'y':y}
 (R/'blockstates/resistive_heater.json').write_text(json.dumps({'variants':variants},indent=2)+'\n')
if __name__=='__main__':main()
