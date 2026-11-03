#!/usr/bin/env python3
"""Storage QA on a preserved copy of the accepted room replay. Preparation is owner-clicked once."""
import argparse
import gzip
import hashlib
import json
import shutil
from pathlib import Path
from prepare_emergency_eva_playtest import compound_child, payload_end
NS="o2_storage_check"
TITLE="Room O2 Storage Check"
GUARD="execute unless score #ready ostore matches 1 run return 0"
def tell(text):return "tellraw @s "+json.dumps({"text":text,"color":"aqua"})
def row(buttons):return "tellraw @s "+json.dumps([{"text":""}]+[{"text":label+"  ","color":"aqua","clickEvent":{"action":"run_command","value":value if value.startswith("/") else "/function "+NS+":"+value}} for label,value in buttons])
def scripts():
    def action(commands,text):return "\n".join([GUARD,*commands,tell(text),"return 1"])
    setup=["execute unless score #built roxygen matches 1 run return 0","execute unless score #ready ostore matches 0 run return 0","scoreboard players set #ready ostore -1","gamemode creative @s",
        "setblock -5 66 0 minecraft:glass","setblock 0 65 0 minecraft:stone","setblock 0 67 0 frozendawn:geothermal_core",
        "setblock 0 65 -1 minecraft:stone","setblock 0 66 -1 minecraft:stone",
        "setblock -3 65 1 frozendawn:oxygen_reservoir[facing=north]",
        "item replace block 0 66 0 container.3 with frozendawn:o2_tank_mk3[frozendawn:o2_level=0]",
        # Small native-built 18-cell chamber outside the existing right room; its own air stays dedicated.
        "fill 5 64 -2 9 67 2 minecraft:glass hollow","fill 5 64 -2 9 64 2 minecraft:stone",
        "setblock 5 65 0 frozendawn:airlock_door[facing=east,half=lower]","setblock 5 66 0 frozendawn:airlock_door[facing=east,half=upper]",
        "setblock 9 65 0 frozendawn:airlock_door[facing=east,half=lower]","setblock 9 66 0 frozendawn:airlock_door[facing=east,half=upper]",
        "setblock 7 66 -2 frozendawn:airlock_controller[facing=south]",
        "function room_oxygen_check:repair_cows","tp @s -2.5 65 .5 0 15",
        "give @s minecraft:iron_ingot 6","give @s minecraft:copper_ingot 2","give @s minecraft:glass 1",
        "give @s frozendawn:oxygen_reservoir 1","give @s patchouli:guide_book[patchouli:book=\"frozendawn:frozen_dawn_guide\"]",
        "scoreboard players set #ready ostore 1",tell("Prepared once. Empty tank + empty Core canister + airlock reserve share spare production. Original world preserved."),"function "+NS+":controls","return 1"]
    return {
      "load":"scoreboard objectives add ostore dummy\nexecute unless score #ready ostore matches 1 run scoreboard players set #ready ostore 0\nexecute as @a run function "+NS+":controls",
      "controls":"\n".join(["execute unless score #built roxygen matches 1 run return 0",tell("O2 STORAGE CHECK"),"execute if score #ready ostore matches 0 run "+row([("[Prepare copy]","prepare")]),"execute if score #ready ostore matches 1 run "+row([("[Supply 16]","support"),("[Supply 8]","low"),("[Read]","read")]),"execute if score #ready ostore matches 1 run "+row([("[Breach]","breach"),("[Seal]","seal"),("[Refill suit]","suit")]),"execute if score #ready ostore matches 1 run "+row([("[Second tank]","second"),("[Left]","left"),("[Airlock]","airlock")]),tell("Start in Creative. Supply 16 charges; supply 8 draws 2/s for the two cows. Wait 20s between reads."),"return 1"]),
      "prepare":"\n".join(setup),
      "support":action(["setblock 0 67 0 frozendawn:geothermal_core"],"Production 16. Reserve fills only from spare production."),
      "low":action(["setblock 0 67 0 minecraft:stone"],"Production 8. Watch ON RESERVE and contents fall; it cannot recharge other storage."),
      "breach":action(["setblock -5 66 0 minecraft:air"],"Tank valve closes. Contents must stop falling. Stay Creative."),
      "seal":action(["setblock -5 66 0 minecraft:glass"],"Seal repaired. Normal gas recovery takes 5s before the valve reconnects."),
      "suit":action(["item replace entity @s armor.head with frozendawn:eva_helmet","item replace entity @s armor.chest with frozendawn:eva_chestplate","item replace entity @s armor.legs with frozendawn:eva_leggings","item replace entity @s armor.feet with frozendawn:eva_boots","item replace entity @s hotbar.8 with frozendawn:o2_tank_mk3[frozendawn:o2_level=0]"],"Intact EVA adds a fourth refill recipient. Stay Creative; watch the canister refill share."),
      "second":"\n".join([GUARD,"execute if score #second ostore matches 1 run return 0","setblock 3 65 0 frozendawn:oxygen_reservoir[facing=west]","scoreboard players set #second ostore 1",tell("Second empty tank shares the same pool. Added capacity is 1,200s; no new production."),"return 1"]),
      "left":action(["tp @s -2.5 65 .5 0 15"],"Left tank and room gauge."),
      "airlock":action(["tp @s 7.5 65 .5 180 10"],"Airlock panel: its dedicated reserve also shares spare Core production."),
      "read":"\n".join([GUARD,"fd world oxygen here","execute store result score #stored ostore run data get block -3 65 1 StoredOxygen 0.0001","tellraw @s [{\"text\":\"Left tank: \"},{\"score\":{\"name\":\"#stored\",\"objective\":\"ostore\"}},{\"text\":\" player-seconds.\"}]","return 1"])
    }
def pack_only(destination):
    for name,body in scripts().items():
        p=destination/f"data/{NS}/function/{name}.mcfunction";p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body+"\n")
def rename_level(path):
    raw=gzip.decompress(path.read_bytes());assert raw[:3]==b"\x0a\x00\x00"
    kind,data_start,_=compound_child(raw,3,"Data");assert kind==10
    kind,start,end=compound_child(raw,data_start,"LevelName");assert kind==8
    encoded=TITLE.encode();result=raw[:start]+len(encoded).to_bytes(2,"big")+encoded+raw[end:]
    assert payload_end(result,10,3)==len(result)
    path.write_bytes(gzip.compress(result))
def prepare(source,destination):
    if destination.exists():raise SystemExit("Refusing to overwrite an existing save")
    if not (source/"level.dat").is_file():raise SystemExit("Source must be the accepted closed Room O2 Check save")
    before={str(p.relative_to(source)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source.rglob("*") if p.is_file()}
    shutil.copytree(source,destination);rename_level(destination/"level.dat")
    pack=destination/"datapacks/room-oxygen-check";pack_only(pack)
    tags=pack/"data/minecraft/tags/function/load.json";data=json.loads(tags.read_text());data["values"].append(NS+":load");tags.write_text(json.dumps(data)+"\n")
    # Supersede only the copied replay controls; the original save remains byte-identical.
    p=pack/"data/room_oxygen_check/function/controls.mcfunction";p.write_text("function "+NS+":controls\n")
    p=pack/"data/room_oxygen_check/function/load.mcfunction";p.write_text(p.read_text().replace("execute if score #built roxygen matches 1 as @a run function room_oxygen_check:controls", ""))
    after={str(p.relative_to(source)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source.rglob("*") if p.is_file()};assert before==after
    (destination/"storage-preparation.json").write_text(json.dumps({"source":str(source.resolve()),"source_sha256":before,"world":TITLE,"fixture":"Complete accepted-world copy. Only copied metadata/datapack controls changed; owner clicks Prepare once.","rates":"1200/4/8 production defaults, no accelerated reservoir values"},indent=2)+"\n")
    print(destination.resolve())
if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--source",type=Path);parser.add_argument("--destination",type=Path);parser.add_argument("--pack-only",type=Path);args=parser.parse_args()
    if args.pack_only:pack_only(args.pack_only)
    elif args.source and args.destination:prepare(args.source,args.destination)
    else:parser.error("Pass --source/--destination or --pack-only")
