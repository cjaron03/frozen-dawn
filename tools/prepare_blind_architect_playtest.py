#!/usr/bin/env python3
"""Create a fresh lab save from generator metadata only; never copy gameplay history."""
import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import struct
import time

ROOT = Path(__file__).resolve().parents[1]
TITLE = 'Blind Architect Lab'
CASES = {
    'first': (TITLE, 'blind_lab', 90),
    'appearance': ('Blind Architect 01 Appearance', 'blind_appearance', 60),
    'audio': ('Blind Architect 02 Idle Audio', 'blind_audio', 30),
    'echo_open': ('Blind Architect 03 Open Echo', 'blind_echo_open', 30),
    'echo_sealed': ('Blind Architect 04 Sealed Echo', 'blind_echo_sealed', 30),
    'echo_wool': ('Blind Architect 05 Wool Echo', 'blind_echo_wool', 30),
    'vibration_connected': ('Blind Architect 08 Connected Supports', 'blind_vibration_connected', 24),
    'vibration_lab': ('Blind Architect Reusable Lab', 'blind_vibration_lab', 24),
    'vibration_disconnected': ('Blind Architect 09 Disconnected Supports', 'blind_vibration_disconnected', 24),
}


def string(stream):
    return stream.read(struct.unpack('>H', stream.read(2))[0]).decode('utf-8')


def read(stream, kind):
    if kind in range(1, 7):
        fmt = '>' + {1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind]
        return struct.unpack(fmt, stream.read(struct.calcsize(fmt)))[0]
    if kind == 8:
        return string(stream)
    if kind == 10:
        fields = {}
        while True:
            tag = stream.read(1)[0]
            if tag == 0:
                return fields
            name = string(stream)
            fields[name] = (tag, read(stream, tag))
    if kind == 9:
        element = stream.read(1)[0]
        count = struct.unpack('>i', stream.read(4))[0]
        return element, [read(stream, element) for _ in range(count)]
    if kind in (7, 11, 12):
        count = struct.unpack('>i', stream.read(4))[0]
        width = {7:1,11:4,12:8}[kind]
        return count, stream.read(count * width)
    raise ValueError(f'Unsupported NBT tag {kind}')


def utf(value):
    data = value.encode('utf-8')
    return struct.pack('>H', len(data)) + data


def write(kind, value):
    if kind in range(1, 7):
        return struct.pack('>' + {1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind], value)
    if kind == 8:
        return utf(value)
    if kind == 10:
        return b''.join(bytes([tag])+utf(name)+write(tag,payload) for name,(tag,payload) in value.items())+b'\0'
    if kind == 9:
        element, items = value
        return bytes([element])+struct.pack('>i',len(items))+b''.join(write(element,item) for item in items)
    if kind in (7, 11, 12):
        count, data = value
        return struct.pack('>i',count)+data
    raise ValueError(f'Unsupported NBT tag {kind}')


def tell(text, command=None):
    value = {'text':text, 'color':'aqua'}
    if command:
        value['clickEvent']={'action':'run_command','value':command}
    return 'tellraw @s ' + json.dumps(value)


def pack_metadata(title):
    # Only this generated world's datapack hides advancement definitions. This
    # prevents both toast and chat noise, including equipment/phase awards.
    return {'pack': {'pack_format': 48, 'description': title},
            'filter': {'block': [{'path': 'advancement/.*'}]}}


def write_sync_advancement(pack):
    # Patchouli loads books on the first advancement packet, and vanilla sends
    # none when nothing is visible, which leaves the Field Manual empty. The
    # filter only blocks lower packs, so this silent self-granted one survives.
    target = pack/'data/fd_test_sync/advancement/synced.json'
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps({
        'display': {'icon': {'id': 'minecraft:book'}, 'title': 'Test world sync',
                    'description': 'Lets the Field Manual load in this test world.',
                    'background': 'minecraft:textures/gui/advancements/backgrounds/stone.png',
                    'show_toast': False, 'announce_to_chat': False},
        'criteria': {'tick': {'trigger': 'minecraft:tick'}}}, indent=2)+'\n')


def lab_state(data_version):
    # Establish a fresh fixed default before the first player joins, so the
    # normal one-time difficulty picker has no outstanding choice to request.
    state = {'apocalypseTicks': (4, 0), 'initialized': (1, 1),
             'presetName': (8, 'default'), 'difficultyLocked': (1, 1),
             'blocksFrozen': (4, 0)}
    return b'\x0a'+utf('')+write(10, {'data': (10, state), 'DataVersion': (3, data_version)})


SCRIPTS = {
    'load': '''scoreboard objectives add blind_lab dummy
scoreboard objectives add blind_deaths deathCount
execute unless score #stage blind_lab matches 0.. run scoreboard players set #stage blind_lab 0''',
    'tick': '''execute if score #stage blind_lab matches 0 as @a[gamemode=creative,limit=1] run function blind_lab:prepare
execute if score #stage blind_lab matches 2 run scoreboard players add #timer blind_lab 1
execute if score #stage blind_lab matches 2 if score #timer blind_lab matches 1800.. as @a[limit=1] run function blind_lab:finish
execute if score #stage blind_lab matches 2 as @a[scores={blind_deaths=1..},limit=1] run function blind_lab:finish''',
    'prepare': '''execute store result score #access blind_lab run fdlab blind_access
execute unless score #access blind_lab matches 1 run return 0
execute unless score #stage blind_lab matches 0 run return 0
execute unless entity @s[gamemode=creative] run return 0
execute store result score #loaded blind_lab run fdlab blind_load
execute unless score #loaded blind_lab matches 1 run return 0
scoreboard players set #stage blind_lab 9
fill 0 -45 0 30 -40 10 minecraft:bedrock
fill 1 -45 1 29 -45 9 minecraft:stone
fill 1 -44 1 29 -41 9 minecraft:air
gamerule doMobSpawning false
gamerule keepInventory true
difficulty normal
fd world set phase 6 late
item replace entity @s armor.head with frozendawn:eva_helmet
item replace entity @s armor.chest with frozendawn:eva_chestplate
item replace entity @s armor.legs with frozendawn:eva_leggings
item replace entity @s armor.feet with frozendawn:eva_boots
item replace entity @s inventory.0 with frozendawn:o2_tank_mk3
item replace entity @s hotbar.0 with minecraft:iron_sword
item replace entity @s hotbar.1 with minecraft:iron_pickaxe
item replace entity @s hotbar.2 with minecraft:stone 64
item replace entity @s hotbar.3 with minecraft:snowball 16
item replace entity @s hotbar.4 with minecraft:bell
item replace entity @s hotbar.5 with minecraft:cooked_beef 32
item replace entity @s hotbar.6 with minecraft:white_wool 64
spawnpoint @s 25 -44 5
tp @s 25.5 -44 5.5 90 0
tag @s add blind_lab_player
summon frozendawn:architect 7.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,PersistenceRequired:1b,Tags:["blind_lab_actor"]}
fd architect record @e[tag=blind_lab_actor,limit=1] 1337
scoreboard players set #stage blind_lab 1
''' + tell('Ready: enclosed underground chamber, supplied EVA armor/O2 and tools. Natural mobs are disabled. Start enables normal Survival damage and Blind AI for 90 seconds. First describe what you heard and saw.', '/function blind_lab:start'),
    'start': '''execute store result score #access blind_lab run fdlab blind_access
execute unless score #access blind_lab matches 1 run return 0
execute unless score #stage blind_lab matches 1 run return 0
execute unless entity @s[tag=blind_lab_player,gamemode=creative] run return 0
execute unless entity @e[tag=blind_lab_actor,nbt={BlindArchitect:1b,NoAI:1b},limit=1] run return 0
scoreboard players set #stage blind_lab 2
scoreboard players set #timer blind_lab 0
scoreboard players set @s blind_deaths 0
gamemode survival @s
data merge entity @e[tag=blind_lab_actor,limit=1] {NoAI:0b}
''' + tell('Encounter started. You can move and use the supplied tools. The view ends after 90 seconds or death.', '/function blind_lab:finish'),
    'finish': '''execute store result score #access blind_lab run fdlab blind_access
execute unless score #access blind_lab matches 1 run return 0
execute unless score #stage blind_lab matches 2 run return 0
execute unless entity @s[tag=blind_lab_player] run return 0
scoreboard players set #stage blind_lab 3
data merge entity @e[tag=blind_lab_actor,limit=1] {NoAI:1b,Motion:[0.0d,0.0d,0.0d]}
fd architect stop @e[tag=blind_lab_actor,limit=1]
gamemode creative @s
''' + tell('View ended. The actor and world are preserved. Describe what happened before looking at diagnostics. This view cannot be restarted; use another fresh world copy for comparison.'),
}


def scripts_for(case):
    title, namespace, seconds = CASES[case]
    if case == 'vibration_lab':
        return workbench_scripts()
    if case.startswith('vibration_'):
        return vibration_scripts(case)
    if case == 'first':
        return SCRIPTS
    scripts = {name:script.replace('blind_lab:', namespace+':')
               .replace('fdlab blind_access', 'fdlab blind_access '+case)
               .replace('fdlab blind_load', 'fdlab blind_load '+case)
               for name,script in SCRIPTS.items()}
    scripts['tick'] = scripts['tick'].replace('1800..', str(seconds*20)+'..')
    # Remove the mixed first-view tool kit: sensing views have no decoy,
    # bell, sword or mining action to obscure the single owner instruction.
    lines = [line for line in scripts['prepare'].splitlines()
             if not line.startswith('item replace entity @s hotbar.') and not line.startswith('tellraw ')]
    extra = []
    if case == 'appearance':
        lines = [line for line in lines if not line.startswith('fd architect record ')]
        extra = ['setblock 8 -45 4 minecraft:sea_lantern', 'setblock 13 -45 4 minecraft:sea_lantern',
                 'data merge entity @e[tag=blind_lab_actor,limit=1] {Rotation:[180f,0f]}',
                 'summon frozendawn:architect 12.5 -44 5.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["blind_lab_comparison"]}',
                 'tp @s 10.5 -44 2.5 0 0']
        action = 'Look around both stationary actors. Judge the Blind leather helmet, face and silhouette. No combat or sound test.'
        scripts['start'] = scripts['start'].replace('gamemode survival @s\n', '').replace('data merge entity @e[tag=blind_lab_actor,limit=1] {NoAI:0b}\n', '')
        scripts['finish'] = scripts['finish'].replace('fd architect stop @e[tag=blind_lab_actor,limit=1]\n', '')
    elif case == 'audio':
        action = 'Stay Creative, move/turn to listen to idle clicks. No player detection or combat is measured.'
        scripts['start'] = scripts['start'].replace('gamemode survival @s\n', '')
    else:
        # The same protected two-high barrier prevents melee/breaching in all
        # sensing comparisons. Its upper two-block air gap carries echoes.
        extra = ['fill 16 -44 1 16 -43 9 minecraft:bedrock',
                 'data merge entity @e[tag=blind_lab_actor,limit=1] {Pos:[9.5d,-44d,5.5d]}',
                 'tp @s 23.5 -44 5.5 90 0']
        if case == 'echo_sealed':
            extra.append('fill 16 -42 1 16 -41 9 minecraft:bedrock')
        if case == 'echo_wool':
            extra.append('fill 1 -45 1 29 -45 9 minecraft:white_wool')
        action = 'Stay on the starting square without walking, mining or using items. Observe clicks and approach for 30 seconds.'
    stage = lines.index('scoreboard players set #stage blind_lab 1')
    lines[stage:stage] = extra
    scripts['prepare'] = '\n'.join(lines)+'\n'+tell(title+'. '+action+' Click to begin.', '/function '+namespace+':start')
    scripts['start'] = '\n'.join(line for line in scripts['start'].splitlines() if not line.startswith('tellraw '))+'\n'+tell(
        action+' Click to finish early.', '/function '+namespace+':finish')
    return scripts


def vibration_layout(case):
    layout = [
        ([0,-49,-12],[30,-38,10],'minecraft:bedrock'),
        ([1,-48,-11],[29,-39,9],'minecraft:air'),
        ([8,-45,4],[10,-45,6],'minecraft:stone'),
        ([20,-45,4],[22,-45,6],'minecraft:stone'),
        ([11,-45,5],[19,-45,5],'minecraft:stone'),
        ([14,-45,-10],[16,-45,-8],'minecraft:glass'),
        ([9,-40,5],[9,-40,5],'minecraft:black_concrete'),
        ([21,-40,5],[21,-40,5],'minecraft:white_concrete'),
        ([15,-39,-9],[15,-39,-9],'minecraft:sea_lantern'),
    ]
    if case == 'vibration_disconnected':
        layout.append(([15,-45,5],[15,-45,5],'minecraft:air'))
    return [{'from':a,'to':b,'block':block} for a,b,block in layout]


def vibration_scripts(case):
    title, namespace, seconds = CASES[case]
    scripts = {name:script.replace('blind_lab:',namespace+':')
               .replace('fdlab blind_access','fdlab blind_access '+case)
               .replace('fdlab blind_load','fdlab blind_load '+case)
               for name,script in SCRIPTS.items()}
    lines = [line for line in scripts['prepare'].splitlines()
             if not line.startswith(('fill ','tp ','summon ','fd architect record ',
                                     'item replace entity @s hotbar.','tellraw '))]
    insertion = lines.index('gamerule doMobSpawning false')
    terrain = ['fill '+' '.join(map(str,row['from']+row['to']))+' '+row['block'] for row in vibration_layout(case)]
    lines[insertion:insertion] = terrain
    stage = lines.index('scoreboard players set #stage blind_lab 1')
    # A held NoAI actor does not settle through movement. Initialize its resting
    # state as well as its position; the production solver still checks actual
    # collision contact and rejects floating feet even when OnGround is true.
    lines[stage:stage] = ['tp @s 15.5 -44 -8.5 0 0',
        'summon frozendawn:architect 9.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["blind_lab_actor"]}']
    for x, label, color in [(21.5, 'SOURCE\nWhite flash = a strike', 'white'),
                            (9.5, 'BA SENSOR\nGreen = felt the strike\nBlack = no signal\nOrange = test issue', 'aqua')]:
        text = json.dumps({'text': label, 'color': color})
        lines.append('summon minecraft:text_display '+str(x)+' -39.3 5.5 '+
                     '{billboard:"center",alignment:"center",line_width:180,shadow:1b,'+
                     'background:1073741824,brightness:{block:15,sky:15},Tags:["blind_lab_label"],text:'+json.dumps(text)+'}')
    lines += ['title @s times 10 80 20',
              'title @s subtitle '+json.dumps({'text': 'Stay on the glass. The BA stays still.', 'color': 'white'}),
              'title @s title '+json.dumps({'text': 'VIBRATION TEST', 'color': 'aqua'}),
              tell('Ready: '+title+'. Watch SOURCE flash, then read BA SENSOR. We will strike the source three times and measure whether the BA feels it.'),
              tell('Stay on the glass. You do not need to walk, mine or provoke the BA. It stays still in this test.'),
              tell('Press T and click here: [START TEST]. Watch three source flashes and read each result.', '/function '+namespace+':start')]
    scripts['prepare']='\n'.join(lines)
    scripts['start']='\n'.join(line for line in scripts['start'].splitlines()
        if not line.startswith(('gamemode ','data merge ','tellraw ')))
    for holder,value in [('#last_impulse',0),('#samples',0),('#detected',0),('#inconclusive',0),('#outcome',-1),('#strength_milli',0),('#work',0)]:
        scripts['start']+='\nscoreboard players set '+holder+' blind_lab '+str(value)
    scripts['start']+='\ntitle @s clear\n'+tell('Stay on the glass for 24 seconds. The BA stays still. Watch three white source flashes: green receiver = vibration received, black = none, orange = inconclusive. Each pulse also prints its result.')
    scripts['tick']=scripts['tick'].replace('1800..','480..')
    for pulse, tick in enumerate((100,220,340), start=1):
        for remaining in (3, 2, 1):
            text = json.dumps({'text': 'Strike '+str(pulse)+'/3 in '+str(remaining)+'... Watch SOURCE, then BA SENSOR.', 'color': 'yellow'})
            scripts['tick']+='\nexecute if score #stage blind_lab matches 2 if score #timer blind_lab matches '+str(tick-20*remaining)+' as @a[tag=blind_lab_player,limit=1] run title @s actionbar '+text
        scripts['tick']+='\nexecute if score #stage blind_lab matches 2 if score #timer blind_lab matches '+str(tick)+' as @a[tag=blind_lab_player,limit=1] run function '+namespace+':impulse'
    for tick in (140,260,380):
        for xyz,block in [('21 -40 5','minecraft:white_concrete'),('9 -40 5','minecraft:black_concrete')]:
            scripts['tick']+='\nexecute if score #stage blind_lab matches 2 if score #timer blind_lab matches '+str(tick)+' run setblock '+xyz+' '+block
    scripts['impulse']='\n'.join([
        'execute store result score #access blind_lab run fdlab blind_access '+case,
        'execute unless score #access blind_lab matches 1 run return 0',
        'execute unless score #stage blind_lab matches 2 run return 0',
        'execute unless entity @s[tag=blind_lab_player,gamemode=creative] run return 0',
        'fdlab blind_vibration'])
    scripts['finish']='\n'.join(line for line in scripts['finish'].splitlines()
        if not line.startswith(('fd architect stop ','tellraw ')))
    scripts['finish']+='\ntitle @s subtitle '+json.dumps({'text': 'Tell me what the three BA sensor messages said.', 'color': 'white'})
    scripts['finish']+='\ntitle @s title '+json.dumps({'text': 'VIEW COMPLETE', 'color': 'aqua'})
    scripts['finish']+='\n'+tell('Finished. Describe the source flashes and three BA sensor messages. The BA was meant to stay still. Pause here; this world cannot restart.')
    return scripts


def workbench_scripts():
    namespace = 'blind_vibration_lab'
    access = ['execute unless function blind_vibration_lab:access run return 0',
              'execute unless entity @s[gamemode=creative] run return 0']
    participant = access + ['execute unless entity @s[tag=blind_lab_player] run return 0']
    scripts = {
        'access': 'return run fdlab workbench_access',
        'load': 'scoreboard objectives add blind_vib dummy\nexecute unless score #stage blind_vib matches 0.. run scoreboard players set #stage blind_vib 0',
        'prepare': '\n'.join(access + [
            'execute unless score #stage blind_vib matches 0 run return 0',
            'execute store result score #loaded blind_vib run fdlab blind_load vibration_lab',
            'execute unless score #loaded blind_vib matches 1 run return 0',
            'tag @s add blind_lab_player',
            'scoreboard players set #stage blind_vib 9',
            'scoreboard players set #run blind_vib 1',
            'scoreboard players set #case blind_vib 1',
            'return run function '+namespace+':scene']),
    }
    terrain = ['fill '+' '.join(map(str,row['from']+row['to']))+' '+row['block'] for row in vibration_layout('vibration_lab')]
    lines = participant + [
        'execute unless score #stage blind_vib matches 9 run return 0',
        'execute unless score #case blind_vib matches 1..3 run return 0',
        'execute store result score #cleared blind_vib run fdlab workbench_clear',
        'execute unless score #cleared blind_vib matches 1 run return 0'] + terrain + [
        'execute if score #case blind_vib matches 2 run setblock 21 -44 5 minecraft:white_carpet',
        'gamerule doMobSpawning false', 'gamerule announceAdvancements false',
        'gamerule keepInventory true', 'difficulty normal', 'fd world set phase 6 late',
        'tp @s 15.5 -44 -8.5 0 0',
        'execute unless score #case blind_vib matches 3 run summon frozendawn:architect 9.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["blind_lab_actor"]}',
        'execute if score #case blind_vib matches 3 run summon frozendawn:architect 9.5 -43.5 5.5 {BlindArchitect:1b,NoAI:1b,NoGravity:1b,OnGround:0b,PersistenceRequired:1b,Rotation:[180f,0f],Tags:["blind_lab_actor"]}',
    ]
    for x, label, color in [(21.5, 'SOURCE\nWhite flash = footstep', 'white'),
                            (9.5, 'BA SENSOR\nGreen = received\nBlack = no reception\nOrange = test issue', 'aqua')]:
        text = json.dumps({'text':label, 'color':color})
        lines.append('summon minecraft:text_display '+str(x)+' -39.3 5.5 '+
                     '{billboard:"center",alignment:"center",line_width:180,shadow:1b,background:1073741824,'+
                     'brightness:{block:15,sky:15},Tags:["blind_lab_label"],text:'+json.dumps(text)+'}')
    for holder in ('#timer','#last_impulse','#samples','#detected','#inconclusive','#archived'):
        lines.append('scoreboard players set '+holder+' blind_vib 0')
    lines += ['scoreboard players set #stage blind_vib 1', 'title @s times 10 80 20',
              'execute if score #case blind_vib matches 1 run title @s title '+json.dumps({'text':'BARE STONE','color':'aqua'}),
              'execute if score #case blind_vib matches 2 run title @s title '+json.dumps({'text':'WOOL CARPET','color':'aqua'}),
              'execute if score #case blind_vib matches 3 run title @s title '+json.dumps({'text':'BA OFF THE FLOOR','color':'aqua'}),
              'execute if score #case blind_vib matches 3 run '+tell('The BA is held half a block above its support. The source and stone path match the completed bare-stone control.'),
              tell('Ready. Stay on the glass. Three equal footstep impulses will test the surface. The BA stays still; no walking animation is used.'),
              tell('Pause and tell the operator you are ready, or press T and click [START TEST].', '/function '+namespace+':start'),
              'return 1']
    scripts['scene']='\n'.join(lines)
    scripts['start']='\n'.join(['execute if score #case blind_vib matches 36..53 run return run function '+namespace+':decoy_start', 'execute if score #case blind_vib matches 34..35 run return run function '+namespace+':channels_start'] + participant + [
        'execute if score #case blind_vib matches 20..35 run return run function '+namespace+':channels_start',
        'execute if score #case blind_vib matches 18..19 run return run function '+namespace+':cave_start',
        'execute if score #case blind_vib matches 12..17 run return run function '+namespace+':channels_start',
        'execute if score #case blind_vib matches 11 run return run function '+namespace+':behavior_base_start',
        'execute if score #case blind_vib matches 4..10 run return run function '+namespace+':behavior_start',
        'execute unless score #stage blind_vib matches 1 run return 0',
        'execute store result score #valid blind_vib run fdlab workbench_check',
        'execute unless score #valid blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 2', 'scoreboard players set #timer blind_vib 0',
        'title @s clear', tell('Watch the three source flashes and read each BA sensor message. This view lasts 24 seconds.'), 'return 1'])
    scripts['impulse']='\n'.join(participant+['execute unless score #stage blind_vib matches 2 run return 0','return run fdlab workbench_sample'])
    scripts['finish']='\n'.join(['execute if score #case blind_vib matches 4..10 run return run function '+namespace+':behavior_finish']+participant+[
        'execute unless score #stage blind_vib matches 2 run return 0',
        'scoreboard players set #stage blind_vib 3',
        'execute store result score #archive_result blind_vib run fdlab workbench_archive',
        'title @s subtitle '+json.dumps({'text':'Describe the three sensor messages, then pause.','color':'white'}),
        'title @s title '+json.dumps({'text':'VIEW COMPLETE','color':'aqua'}),
        'execute if score #case blind_vib matches 1 run '+tell('Finished. Describe what you saw. Stay in this world; the operator will prepare the next surface after recording your report.'),
        'execute if score #case blind_vib matches 2 run '+tell('Both surfaces are complete. Describe what you saw, then pause. The results are preserved.'),
        'execute if score #case blind_vib matches 3 run '+tell('Receiver-contact view complete. Describe the three sensor messages, then pause. The results are preserved.'),
        'return 1'])
    scripts['next']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 1 run return 0',
        'execute unless score #case blind_vib matches 1 run return 0',
        'execute unless score #timer blind_vib matches 480 run return 0',
        'execute unless score #samples blind_vib matches 3 run return 0',
        'execute unless score #inconclusive blind_vib matches 0 run return 0',
        'execute store result score #preserved blind_vib run fdlab workbench_archived',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players add #run blind_vib 1', 'scoreboard players set #case blind_vib 2',
        'return run function '+namespace+':scene'])
    scripts['receiver'] = scripts['next'].replace('#run blind_vib matches 1', '#run blind_vib matches 2').replace('#case blind_vib matches 1', '#case blind_vib matches 2').replace('set #case blind_vib 2', 'set #case blind_vib 3')
    tick = ['execute if score #stage blind_vib matches 0 as @a[gamemode=creative,limit=1] run function '+namespace+':prepare',
            'execute if score #stage blind_vib matches 2 run scoreboard players add #timer blind_vib 1',
            'execute if score #stage blind_vib matches 2 if score #timer blind_vib matches 480.. as @a[tag=blind_lab_player,limit=1] run function '+namespace+':finish']
    for pulse, at in enumerate((100,220,340),start=1):
        for remaining in (3,2,1):
            text=json.dumps({'text':'Footstep '+str(pulse)+'/3 in '+str(remaining)+'... Watch SOURCE, then BA SENSOR.','color':'yellow'})
            tick.append('execute if score #stage blind_vib matches 2 if score #timer blind_vib matches '+str(at-20*remaining)+' as @a[tag=blind_lab_player,limit=1] run title @s actionbar '+text)
        tick.append('execute if score #stage blind_vib matches 2 if score #timer blind_vib matches '+str(at)+' as @a[tag=blind_lab_player,limit=1] run function '+namespace+':impulse')
        for xyz,block in [('21 -40 5','minecraft:white_concrete'),('9 -40 5','minecraft:black_concrete')]:
            tick.append('execute if score #stage blind_vib matches 2 if score #timer blind_vib matches '+str(at+40)+' run setblock '+xyz+' '+block)
    scripts['tick']='\n'.join(['execute if score #case blind_vib matches 36..53 as @a[tag=blind_lab_player,limit=1] run function '+namespace+':decoy_tick', 'execute if score #case blind_vib matches 36..53 run return 1', 'execute if score #case blind_vib matches 20..35 as @a[tag=blind_lab_player,limit=1] run function '+namespace+':channels_tick',
                             'execute if score #case blind_vib matches 20..35 run return 1',
                             'execute if score #case blind_vib matches 18..19 as @a[tag=blind_lab_player,limit=1] run function '+namespace+':cave_tick',
                             'execute if score #case blind_vib matches 18..19 run return 1',
                             'execute if score #case blind_vib matches 12..17 as @a[tag=blind_lab_player,limit=1] run function '+namespace+':channels_tick',
                             'execute if score #case blind_vib matches 12..17 run return 1',
                             'execute if score #case blind_vib matches 11 as @a[tag=blind_lab_player,limit=1] run function '+namespace+':behavior_base_tick',
                             'execute if score #case blind_vib matches 11 run return 1',
                             'execute if score #case blind_vib matches 4..10 as @a[tag=blind_lab_player,limit=1] run function '+namespace+':behavior_tick',
                             'execute if score #case blind_vib matches 4..10 run return 1']+tick)
    scripts.update(behavior_scripts(participant, scripts['next']))
    scripts.update(base_scripts(participant))
    scripts.update(channel_scripts(participant))
    scripts.update(cave_scripts(participant))
    scripts.update(decoy_scripts(participant))
    return scripts



def decoy_scripts(participant):
    ns='blind_vibration_lab'
    live=['execute unless function '+ns+':access run return 0','execute unless entity @s[tag=blind_lab_player] run return 0',
          'execute unless score #run blind_vib matches 36..53 run return 0','execute unless score #case blind_vib matches 36..53 run return 0']
    scripts={'projectile_decoy':'\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 35 run return 0',
        'execute unless score #case blind_vib matches 35 run return 0','execute store result score #preserved blind_vib run fdlab decoy_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 36','scoreboard players set #case blind_vib 36','return run function '+ns+':decoy_scene'])}
    scripts['projectile_decoy_repeat']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 36 run return 0',
        'execute unless score #case blind_vib matches 36 run return 0','execute store result score #preserved blind_vib run fdlab decoy_repeat_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 37','scoreboard players set #case blind_vib 37','return run function '+ns+':decoy_scene'])
    scripts['projectile_decoy_contact']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 37 run return 0',
        'execute unless score #case blind_vib matches 37 run return 0','execute store result score #preserved blind_vib run fdlab decoy_contact_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 38','scoreboard players set #case blind_vib 38','return run function '+ns+':decoy_scene'])
    scripts['door_hearing']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 38 run return 0',
        'execute unless score #case blind_vib matches 38 run return 0','execute store result score #preserved blind_vib run fdlab door_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 39','scoreboard players set #case blind_vib 39','return run function '+ns+':decoy_scene'])
    scripts['door_hearing_repeat']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 39 run return 0',
        'execute unless score #case blind_vib matches 39 run return 0','execute store result score #preserved blind_vib run fdlab door_repeat_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 40','scoreboard players set #case blind_vib 40','return run function '+ns+':decoy_scene'])
    scripts['machine_hearing']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 40 run return 0',
        'execute unless score #case blind_vib matches 40 run return 0','execute store result score #preserved blind_vib run fdlab machine_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 41','scoreboard players set #case blind_vib 41','return run function '+ns+':decoy_scene'])
    scripts['door_hearing_contact']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 41 run return 0',
        'execute unless score #case blind_vib matches 41 run return 0','execute store result score #preserved blind_vib run fdlab door_contact_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 42','scoreboard players set #case blind_vib 42','return run function '+ns+':decoy_scene'])
    scripts['door_hearing_contact_repeat']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 42 run return 0',
        'execute unless score #case blind_vib matches 42 run return 0','execute store result score #preserved blind_vib run fdlab door_contact_repeat_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 43','scoreboard players set #case blind_vib 43','return run function '+ns+':decoy_scene'])
    scripts['bell_reaction']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 43 run return 0','execute unless score #case blind_vib matches 43 run return 0','execute store result score #preserved blind_vib run fdlab bell_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 44','scoreboard players set #case blind_vib 44','return run function '+ns+':decoy_scene'])
    scripts['explosion_reaction']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 44 run return 0','execute unless score #case blind_vib matches 44 run return 0','execute store result score #preserved blind_vib run fdlab explosion_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 45','scoreboard players set #case blind_vib 45','return run function '+ns+':decoy_scene'])
    scripts['heat_reaction']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 45 run return 0','execute unless score #case blind_vib matches 45 run return 0','execute store result score #preserved blind_vib run fdlab heat_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 46','scoreboard players set #case blind_vib 46','return run function '+ns+':decoy_scene'])
    scene=participant+live+['execute unless score #stage blind_vib matches 9 run return 0',
        'execute store result score #clean blind_vib run fdlab behavior_isolate','execute unless score #clean blind_vib matches 1 run return 0',
        'execute store result score #cleared blind_vib run fdlab workbench_clear','execute unless score #cleared blind_vib matches 1 run return 0',
        'fill 1 -48 -11 29 -38 9 minecraft:air',
        'fill 3 -45 3 20 -40 7 minecraft:glass','fill 4 -44 4 19 -41 6 minecraft:air',
        'fill 4 -45 4 14 -45 6 minecraft:stone','fill 17 -45 4 19 -45 6 minecraft:stone','fill 15 -45 4 16 -45 6 minecraft:air',
        'fill 15 -44 4 15 -43 6 minecraft:glass',
        'execute if score #run blind_vib matches 40..43 run fill 3 -45 3 12 -40 7 minecraft:glass',
        'execute if score #run blind_vib matches 38 run fill 15 -44 4 15 -43 6 minecraft:air',
        'execute if score #run blind_vib matches 42..43 run fill 15 -44 4 15 -43 6 minecraft:air',
        'execute if score #run blind_vib matches 36 run setblock 9 -40 5 minecraft:sea_lantern',
        'execute if score #run blind_vib matches 37..43 run setblock 11 -40 5 minecraft:sea_lantern',
        'gamerule mobGriefing false','gamerule doMobSpawning false','gamerule announceAdvancements false','gamerule keepInventory true',
        'clear @s','effect clear @s','tp @s 17.5 -44 5.5 90 -20',
        'item replace entity @s armor.head with frozendawn:eva_helmet','item replace entity @s armor.chest with frozendawn:eva_chestplate',
        'item replace entity @s armor.legs with frozendawn:eva_leggings','item replace entity @s armor.feet with frozendawn:eva_boots',
        'execute if score #run blind_vib matches 36..38 run item replace entity @s hotbar.0 with minecraft:snowball 1',
        'execute if score #run blind_vib matches 39..40 run setblock 18 -44 5 minecraft:oak_door[facing=west,half=lower,hinge=left,open=false,powered=false]',
        'execute if score #run blind_vib matches 39..40 run setblock 18 -43 5 minecraft:oak_door[facing=west,half=upper,hinge=left,open=false,powered=false]',
        'execute if score #run blind_vib matches 39..40 run tp @s 17.5 -44 5.5 -90 8',
        'item replace entity @s hotbar.1 with frozendawn:o2_tank_mk3[frozendawn:o2_level=24000]',
        'data modify entity @s SelectedItemSlot set value 0',
        'execute unless score #run blind_vib matches 40..46 run summon frozendawn:architect 9.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[270f,0f],Tags:["blind_lab_actor"]}']
    scene.append('execute if score #run blind_vib matches 40..43 run summon frozendawn:architect 13.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[270f,0f],Tags:["blind_lab_actor"]}')
    scene += ['execute if score #run blind_vib matches 41 run fill 16 -45 3 16 -40 7 minecraft:air', 'execute if score #run blind_vib matches 41 run setblock 18 -45 5 minecraft:air', 'execute if score #run blind_vib matches 41 run setblock 19 -45 4 minecraft:air', 'execute if score #run blind_vib matches 41 run setblock 19 -45 6 minecraft:air', 'execute if score #run blind_vib matches 41 run fill 20 -45 5 20 -44 5 minecraft:air', 'execute if score #run blind_vib matches 41 run setblock 19 -44 5 frozendawn:thermal_heater[lit=false,glow_stage=0]', 'execute if score #run blind_vib matches 41 run tp @s 17.5 -44 5.5 90 8']
    scene += ['execute if score #run blind_vib matches 42..43 run setblock 18 -44 5 minecraft:oak_door[facing=west,half=lower,hinge=left,open=false,powered=false]', 'execute if score #run blind_vib matches 42..43 run setblock 18 -43 5 minecraft:oak_door[facing=west,half=upper,hinge=left,open=false,powered=false]', 'execute if score #run blind_vib matches 42..43 run tp @s 17.5 -44 5.5 -90 8']
    scene += ['execute if score #run blind_vib matches 44..46 run fill 3 -45 3 9 -40 7 minecraft:glass','execute if score #run blind_vib matches 44..46 run setblock 11 -40 5 minecraft:sea_lantern','execute if score #run blind_vib matches 44 run setblock 14 -44 5 minecraft:bell[attachment=floor,facing=west,powered=false]','execute if score #run blind_vib matches 44..46 run summon frozendawn:architect 13.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[270f,0f],Tags:["blind_lab_actor"]}']
    # SelectedItemSlot is set by the guarded lab inventory helper, since player /data writes are refused.
    scene.remove('data modify entity @s SelectedItemSlot set value 0')
    for holder in ['#timer','#frames','#last_frame','#archived','#inconclusive','#decoy_balls','#decoy_impacts','#decoy_cover']:scene.append('scoreboard players set '+holder+' blind_vib 0')
    scene+=['execute store result score #equipped blind_vib run fdlab decoy_equip','execute unless score #equipped blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 1','execute if score #run blind_vib matches 36..38 run '+tell('PROJECTILE DECOY: pause for the operator. Start arms GROUND CHECK. Use slot1. At THROW aim at the ceiling light over the receiver, then right click once; stay still until VIEW COMPLETE.'),'execute if score #run blind_vib matches 39..40 run '+tell('DOOR VIEW: pause for the operator. Slot1 is empty. At OPEN right click the door once and release; at CLOSE right click it once and release. Stay still through VIEW COMPLETE.'),'execute if score #run blind_vib matches 41 run '+tell('MACHINE VIEW: pause for the operator. Stay still and watch; the heater will run automatically at MACHINE ON and stop at MACHINE OFF. Mouse turning is allowed. Do not click or use items.'),'return 1']
    scene.insert(-1,'execute if score #run blind_vib matches 42 run '+tell('DOOR CONTACT VIEW: pause for the operator. Aim at the wooden door. At OPEN NOW click once and release within two seconds; at CLOSE click once. Stay still; mouse turning allowed.'))
    scene.insert(-1,'execute if score #run blind_vib matches 43 run '+tell('DOOR REPEAT: pause. Empty slot1; aim at door.'))
    scene.insert(-1,'execute if score #run blind_vib matches 44 run '+tell('BELL VIEW: pause. Stay still and watch automatic rings.'))
    scene.insert(-1,'execute if score #run blind_vib matches 45 run '+tell('EXPLOSION VIEW: pause. Stay still; two small blasts happen automatically.'))
    scene.insert(-1,'execute if score #run blind_vib matches 46 run setblock 14 -44 5 frozendawn:thermal_heater[lit=false]')
    scene.insert(-1,'execute if score #run blind_vib matches 46 run fill 15 -44 4 15 -41 6 minecraft:glass')
    scene.insert(-1,'execute if score #run blind_vib matches 46 run '+tell('HEAT VIEW: pause. Stay still; heater fuel changes automatically.'))
    scripts['decoy_scene']='\n'.join(scene)
    scripts['decoy_start']='\n'.join(live+['execute store result score #started blind_vib run fdlab decoy_start',
        'execute unless score #started blind_vib matches 1 run return 0','title @s clear','return 1'])
    scripts['decoy_tick']='\n'.join(live+['execute unless score #stage blind_vib matches 2 run return 0',
        'execute store result score #recorded blind_vib run fdlab decoy_tick','execute unless score #recorded blind_vib matches 1 run return 0',
                'execute if score #run blind_vib matches 36..37 if score #stage blind_vib matches 2 if score #timer blind_vib matches 80 run '+tell('THROW: aim at the ceiling light above the receiver; right click once with slot1. Stay still.'),
        'execute if score #run blind_vib matches 36..37 if score #stage blind_vib matches 2 if score #timer blind_vib matches 80 run title @s actionbar '+json.dumps({'text':'THROW: right click once.','color':'yellow'}),
        'execute if score #run blind_vib matches 38 if score #stage blind_vib matches 2 if score #timer blind_vib = #decoy_cover blind_vib run '+tell('THROW NOW: shutter closed. Aim at the ceiling light; right click once with slot1 and release. Stay still.'),
        'execute if score #run blind_vib matches 38 if score #stage blind_vib matches 2 if score #timer blind_vib = #decoy_cover blind_vib run title @s actionbar '+json.dumps({'text':'THROW NOW: right click once.','color':'yellow'}),
        'execute if score #run blind_vib matches 39..40 if score #timer blind_vib matches 80 run '+tell('OPEN: right click the wooden door once with the empty hand, then release. Stay still.'),
        'execute if score #run blind_vib matches 39..40 if score #timer blind_vib matches 80 run title @s actionbar '+json.dumps({'text':'OPEN: right click the door once.','color':'yellow'}),
        'execute if score #run blind_vib matches 39..40 if score #timer blind_vib matches 200 run '+tell('CLOSE: right click the wooden door once, then release. Stay still.'),
        'execute if score #run blind_vib matches 39..40 if score #timer blind_vib matches 200 run title @s actionbar '+json.dumps({'text':'CLOSE: right click the open door once.','color':'yellow'}),
        'execute if score #run blind_vib matches 42 if score #decoy_cover blind_vib matches 1.. if score #timer blind_vib = #decoy_cover blind_vib run title @s actionbar '+json.dumps({'text':'OPEN NOW: click the door once and release.','color':'yellow'}),
        'execute if score #run blind_vib matches 42 if score #timer blind_vib matches 200 run title @s actionbar '+json.dumps({'text':'CLOSE: click the open door once.','color':'yellow'}),
        'execute if score #run blind_vib matches 41 if score #timer blind_vib matches 80 run title @s actionbar '+json.dumps({'text':'MACHINE ON: stay still and watch.','color':'yellow'}),
        'execute if score #run blind_vib matches 41 if score #timer blind_vib matches 240 run title @s actionbar '+json.dumps({'text':'MACHINE OFF: keep watching.','color':'yellow'}),
        'execute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished','return 1'])
    scripts['decoy_finished']='\n'.join(live+['execute unless score #stage blind_vib matches 3 run return 0',
        'title @s title '+json.dumps({'text':'VIEW COMPLETE','color':'aqua'}),tell('Pause and describe what you saw and heard before diagnostics. Preserve this scene.'),'return 1'])
    #43 uses center titles; long chat and actionbar instructions remain historical42 only.
    open_cue='execute if score #run blind_vib matches 43 if score #decoy_cover blind_vib matches 1.. if score #timer blind_vib = #decoy_cover blind_vib run '
    close_cue='execute if score #run blind_vib matches 43 if score #timer blind_vib matches 200 run '
    cues=[]
    for guard,label,stay in [(open_cue,'OPEN NOW',40),(close_cue,'CLOSE',60)]:
        cues += [guard+'title @s times 0 '+str(stay)+' 0',
                 guard+'title @s subtitle '+json.dumps({'text':'Click once, then release.','color':'white'}),
                 guard+'title @s title '+json.dumps({'text':label,'color':'yellow','bold':True})]
    scripts['decoy_tick']=scripts['decoy_tick'].replace('execute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished','\n'.join(cues)+'\nexecute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished')
    scripts['decoy_finished']=scripts['decoy_finished'].replace('title @s title ', 'execute if score #run blind_vib matches 43..53 run title @s times 0 100 0\nexecute if score #run blind_vib matches 43..53 run title @s subtitle '+json.dumps({'text':'Press Esc to pause.','color':'white'})+'\ntitle @s title ',1)
    blast_cues=[]
    for at,label in [(80,'NEAR BLAST'),(340,'DISTANT BLAST')]:
        guard='execute if score #run blind_vib matches 45 if score #timer blind_vib matches '+str(at)+' run '
        blast_cues += [guard+'title @s actionbar '+json.dumps({'text':label+': stay still and watch.','color':'yellow'})]
    scripts['decoy_tick']=scripts['decoy_tick'].replace('execute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished','\n'.join(blast_cues)+'\nexecute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished')
    scripts['breach_reaction']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 46 run return 0','execute unless score #case blind_vib matches 46 run return 0','execute store result score #preserved blind_vib run fdlab breach_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 47','scoreboard players set #case blind_vib 47','return run function '+ns+':breach_scene'])
    breach=participant+['execute unless score #run blind_vib matches 47 run return 0','execute unless score #case blind_vib matches 47 run return 0','execute unless score #stage blind_vib matches 9 run return 0','execute store result score #clean blind_vib run fdlab behavior_isolate','execute unless score #clean blind_vib matches 1 run return 0','execute store result score #cleared blind_vib run fdlab workbench_clear','execute unless score #cleared blind_vib matches 1 run return 0',
        'fill 1 -48 -11 29 -38 9 minecraft:air','fill 3 -45 3 20 -40 7 minecraft:glass','fill 4 -44 4 19 -41 6 minecraft:air','fill 12 -45 4 14 -45 6 minecraft:stone','fill 17 -45 4 19 -45 6 minecraft:stone','fill 15 -45 4 16 -45 6 minecraft:air','fill 15 -46 4 16 -46 6 minecraft:glass','fill 4 -44 4 11 -41 6 minecraft:glass','fill 15 -44 4 15 -41 6 minecraft:glass','setblock 15 -43 4 minecraft:air',
        'fill 13 -44 4 13 -44 6 minecraft:stone','fill 13 -43 4 13 -43 6 minecraft:stone_slab[type=bottom]','fill 13 -42 4 13 -41 6 minecraft:glass','setblock 9 -40 5 minecraft:sea_lantern',
        'setblock 18 -44 5 minecraft:oak_door[facing=west,half=lower,hinge=left,open=false,powered=false]','setblock 18 -43 5 minecraft:oak_door[facing=west,half=upper,hinge=left,open=false,powered=false]',
        'gamerule mobGriefing true','gamerule doMobSpawning false','gamerule announceAdvancements false','gamerule keepInventory true','clear @s','effect clear @s','tp @s 17.5 -44 5.5 -90 8',
        'item replace entity @s armor.head with frozendawn:eva_helmet','item replace entity @s armor.chest with frozendawn:eva_chestplate','item replace entity @s armor.legs with frozendawn:eva_leggings','item replace entity @s armor.feet with frozendawn:eva_boots','item replace entity @s hotbar.1 with frozendawn:o2_tank_mk3[frozendawn:o2_level=24000]',
        'summon frozendawn:architect 12.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[270f,0f],Tags:["blind_lab_actor"]}']
    for holder in ['#timer','#frames','#last_frame','#archived','#inconclusive','#decoy_balls','#decoy_impacts','#decoy_cover']:breach.append('scoreboard players set '+holder+' blind_vib 0')
    breach+=['execute store result score #equipped blind_vib run fdlab decoy_equip','execute unless score #equipped blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 1',tell('WALL VIEW: pause. Empty slot1; aim at upper door.'),'return 1']
    scripts['breach_scene']='\n'.join(breach)
    scripts['protected_wall']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 47 run return 0','execute unless score #case blind_vib matches 47 run return 0','execute store result score #preserved blind_vib run fdlab protected_wall_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 48','scoreboard players set #case blind_vib 48','return run function '+ns+':protected_wall_scene'])
    protected=[line.replace('matches 47','matches 48').replace('WALL VIEW:','PROTECTED WALL:') for line in breach]
    protected=[line.replace('fill 13 -44 4 13 -44 6 minecraft:stone','fill 13 -44 4 13 -44 6 frozendawn:acheronite_block').replace('fill 13 -43 4 13 -43 6 minecraft:stone_slab[type=bottom]','fill 13 -43 4 13 -43 6 frozendawn:acheronite_block\nsetblock 13 -43 4 minecraft:air\nsetblock 13 -43 5 minecraft:air') for line in protected]
    scripts['protected_wall_scene']='\n'.join(protected)
    scripts['griefing_disabled']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 48 run return 0','execute unless score #case blind_vib matches 48 run return 0','execute store result score #preserved blind_vib run fdlab griefing_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 49','scoreboard players set #case blind_vib 49','return run function '+ns+':griefing_scene'])
    griefing=[line.replace('matches 47','matches 49').replace('gamerule mobGriefing true','gamerule mobGriefing false').replace('WALL VIEW:','GRIEFING OFF:') for line in breach]
    scripts['griefing_scene']='\n'.join(griefing)
    scripts['melee_reach']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 49 run return 0','execute unless score #case blind_vib matches 49 run return 0','execute store result score #preserved blind_vib run fdlab melee_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 50','scoreboard players set #case blind_vib 50','return run function '+ns+':melee_scene'])
    melee=[line.replace('matches 47','matches 50').replace('gamerule mobGriefing true','gamerule mobGriefing false').replace('tp @s 17.5 -44 5.5 -90 8','tp @s 17.5 -44 5.5 90 8').replace('summon frozendawn:architect 12.5','summon frozendawn:architect 15.5').replace('WALL VIEW: pause. Empty slot1; aim at upper door.','MELEE VIEW: pause. Stay still; watch the automatic hit and shutter.') for line in breach]
    begin=melee.index('fill 3 -45 3 20 -40 7 minecraft:glass');end=melee.index('gamerule mobGriefing false')
    melee[begin:end]=['fill 3 -45 3 20 -40 7 minecraft:white_wool','fill 15 -44 5 15 -41 5 minecraft:air','fill 17 -44 5 17 -41 5 minecraft:air','setblock 16 -43 5 minecraft:air','setblock 15 -45 5 minecraft:stone','setblock 17 -45 5 minecraft:stone','setblock 16 -46 5 minecraft:glass','setblock 15 -40 5 minecraft:sea_lantern','gamerule naturalRegeneration false']
    scripts['melee_scene']='\n'.join(melee)
    scripts['melee_range']='\n'.join(participant+['execute unless score #stage blind_vib matches 3 run return 0','execute unless score #run blind_vib matches 50 run return 0','execute unless score #case blind_vib matches 50 run return 0','execute store result score #preserved blind_vib run fdlab melee_range_ready','execute unless score #preserved blind_vib matches 1 run return 0','scoreboard players set #stage blind_vib 9','scoreboard players set #run blind_vib 51','scoreboard players set #case blind_vib 51','return run function '+ns+':melee_range_scene'])
    ranged=[line.replace('matches 50','matches 51').replace('summon frozendawn:architect 15.5','summon frozendawn:architect 14.5').replace('fill 15 -44 5 15 -41 5','fill 14 -44 5 14 -41 5').replace('setblock 16 -43 5 minecraft:air','fill 15 -43 5 16 -43 5 minecraft:air').replace('setblock 15 -45 5','setblock 14 -45 5').replace('setblock 15 -40 5','setblock 14 -40 5').replace('MELEE VIEW: pause. Stay still; watch the automatic hit and shutter.','RANGE VIEW: pause. Stay still; watch the distant actor and automatic shutter.') for line in melee]
    scripts['melee_range_scene']='\n'.join(ranged)
    scripts['melee_range_repeat']=scripts['melee_range'].replace('matches 50','matches 51').replace('blind_vib 51','blind_vib 52').replace('fdlab melee_range_ready','fdlab melee_range_repeat_ready').replace(':melee_range_scene',':melee_range_repeat_scene')
    scripts['melee_range_repeat_scene']='\n'.join(line.replace('matches 51','matches 52').replace('RANGE VIEW: pause. Stay still; watch the distant actor and automatic shutter.','RANGE REPEAT: pause. Stay still; wait for VIEW COMPLETE before opening any screen.') for line in ranged)
    scripts['melee_obstruction']=scripts['melee_range'].replace('matches 50','matches 52').replace('blind_vib 51','blind_vib 53').replace('fdlab melee_range_ready','fdlab melee_obstruction_ready').replace(':melee_range_scene',':melee_obstruction_scene')
    obstructed=[line.replace('matches 50','matches 53').replace('setblock 16 -43 5 minecraft:air','setblock 16 -43 5 minecraft:oak_slab[type=top]\nsetblock 16 -44 5 minecraft:air').replace('tp @s 17.5 -44 5.5 90 8','tp @s 17.5 -44 5.5 90 20').replace('MELEE VIEW: pause. Stay still; watch the automatic hit and shutter.','OBSTRUCTION: pause. Stay still; look through the lower gap. Wait for VIEW COMPLETE.') for line in melee]
    scripts['melee_obstruction_scene']='\n'.join(obstructed)
    cue='execute if score #run blind_vib matches 47..49 if score #timer blind_vib matches 80 run '
    cues=[cue+'title @s times 0 60 0',cue+'title @s subtitle '+json.dumps({'text':'Open the door once, then release.','color':'white'}),cue+'title @s title '+json.dumps({'text':'OPEN NOW','color':'yellow','bold':True})]
    scripts['decoy_tick']=scripts['decoy_tick'].replace('execute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished','\n'.join(cues)+'\nexecute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished')
    heat_cues=[]
    for at,label in [(80,'HEATER ON'),(240,'HEATER OFF')]:
        heat_cues.append('execute if score #run blind_vib matches 46 if score #timer blind_vib matches '+str(at)+' run title @s actionbar '+json.dumps({'text':label,'color':'yellow'}))
    scripts['decoy_tick']=scripts['decoy_tick'].replace('execute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished','\n'.join(heat_cues)+'\nexecute if score #stage blind_vib matches 3 run function '+ns+':decoy_finished')
    scripts['unload_repeat_prepare']='\n'.join(participant+['return run fdlab unload_repeat_prepare'])
    scripts['attention_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab attention_prepare'
    scripts['attention_publish']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab attention_publish'
    scripts['attention_stage']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab attention_stage'
    scripts['attention_start']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab attention_start'
    scripts['attention_repeat_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab attention_repeat_prepare'
    scripts['attention_safe_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab attention_safe_prepare'
    scripts['reconnect_clock_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_clock_prepare'
    scripts['reconnect_repeat_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_repeat_prepare'
    scripts['reconnect_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_prepare'
    scripts['reconnect_publish']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_publish'
    scripts['reconnect_stage']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_stage'
    scripts['reconnect_start']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_start'
    scripts['reconnect_restage']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_restage'
    scripts['reconnect_resume']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab reconnect_resume'
    scripts['lan_repeat_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab lan_repeat_prepare'
    scripts['lan_prepare']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab lan_prepare'
    scripts['lan_publish']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab lan_publish'
    scripts['lan_stage']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab lan_stage'
    scripts['lan_start']='execute unless entity @s[tag=blind_lab_player] run return 0\nreturn run fdlab lan_start'
    scripts['save_prepare']='\n'.join(participant+['return run fdlab save_prepare'])
    scripts['save_resume']='\n'.join(participant+['return run fdlab save_resume'])
    scripts['save_start']='\n'.join(participant+['return run fdlab save_start'])
    scripts['stale_prepare']='\n'.join(participant+['return run fdlab stale_prepare'])
    scripts['stale_start']='\n'.join(participant+['return run fdlab stale_start'])
    scripts['unload_prepare']='\n'.join(participant+['return run fdlab unload_prepare'])
    scripts['unload_start']='\n'.join(participant+['return run fdlab unload_start'])
    return scripts


def behavior_scripts(participant, transition):
    namespace = 'blind_vibration_lab'
    live = ['execute unless function '+namespace+':access run return 0',
            'execute unless entity @s[tag=blind_lab_player] run return 0',
            'execute unless score #case blind_vib matches 4..10 run return 0',
            'execute unless score #run blind_vib = #case blind_vib run return 0']
    scripts = {'behavior': transition.replace('#run blind_vib matches 1', '#run blind_vib matches 3')
               .replace('#case blind_vib matches 1', '#case blind_vib matches 3')
               .replace('set #case blind_vib 2', 'set #case blind_vib 4')
               .replace(':scene', ':behavior_scene')}
    for run,name in ((34,'mining_connected'),(35,'mining_isolated')):
        scripts[name] = '\n'.join(participant + [
            'execute unless score #stage blind_vib matches 3 run return 0',
            'execute unless score #run blind_vib matches '+str(run-1)+' run return 0',
            'execute unless score #case blind_vib matches '+str(run-1)+' run return 0',
            'execute store result score #preserved blind_vib run fdlab channels_mining_ready',
            'execute unless score #preserved blind_vib matches 1 run return 0',
            'scoreboard players set #stage blind_vib 9',
            'scoreboard players set #run blind_vib '+str(run),
            'scoreboard players set #case blind_vib '+str(run),
            'return run function '+namespace+':channels_scene'])
    scene = participant + live + [
        'execute unless score #case blind_vib matches 4 run return 0',
        'execute unless score #stage blind_vib matches 9 run return 0',
        'execute store result score #cleared blind_vib run fdlab workbench_clear',
        'execute unless score #cleared blind_vib matches 1 run return 0',
        'fill 1 -48 -11 29 -39 9 minecraft:air',
        'fill 8 -45 4 18 -45 6 minecraft:stone',
        'fill 8 -44 4 18 -42 6 minecraft:glass',
        'fill 9 -44 5 17 -43 5 minecraft:air',
        'fill 13 -44 5 13 -43 5 minecraft:glass',
        'setblock 10 -42 5 minecraft:sea_lantern',
        'setblock 16 -42 5 minecraft:sea_lantern',
        'gamerule mobGriefing false', 'gamerule doMobSpawning false',
        'gamerule announceAdvancements false', 'gamerule keepInventory true',
        'tp @s 15.5 -44 5.5 90 0',
        'summon frozendawn:architect 9.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[270f,0f],Tags:["blind_lab_actor"]}',
    ]
    for holder in ('#timer','#last_frame','#frames','#samples','#detected','#inconclusive','#archived'):
        scene.append('scoreboard players set '+holder+' blind_vib 0')
    scene += ['scoreboard players set #stage blind_vib 1','title @s times 10 60 10',
              'title @s title '+json.dumps({'text':'LIVE FOOTSTEPS','color':'aqua'}),
              tell('Ready. Face the BA through the glass. Start releases its AI and puts you in Survival. Stand still first, walk forward and backward on WALK, then stop on STOP. No sprinting, jumping, crouching or mining.'),
              tell('Pause and tell the operator you are ready, or click [START TEST].','/function '+namespace+':start'), 'return 1']
    scripts['behavior_scene']='\n'.join(scene)
    scripts['behavior_broad']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 4 run return 0',
        'execute unless score #case blind_vib matches 4 run return 0',
        'execute store result score #preserved blind_vib run fdlab behavior_archived',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 5', 'scoreboard players set #case blind_vib 5',
        'return run function '+namespace+':behavior_broad_scene'])
    scripts['behavior_broad_scene']=scripts['behavior_scene'].replace(
        'unless score #case blind_vib matches 4 run', 'unless score #case blind_vib matches 5 run').replace(
        'fill 8 -45 4 18 -45 6', 'fill 8 -45 2 20 -45 8').replace(
        'fill 8 -44 4 18 -42 6', 'fill 8 -44 2 20 -41 8').replace(
        'fill 9 -44 5 17 -43 5', 'fill 9 -44 3 19 -42 7').replace(
        'fill 13 -44 5 13 -43 5', 'fill 13 -44 3 13 -42 7').replace(
        'setblock 10 -42 5', 'setblock 10 -41 5').replace(
        'setblock 16 -42 5', 'setblock 17 -41 5').replace(
        'tp @s 15.5 -44 5.5', 'tp @s 17.5 -44 5.5').replace(
        'LIVE FOOTSTEPS', 'WIDE FLOOR').replace(
        'walk forward and backward on WALK', 'walk sideways with A/D on WALK')
    scripts['behavior_listen']=scripts['behavior_broad'].replace(
        'matches 4 run', 'matches 5 run').replace('blind_vib 5', 'blind_vib 6').replace(
        ':behavior_broad_scene', ':behavior_listen_scene')
    scripts['behavior_listen_scene']=scripts['behavior_broad_scene'].replace(
        'unless score #case blind_vib matches 5 run', 'unless score #case blind_vib matches 6 run')
    scripts['behavior_first']=scripts['behavior_listen'].replace(
        'matches 5 run', 'matches 6 run').replace('blind_vib 6', 'blind_vib 7').replace(
        ':behavior_listen_scene', ':behavior_first_scene')
    scripts['behavior_first_scene']=scripts['behavior_listen_scene'].replace(
        'unless score #case blind_vib matches 6 run', 'unless score #case blind_vib matches 7 run').replace(
        'execute store result score #cleared blind_vib run fdlab workbench_clear',
        'execute store result score #isolation_result blind_vib run fdlab behavior_isolate\n'
        'execute unless score #isolation_result blind_vib matches 1 run return 0\n'
        'execute store result score #cleared blind_vib run fdlab workbench_clear')
    scripts['behavior_echo']=scripts['behavior_first'].replace(
        'matches 6 run', 'matches 7 run').replace('blind_vib 7', 'blind_vib 8').replace(
        ':behavior_first_scene', ':behavior_echo_scene')
    echo_scene=participant+live+[
        'execute unless score #case blind_vib matches 8 run return 0',
        'execute unless score #stage blind_vib matches 9 run return 0',
        'execute store result score #isolation_result blind_vib run fdlab behavior_isolate',
        'execute unless score #isolation_result blind_vib matches 1 run return 0',
        'execute store result score #cleared blind_vib run fdlab workbench_clear',
        'execute unless score #cleared blind_vib matches 1 run return 0',
        'fill 1 -48 -11 29 -39 9 minecraft:air',
        'fill 8 -45 3 11 -45 7 minecraft:stone',
        'fill 8 -44 3 11 -42 7 minecraft:glass',
        'fill 9 -44 4 10 -43 6 minecraft:air',
        'fill 11 -43 4 11 -43 6 minecraft:air',
        'setblock 9 -42 5 minecraft:sea_lantern',
        'fill 16 -45 3 19 -45 7 minecraft:stone',
        'setblock 17 -41 5 minecraft:sea_lantern',
        'gamerule mobGriefing false','gamerule doMobSpawning false',
        'gamerule announceAdvancements false','gamerule keepInventory true',
        'tp @s 17.5 -44 5.5 90 0',
        'summon frozendawn:architect 9.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[225f,0f],Tags:["blind_lab_actor"]}',
    ]
    for holder in ('#timer','#last_frame','#frames','#samples','#detected','#inconclusive','#archived'):
        echo_scene.append('scoreboard players set '+holder+' blind_vib 0')
    echo_scene += ['scoreboard players set #stage blind_vib 1','title @s times 10 60 10',
                  'title @s title '+json.dumps({'text':'STATIONARY ECHO','color':'aqua'}),
                  tell('Ready. Stay in this exact spot and watch the BA for 24 seconds. You may move the camera. Do not walk, crouch, jump, mine or use items.'),
                  tell('Pause and tell the operator you are ready, or click [START TEST].','/function '+namespace+':start'),'return 1']
    scripts['behavior_echo_scene']='\n'.join(echo_scene)
    scripts['behavior_echo_loss']=scripts['behavior_echo'].replace(
        'matches 7 run', 'matches 8 run').replace('blind_vib 8', 'blind_vib 9').replace(
        ':behavior_echo_scene', ':behavior_echo_loss_scene')
    scripts['behavior_echo_loss_scene']=scripts['behavior_echo_scene'].replace(
        'unless score #case blind_vib matches 8 run', 'unless score #case blind_vib matches 9 run').replace(
        'scoreboard players set #stage blind_vib 1',
        'scoreboard players set #echo_closed blind_vib 0\n'
        'scoreboard players set #closed_at blind_vib -1\n'
        'scoreboard players set #stage blind_vib 1').replace('STATIONARY ECHO', 'ECHO OPENING').replace(
        'You may move the camera.', 'The opening closes after six seconds. You may move the camera.')
    scripts['behavior_echo_search']=scripts['behavior_echo_loss'].replace(
        'matches 8 run', 'matches 9 run').replace('set #run blind_vib 9', 'set #run blind_vib 10').replace(
        'set #case blind_vib 9', 'set #case blind_vib 10').replace(
        ':behavior_echo_loss_scene', ':behavior_echo_search_scene')
    scripts['behavior_echo_search_scene']=scripts['behavior_echo_loss_scene'].replace(
        'unless score #case blind_vib matches 9 run', 'unless score #case blind_vib matches 10 run')
    scripts['behavior_echo_close']='\n'.join(live+[
        'execute unless score #case blind_vib matches 9..10 run return 0',
        'execute unless score #stage blind_vib matches 2 run return 0',
        'execute unless score #timer blind_vib matches 120 run return 0',
        'execute unless score #echo_closed blind_vib matches 0 run return 0',
        'fill 11 -43 4 11 -43 6 minecraft:glass',
        *['execute unless block 11 -43 '+str(z)+' minecraft:glass run return 0' for z in range(4,7)],
        'scoreboard players set #echo_closed blind_vib 1',
        'scoreboard players operation #closed_at blind_vib = #timer blind_vib',
        'title @s actionbar '+json.dumps({'text':'Opening closed. Stay still and keep watching.','color':'yellow'}),
        'return 1'])
    scripts['behavior_start']='\n'.join(live+[
        'execute store result score #started blind_vib run fdlab behavior_start',
        'execute unless score #started blind_vib matches 1 run return 0',
        'title @s clear',
        'execute if score #case blind_vib matches 4..7 run '+tell('Stand still and watch the BA. Wait for WALK.'),
        'execute if score #case blind_vib matches 8..10 run '+tell('Stay in this spot and watch the BA for 24 seconds. Camera movement is fine.'),'return 1'])
    scripts['behavior_finish']='\n'.join(live+[
        'execute store result score #archive_result blind_vib run fdlab behavior_finish',
        'execute unless score #archive_result blind_vib matches 1 run return 0',
        'execute if score #case blind_vib matches 4..7 run title @s subtitle '+json.dumps({'text':'Describe how the BA changed while you walked and stopped.','color':'white'}),
        'execute if score #case blind_vib matches 8..10 run title @s subtitle '+json.dumps({'text':'Describe what you saw and heard, then pause.','color':'white'}),
        'title @s title '+json.dumps({'text':'VIEW COMPLETE','color':'aqua'}),
        tell('Complete. The BA is held and you are back in Creative. Describe what you saw, then pause. Stay in this world.'),'return 1'])
    tick=live+['execute unless score #stage blind_vib matches 2 run return 0',
               'scoreboard players add #timer blind_vib 1',
               'execute if score #case blind_vib matches 9..10 if score #timer blind_vib matches 120 run function '+namespace+':behavior_echo_close',
               'fdlab behavior_record']
    for at,text in [(80,'WALK: use W and S to walk forward and backward. Keep facing the BA.'),(240,'STOP: stand still and keep watching the BA.')]:
        for scenario in (4,5,6,7):
            message=text if scenario==4 else text.replace('use W and S to walk forward and backward', 'use A and D to walk sideways back and forth')
            tick += ['execute if score #case blind_vib matches '+str(scenario)+' if score #timer blind_vib matches '+str(at)+' run '+tell(message),
                     'execute if score #case blind_vib matches '+str(scenario)+' if score #timer blind_vib matches '+str(at)+' run title @s actionbar '+json.dumps({'text':message,'color':'yellow'})]
    tick += ['execute if score #timer blind_vib matches 480 run function '+namespace+':behavior_finish','return 1']
    scripts['behavior_tick']='\n'.join(tick)
    return scripts


def base_scripts(participant):
    namespace = 'blind_vibration_lab'
    live = ['execute unless function '+namespace+':access run return 0',
            'execute unless entity @s[tag=blind_lab_player] run return 0',
            'execute unless score #run blind_vib matches 11 run return 0',
            'execute unless score #case blind_vib matches 11 run return 0']
    scripts = {'behavior_base': '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 10 run return 0',
        'execute unless score #case blind_vib matches 10 run return 0',
        'execute store result score #preserved blind_vib run fdlab behavior_archived',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 11', 'scoreboard players set #case blind_vib 11',
        'return run function '+namespace+':behavior_base_scene'])}
    scripts['steps_crouched_retry'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 23 run return 0',
        'execute unless score #case blind_vib matches 23 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_retry_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 24',
        'scoreboard players set #case blind_vib 24',
        'return run function '+namespace+':channels_scene'])
    scene = participant + live + [
        'execute unless score #stage blind_vib matches 9 run return 0',
        'execute store result score #isolation_result blind_vib run fdlab behavior_isolate',
        'execute unless score #isolation_result blind_vib matches 1 run return 0',
        'execute store result score #cleared blind_vib run fdlab workbench_clear',
        'execute unless score #cleared blind_vib matches 1 run return 0',
        'fill 1 -48 -11 29 -39 9 minecraft:air',
        'fill 2 -45 -10 28 -40 8 minecraft:stone',
        'fill 3 -44 -9 27 -41 7 minecraft:air',
        'fill 15 -44 -8 27 -40 6 minecraft:stone_bricks',
        'fill 16 -44 -7 26 -41 5 minecraft:air',
        # A half-block-high air opening admits an echo, but no actor can walk through it.
        'fill 15 -43 -4 15 -43 2 minecraft:stone_slab[type=bottom]',
        'setblock 6 -40 0 minecraft:sea_lantern',
        'setblock 11 -40 -5 minecraft:sea_lantern',
        'setblock 20 -40 -3 minecraft:sea_lantern',
        'setblock 24 -40 3 minecraft:sea_lantern',
        'setblock 26 -44 -6 minecraft:chest[facing=west]',
        'setblock 25 -44 -6 minecraft:crafting_table',
        'setblock 24 -44 -6 minecraft:furnace[facing=south]',
        'item replace block 26 -44 -6 container.0 with minecraft:oak_log 16',
        'item replace block 26 -44 -6 container.1 with minecraft:coal 16',
        'item replace block 26 -44 -6 container.2 with minecraft:raw_iron 8',
        'fill 24 -44 2 25 -43 4 minecraft:iron_ore',
        'gamerule mobGriefing true', 'gamerule doMobSpawning false',
        'gamerule announceAdvancements false', 'gamerule keepInventory true',
        'difficulty normal', 'fd world set phase 6 late',
        'tp @s 22.5 -44 0.5 90 0', 'spawnpoint @s 22 -44 0',
        'item replace entity @s hotbar.0 with minecraft:iron_pickaxe',
        'item replace entity @s hotbar.1 with minecraft:iron_sword',
        'item replace entity @s hotbar.2 with minecraft:cooked_beef 32',
        'item replace entity @s hotbar.3 with minecraft:torch 32',
        'item replace entity @s hotbar.4 with minecraft:cobblestone 32',
        'item replace entity @s hotbar.5 with minecraft:oak_planks 16',
        'item replace entity @s armor.head with frozendawn:eva_helmet',
        'item replace entity @s armor.chest with frozendawn:eva_chestplate',
        'item replace entity @s armor.legs with frozendawn:eva_leggings',
        'item replace entity @s armor.feet with frozendawn:eva_boots',
        'item replace entity @s inventory.0 with frozendawn:o2_tank_mk3',
        'summon frozendawn:architect 9.5 -44 0.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[225f,0f],Tags:["blind_lab_actor"]}',
    ]
    for holder in ('#timer', '#frames', '#last_frame', '#inconclusive', '#archived', '#base_end'):
        scene.append('scoreboard players set '+holder+' blind_vib 0')
    for holder in ('#base_acquired', '#base_broken', '#base_entered', '#base_close'):
        scene.append('scoreboard players set '+holder+' blind_vib -1')
    scene += ['scoreboard players set #stage blind_vib 1', 'title @s times 10 60 10',
              'title @s title '+json.dumps({'text':'UNDERGROUND BASE','color':'aqua'}),
              tell('Ready. EVA, oxygen and tools are supplied. Start puts you in Survival and releases the BA outside. Stay still briefly, then do normal chores on WORK: storage, crafting, furnace or the iron-ore pile. Keep the low window as prepared. React naturally if it reaches you.'),
              tell('This lasts at most 90 seconds, ending ten seconds after entry or on a death. Pause and tell the operator you are ready, or click [START TEST].','/function '+namespace+':start'), 'return 1']
    scripts['behavior_base_scene'] = '\n'.join(scene)
    scripts['behavior_base_start'] = '\n'.join(live + [
        'execute store result score #started blind_vib run fdlab blind_base_start',
        'execute unless score #started blind_vib matches 1 run return 0',
        'title @s clear', tell('Stand still briefly. At WORK, do normal base chores. Keep the low window as prepared. If the BA reaches you, react naturally.'), 'return 1'])
    scripts['behavior_base_tick'] = '\n'.join(live + [
        'execute unless score #stage blind_vib matches 2 run return 0',
        'execute store result score #base_recorded blind_vib run fdlab blind_base_tick',
        'execute unless score #base_recorded blind_vib matches 1 run return 0',
        'execute if score #stage blind_vib matches 2 if score #timer blind_vib matches 100 run '+tell('WORK: do normal base chores. Use storage, craft or smelt, and mine some of the iron-ore pile. Stay inside until it reaches you.'),
        'execute if score #stage blind_vib matches 2 if score #timer blind_vib matches 100 run title @s actionbar '+json.dumps({'text':'WORK: normal base chores.','color':'yellow'}),
        'execute if score #stage blind_vib matches 3 run title @s subtitle '+json.dumps({'text':'Describe what happened, then pause.','color':'white'}),
        'execute if score #stage blind_vib matches 3 run title @s title '+json.dumps({'text':'VIEW COMPLETE','color':'aqua'}),
        'execute if score #stage blind_vib matches 3 run '+tell('Complete. The encounter is held and Creative is restored. Describe what you saw and heard, then pause. Do not restart.'), 'return 1'])
    return scripts


def channel_scripts(participant):
    namespace = 'blind_vibration_lab'
    live = ['execute unless function '+namespace+':access run return 0',
            'execute unless entity @s[tag=blind_lab_player] run return 0',
            'execute unless score #run blind_vib = #case blind_vib run return 0',
            'execute unless score #case blind_vib matches 12..17 unless score #case blind_vib matches 20..35 run return 0']
    scripts = {}
    names = ('channels_hearing','channels_hearing_closed','channels_vibration',
             'channels_vibration_gap','channels_echo','channels_echo_closed')
    for run, name in enumerate(names, 12):
        scripts[name] = '\n'.join(participant + [
            'execute unless score #stage blind_vib matches 3 run return 0',
            'execute unless score #run blind_vib matches '+str(run-1)+' run return 0',
            'execute unless score #case blind_vib matches '+str(run-1)+' run return 0',
            'execute store result score #preserved blind_vib run fdlab '+('blind_base_archived' if run==12 else 'channels_archived'),
            'execute unless score #preserved blind_vib matches 1 run return 0',
            'scoreboard players set #stage blind_vib 9',
            'scoreboard players set #run blind_vib '+str(run),
            'scoreboard players set #case blind_vib '+str(run),
            'return run function '+namespace+':channels_scene'])
    for run, name in ((20, 'posture_standing'), (21, 'posture_crouched'), (22, 'steps_walk'), (23, 'steps_crouched')):
        scripts[name] = '\n'.join(participant + [
            'execute unless score #stage blind_vib matches 3 run return 0',
            'execute unless score #run blind_vib matches '+str(run-1)+' run return 0',
            'execute unless score #case blind_vib matches '+str(run-1)+' run return 0',
            'execute store result score #preserved blind_vib run fdlab '+('cave_archived' if run==20 else 'channels_archived'),
            'execute unless score #preserved blind_vib matches 1 run return 0',
            'scoreboard players set #stage blind_vib 9',
            'scoreboard players set #run blind_vib '+str(run),
            'scoreboard players set #case blind_vib '+str(run),
            'return run function '+namespace+':channels_scene'])
    scripts['steps_crouched_retry'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 23 run return 0',
        'execute unless score #case blind_vib matches 23 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_retry_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 24',
        'scoreboard players set #case blind_vib 24',
        'return run function '+namespace+':channels_scene'])
    scripts['steps_crouched_input'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 24 run return 0',
        'execute unless score #case blind_vib matches 24 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_input_retry_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 25',
        'scoreboard players set #case blind_vib 25',
        'return run function '+namespace+':channels_scene'])
    scripts['steps_crouched_fixed'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 25 run return 0',
        'execute unless score #case blind_vib matches 25 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_fixed_retry_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 26',
        'scoreboard players set #case blind_vib 26',
        'return run function '+namespace+':channels_scene'])
    scripts['steps_crouched_ready'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 26 run return 0',
        'execute unless score #case blind_vib matches 26 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_crouch_ready_retry',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 27',
        'scoreboard players set #case blind_vib 27',
        'return run function '+namespace+':channels_scene'])
    scripts['steps_crouched_repeat'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 27 run return 0',
        'execute unless score #case blind_vib matches 27 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_crouch_repeat_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 28',
        'scoreboard players set #case blind_vib 28',
        'return run function '+namespace+':channels_scene'])
    for run, name in ((29, 'steps_material_stone'), (30, 'steps_material_wool')):
        scripts[name] = '\n'.join(participant + [
            'execute unless score #stage blind_vib matches 3 run return 0',
            'execute unless score #run blind_vib matches '+str(run-1)+' run return 0',
            'execute unless score #case blind_vib matches '+str(run-1)+' run return 0',
            'execute store result score #preserved blind_vib run fdlab channels_material_ready',
            'execute unless score #preserved blind_vib matches 1 run return 0',
            'scoreboard players set #stage blind_vib 9',
            'scoreboard players set #run blind_vib '+str(run),
            'scoreboard players set #case blind_vib '+str(run),
            'return run function '+namespace+':channels_scene'])
    scripts['echo_motion'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 30 run return 0',
        'execute unless score #case blind_vib matches 30 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_echo_motion_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 31',
        'scoreboard players set #case blind_vib 31',
        'return run function '+namespace+':channels_scene'])
    scripts['echo_motion_repeat'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 31 run return 0',
        'execute unless score #case blind_vib matches 31 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_echo_motion_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'return run function '+namespace+':channels_scene'])
    for run, name in ((32, 'hearing_bend'), (33, 'hearing_bend_closed')):
        scripts[name] = '\n'.join(participant + [
            'execute unless score #stage blind_vib matches 3 run return 0',
            'execute unless score #run blind_vib matches '+str(run-1)+' run return 0',
            'execute unless score #case blind_vib matches '+str(run-1)+' run return 0',
            'execute store result score #preserved blind_vib run fdlab channels_hearing_ready',
            'execute unless score #preserved blind_vib matches 1 run return 0',
            'scoreboard players set #stage blind_vib 9',
            'scoreboard players set #run blind_vib '+str(run),
            'scoreboard players set #case blind_vib '+str(run),
            'return run function '+namespace+':channels_scene'])
    scripts['hearing_bend_closed_qualified'] = '\n'.join(participant + [
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 32 run return 0',
        'execute unless score #case blind_vib matches 32 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_hearing_qualified_ready',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 33',
        'scoreboard players set #case blind_vib 33',
        'return run function '+namespace+':channels_scene'])
    scene = participant + live + [
        'execute unless score #stage blind_vib matches 9 run return 0',
        'execute store result score #isolation_result blind_vib run fdlab behavior_isolate',
        'execute unless score #isolation_result blind_vib matches 1 run return 0',
        'execute store result score #cleared blind_vib run fdlab workbench_clear',
        'execute unless score #cleared blind_vib matches 1 run return 0',
        'fill 1 -48 -11 29 -39 9 minecraft:air',
        'fill 8 -45 3 11 -45 7 minecraft:stone',
        'fill 8 -44 3 11 -42 7 minecraft:glass',
        'fill 9 -44 4 10 -43 6 minecraft:air',
        'setblock 9 -42 5 minecraft:sea_lantern',
        'fill 16 -45 3 19 -45 7 minecraft:stone',
        'setblock 17 -41 5 minecraft:sea_lantern',
        'execute if score #case blind_vib matches 12..13 run fill 16 -45 3 19 -45 7 minecraft:air',
        'execute if score #case blind_vib matches 32..33 run fill 16 -45 3 19 -45 7 minecraft:air',
        'execute if score #case blind_vib matches 12..13 run fill 13 -45 3 16 -45 7 minecraft:stone',
        'execute if score #case blind_vib matches 32..33 run fill 13 -45 3 16 -45 7 minecraft:stone',
        'execute if score #case blind_vib matches 12..13 run fill 9 -44 4 10 -43 6 minecraft:glass',
        'execute if score #case blind_vib matches 32..33 run fill 9 -44 4 10 -43 6 minecraft:glass',
        'execute if score #case blind_vib matches 12..13 run fill 9 -44 5 9 -43 5 minecraft:air',
        'execute if score #case blind_vib matches 32..33 run fill 9 -44 5 9 -43 5 minecraft:air',
        'execute if score #case blind_vib matches 12..13 run fill 9 -42 4 12 -40 6 minecraft:glass',
        'execute if score #case blind_vib matches 32..33 run fill 9 -42 4 12 -40 6 minecraft:glass',
        'execute if score #case blind_vib matches 12..13 run fill 9 -41 5 12 -41 5 minecraft:air',
        'execute if score #case blind_vib matches 32..33 run fill 9 -41 5 12 -41 5 minecraft:air',
        'execute if score #case blind_vib matches 12..13 run setblock 9 -42 5 minecraft:glass',
        'execute if score #case blind_vib matches 12 run setblock 9 -42 5 minecraft:air',
        'execute if score #case blind_vib matches 32 run setblock 9 -42 5 minecraft:air',
        'execute if score #case blind_vib matches 12..13 run fill 14 -44 5 14 -43 5 minecraft:obsidian',
        'execute if score #case blind_vib matches 32..33 run fill 14 -44 5 14 -43 5 minecraft:obsidian',
        'execute if score #case blind_vib matches 34..35 run fill 9 -44 4 10 -43 6 minecraft:glass',
        'execute if score #case blind_vib matches 34..35 run fill 9 -44 5 9 -43 5 minecraft:air',
        'execute if score #case blind_vib matches 34 run fill 12 -45 3 15 -45 7 minecraft:stone',
        'execute if score #case blind_vib matches 34..35 run fill 18 -44 5 18 -43 5 minecraft:obsidian',
        'execute if score #case blind_vib matches 14 run fill 12 -45 3 15 -45 7 minecraft:stone',
        'execute if score #case blind_vib matches 22..30 run fill 12 -45 3 15 -45 7 minecraft:stone',
        'execute if score #case blind_vib matches 30 run fill 16 -45 3 19 -45 7 minecraft:white_wool',
        'execute if score #case blind_vib matches 31 run fill 16 -45 3 19 -45 7 minecraft:air',
        'execute if score #case blind_vib matches 16 run fill 11 -43 4 11 -43 6 minecraft:air',
        'execute if score #case blind_vib matches 20..21 run fill 11 -43 4 11 -43 6 minecraft:air',
        'execute if score #case blind_vib matches 31 run fill 11 -43 4 11 -43 6 minecraft:air',
        'gamerule mobGriefing false', 'gamerule doMobSpawning false',
        'gamerule announceAdvancements false', 'gamerule keepInventory true',
        'difficulty normal', 'fd world set phase 6 late',
        'tp @s 17.5 -44 5.5 90 0',
        'execute if score #case blind_vib matches 12..13 run tp @s 13.5 -44 5.5 90 0',
        'execute if score #case blind_vib matches 32..33 run tp @s 13.5 -44 5.5 -90 7',
        'execute if score #case blind_vib matches 34..35 run tp @s 17.5 -44 5.5 -90 7',
        'item replace entity @s hotbar.0 with minecraft:iron_pickaxe',
        'item replace entity @s armor.head with frozendawn:eva_helmet',
        'item replace entity @s armor.chest with frozendawn:eva_chestplate',
        'item replace entity @s armor.legs with frozendawn:eva_leggings',
        'item replace entity @s armor.feet with frozendawn:eva_boots',
        'item replace entity @s inventory.0 with frozendawn:o2_tank_mk3',
        'summon frozendawn:architect 9.5 -44 5.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[225f,0f],Tags:["blind_lab_actor"]}']
    for holder in ('#timer','#frames','#last_frame','#inconclusive','#archived'):
        scene.append('scoreboard players set '+holder+' blind_vib 0')
    scene += ['scoreboard players set #stage blind_vib 1', 'title @s times 10 60 10',
              'title @s title '+json.dumps({'text':'SENSING VIEW','color':'aqua'}),
              'execute if score #case blind_vib matches 12..13 run '+tell('Stay on your exact spot. At WORK, hold left click with the iron pickaxe on the obsidian block beside you until STOP. Camera movement is allowed. Do not walk or use other items.'),
              'execute if score #case blind_vib matches 14..15 run '+tell('Stay still until WALK, then walk sideways with A/D on your stone platform until STOP. Face the BA. Do not sprint, crouch, jump or use items.'),
              'execute if score #case blind_vib matches 16..17 run '+tell('Stay in this exact spot for the whole view. Move only the camera; do not walk, jump, crouch or use items.'),
              'execute if score #case blind_vib matches 20 run '+tell('STANDING: stay on this exact spot, standing normally. Move only the camera. Do not walk, jump or use items.'),
              'execute if score #case blind_vib matches 21 run '+tell('CROUCHING: hold Shift as soon as you unpause and keep holding it until VIEW COMPLETE. Stay on this exact spot. Move only the camera; do not walk, jump or use items.'),
              'execute if score #case blind_vib matches 22 run '+tell('NORMAL WALK: stay still until WALK, then use A/D back and forth on your platform until STOP. Face the BA and turn before the edges. Do not sprint, crouch, jump or use items.'),
              'execute if score #case blind_vib matches 23..24 run '+tell('CROUCH WALK: hold Shift immediately and keep holding it until VIEW COMPLETE. Stay still until WALK, then use A/D back and forth on your platform until STOP. Face the BA and turn before the edges. Do not jump or use items.'),
              'execute if score #case blind_vib matches 25..26 run '+tell('INPUT-RECORDED CROUCH WALK: hold LEFT SHIFT from unpause through VIEW COMPLETE. Stay still until WALK. At WALK keep holding LEFT SHIFT and use A/D back and forth. At STOP release A/D only; keep LEFT SHIFT held.'),
              'execute if score #case blind_vib matches 27..28 run '+tell('CROUCH CHECK: unpause, stay still and hold LEFT SHIFT. After two continuous seconds crouching, the view starts automatically. Keep Shift through VIEW COMPLETE; move A/D only from WALK to STOP.'),
              'execute if score #case blind_vib matches 29..30 run '+tell('FOOTSTEP MATERIAL: stand normally and stay still until WALK. Use A/D back and forth on your platform until STOP. Stop moving and watch until VIEW COMPLETE. Do not crouch, sprint, jump or use items.'),
              'execute if score #case blind_vib matches 31 run '+tell('MOVING ECHO: Start supplies hovering flight so no footsteps are emitted. Stay still until MOVE, then use A/D sideways inside your usual platform area. Keep moving when COVERED appears. At STOP release A/D and stay still through REOPEN and VIEW COMPLETE. Do not use Shift, Space, sprint or items.'),
              'execute if score #case blind_vib matches 34..35 run '+tell('MINING SUPPORTS: unpause and stay still for GROUND CHECK and VIEW STARTED. At WORK mine the upper obsidian beside you with slot1 until STOP, then release left click and watch. Do not walk, crouch, jump or use other items.'),
              'execute if score #case blind_vib matches 32..33 run '+tell('HEARING: stand still on your exact spot. At WORK hold left click with the supplied iron pickaxe on the obsidian beside you until STOP. Release left click at STOP and watch until VIEW COMPLETE. Do not walk, crouch, jump or use other items.'),
              tell('Each view lasts 24 seconds. Give your first account before the operator reads diagnostics. Pause for Start.'),'return 1']
    scripts['channels_scene'] = '\n'.join(scene)
    scripts['channels_start'] = '\n'.join(live + [
        'execute store result score #started blind_vib run fdlab channels_start',
        'execute unless score #started blind_vib matches 1 run return 0',
        'title @s clear',tell('Stay still and watch. Follow the WORK/WALK and STOP cues if shown.'),'return 1'])
    tick = live + ['execute unless score #stage blind_vib matches 2 run return 0',
                   'execute store result score #channel_recorded blind_vib run fdlab channels_tick',
                   'execute unless score #channel_recorded blind_vib matches 1 run return 0']
    for cases,at,message in [('34..35',80,'WORK: hold left click on the upper obsidian with your iron pickaxe.'),
                             ('34..35',240,'STOP: release left click and stay still through VIEW COMPLETE.'),
                             ('32..33',80,'WORK: hold left click on the obsidian with your iron pickaxe.'),
                             ('32..33',240,'STOP: release left click. Stay still and watch until VIEW COMPLETE.'),
                             ('12..13',80,'WORK: hold left click on the obsidian with your iron pickaxe.'),
                             ('14..15',80,'WALK: use A/D on your stone platform. Keep facing the BA.'),
                             ('12..15',240,'STOP: stop working or walking. Keep watching.'),
                             ('22..24',80,'WALK: use A/D back and forth on your platform. Keep the requested posture.'),
                             ('22..24',240,'STOP: stop moving and keep watching. Keep the requested posture until VIEW COMPLETE.'),
                             ('25..28',80,'WALK: keep LEFT SHIFT held and use A/D back and forth on your platform.'),
                             ('25..28',240,'STOP: release A/D only. Keep LEFT SHIFT held until VIEW COMPLETE.'),
                             ('29..30',80,'WALK: use A/D back and forth, standing normally. Face the BA and turn before the edges.'),
                             ('29..30',240,'STOP: release A/D. Stand still and watch until VIEW COMPLETE.'),
                             ('31',80,'MOVE: use A/D back and forth while hovering. Stay inside x16–19/z3–7; do not use Shift or Space.'),
                             ('31',121,'COVERED: keep moving sideways. The aperture is closed.'),
                             ('31',300,'STOP: release A/D and hover still until VIEW COMPLETE.'),
                             ('31',361,'REOPEN: stay still and observe the next returned clicks.')]:
        prefix='execute if score #stage blind_vib matches 2 if score #case blind_vib matches '+cases+' if score #timer blind_vib matches '+str(at)+' run '
        tick += [prefix+tell(message),prefix+'title @s actionbar '+json.dumps({'text':message,'color':'yellow'})]
    tick += ['execute if score #stage blind_vib matches 3 run title @s title '+json.dumps({'text':'VIEW COMPLETE','color':'aqua'}),
             'execute if score #stage blind_vib matches 3 if score #case blind_vib matches 25..28 run '+tell('INPUT RECORDING ENDED. You may release LEFT SHIFT now.'),
             'execute if score #stage blind_vib matches 3 run '+tell('The BA is held and Creative is restored. Describe what you saw and heard, then pause. Do not restart.'),'return 1']
    scripts['channels_tick'] = '\n'.join(tick)
    return scripts


def cave_layout():
    """A constructed underground loop with stone pillars and unlit side chambers."""
    def segment(x, z, a, b):
        dx, dz = b[0]-a[0], b[1]-a[1]
        t = max(0, min(1, ((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz)))
        return (x-a[0]-t*dx)**2+(z-a[1]-t*dz)**2 <= 2.5**2
    centers = [(15,0,12,10),(43,0,14,10),(29,20,14,8),(32,-15,15,6)]
    links = [((15,0),(32,-15)),((32,-15),(43,0)),((43,0),(29,20)),((29,20),(15,0)),((15,0),(43,0))]
    columns = {}
    for x in range(2,62):
        for z in range(-22,30):
            if any(((x-cx)/rx)**2+((z-cz)/rz)**2 <= 1 for cx,cz,rx,rz in centers) or any(segment(x,z,a,b) for a,b in links):
                if 14 <= x <= 17 and -2 <= z <= 2 or 37 <= x <= 40 and -1 <= z <= 3:
                    continue
                columns[(x,z)] = -39 + ((x//4+z//5)%3)
    rows = ['fill 0 -49 -24 31 -34 31 minecraft:stone','fill 32 -49 -24 63 -34 31 minecraft:stone']
    # Consecutive equal-height columns become one fill, preserving irregular room outlines.
    for z in range(-22,30):
        x=2
        while x<62:
            if (x,z) not in columns:
                x+=1;continue
            end=x
            while (end+1,z) in columns and columns[(end+1,z)]==columns[(x,z)]:end+=1
            rows.append(f'fill {x} -44 {z} {end} {columns[(x,z)]} {z} minecraft:air');x=end+1
    for x,z in [(5,0),(23,8),(54,0)]:rows.append(f'setblock {x} -45 {z} minecraft:sea_lantern')
    return rows


def cave_scripts(participant):
    namespace='blind_vibration_lab'
    live=['execute unless function '+namespace+':access run return 0',
          'execute unless entity @s[tag=blind_lab_player] run return 0',
          'execute unless score #run blind_vib matches 18..19 run return 0',
          'execute unless score #case blind_vib matches 18..19 run return 0']
    scripts={'cave': '\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 17 run return 0',
        'execute unless score #case blind_vib matches 17 run return 0',
        'execute store result score #preserved blind_vib run fdlab channels_archived',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 18','scoreboard players set #case blind_vib 18',
        'return run function '+namespace+':cave_scene'])}
    # Load only the bounded setup area; no forced chunks are retained during play.
    scene=participant+live+[
        'execute unless score #stage blind_vib matches 9 run return 0',
        'execute store result score #loaded blind_vib run fdlab cave_load',
        'execute unless score #loaded blind_vib matches 1 run return 0',
        'execute store result score #isolation_result blind_vib run fdlab behavior_isolate',
        'execute unless score #isolation_result blind_vib matches 1 run return 0',
        'execute store result score #cleared blind_vib run fdlab workbench_clear',
        'execute unless score #cleared blind_vib matches 1 run return 0']+cave_layout()+[
        'gamerule mobGriefing true','gamerule doMobSpawning false',
        'gamerule announceAdvancements false','gamerule keepInventory true',
        'difficulty normal','fd world set phase 6 late',
        'effect clear @s minecraft:night_vision',
        'tp @s 23.5 -44 0.5 90 0','spawnpoint @s 23 -44 0',
        'item replace entity @s hotbar.0 with minecraft:iron_pickaxe',
        'item replace entity @s hotbar.1 with minecraft:iron_sword',
        'item replace entity @s hotbar.2 with minecraft:cooked_beef 32',
        'item replace entity @s hotbar.3 with minecraft:torch 32',
        'item replace entity @s hotbar.4 with minecraft:cobblestone 32',
        'item replace entity @s hotbar.5 with minecraft:oak_planks 16',
        'item replace entity @s armor.head with frozendawn:eva_helmet',
        'item replace entity @s armor.chest with frozendawn:eva_chestplate',
        'item replace entity @s armor.legs with frozendawn:eva_leggings',
        'item replace entity @s armor.feet with frozendawn:eva_boots',
        'item replace entity @s inventory.0 with frozendawn:o2_tank_mk3',
        'summon frozendawn:architect 8.5 -44 0.5 {BlindArchitect:1b,NoAI:1b,OnGround:1b,PersistenceRequired:1b,Rotation:[225f,0f],Tags:["blind_lab_actor"]}']
    for holder in ('#timer','#frames','#last_frame','#inconclusive','#archived','#cave_end'):
        scene.append('scoreboard players set '+holder+' blind_vib 0')
    scene+=['scoreboard players set #stage blind_vib 1','title @s times 10 60 10',
            'title @s title '+json.dumps({'text':'CAVE ENCOUNTER','color':'aqua'}),
            tell('Ready. Three minutes in Survival. Move, hide, build, mine or defend yourself naturally. The cave loops through dark side rooms. EVA, oxygen, food and torches are supplied.'),
            tell('Pause and tell the operator you are ready. The BA is held until Start. Death or the time limit ends capture. You can pause early and ask to stop.'),'return 1']
    scripts['cave_scene']='\n'.join(scene)
    scripts['cave_start']='\n'.join(live+[
        'execute store result score #started blind_vib run fdlab cave_start',
        'execute unless score #started blind_vib matches 1 run return 0','title @s clear',
        tell('The encounter is live. Explore and hide naturally. Three minutes maximum.'),'return 1'])
    scripts['cave_tick']='\n'.join(live+[
        'execute unless score #stage blind_vib matches 2 run return 0',
        'execute store result score #cave_recorded blind_vib run fdlab cave_tick',
        'execute unless score #cave_recorded blind_vib matches 1 run return 0',
        'execute if score #stage blind_vib matches 2 if score #timer blind_vib matches 2400 run '+tell('One minute remains.'),
        'execute if score #stage blind_vib matches 3 run function '+namespace+':cave_finished','return 1'])
    scripts['cave_finished']='\n'.join(live+[
        'execute unless score #stage blind_vib matches 3 run return 0',
        'title @s subtitle '+json.dumps({'text':'Describe what happened, then pause.','color':'white'}),
        'title @s title '+json.dumps({'text':'ENCOUNTER COMPLETE','color':'aqua'}),
        tell('Capture ended. Describe what you saw before we inspect the trace. The BA is now held.'),'return 1'])
    scripts['cave_stop']='\n'.join(live+[
        'execute store result score #cave_stopped blind_vib run fdlab cave_stop',
        'execute unless score #cave_stopped blind_vib matches 1 run return 0',
        'return run function '+namespace+':cave_finished'])
    scripts['cave_range']='\n'.join(participant+[
        'execute unless score #stage blind_vib matches 3 run return 0',
        'execute unless score #run blind_vib matches 18 run return 0',
        'execute unless score #case blind_vib matches 18 run return 0',
        'execute store result score #preserved blind_vib run fdlab cave_archived',
        'execute unless score #preserved blind_vib matches 1 run return 0',
        'scoreboard players set #stage blind_vib 9',
        'scoreboard players set #run blind_vib 19','scoreboard players set #case blind_vib 19',
        'return run function '+namespace+':cave_scene'])
    scripts['cave_range_stop']='\n'.join(live+[
        'execute unless score #run blind_vib matches 19 run return 0',
        'return run function '+namespace+':cave_stop'])

    return scripts


def write_pack(destination, fixture=False, case='first'):
    title, namespace, seconds = CASES[case]
    functions = destination / ('data/'+namespace+'/function')
    functions.mkdir(parents=True, exist_ok=True)
    for name, script in scripts_for(case).items():
        (functions / (name+'.mcfunction')).write_text(script+'\n')
    if case.startswith('vibration_'):
        qa = destination / ('data/'+namespace+'/qa')
        qa.mkdir(parents=True, exist_ok=True)
        (qa/'layout.json').write_text(json.dumps(vibration_layout(case),indent=2)+'\n')
    if not fixture:
        (destination/'pack.mcmeta').write_text(json.dumps(pack_metadata(title))+'\n')
        write_sync_advancement(destination)
        tags = destination/'data/minecraft/tags/function'
        tags.mkdir(parents=True, exist_ok=True)
        for name in ('load','tick'):
            (tags/(name+'.json')).write_text(json.dumps({'values':[namespace+':'+name]})+'\n')


METADATA_KEYS = ('version', 'DataVersion', 'Version', 'WorldGenSettings')


def encode_metadata(original, root_name='', case='first'):
    # Only immutable generator/version schema comes from the closed native fixture.
    # No Player, chunks, SavedData, history, entities, scores or prior datapacks are copied.
    if original.get('version') != (3, 19133):
        raise ValueError('Template must use the current Anvil storage version 19133')
    data = {name:original[name] for name in METADATA_KEYS}
    data['WorldGenSettings'][1]['seed']=(4,77371337)
    # NeoForge's required combined mod_data root must precede our filter. Per-mod
    # IDs let the loader append mod_data afterward, reintroducing advancements.
    data.update({'LevelName':(8,CASES[case][0]),'GameType':(3,1),'allowCommands':(1,1),'Difficulty':(1,2),
        'DifficultyLocked':(1,0),'hardcore':(1,0),'initialized':(1,1),'Time':(4,0),'DayTime':(4,6000),
        'LastPlayed':(4,int(time.time()*1000)),'SpawnX':(3,24),'SpawnY':(3,64),'SpawnZ':(3,5),
        'SpawnAngle':(5,90.0),'WasModded':(1,1),
        'GameRules':(10,{'doMobSpawning':(8,'false'),'keepInventory':(8,'true'),'announceAdvancements':(8,'false')}),
        'DataPacks':(10,{'Enabled':(9,(8,['vanilla','mod_data','file/blind-architect-lab'])),'Disabled':(9,(8,[]))}),
        'enabled_features':(9,(8,['minecraft:vanilla']))})
    root = {'Data':(10,data)}
    encoded = b'\x0a'+utf(root_name)+write(10,root)
    # Independently round-trip the emitted schema before writing the new save.
    check=io.BytesIO(encoded);assert check.read(1)==b'\x0a';string(check)
    assert read(check,10)==root and check.read()==b''
    return encoded


def prepare(template, case='first'):
    title, namespace, seconds = CASES[case]
    destination = ROOT/'run-lab/saves'/title
    if destination.exists():
        raise SystemExit(f'Refusing to overwrite existing save: {destination}')
    status = json.loads((ROOT/'run-lab/lab-bridge/status.json').read_text())
    if status['worldLoaded'] or time.time()*1000-status['heartbeat']>5000 or Path(status['checkout']).resolve()!=ROOT:
        raise SystemExit('Prepare only while the owning lab client is idle with a fresh heartbeat')
    stream = io.BytesIO(gzip.decompress(template.read_bytes()))
    assert stream.read(1)==b'\x0a'
    root_name = string(stream)
    original = read(stream,10)['Data'][1]
    encoded = encode_metadata(original, root_name, case)
    destination.mkdir(parents=True)
    (destination/'level.dat').write_bytes(gzip.compress(encoded))
    (destination/'data').mkdir()
    (destination/'data/frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(original['DataVersion'][1])))
    write_pack(destination/'datapacks/blind-architect-lab', case=case)
    metadata={'world':title,'case':case,'duration_seconds':seconds,'namespace':namespace,'seed':77371337,'status':'Prepared, not opened or started','template_metadata_only':str(template.resolve()),
        'copied_metadata_keys':list(METADATA_KEYS),'copied_gameplay_files':0,
        'fresh_lab_settings':{'difficulty_preset':'default','difficulty_locked':True,'advancements':'filtered by world-local datapack'},
        'files':{str(p.relative_to(destination)):hashlib.sha256(p.read_bytes()).hexdigest() for p in destination.rglob('*') if p.is_file()}}
    (destination/'blind-lab-preparation.json').write_text(json.dumps(metadata,indent=2)+'\n')
    print(f'Prepared fresh {destination}. Owner opens it; setup stages actor with NoAI. Click Start when ready.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack-only',type=Path)
    parser.add_argument('--template',type=Path)
    parser.add_argument('--case', choices=CASES, default='first')
    args=parser.parse_args()
    if args.pack_only:
        for case in CASES:
            write_pack(args.pack_only,True,case)
        # Use the same metadata encoder as preparation, then let Minecraft's
        # actual world-list reader validate it in the native gate. World-list
        # scanning skips WorldGenSettings; this fixture tests save discovery.
        fixture = {'version':(3,19133), 'DataVersion':(3,3955),
            'Version':(10,{'Name':(8,'1.21.1'),'Id':(3,3955),'Series':(8,'main'),'Snapshot':(1,0)}),
            'WorldGenSettings':(10,{'seed':(4,0)})}
        metadata = args.pack_only/'data/blind_lab/qa/level.dat'
        metadata.parent.mkdir(parents=True, exist_ok=True)
        metadata.write_bytes(gzip.compress(encode_metadata(fixture)))
        (metadata.parent/'frozendawn_apocalypse.dat').write_bytes(gzip.compress(lab_state(3955)))
        (metadata.parent/'quiet-pack.mcmeta').write_text(json.dumps(pack_metadata('Blind lab'))+'\n')
    elif args.template:
        prepare(args.template,args.case)
    else:
        parser.error('Use --template with a closed native fixture level.dat, or --pack-only')
