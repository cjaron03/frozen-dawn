#!/usr/bin/env python3
"""Prepare a new disposable flame check, preserving every existing save."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import prepare_ration_warmer_playtest as seed

TITLE = 'Vacuum Flame Check'
PACK = 'vacuum-flame-check'
NS = 'vacuum_flame_check'

def tell(text, action=None):
    data={'text':text,'color':'aqua'}
    if action:data['clickEvent']={'action':'run_command','value':f'/function {NS}:{action}'}
    return 'tellraw @s '+json.dumps(data)

def controls():
    return '\n'.join([
      tell('OUTDOOR: ordinary torches, lantern, candle, campfire and furnace. BLUE: soul equivalents. GLASS ROOM: ordinary lights with sealed-room air.'),
      tell('[Advance this test world to VACUUM]', 'vacuum'),
      tell('[Open the sealed room]', 'breach'),
      tell('[Restore roof — relight with flint and steel]', 'seal'),
      tell('At vacuum, the outdoor ordinary sources go dark. Torches keep vanilla wood with black tips. Blue soul lights keep working. Roof breach extinguishes the indoor ordinary sources.'),
      tell('No scene reset runs after initialization. This disposable fixture is separate from every saved playtest.')])

def scripts():
    return {
      'load':'scoreboard objectives add vfcheck dummy\nexecute unless score #built vfcheck matches 1 run scoreboard players set #built vfcheck 0',
      'tick':f'execute if score #built vfcheck matches 0 as @a[limit=1] at @s run function {NS}:setup',
      'setup':'\n'.join([
        'execute unless score #built vfcheck matches 0 run return 0','scoreboard players set #built vfcheck -1',
        'gamemode creative @s','gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false','gamerule doFireTick false','gamerule keepInventory true','weather clear','time set noon',
        'fd world preset default','fd world set phase 6 mid','fd world pause',
        'fill -14 64 -14 14 64 14 minecraft:bedrock','fill -14 65 -14 14 98 14 minecraft:air',
        'setworldspawn 0 65 8','spawnpoint @s 0 65 8','tp @s 0.5 65 8.5 180 10',
        'setblock -8 65 0 minecraft:torch','setblock -6 65 0 minecraft:lantern',
        'setblock -4 65 0 minecraft:candle[lit=true]','setblock -2 65 0 minecraft:campfire[lit=true]',
        'setblock -9 66 -3 minecraft:stone','setblock -9 66 -2 minecraft:wall_torch[facing=south]',
        'setblock 2 64 0 minecraft:soul_soil','setblock 2 65 0 minecraft:soul_fire',
        'setblock 4 65 0 minecraft:soul_torch','setblock 6 65 0 minecraft:soul_lantern','setblock 8 65 0 minecraft:soul_campfire',
        'setblock -5 65 -4 minecraft:furnace','item replace block -5 65 -4 container.0 with minecraft:raw_iron 64','item replace block -5 65 -4 container.1 with minecraft:coal 64',
        'fill -3 65 -10 3 69 -5 minecraft:glass','fill -2 65 -9 2 68 -6 minecraft:air',
        'setblock -1 65 -8 minecraft:torch','setblock 1 65 -8 minecraft:campfire[lit=true]',
        'setblock 0 65 -7 minecraft:lantern','give @s minecraft:flint_and_steel',
        'give @s minecraft:torch 16','give @s minecraft:soul_torch 16','give @s minecraft:campfire 4','give @s minecraft:lantern 4',
        'give @s patchouli:guide_book[patchouli:book="frozendawn:frozen_dawn_guide"]',
        'scoreboard players set #built vfcheck 1',f'function {NS}:controls',tell('READY: mid Phase 6 first. Look at the original torch tips before clicking VACUUM.')]),
      'vacuum':'\n'.join(['execute unless score #built vfcheck matches 1 run return 0','fd world set phase 6 late','fd world pause',tell('Vacuum active. Ordinary exposed flames extinguish; soul lights and the sealed-room lights remain.')]),
      'breach':'\n'.join(['execute unless score #built vfcheck matches 1 run return 0','setblock 0 69 -8 minecraft:air',tell('Roof breached. Indoor ordinary flames should now go out.')]),
      'seal':'\n'.join(['execute unless score #built vfcheck matches 1 run return 0','setblock 0 69 -8 minecraft:glass',tell('Roof restored. Wait a moment for sky light and air checks, then use flint and steel on the extinguished torch or lantern. Lights do not relight automatically.')]),
      'controls':controls()}

def prepare(source,destination):
    if destination.exists():raise SystemExit('Destination already exists; refusing to overwrite a save.')
    before=hashlib.sha256(source.read_bytes()).hexdigest()
    seed.TITLE=TITLE;seed.PACK=PACK
    raw=seed.metadata(source)
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(raw))
    pack=destination/'datapacks'/PACK
    for tag in ('load','tick'):
        p=pack/f'data/minecraft/tags/function/{tag}.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'values':[f'{NS}:{tag}']})+'\n')
    for name,contents in scripts().items():
        p=pack/f'data/{NS}/function/{name}.mcfunction';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(contents+'\n')
    (pack/'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':TITLE}})+'\n')
    assert before==hashlib.sha256(source.read_bytes()).hexdigest(),'Source metadata changed'
    (destination/'preparation.json').write_text(json.dumps({'source_metadata':str(source.resolve()),'source_sha256':before,'copied':'fresh seed metadata only; no chunks, players, inventory or mod saved data','world':TITLE},indent=2)+'\n')
    print(destination.resolve())

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',type=Path,required=True);p.add_argument('--destination',type=Path,required=True);a=p.parse_args();prepare(a.source,a.destination)
