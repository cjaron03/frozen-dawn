#!/usr/bin/env python3
"""Fresh Power Network Check. Copy generator schema only; refuse existing destinations."""
import argparse
import copy
import gzip
import hashlib
import io
import json
import time
from pathlib import Path
import prepare_blind_architect_playtest as nbt

TITLE = 'Power Network Check'
NS = 'power_network_check'
PACK = 'power-network-check'
ROOT = Path(__file__).resolve().parents[1]
GUARD = 'execute unless score #built pnetwork matches 1 run return 0'


def tell(text, function=None):
    value = {'text': text, 'color': 'aqua'}
    if function:
        value['clickEvent'] = {'action': 'run_command', 'value': '/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(value)


def scripts():
    setup = ['execute unless score #built pnetwork matches 0 run return 0',
             'scoreboard players set #built pnetwork -1', 'gamemode creative @s',
             'gamerule doMobSpawning false', 'gamerule doDaylightCycle false',
             'gamerule doWeatherCycle false', 'gamerule keepInventory true',
             'gamerule announceAdvancements false', 'difficulty normal', 'time set noon', 'weather clear',
             'fd world pause']
    # Small bounded fills keep the vanilla fill limit intact. Only this new lab is staged.
    for x in range(-18, 43, 10):
        setup += [f'fill {x} 64 -5 {x+9} 74 46 minecraft:air',
                  f'fill {x} 64 -5 {x+9} 64 46 minecraft:smooth_stone']
    stations = [(0,0,'power_relay'),(16,0,'power_relay'),(17,0,'power_relay'),
                (0,12,'large_power_relay'),(32,12,'large_power_relay'),(33,12,'large_power_relay'),
                (-8,25,'power_relay'),(8,25,'power_relay'),
                (-12,42,'power_relay'),(-4,42,'power_relay'),(4,42,'power_relay')]
    for x,z,block in stations:
        setup += [f'setblock {x} 65 {z} frozendawn:{block}', f'setblock {x} 64 {z} minecraft:cyan_concrete']
    setup += ['fill 0 65 23 0 68 27 minecraft:stone']
    for z,feedthrough in ((25,False),(40,True)):
        setup += [f'fill 26 64 {z-3} 34 69 {z+3} minecraft:glass hollow',
                  f'fill 26 64 {z-3} 34 64 {z+3} minecraft:smooth_stone',
                  f'setblock 29 65 {z} frozendawn:power_relay',
                  f'setblock 38 65 {z} frozendawn:power_relay',
                  f'setblock 34 65 {z} '+('frozendawn:bulkhead_feedthrough' if feedthrough else 'minecraft:stone')]
    setup += ['setblock -14 65 8 frozendawn:geothermal_core',
              'setblock -8 65 8 frozendawn:power_relay',
              'give @s frozendawn:conduit_binder', 'give @s frozendawn:power_relay 8',
              'give @s frozendawn:large_power_relay 4', 'give @s frozendawn:bulkhead_feedthrough 8',
              'give @s minecraft:glass 32', 'give @s minecraft:stone 32',
              'effect give @s minecraft:night_vision infinite 0 true',
              'setworldspawn 3 65 4', 'spawnpoint @s 3 65 4', 'tp @s 3.5 65 4.5 180 15',
              'scoreboard players set #built pnetwork 1', 'function '+NS+':controls', 'return 1']
    result = {
        'load': 'scoreboard objectives add pnetwork dummy\nexecute unless score #built pnetwork matches -1..1 run scoreboard players set #built pnetwork 0\nexecute if score #built pnetwork matches 1 as @a run function '+NS+':controls',
        'tick': 'execute if score #built pnetwork matches 0 as @a[limit=1] run function '+NS+':setup',
        'setup': '\n'.join(setup),
        'controls': '\n'.join([GUARD, tell('POWER NETWORK CHECK: Creative, Default difficulty. Use Binder on two nodes; Shift-use clears a node.'),
            tell('[1 Small / Core]', 'basic'), tell('[2 Large range]', 'large'), tell('[3 Ordinary stone]', 'stone'),
            tell('[4 Sealed wall]', 'sealed'), tell('[5 Feedthrough]', 'feedthrough'), tell('[6 Chain / cycle]', 'chain'),
            tell('Hold Binder and look at a node for counts. Supply, demand and storage are 0 in this topology slice. Save and Quit, then reopen to check links persist.'), 'return 1'])}
    for name,(x,z,text) in {
        'basic':(3,4,'Small: link x0 to x16 at z0 (PASS), then x0 to x17 (RANGE). Reverse click is DUPLICATE. Click a selected node again to cancel. Core at -14,65,8 should select with Binder and open normally empty-handed.'),
        'large':(14,16,'Large: x0 to x32 at z12 passes; x0 to x33 fails. Replacing one endpoint with a small relay caps range at 16.'),
        'stone':(-5,29,'Ordinary stone: link pylons at -8,65,25 and 8,65,25. Beam passes through the stone panel. It does not need line of sight.'),
        'sealed':(29,25,'Sealed wall: inside pylon at 29,65,25 to outside at 38,65,25 must fail. Wall at 34,65,25 needs a feedthrough. Replace stone with the supplied block, then retry.'),
        'feedthrough':(29,40,'Feedthrough: link inside 29,65,40 to outside 38,65,40 directly; or link both to feedthrough at 34,65,40. Replace it with glass: direct link goes inactive. Restore it: direct link reconnects. Breaking a linked feedthrough removes its incident links.'),
        'chain':(-4,45,'Chain: link -12 -> -4 -> 4 at y65,z42. All three nodes join. Add -12 -> 4 to form a cycle. Shift-clear the middle, then break and replace it: its old links must stay removed.')}.items():
        result[name]='\n'.join([GUARD,f'tp @s {x+.5} 65 {z+.5} 180 15',tell(text),'return 1'])
    return result


def pack_only(destination, hooks=False, title=TITLE, namespace=NS, build_scripts=scripts):
    destination.mkdir(parents=True, exist_ok=True)
    for name, body in build_scripts().items():
        target = destination/f'data/{namespace}/function/{name}.mcfunction'
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(body+'\n')
    if hooks:
        (destination/'pack.mcmeta').write_text(json.dumps(nbt.pack_metadata(title))+'\n')
        for name in ('load','tick'):
            target=destination/f'data/minecraft/tags/function/{name}.json'
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps({'values':[namespace+':'+name]})+'\n')


def prepare(template, destination, title=TITLE, namespace=NS, pack=PACK, build_scripts=scripts, surface=128, structures=True):
    if destination.exists():
        raise SystemExit('Refusing to overwrite existing world: '+str(destination))
    original=template.read_bytes(); source_hash=hashlib.sha256(original).hexdigest()
    stream=io.BytesIO(gzip.decompress(original))
    if stream.read(1)!=b'\x0a':
        raise ValueError('Expected compound NBT root')
    nbt.string(stream); old=nbt.read(stream,10)['Data'][1]
    data={key:copy.deepcopy(old[key]) for key in nbt.METADATA_KEYS}
    data['WorldGenSettings'][1]['seed']=(4,77371337)
    # Flat safe lab, with no copied terrain. Required dimensions retain their generator schemas.
    dims=data['WorldGenSettings'][1]['dimensions'][1]
    dims['minecraft:overworld'][1]['generator']=(10,{'type':(8,'minecraft:flat'), 'settings':(10,{
        'biome':(8,'minecraft:plains'), 'lakes':(1,0), 'features':(1,0),
        'layers':(9,(10,[{'height':(3,1),'block':(8,'minecraft:bedrock')},{'height':(3,surface-1),'block':(8,'minecraft:stone')}]))})})
    if not structures:
        # No villages or other flat-world structures on top of lab fixtures.
        dims['minecraft:overworld'][1]['generator'][1]['settings'][1]['structure_overrides']=(9,(8,[]))
    data.update({'LevelName':(8,title),'GameType':(3,1),'allowCommands':(1,1),'Difficulty':(1,2),
                 'DifficultyLocked':(1,0),'hardcore':(1,0),'initialized':(1,1),'Time':(4,0),'DayTime':(4,6000),
                 'LastPlayed':(4,int(time.time()*1000)),'SpawnX':(3,3),'SpawnY':(3,65),'SpawnZ':(3,4),'WasModded':(1,1),
                 'GameRules':(10,{'doMobSpawning':(8,'false'),'announceAdvancements':(8,'false'),'keepInventory':(8,'true')}),
                 'DataPacks':(10,{'Enabled':(9,(8,['vanilla','mod_data','file/'+pack])),'Disabled':(9,(8,[]))}),
                 'enabled_features':(9,(8,['minecraft:vanilla']))})
    encoded=b'\x0a'+nbt.utf('')+nbt.write(10,{'Data':(10,data)})
    verify=io.BytesIO(encoded);assert verify.read(1)==b'\x0a';nbt.string(verify)
    assert nbt.read(verify,10)=={'Data':(10,data)} and not verify.read()
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(encoded))
    (destination/'data').mkdir()
    (destination/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(nbt.lab_state(old['DataVersion'][1])))
    pack_only(destination/'datapacks'/pack,hooks=True,title=title,namespace=namespace,build_scripts=build_scripts)
    assert hashlib.sha256(template.read_bytes()).hexdigest()==source_hash
    (destination/'preparation.json').write_text(json.dumps({'world':title,'source_metadata':str(template.resolve()),
        'source_sha256':source_hash,'copied_gameplay_files':0,'difficulty_preset':'default','difficulty_locked':True,
        'advancements':'filtered after mod_data in this world only','state':'Prepared, not opened'},indent=2)+'\n')
    print(destination.resolve())


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path);parser.add_argument('--destination',type=Path)
    parser.add_argument('--pack-only',type=Path);args=parser.parse_args()
    if args.pack_only: pack_only(args.pack_only)
    elif args.source and args.destination: prepare(args.source,args.destination)
    else: parser.error('Use --source/--destination or --pack-only')
