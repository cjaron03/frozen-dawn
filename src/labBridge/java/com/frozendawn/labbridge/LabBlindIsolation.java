package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Opted-in views7–24 isolation. Lab classes are absent from the distributed mod. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabBlindIsolation {
    // Every source within the receiver's40-block delivery radius lies here.
    private static final AABB AREA = new AABB(0,-49,-12,31,-37,11).inflate(40);
    private static AABB area(ServerLevel level) {
        return score(level, "#run") >= 18 ? new AABB(0,-49,-24,64,-33,32).inflate(40) : AREA;
    }
    private LabBlindIsolation() { }
    private static int score(ServerLevel level,String holder) {
        var board=level.getScoreboard();var objective=board.getObjective("blind_vib");
        if(objective==null)return -1;
        var value=board.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder),objective);
        return value==null?-1:value.value();
    }
    private static void score(ServerLevel level,String holder,int value) {
        var board=level.getScoreboard();
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder),board.getObjective("blind_vib")).set(value);
    }
    private static boolean fixture(Mob mob) {
        return mob instanceof ArchitectEntity actor&&actor.isBlindArchitect()&&actor.getTags().contains("blind_lab_actor");
    }
    static java.util.List<Mob> unrelated(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class,area(level),mob->!fixture(mob));
    }
    private static boolean sourceObject(Entity entity) {
        return !(entity instanceof Mob) && !(entity instanceof ServerPlayer)
                && !(entity instanceof net.minecraft.world.entity.Display && entity.getTags().contains("blind_lab_label"));
    }
    private static java.util.List<Entity> sourceObjects(ServerLevel level) {
        return level.getEntities((Entity) null, area(level), entity -> sourceObject(entity) && !LabBlindDecoy.allowedProjectile(level,entity));
    }
    static boolean sourcesClean(ServerLevel level) {
        if (score(level, "#run") < 12) return true;
        var players = level.getEntitiesOfClass(ServerPlayer.class, area(level));
        return sourceObjects(level).isEmpty() && score(level, "#source_audit_failed") == 0
                && players.size() == 1 && players.getFirst().getTags().contains("blind_lab_player");
    }
    private static JsonObject preserved(Entity entity) {
        var entry = new JsonObject(); entry.addProperty("uuid", entity.getUUID().toString());
        entry.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        entry.addProperty("nbt", entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());
        return entry;
    }
    static boolean enabled(ServerLevel level) {
        if(LabBlindLan.isolationAllowed(level)||LabBlindAttention.isolationAllowed(level)||LabBlindReconnect.isolationAllowed(level))return true;
        int stage=score(level,"#stage");
        int run=score(level,"#run");
        if((run<7||run>53)||score(level,"#case")!=run||score(level,"#isolated")!=run
                ||(stage!=1&&stage!=2&&stage!=3&&stage!=9&&!(stage==4&&run==53&&(score(level,"#unload")==1||score(level,"#stale")==1||score(level,"#persist")>=0&&score(level,"#persist")<=4))))return false;
        return level.players().stream().anyMatch(p->p.getTags().contains("blind_lab_player")
                &&LabBlindReplay.allowed(p.createCommandSourceStack(),"vibration_lab"));
    }
    static boolean unloadSourcesClean(ServerLevel level) {return unrelated(level).isEmpty()&&sourceObjects(level).isEmpty()&&score(level,"#source_audit_failed")==0;}
    /** A fresh UUID may reuse run31's retained clean isolation; never rewrite its archive. */
    static boolean confirmedEchoIsolation(CommandSourceStack source) {
        var level = source.getLevel();
        if (LabBlindWorkbench.access(source) != 1 || !(source.getEntity() instanceof ServerPlayer player)
                || !player.isCreative() || !player.getTags().contains("blind_lab_player")
                || score(level,"#run") != 31 || score(level,"#case") != 31 || score(level,"#isolated") != 31
                || (score(level,"#stage") != 3 && score(level,"#stage") != 9)
                || !enabled(level) || !unrelated(level).isEmpty() || !sourcesClean(level)) return false;
        try {
            var archive = source.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("blind-lab-isolation/run-31-before.json");
            var record = com.google.gson.JsonParser.parseString(Files.readString(archive)).getAsJsonObject();
            return record.get("run").getAsInt() == 31
                    && record.get("world").getAsString().equals(source.getServer().getWorldData().getLevelName())
                    && record.has("preservedMobs") && record.has("preservedSourceObjects");
        } catch (Exception failure) { return false; }
    }
    public static int prepare(CommandSourceStack source) {
        var level=source.getLevel();
        int run=score(level,"#run");
        if(LabBlindWorkbench.access(source)!=1||!(source.getEntity() instanceof ServerPlayer player)
                ||!player.isCreative()||!player.getTags().contains("blind_lab_player")
                ||(run<7||run>53)||score(level,"#case")!=run||score(level,"#stage")!=9)return 0;
        if (score(level,"#isolated") == run) return confirmedEchoIsolation(source) ? 1 : 0;
        var mobs=unrelated(level);
        var objects=run>=12?sourceObjects(level):java.util.List.<Entity>of();
        if(mobs.size()>128 || objects.size()>128)return 0;
        var record=new JsonObject();var preserved=new JsonArray();
        record.addProperty("run",run);record.addProperty("gameTime",level.getGameTime());
        record.addProperty("world",level.getServer().getWorldData().getLevelName());
        for(var mob:mobs) {
            var entry=new JsonObject();entry.addProperty("uuid",mob.getUUID().toString());
            entry.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
            entry.addProperty("nbt",mob.saveWithoutId(new net.minecraft.nbt.CompoundTag()).toString());preserved.add(entry);
        }
        record.add("preservedMobs",preserved);
        if(run>=12) {
            var preservedObjects=new JsonArray(); objects.forEach(entity->preservedObjects.add(preserved(entity)));
            record.add("preservedSourceObjects",preservedObjects);
        }
        try {
            var directory=source.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-isolation");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("run-"+run+"-before.json"),new com.google.gson.Gson().toJson(record)+"\n",StandardOpenOption.CREATE_NEW);
        } catch(java.io.IOException failure) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Isolation evidence failed. Preserve the scene; do not retry."));return 0;
        }
        // Persist each existing mob before removing it without death, drops or events.
        mobs.forEach(Mob::discard);score(level,"#isolated",run);score(level,"#removed_mobs",mobs.size());score(level,"#blocked_mobs",0);
        if(run>=12) {
            objects.forEach(Entity::discard);score(level,"#removed_sources",objects.size());
            score(level,"#blocked_sources",0);score(level,"#source_audit_failed",0);
        }
        return 1;
    }
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!area(level).intersects(event.getEntity().getBoundingBox())
                ||!enabled(level))return;
        var entity=event.getEntity();
        if(LabBlindDecoy.allowedProjectile(level,entity)) { LabBlindDecoy.joined(level,(net.minecraft.world.entity.projectile.Snowball)entity);return; }
        if(entity instanceof Mob mob) {
            if(fixture(mob))return;
            event.setCanceled(true);score(level,"#blocked_mobs",score(level,"#blocked_mobs")+1);
            com.mojang.logging.LogUtils.getLogger().info("[BlindLabIsolation] blocked type={} uuid={} position={}",
                    BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),mob.getUUID(),mob.position());
        } else if(score(level,"#run")>=12 && (score(level,"#run")<=17 || score(level,"#run")>=20 || score(level,"#stage")==9) && sourceObject(entity)) {
            // Terrain replacement can release container contents after prepare().
            // Preserve every such object before denying admission to these isolated views.
            var record=preserved(entity);record.addProperty("run",score(level,"#run"));
            record.addProperty("gameTime",level.getGameTime());record.addProperty("stage",score(level,"#stage"));
            int index=score(level,"#blocked_sources")+1;
            try {
                var directory=level.getServer().getWorldPath(LevelResource.ROOT).resolve("blind-lab-isolation");
                Files.createDirectories(directory);
                Files.writeString(directory.resolve("run-"+score(level,"#run")+"-blocked-source-"+index+"-"+entity.getUUID()+".json"),
                        new com.google.gson.Gson().toJson(record)+"\n",StandardOpenOption.CREATE_NEW);
                event.setCanceled(true);score(level,"#blocked_sources",index);
            } catch(java.io.IOException failure) {
                // A failed archive leaves the object intact and invalidates the test.
                score(level,"#source_audit_failed",score(level,"#source_audit_failed")+1);
                com.mojang.logging.LogUtils.getLogger().error("[BlindLabIsolation] source-object archive failed",failure);
            }
        }
    }
    static JsonObject snapshot(ServerLevel level) {
        var result=new JsonObject();result.addProperty("enabled",enabled(level));
        result.addProperty("removedMobs",score(level,"#removed_mobs"));result.addProperty("blockedMobs",score(level,"#blocked_mobs"));
        var unexpected=new JsonArray();
        for(var mob:unrelated(level)) {
            var entry=new JsonObject();entry.addProperty("uuid",mob.getUUID().toString());
            entry.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
            entry.addProperty("position",mob.position().toString());unexpected.add(entry);
        }
        result.add("unexpectedMobs",unexpected);
        if(score(level,"#run")>=12) {
            var objects=new JsonArray();
            for(var entity:sourceObjects(level)) {
                var entry=new JsonObject();entry.addProperty("uuid",entity.getUUID().toString());
                entry.addProperty("type",BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
                entry.addProperty("position",entity.position().toString());objects.add(entry);
            }
            result.add("unexpectedSourceObjects",objects);result.addProperty("removedSourceObjects",score(level,"#removed_sources"));
            result.addProperty("blockedSourceObjects",score(level,"#blocked_sources"));
            result.addProperty("sourceAuditFailures",score(level,"#source_audit_failed"));
            result.addProperty("sourcesClean",sourcesClean(level));
            result.addProperty("ordinaryObjectsAllowedDuringEncounter", score(level,"#run")>=18 && score(level,"#run")<=19 && score(level,"#stage")!=9);
        }
        return result;
    }
}
