#!/usr/bin/env python3
"""Fresh slice-two equipment replay, reusing the accepted phase-zero lighting fixtures."""
import argparse
import fcntl
import gzip
import io
import json
import tomllib
from pathlib import Path
import prepare_blind_architect_playtest as nbt
import prepare_headlamp_playtest as aim
from prepare_power_network_playtest import prepare, pack_only

TITLE='Headlamp Equipment Check'
NS='headlamp_equipment'
PACK='headlamp-equipment'
GUARD='execute unless score #built hlequip matches 1 run return 0'

def tell(text,function=None):
    value={'text':text,'color':'aqua'}
    if function:value['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)

def scripts():
    result={name:body.replace(aim.NS,NS).replace('hlamp','hlequip') for name,body in aim.scripts().items() if name not in ('on','off')}
    result['setup']=result['setup'].replace('headlamp_prototype off', '\n'.join([
        'item replace entity @s hotbar.2 with frozendawn:power_cell[frozendawn:cell_joules=500000]',
        'item replace entity @s hotbar.3 with frozendawn:suit_headlamp',
        'item replace entity @s hotbar.4 with frozendawn:orsa_thermal_visor',
        'item replace entity @s hotbar.5 with minecraft:iron_helmet',
        'item replace entity @s hotbar.6 with frozendawn:suit_headlamp']))
    result['controls']='\n'.join([GUARD,
        tell('HEADLAMP SLICE 2: Equip in Curios > Helmet Mount; H toggles the mounted module. EVA Helmet or Thermal Visor required; full suit is optional. 50 W from loose cells, hotbar then inventory, offhand last.'),
        tell('Slot 2: Lantern (own cell). Slot 3: full loose cell. Slot 4: Headlamp (equip in Helmet Mount). Slot 5: Thermal Visor; slot 6: unsupported Iron Helmet; slot 7: spare module.'),
        tell('[Read equipment/cells]','read'),tell('[Give 10 J cell]','short_cell'),tell('[Give full cell]','full_cell'),
        tell('[Long tunnel]','tunnel'),tell('[Solid wall]','wall'),tell('[Corner]','corner'),tell('[Vertical aim]','vertical'),tell('[Underwater]','water'),
        tell('To test depletion: store the full loose cell in the chest, then give a 10 J cell. Never reset this save. Storage chest is behind the starting position.'),
        tell('Switch and exact charge survive reload. Helmet/module removal pauses drain. Only Helmet Mount activates a headlamp; inventory/offhand copies stay inactive.'),
        tell('Reopen with /function headlamp_equipment:controls.'),'return 1'])
    result['setup']=result['setup'].replace('setworldspawn 0 65 5','setblock 0 65 2 minecraft:chest\nsetworldspawn 0 65 5')
    result['read']='\n'.join([GUARD,'headlamp_equipment status','return 1'])
    for name,joules in [('short_cell',10),('full_cell',500000)]:
        result[name]='\n'.join([GUARD,f'give @s frozendawn:power_cell[frozendawn:cell_joules={joules}]','return 1'])
    return result

def prepare_world(source,destination):
    config=Path(__file__).resolve().parents[1]/'run-lab/config/frozendawn-common.toml'
    assert tomllib.loads(config.read_text())['general']['pauseProgression'] is True,'Equipment replay requires paused progression'
    with (source.parent/'session.lock').open('rb') as lock:
        fcntl.lockf(lock,fcntl.LOCK_SH|fcntl.LOCK_NB)
        prepare(source,destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64,structures=False)
    for filename in ('level.dat','data/frozendawn_apocalypse.dat'):
        f=destination/filename;stream=io.BytesIO(gzip.decompress(f.read_bytes()));assert stream.read(1)==b'\x0a';name=nbt.string(stream);root=nbt.read(stream,10)
        if filename=='level.dat':root['Data'][1].update({'DifficultyLocked':(1,1),'confirmedExperimentalSettings':(1,1),'DragonFight':(10,{}),'SpawnX':(3,0),'SpawnY':(3,65),'SpawnZ':(3,5),'DayTime':(4,18000)})
        else:root['data'][1]['apocalypseTicks']=(4,0)
        f.write_bytes(gzip.compress(b'\x0a'+nbt.utf(name)+nbt.write(10,root)))
    report=destination/'preparation.json';v=json.loads(report.read_text());v.update({'apocalypseTicks':0,'slice':2,'fixtures':'accepted tunnel/wall/corner/shaft/water plus equipment and cell controls'});report.write_text(json.dumps(v,indent=2)+'\n')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path);p.add_argument('--destination',type=Path);p.add_argument('--pack-only',type=Path);a=p.parse_args()
    if a.pack_only:pack_only(a.pack_only,title=TITLE,namespace=NS,build_scripts=scripts)
    elif a.source and a.destination:prepare_world(a.source,a.destination)
    else:p.error('Use --source/--destination or --pack-only')
