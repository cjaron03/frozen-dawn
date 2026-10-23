#!/usr/bin/env python3
"""Opaque ORSA exchanger casing and native emissive outdoor fins; no new textures."""
import json
from pathlib import Path
R=Path(__file__).resolve().parents[1]/'src/main/resources/assets/frozendawn'
DIRECTIONS=['north','south','east','west','up','down']
def box(a,b,t):return {'from':a,'to':b,'faces':{face:{'texture':'#'+t,'uv':[0,0,16,16]} for face in DIRECTIONS}}
def model(opened,light,radiating=False):
    # Fill every block boundary. An inset body leaves visible edge gaps because
    # adjacent full wall blocks correctly cull their faces against this block.
    elements=[box([0,0,0],[16,16,16],'casing')]
    # North points outdoors. The opaque backing stays in place behind the fins.
    elements += [box([2,2,-.03],[14,15,0],'recess')]
    if opened:
        for y in (2,5,8,11,14):
            fin=box([2,y,-1.75],[14,y+1,-.04],'metal')
            if radiating:
                fin['shade']=False
                fin['neoforge_data']={'color':'ffff8b24','block_light':15,'sky_light':15,'ambient_occlusion':False}
            elements.append(fin)
    else:elements += [box([2,2,-.15],[14,15,-.04],'metal')]
    # South faces the room. Service grille, lever and light remain cold metal.
    elements += [box([3,4,16.01],[13,13,16.10],'recess')]
    elements += [box([3,y,16.10],[13,y+.75,16.50],'metal') for y in (4,6,8,10,12)]
    elements += [box([x,y,16.01],[x+1,y+1,16.50],'metal') for x in (1,14) for y in (1,14)]
    elements += [box([10,1,16.01],[14,3,16.30],'recess'),box([11 if opened else 10,1.5,16.30],[13 if opened else 12,2.5,16.50],'metal')]
    elements += [box([3,1,16.01],[6,3,16.30],'recess'),box([4,1.5,16.30],[5,2.5,16.50],'indicator')]
    return {'parent':'minecraft:block/block','textures':{'casing':'frozendawn:block/airlock_casing','metal':'minecraft:block/iron_block','recess':'minecraft:block/black_concrete','indicator':'minecraft:block/'+['gray_concrete','yellow_concrete','lime_concrete'][light],'particle':'frozendawn:block/airlock_casing'},'elements':elements}
def name(opened,light,radiating=False):
    return f'heat_vent_{"open" if opened else "closed"}'+('_hot' if opened and radiating else '')+('' if light==0 else f'_{light}')
def main():
    variants={}
    rotations={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270},'up':{'x':270},'down':{'x':90}}
    for opened in (False,True):
        for radiating in (False,True) if opened else (False,):
            for light in range(3):
                (R/f'models/block/{name(opened,light,radiating)}.json').write_text(json.dumps(model(opened,light,radiating),indent=2)+'\n')
    for direction,rotation in rotations.items():
        for opened in (False,True):
            for radiating in (False,True):
                for light in range(3):
                    key=f'facing={direction},venting={str(opened).lower()},radiating={str(radiating).lower()},indicator={light}'
                    variants[key]={'model':'frozendawn:block/'+name(opened,light,radiating),**rotation}
    (R/'blockstates/heat_vent.json').write_text(json.dumps({'variants':variants},indent=2)+'\n')
if __name__=='__main__':main()
