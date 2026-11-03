#!/usr/bin/env python3
"""Fresh Power & Air Check; metadata only, four real advancement triggers, no old save edits."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
from prepare_power_network_playtest import prepare, pack_only
import prepare_blind_architect_playtest as nbt

ROOT=Path(__file__).resolve().parents[1]
TITLE='Power & Air Check'
NS='power_air_check'
PACK='power-air-check'
OBJECTIVE='paircheck'
GUARD='execute unless score #built '+OBJECTIVE+' matches 1 run return 0'
ADVANCEMENTS=('in_space_no_one_can_hear_you_brown_out','unlimited_power','canary_in_the_furnace','holy_cow')

def tell(text,function=None):
    msg={'text':text,'color':'aqua'}
    if function:msg['clickEvent']={'action':'run_command','value':'/function '+NS+':'+function}
    return 'tellraw @s '+json.dumps(msg)

def scripts():
    loaded=[f'execute unless loaded {x} 64 {z} run return 0' for x in (-8,0,16,32,48,64) for z in (-6,0)]
    setup=['execute unless score #built paircheck matches 0 run return 0',*loaded,
        'scoreboard players set #built paircheck -1','gamemode creative @s',
        'gamerule doMobSpawning false','gamerule doDaylightCycle false','gamerule doWeatherCycle false',
        'gamerule doFireTick false','gamerule keepInventory true','gamerule announceAdvancements true',
        'weather clear','time set midnight','fd world set phase 6 late']
    # Small independent rooms; the power Core sits in an opaque side cabinet so
    # its permanent level-15 light cannot mask the emergency fixture.
    setup+=['fill -8 64 -6 75 64 7 minecraft:smooth_stone',
        'fill 0 64 -4 10 69 4 frozendawn:insulated_glass hollow',
        'fill 0 69 -4 10 69 4 minecraft:stone',
        'fill -6 64 -2 0 68 2 minecraft:stone hollow',
        'setblock -3 65 0 frozendawn:geothermal_core',
        'setblock 0 65 0 frozendawn:bulkhead_feedthrough',
        'setblock 3 65 0 frozendawn:power_relay',
        'setblock 7 66 -1 frozendawn:panel_light',
        'setblock 7 68 1 frozendawn:orsa_emergency_light[facing=down]']
    for x in (32,64):
        setup += [f'fill {x-6} 64 -4 {x+6} 69 4 frozendawn:insulated_glass hollow',
            f'setblock {x-3} 65 0 frozendawn:geothermal_core',
            f'setblock {x+4} 66 3 frozendawn:oxygen_gauge[facing=north]']
        for z in (-2,0,2) if x==32 else (-2,0):
            setup += [f'setblock {x+2} 65 {z} minecraft:furnace[facing=west]']
    setup+=['give @s frozendawn:conduit_binder','give @s minecraft:raw_iron 128',
        'give @s minecraft:coal 64','give @s frozendawn:orsa_emergency_light 2',
        'give @s minecraft:glass 16',
        'setworldspawn 5 65 2','spawnpoint @s 5 65 2','tp @s 5.5 65 2.5 -90 10',
        'execute unless block 58 64 -4 frozendawn:insulated_glass run return 0',
        'execute unless block 70 69 4 frozendawn:insulated_glass run return 0',
        'execute unless block 61 65 0 frozendawn:geothermal_core run return 0',
        'execute unless block 68 66 3 frozendawn:oxygen_gauge run return 0',
        'execute unless block 66 65 -2 minecraft:furnace run return 0',
        'execute unless block 66 65 0 minecraft:furnace run return 0',
        'forceload remove -8 -6 75 7','scoreboard players set #forced paircheck 0',
        'scoreboard players set #built paircheck 1','function '+NS+':controls','return 1']
    result={
        'load':'scoreboard objectives add paircheck dummy\nexecute unless score #built paircheck matches -1..1 run scoreboard players set #built paircheck 0\nexecute if score #built paircheck matches 1 as @a run function '+NS+':controls',
        'load_chunks':'\n'.join(['execute unless score #built paircheck matches 0 run return 0',
            'execute if score #forced paircheck matches 1 run return 0','forceload add -8 -6 75 7',
            'scoreboard players set #forced paircheck 1','return 1']),
        'tick':'execute if score #built paircheck matches 0 run function '+NS+':load_chunks\nexecute if score #built paircheck matches 0 as @a[limit=1] run function '+NS+':setup',
        'setup':'\n'.join(setup),
        'controls':'\n'.join([GUARD,tell('POWER & AIR CHECK - Creative, Default, paused late vacuum. Only the four tested advancements are enabled.'),
            tell('[1 Power room / emergency light]','power'),tell('[Core controls / Unlimited Power!]','core'),
            tell('[2 Canary in the Furnace]','canary'),tell('[3 Holy Cow]','cow_room'),tell('[Advancement progress]','progress'),'return 1'])}
    actions={
        'power':('tp @s 5.5 65 2.5 -90 10',[
            'Bind Core (-3,65,0) -> feedthrough (0,65,0) -> relay (3,65,0) -> BOTH lights (7,66,-1 and 7,68,1). Use Core controls to reach the side cabinet.',
            'Set Core to 1% power. Return here: white panel on, emergency light off. Right-click red light: reserve ready, about 10:00.',
            'Set Core to 0%, return here: white off, red on at level 6, reserve counts down. Brown Out should pop while you are inside this room.',
            'Restore 1% to re-arm. Save/reopen during backup to check remaining time; about ten loaded minutes empty the reserve. No night vision in this check.']),
        'core':('tp @s -3.5 65 .5 -90 10',[
            'Open this Core empty-handed and set power to 100%: Unlimited Power! should pop from your menu action.',
            'Then set 1% to arm the emergency light; 0% to test backup. Use Power room to return. This cabinet blocks the Core light from spoiling the dark-room check.']),
        'canary':('tp @s 32.5 65 2.5 -90 10',[
            'Wait for the gauge to say Supported. This separate Core stays at Default: O2 supply 8. Keep Creative.',
            'Put 16 raw iron and coal into each of TWO furnaces (demand 6): no award. Then start the THIRD (demand 9).',
            'Wait roughly 10-15 seconds. A fueled furnace should pause for oxygen and Canary in the Furnace should pop. Fuel running out alone does not count.']),
        'cow_room':('tp @s 64.5 65 2.5 -90 10',[
            'Wait for Supported, then put 16 raw iron and coal in BOTH furnaces: demand 6 fits supply 8; no Holy Cow yet.',
            'Click Add cow AFTER both furnaces are burning. Cow adds 5, tipping demand to 11. Holy Cow should pop. Keep Creative; the cow is protected for this lab.'])}
    for name,(pos,lines) in actions.items():
        result[name]='\n'.join([GUARD,pos,*(tell(line) for line in lines),*([tell('[Add cow]','add_cow')] if name=='cow_room' else []),tell('[All controls]','controls'),'return 1'])
    result['add_cow']='\n'.join([GUARD,'execute if score #cow paircheck matches 1 run return 0',
        'execute unless entity @s[x=59,y=65,z=-3,dx=10,dy=3,dz=6] run return 0',
        'summon minecraft:cow 62.5 65 1.5 {Tags:["power_air_check"],Invulnerable:1b,NoAI:1b,PersistenceRequired:1b}',
        'scoreboard players set #cow paircheck 1',tell('Added one adult cow (+5 demand). No achievement is granted by this button; the real oxygen allocator decides.'),'return 1'])
    progress=[GUARD,tell('ACTUAL SAVED ADVANCEMENT PROGRESS')]
    for name in ADVANCEMENTS:
        selector='@s[advancements={frozendawn:'+name+'=true}]'
        progress += ['execute if entity '+selector+' run '+tell(name+': DONE'),
            'execute unless entity '+selector+' run '+tell(name+': not earned')]
    result['progress']='\n'.join(progress+[tell('[All controls]','controls'),'return 1'])
    return result

def enable_four(destination):
    pack=destination/'datapacks'/PACK
    # Lower packs remain completely filtered. Re-introduce exact production
    # definitions here, with one silent parent needed for their actual tree.
    for name in ADVANCEMENTS:
        source=ROOT/f'src/main/resources/data/frozendawn/advancement/{name}.json'
        target=pack/f'data/frozendawn/advancement/{name}.json'
        target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
    root={'display':{'icon':{'id':'frozendawn:orsa_emergency_light'},
        'title':{'text':'Power & Air Check'},'description':{'text':'Local test parent'},
        'background':'minecraft:textures/gui/advancements/backgrounds/stone.png',
        'show_toast':False,'announce_to_chat':False,'hidden':True},
        'criteria':{'impossible':{'trigger':'minecraft:impossible'}}}
    (pack/'data/frozendawn/advancement/phase6.json').write_text(json.dumps(root,indent=2)+'\n')
    path=destination/'level.dat';data=io.BytesIO(gzip.decompress(path.read_bytes()))
    assert data.read(1)==b'\x0a';nbt.string(data);compound=nbt.read(data,10)
    compound['Data'][1]['GameRules'][1]['announceAdvancements']=(8,'true')
    path.write_bytes(gzip.compress(b'\x0a'+nbt.utf('')+nbt.write(10,compound)))
    p=destination/'preparation.json';meta=json.loads(p.read_text())
    meta['advancements']='Only four exact production Power/Air definitions plus silent phase6 parent; other advancement definitions filtered locally.'
    meta['automatic_awards']='none; only normal gameplay server hooks'
    meta['source_advancement_sha256']={name:hashlib.sha256((pack/f'data/frozendawn/advancement/{name}.json').read_bytes()).hexdigest() for name in ADVANCEMENTS}
    p.write_text(json.dumps(meta,indent=2)+'\n')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',type=Path,required=True);parser.add_argument('--destination',type=Path,required=True)
    args=parser.parse_args()
    prepare(args.source,args.destination,title=TITLE,namespace=NS,pack=PACK,build_scripts=scripts,surface=64)
    enable_four(args.destination)

    # Pause is an idempotent local profile setting, never a toggle in setup.
    if args.destination.resolve().parent==(ROOT/'run-lab/saves').resolve():
        config=ROOT/'run-lab/config/frozendawn-common.toml';config.parent.mkdir(parents=True,exist_ok=True)
        if config.exists():
            text=config.read_text();assert text.count('pauseProgression = ')==1
            config.write_text(text.replace('pauseProgression = false','pauseProgression = true'))
        else:config.write_text('[general]\npauseProgression = true\n')
