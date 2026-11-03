package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** One-use lab continuation of the archived, simultaneous NATURAL arrivals. */
@EventBusSubscriber(modid = "frozendawn")
public final class LabNaturalArrivalHold {
    public static final String REQUEST = "NATURAL_ARRIVAL_HOLD_1\n";
    public static final UUID PLAYER = UUID.fromString("380df991-f603-344c-a090-369bad2a924a");
    private static final String RECEIPT = "{\"event\":\"NORMAL_PLAY_RELEASE\",\"gameTime\":10200,\"player\":\"" + PLAYER + "\"}\n";
    private static final Map<String, String> FILES = Map.of(
            "arrivals.jsonl", "53d29b0f22eb363288b01d08d68a8ca53ded698e8205bad13baf4421e43a29d8",
            "frames.jsonl", "0a36081bddfc25ab9882920f8013dd937f13a8992ba1540351f70f2599c185d7",
            "waiting.jsonl", "4ad5eaa3dbc9d98a78242d34b07b7fe06c1513470153384cce52bd5b91585d10",
            "end.jsonl", "2a1907ede53dedbdd16df3de0fcb228e53da925bcf5e100d7f00c366602984e1",
            "continuation.json", "1a95bee78b1a16b5bf922a2d128a00d208f3a257fa476d6533437dfce385eb1c");
    private static LabNaturalArrivalHold current;
    private final MinecraftServer server;
    private final Path root;
    private final boolean recorded;
    private boolean released;

    /** Freezes the scheduler before saved entities are loaded; never edits their NBT. */
    public LabNaturalArrivalHold(MinecraftServer server, Path root) {
        this.server = server; this.root = root;
        var manager = server.tickRateManager();
        if (manager.isSprinting() && !manager.stopSprinting()) manager.checkShouldSprintThisTick();
        manager.stopStepping(); manager.setFrozen(true);
        boolean written = false;
        try {
            if (REQUEST.equals(Files.readString(root.resolve("arrival-play/startup-request.txt")))) {
                Files.writeString(root.resolve("arrival-play/startup-held.json"),
                        "{\"event\":\"PRE_ENTITY_LOAD_HOLD\",\"frozen\":true,\"actorNbtEdited\":false}\n", StandardOpenOption.CREATE_NEW);
                written = true;
            }
        } catch (Exception e) { /* Stay frozen on missing/invalid/evidence failure. */ }
        recorded = written;
    }
    @SubscribeEvent public static void starting(ServerAboutToStartEvent event) {
        var server = event.getServer();
        var root = server.getWorldPath(LevelResource.ROOT).resolve("blind-natural");
        if (!Boolean.getBoolean("frozendawn.labBridge") || server.isDedicatedServer()
                || !server.getWorldData().getLevelName().equals(LabBlindNatural.WORLD)
                || !Files.exists(root.resolve("arrival-play/startup-request.txt"))
                || releasedReceipt(root)) return;
        current = new LabNaturalArrivalHold(server, root);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        if (current != null && current.server == event.getServer()) current = null;
    }
    public static LabNaturalArrivalHold current(MinecraftServer server) {
        return current != null && current.server == server ? current : null;
    }
    private static boolean releasedReceipt(Path root) {
        try { return proof(root) && Files.isRegularFile(root.resolve("arrival-play/release.json"))
                && RECEIPT.equals(Files.readString(root.resolve("arrival-play/release.json"))); }
        catch (Exception e) { return false; }
    }
    public static boolean proof(Path root) {
        if (!LabNaturalRepeatProof.verify(root, PLAYER)) return false;
        try {
            for (var entry : FILES.entrySet()) {
                var file = root.resolve("fastwait-repeat1").resolve(entry.getKey());
                if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                        || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))).equals(entry.getValue())) return false;
            }
            return true;
        } catch (Exception e) { return false; }
    }
    /** Validates the exact real predecessor and every loaded actor before release. */
    public boolean evidenceReady() { return recorded && !released && proof(root); }
    public static boolean actorMatches(ArchitectEntity actor, CompoundTag expected) {
        return actor.tickCount == 0 && expected.equals(actor.saveWithoutId(new CompoundTag()));
    }
    public boolean ready(ServerPlayer player) {
        var manager = server.tickRateManager();
        if (!evidenceReady() || player == null || !PLAYER.equals(player.getUUID())
                || !player.isAlive() || player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL
                || !manager.isFrozen() || manager.isSprinting() || manager.isSteppingForward() || manager.tickrate() != 20.0f
                || player.serverLevel() != server.overworld() || server.overworld().getGameTime() != 10200) return false;
        try {
            var end = JsonParser.parseString(Files.readString(root.resolve("fastwait-repeat1/end.jsonl"))).getAsJsonObject();
            if (player.getHealth() != end.get("health").getAsFloat()
                    || !player.position().toString().equals(end.get("position").getAsString())) return false;
            int count = 0;
            for (var entity : server.overworld().getAllEntities()) if (entity instanceof ArchitectEntity) count++;
            if (count != 2) return false;
            for (var value : end.getAsJsonArray("journals")) {
                var journal = value.getAsJsonObject();
                var expected = TagParser.parseTag(journal.get("nbt").getAsString());
                var entity = server.overworld().getEntity(UUID.fromString(journal.get("uuid").getAsString()));
                if (!(entity instanceof ArchitectEntity actor) || !actorMatches(actor, expected)) return false;
            }
            return true;
        } catch (Exception e) { return false; }
    }
    /** Durable one-use receipt first; a failed write cannot unfreeze the encounter. */
    public boolean release(ServerPlayer player) {
        if (!ready(player)) return false;
        try {
            Files.writeString(root.resolve("arrival-play/release.json"), RECEIPT, StandardOpenOption.CREATE_NEW);
        } catch (Exception e) { return false; }
        released = true; server.tickRateManager().setFrozen(false); return true;
    }
}
