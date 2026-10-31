package com.frozendawn.labbridge;

import com.frozendawn.airlock.AirlockSavedState;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.data.RoomIdentityState;
import com.frozendawn.data.RoomThermalState;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Opt-in load proof on an explicitly marked disposable copy; never distributed. */
@EventBusSubscriber(modid="frozendawn",value=Dist.CLIENT)
public final class LabRoomArchiveReplay {
    private LabRoomArchiveReplay() { }
    static boolean matches(Path actual,Path expected,String token) throws java.io.IOException {
        var root=actual.toRealPath();
        return root.equals(expected.toRealPath())&&root.getFileName().toString().startsWith("Power Airlock Archive Load ")
                &&Files.readString(root.resolve(".room-set-archive-replay")).equals(token);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void started(ServerStartedEvent event) {
        String configured=System.getProperty("frozendawn.labRoomArchiveReplay");
        if(configured==null||FMLEnvironment.production||event.getServer().isDedicatedServer())return;
        MinecraftServer server=event.getServer();JsonObject spec;
        try {
            spec=JsonParser.parseString(Files.readString(Path.of(configured))).getAsJsonObject();
            if(!matches(server.getWorldPath(LevelResource.ROOT),Path.of(spec.get("worldRoot").getAsString()),spec.get("token").getAsString())) {
                System.err.println("[RoomArchiveReplay] Refused: active world is not the marked disposable copy");return;
            }
        }catch(Exception error){System.err.println("[RoomArchiveReplay] Refused: "+error);return;}
        var result=new JsonObject();result.addProperty("pid",ProcessHandle.current().pid());
        result.addProperty("world",server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
        result.addProperty("serverTick",server.getTickCount());result.addProperty("beforeFirstTick",server.getTickCount()==0);
        try {
            if(server.getTickCount()!=0)throw new IllegalStateException("Load comparison must run before gameplay advances");
            var level=server.overworld();var expected=Path.of(spec.get("baselineDirectory").getAsString());var components=new JsonObject();
            var identities=RoomIdentityState.get(level);var heat=RoomThermalState.get(level);var gas=AirlockSavedState.get(level);
            check(expected,"frozendawn_room_identities",identities.save(new CompoundTag(),level.registryAccess()),components);
            check(expected,"frozendawn_room_heat",heat.save(new CompoundTag(),level.registryAccess()),components);
            check(expected,"frozendawn_airlocks",gas.save(new CompoundTag(),level.registryAccess()),components);
            check(expected,"frozendawn_room_air",RoomAirState.get(level).save(new CompoundTag(),level.registryAccess()),components);
            result.add("components",components);var rooms=new JsonArray();
            for(var tag:identities.save(new CompoundTag(),level.registryAccess()).getList("rooms",Tag.TAG_COMPOUND)) {
                var room=(CompoundTag)tag;var cells=room.getLongArray("cells");var bytes=ByteBuffer.allocate(cells.length*8);
                for(long pos:cells)bytes.putLong(pos);
                var row=new JsonObject();row.addProperty("id",room.getLong("id"));row.addProperty("cells",cells.length);
                row.addProperty("membershipSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.array())));rooms.add(row);
                if(!(identities.at(net.minecraft.core.BlockPos.of(cells[0])).cells() instanceof ImmutableSet))throw new IllegalStateException("Membership did not load through Guava");
            }
            result.add("rooms",rooms);var chambers=new JsonArray();
            for(var chamber:gas.chambers()) {
                if(!(chamber.cells instanceof ImmutableSet)||!(chamber.doors instanceof ImmutableSet))throw new IllegalStateException("Chamber freeze did not use Guava");
                var row=new JsonObject();row.addProperty("id",chamber.id);row.addProperty("cells",chamber.cells.size());row.addProperty("air",chamber.gas.air());row.addProperty("reserve",chamber.gas.reserve());
                var values=new JsonArray();for(int value:chamber.gas.snapshot())values.add(value);row.add("snapshot",values);chambers.add(row);
            }
            result.add("chambers",chambers);for(var room:heat.rooms.values())if(!(room.cells instanceof ImmutableSet)||!(room.faces instanceof ImmutableSet)||!(room.structure instanceof ImmutableSet))throw new IllegalStateException("Thermal membership did not use Guava");
            result.addProperty("result","PASS");
        }catch(Exception error){result.addProperty("result","FAIL");result.addProperty("error",error.toString());}
        try{Files.writeString(Path.of(spec.get("resultPath").getAsString()),result.toString()+"\n",StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
        catch(Exception error){System.err.println("[RoomArchiveReplay] Could not write result: "+error);}
        System.out.println("[RoomArchiveReplay] "+result);
        // The exact marked copy only: preserve the loaded state without a production/room tick.
        server.halt(false);
    }
    private static void check(Path directory,String name,CompoundTag actual,JsonObject results)throws Exception {
        var baseline=NbtIo.readCompressed(directory.resolve(name+".dat"),NbtAccounter.unlimitedHeap()).getCompound("data");
        if(!actual.equals(baseline))throw new IllegalStateException("Loaded saved data changed: "+name+" expected="+baseline+" actual="+actual);
        results.addProperty(name,true);
    }
}
