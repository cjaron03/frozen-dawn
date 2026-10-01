#!/usr/bin/env python3
"""Masked presentation check; separate disposable copy, no combat/history reset."""
import argparse, gzip, hashlib, json, secrets, shutil
from pathlib import Path

SCRIPTS = {
    "fixture": "return run fdlab signal_access",
    "load": "scoreboard objectives add msignal dummy\nexecute unless score #stage msignal matches 0.. run scoreboard players set #stage msignal 0",
    "prepare": """execute unless score #stage msignal matches 0 run return 0
gamemode creative @s
tag @s add macs_signal_player
fd world set phase 6 late
gamerule doMobSpawning false
gamerule doDaylightCycle false
gamerule doWeatherCycle false
weather clear
time set noon
forceload remove all
effect clear @s
clear @s
tp @s 12000.5 104 12018.5 180 10
fdlab signal_load
function macs_signal:build
return run fdlab signal_prepare""",
    "next": "return run fdlab signal_next",
    "continue": "return run fdlab signal_next",
    "abort": "return run fdlab signal_abort",
    "build": """fill 11968 96 11964 12032 99 12032 minecraft:stone
fill 11968 100 11964 12032 100 12032 frozendawn:frozen_dirt
fill 11968 101 11964 11999 110 12032 minecraft:air
fill 12000 101 11964 12032 110 12032 minecraft:air
fill 11968 101 11964 11985 101 12032 minecraft:snow[layers=2]
fill 12015 101 11964 12032 101 12032 minecraft:snow[layers=2]
fill 11996 100 12016 12004 100 12022 minecraft:stone_bricks""",
}

def write_pack(path, game_test=False):
    functions = path / 'data/macs_signal/function'; functions.mkdir(parents=True, exist_ok=True)
    for name, content in SCRIPTS.items():
        if name not in ('fixture', 'load'):
            content = 'execute unless function macs_signal:fixture run return 0\n' + content
        (functions / (name + '.mcfunction')).write_text(content + '\n')
    if not game_test:
        (path / 'pack.mcmeta').write_text(json.dumps({'pack':{'pack_format':48,'description':'MACS masked visual check'}}))
        tags = path / 'data/minecraft/tags/function'; tags.mkdir(parents=True, exist_ok=True)
        (tags / 'load.json').write_text(json.dumps({'values':['macs_signal:load']}))

def prepare(source, destination):
    if destination.exists(): raise SystemExit('Refusing to overwrite ' + str(destination))
    raw = gzip.decompress((source / 'level.dat').read_bytes()); marker = b'\x08\x00\x09LevelName'
    assert raw.count(marker) == 1
    at = raw.index(marker) + len(marker); length = int.from_bytes(raw[at:at+2], 'big'); name = b'MACS Signal Check'
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('session.lock'))
    (destination / 'level.dat').write_bytes(gzip.compress(raw[:at]+len(name).to_bytes(2,'big')+name+raw[at+2+length:]))
    shutil.rmtree(destination / 'datapacks'); write_pack(destination / 'datapacks/macs-signal')
    order = [True, True, False, False]; secrets.SystemRandom().shuffle(order)
    key = json.dumps({'schema':1,'order':order,'scope':'production presentation playback only'}, separators=(',',':')).encode()
    (destination / 'lab-signal-order.json').write_bytes(key)
    print(json.dumps({'world':str(destination),'title':name.decode(),'sequence_sha256':hashlib.sha256(key).hexdigest()}))

if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('--pack-only',type=Path); p.add_argument('--source',type=Path); p.add_argument('--destination',type=Path); a=p.parse_args()
    if a.pack_only: write_pack(a.pack_only,True)
    elif a.source and a.destination: prepare(a.source,a.destination)
    else: p.error('Use --pack-only or --source/--destination')
