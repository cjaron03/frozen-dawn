#!/usr/bin/env python3
"""Fresh service grille + airlock sound replay; seed metadata only."""
import argparse,gzip,hashlib,json
from pathlib import Path
import prepare_heat_vent_playtest as heat
import prepare_airlock_playtest as airlock
import prepare_ration_warmer_playtest as seed
TITLE='Airlock Service Check';PACK='airlock-service-check'

def shift(command):
    words=command.split();z_indices={'fill':(3,6),'setblock':(3,),'tp':(4,),'setworldspawn':(3,),'spawnpoint':(4,)}.get(words[0],())
    for i in z_indices:
        v=float(words[i])+24;words[i]=str(int(v)) if v.is_integer() else str(v)
    return ' '.join(words) if z_indices else command

def prepare(source,destination):
    if destination.exists():raise SystemExit('Refusing to overwrite existing save')
    original=source.read_bytes();seed.TITLE=TITLE;seed.PACK=PACK
    destination.mkdir(parents=True);(destination/'level.dat').write_bytes(gzip.compress(seed.metadata(source)))
    pack=destination/'datapacks'/PACK;pack.mkdir(parents=True)
    (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':TITLE}})+'\n')
    h=heat.scripts();a=airlock.scripts()
    for name,body in a.items():a[name]='\n'.join(shift(line) for line in body.splitlines())
    h['setup']+='\nfunction airlock_check:setup'
    extra='\n'+airlock.tell('[Airlock entrance]',None)+'\n'+heat.base.row([('[Airlock base]','/function airlock_check:base'),('[Airlock controls]','/function airlock_check:controls'),('[Service grille]','left')])
    h['controls']+=extra;a['controls']+='\n'+heat.base.row([('[Service grille]','left'),('[Fuel test rooms]','fuel'),('[Heat vent controls]','controls')])
    a['base']='\n'.join(['execute unless score #built alcheck matches 1 run return 0','tp @s -0.5 65 24.5 -90 10','function airlock_check:controls'])
    a['setup']+='\n'+heat.base.row([('[Service grille]','left'),('[Airlock base]','/function airlock_check:base')])
    h['controls']=h['controls'].replace('Heat Vent Check:','Airlock Service Check:').replace('Blocking disables cooling.','Blocking disables cooling: amber light. Unblocked cooling: green. Closed: dim.')
    for ns,functions in [('room_heat_check',h),('airlock_check',a)]:
        for name,body in functions.items():
            p=pack/f'data/{ns}/function/{name}.mcfunction';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body+'\n')
    for name,values in [('load',['room_heat_check:load','airlock_check:load']),('tick',['room_heat_check:tick'])]:
        p=pack/f'data/minecraft/tags/function/{name}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'values':values})+'\n')
    assert source.read_bytes()==original
    (destination/'preparation.json').write_text(json.dumps({'world':TITLE,'source':str(source),'source_sha256':hashlib.sha256(original).hexdigest(),'copied':'seed metadata only; no chunks, players, mod SavedData or old inventories'},indent=2)+'\n')
    print(destination)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',required=True,type=Path);p.add_argument('--destination',required=True,type=Path);args=p.parse_args();prepare(args.source,args.destination)
