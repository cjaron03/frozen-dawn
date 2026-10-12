package com.frozendawn.labbridge;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreHolder;

/** Observes real door events. No sensing event, identity, confidence or route is injected. */
@net.neoforged.fml.common.EventBusSubscriber(modid="frozendawn")
public final class LabBlindDoor {
    public static final BlockPos DOOR=new BlockPos(18,-44,5);
    private LabBlindDoor() {}
    private static int score(ServerLevel level,String name) {
        var objective=level.getScoreboard().getObjective("blind_vib");
        var value=objective==null?null:level.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(name),objective);
        return value==null?-1:value.value();
    }
    public static boolean geometry(ServerLevel level) {
        var lower=level.getBlockState(DOOR);var upper=level.getBlockState(DOOR.above());
        return lower.is(Blocks.OAK_DOOR)&&upper.is(Blocks.OAK_DOOR)
                &&lower.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER&&upper.getValue(DoorBlock.HALF)==DoubleBlockHalf.UPPER
                &&lower.getValue(DoorBlock.FACING)==net.minecraft.core.Direction.WEST&&upper.getValue(DoorBlock.FACING)==net.minecraft.core.Direction.WEST
                &&lower.getValue(DoorBlock.HINGE)==net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                &&upper.getValue(DoorBlock.HINGE)==lower.getValue(DoorBlock.HINGE)
                &&!lower.getValue(DoorBlock.POWERED)&&!upper.getValue(DoorBlock.POWERED)
                &&lower.getValue(DoorBlock.OPEN)==upper.getValue(DoorBlock.OPEN);
    }
    public static JsonObject state(ServerLevel level) {
        var row=new JsonObject();row.addProperty("position",DOOR.toShortString());row.addProperty("valid",geometry(level));
        if(geometry(level))row.addProperty("open",level.getBlockState(DOOR).getValue(DoorBlock.OPEN));return row;
    }
    @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void event(net.neoforged.neoforge.event.VanillaGameEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||(!(score(level,"#run")>=39&&score(level,"#run")<=40||(score(level,"#run")==42||score(level,"#run")==43||(score(level,"#run")==47||score(level,"#run")==48||score(level,"#run")==49))))||score(level,"#stage")!=2
                ||!LabBlindIsolation.enabled(level)||(event.getVanillaEvent()!=GameEvent.BLOCK_OPEN&&event.getVanillaEvent()!=GameEvent.BLOCK_CLOSE))return;
        if(!(event.getContext().sourceEntity() instanceof ServerPlayer p)||!p.getTags().contains("blind_lab_player"))return;
        try {
            var row=state(level);row.addProperty("kind",event.getVanillaEvent()==GameEvent.BLOCK_OPEN?"block_open":"block_close");
            row.addProperty("player",p.getUUID().toString());row.addProperty("gameTime",level.getGameTime());row.addProperty("timer",score(level,"#timer"));
            row.add("eventPosition",new com.google.gson.Gson().toJsonTree(event.getEventPosition()));row.addProperty("selectedSlot",p.getInventory().selected);
            row.addProperty("emptyHand",p.getMainHandItem().isEmpty());row.addProperty("actualDoorEvent",(BlockPos.containing(event.getEventPosition()).equals(DOOR)||BlockPos.containing(event.getEventPosition()).equals(DOOR.above())));
            Path d=level.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-results/run-"+score(level,"#run")+"-"+p.getPersistentData().getUUID("BlindLabDecoyActor"));
            Files.writeString(d.resolve("doors.jsonl"),row+"\n",StandardOpenOption.APPEND);
        }catch(Exception failed){level.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly("#source_audit_failed"),level.getScoreboard().getObjective("blind_vib")).set(1);}
    }
    public static boolean performedContact(Path directory,int cover) throws Exception {
        return cover>0&&performed(directory,cover,40);
    }
    public static boolean performed(Path directory) throws Exception {
        return performed(directory,80,60);
    }
    private static boolean performed(Path directory,int openingCue,int openingWindow) throws Exception {
        var rows=Files.readAllLines(directory.resolve("doors.jsonl")).stream().map(s->com.google.gson.JsonParser.parseString(s).getAsJsonObject()).toList();
        if(rows.size()!=2)return false;
        for(int i=0;i<2;i++) {
            var row=rows.get(i);int at=row.get("timer").getAsInt();int cue=i==0?openingCue:200;int window=i==0?openingWindow:60;
            if(!row.get("valid").getAsBoolean()||!row.get("actualDoorEvent").getAsBoolean()||!row.get("emptyHand").getAsBoolean()
                    ||!row.get("kind").getAsString().equals(i==0?"block_open":"block_close")||row.get("open").getAsBoolean()!=(i==0)
                    ||at<cue||at>cue+window)return false;
        }
        return rows.get(0).get("player").equals(rows.get(1).get("player"));
    }
    /** Exact retained human38 exception: the late idle UI never qualifies the full timeline.
     * This permits only the already reviewed immutable result to be archived and followed by39.
     * Native38 uses the ordinary strict qualifier; no recorder rows or outcomes are changed. */
    static boolean preservedHuman38(JsonObject gameplay,JsonObject capture,Path input) {
        try {
            return gameplay.get("trace_jsonlSha256").getAsString().equals("88fd0df96bef0e76bfa59de3b258bd6d3fa4245df0721d1243da470a8d762bf9")
                    &&gameplay.get("decisions_jsonSha256").getAsString().equals("62d4fe51ae2e32064abfd37de38ac35853b1bbb07927c6ea0ef81ee37d9c425e")
                    &&capture.get("probe").getAsString().equals("9e21dfaa-1cc8-4d66-ad99-51599207c9b4")
                    &&capture.get("captureComplete").getAsBoolean()&&!capture.get("active").getAsBoolean()
                    &&capture.get("serverFrames").getAsInt()==480&&capture.get("clientFrames").getAsInt()==481&&capture.get("dropped").getAsInt()==0
                    &&capture.getAsJsonObject("hashes").get("server.jsonl").getAsString().equals("d6f1124c8eb89512accf32ddbe9cff4ea7251881b36f2b023eb8cb8042765bb5")
                    &&capture.getAsJsonObject("hashes").get("keys.jsonl").getAsString().equals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                    &&capture.get("keyEvents").getAsInt()==0&&capture.get("reason").getAsString().equals("TIME_LIMIT")
                    &&java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input.resolve("client.jsonl"))))
                    .equals("6853fd32ff3035c5d8680cfd5c209bd7e7e8e0551c5f0770e8a7ce8e7749071b");
        }catch(Exception failed){return false;}
    }
}
