package com.frozendawn.labbridge;

import com.frozendawn.block.ThermalHeaterBlockEntity;
import com.frozendawn.block.ThermalHeaterBlock;
import com.frozendawn.init.ModBlocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Supplies and removes real heater fuel; ordinary block ticks alone emit machine sensing. */
public final class LabBlindMachine {
    public static final BlockPos MACHINE = new BlockPos(19,-44,5);
    private LabBlindMachine() {}
    public static boolean geometry(ServerLevel level) {
        if(!level.getBlockState(MACHINE.below()).is(net.minecraft.world.level.block.Blocks.STONE))return false;
        for(var direction:net.minecraft.core.Direction.values()) {
            if(direction!=net.minecraft.core.Direction.DOWN&&!level.getBlockState(MACHINE.relative(direction)).isAir())return false;
            if(direction!=net.minecraft.core.Direction.UP&&!level.getBlockState(MACHINE.below().relative(direction)).isAir())return false;
        }
        return level.getBlockState(MACHINE).is(ModBlocks.THERMAL_HEATER.get())
                && level.getBlockEntity(MACHINE) instanceof ThermalHeaterBlockEntity
                && level.getBlockState(LabBlindDoor.DOOR).isAir()
                && level.getBlockState(LabBlindDoor.DOOR.above()).isAir();
    }
    public static JsonObject state(ServerLevel level) {
        var row=new JsonObject();row.addProperty("position",MACHINE.toShortString());row.addProperty("valid",geometry(level));
        if(geometry(level)) {
            var heater=(ThermalHeaterBlockEntity)level.getBlockEntity(MACHINE);
            row.addProperty("lit",heater.isLit());row.addProperty("blockLit",level.getBlockState(MACHINE).getValue(ThermalHeaterBlock.LIT));
            row.addProperty("burnTime",heater.saveWithoutMetadata(level.registryAccess()).getInt("BurnTime"));
        }
        return row;
    }
    static boolean control(CommandSourceStack source,Path directory,int timer) {
        var level=source.getLevel();if(!geometry(level))return false;
        var heater=(ThermalHeaterBlockEntity)level.getBlockEntity(MACHINE);
        try {
            if(timer==80||timer==240) {
                if(timer==80)heater.addFuel(10000);else heater.extinguish();
                var row=state(level);row.addProperty("timer",timer);row.addProperty("gameTime",level.getGameTime());
                row.addProperty("action",timer==80?"SUPPLY_FUEL":"EXTINGUISH");
                Files.writeString(directory.resolve("machines.jsonl"),row+"\n",StandardOpenOption.APPEND);
            }
            boolean expected=timer>=80&&timer<240;
            return heater.isLit()==expected && level.getBlockState(MACHINE).getValue(ThermalHeaterBlock.LIT)==expected;
        }catch(Exception failure){return false;}
    }
    static boolean performed(Path directory,JsonArray decisions)throws Exception {
        var changes=Files.readAllLines(directory.resolve("machines.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
        var frames=Files.readAllLines(directory.resolve("trace.jsonl")).stream().map(x->JsonParser.parseString(x).getAsJsonObject()).toList();
        if(changes.size()!=2||frames.size()!=480||changes.get(0).get("timer").getAsInt()!=80||changes.get(1).get("timer").getAsInt()!=240
                ||!changes.get(0).get("action").getAsString().equals("SUPPLY_FUEL")||!changes.get(1).get("action").getAsString().equals("EXTINGUISH"))return false;
        int previous=10001;boolean burned=false;
        for(var f:frames) {
            var machine=f.getAsJsonObject("machine");int at=f.get("timer").getAsInt(),fuel=machine.get("burnTime").getAsInt();boolean active=at>=80&&at<240;
            if(!machine.get("valid").getAsBoolean()||machine.get("lit").getAsBoolean()!=active||machine.get("blockLit").getAsBoolean()!=active
                    ||(active?(fuel<=0||fuel>previous):fuel!=0))return false;
            if(active){burned|=fuel<10000;previous=fuel;}
        }
        // No lab emit/vibrate call: these records must come from the real block ticker.
        int air=0,vibration=0;
        for(var value:decisions) {
            var e=value.getAsJsonObject();String kind=e.get("event").getAsString();
            if(!kind.equals("BLIND_AIR")&&!kind.equals("BLIND_VIBRATION"))continue;
            if(!e.get("detail").getAsString().contains("kind=MACHINE "))return false;
            long tick=e.get("tick").getAsLong();
            if(tick<changes.get(0).get("gameTime").getAsLong()||tick>changes.get(1).get("gameTime").getAsLong())return false;
            if(kind.equals("BLIND_AIR"))air++;else vibration++;
        }
        return burned&&air>=1&&air<=2&&vibration==air;
    }
}
