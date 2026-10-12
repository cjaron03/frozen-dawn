#!/usr/bin/env python3
"""Original ORSA service-grille cuboids; existing owned casing and vanilla materials."""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[1]/'src/main/resources/assets/frozendawn'
DIRECTIONS=['north','south','east','west','up','down']
def box(a,b,t):return {'from':a,'to':b,'faces':{face:{'texture':'#'+t,'uv':[0,0,16,16]} for face in DIRECTIONS}}
def model(opened,light):
    elements=[box([0,0,2 if opened else 1],[16,16,15.5],'casing')]
    if opened:
        elements += [box([2,y,0],[14,y+1,2],'metal') for y in (2,5,8,11,14)]
    else:elements += [box([2,2,0],[14,14,1],'metal')]
    # Room-facing south surface: inset dark cavity, horizontal service slats and four bolts.
    elements += [box([3,4,15.51],[13,13,15.6],'recess')]
    elements += [box([3,y,15.6],[13,y+.75,16],'metal') for y in (4,6,8,10,12)]
    elements += [box([x,y,15.51],[x+1,y+1,16],'metal') for x in (1,14) for y in (1,14)]
    elements += [box([10,1,15.51],[14,3,15.8],'recess'),box([11 if opened else 10,1.5,15.8],[13 if opened else 12,2.5,16],'metal')]
    elements += [box([3,1,15.51],[6,3,15.8],'recess'),box([4,1.5,15.8],[5,2.5,16],'indicator')]
    return {'parent':'minecraft:block/block','textures':{'casing':'frozendawn:block/airlock_casing','metal':'minecraft:block/iron_block','recess':'minecraft:block/black_concrete','indicator':'minecraft:block/'+['gray_concrete','yellow_concrete','lime_concrete'][light],'particle':'frozendawn:block/airlock_casing'},'elements':elements}
def main():
    variants={}
    rotations={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270},'up':{'x':270},'down':{'x':90}}
    for opened in (False,True):
        for light in range(3):
            name=f'heat_vent_{"open" if opened else "closed"}'+('' if light==0 else f'_{light}')
            (R/f'models/block/{name}.json').write_text(json.dumps(model(opened,light),indent=2)+'\n')
    for direction,rotation in rotations.items():
        for opened in (False,True):
            for powered in (False,True):
                for light in range(3):
                    active=opened or powered;name=f'heat_vent_{"open" if active else "closed"}'+('' if light==0 else f'_{light}')
                    key=f'facing={direction},open={str(opened).lower()},powered={str(powered).lower()},indicator={light}'
                    variants[key]={'model':'frozendawn:block/'+name,**rotation}
    (R/'blockstates/heat_vent.json').write_text(json.dumps({'variants':variants},indent=2)+'\n')
if __name__=='__main__':main()
