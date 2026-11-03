package com.frozendawn.labbridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Allows only the exact preserved inconclusive42 to be followed by a new view.
 * It does not qualify the old viewing timeline or rewrite any outcome or score. */
public final class LabBlindDoorRepeat {
    private LabBlindDoorRepeat() {}
    private static final java.util.Map<String,String> REVIEWED = java.util.Map.ofEntries(
            java.util.Map.entry("blind-lab-results/run-42-2861e323-eecc-4dcf-87c6-0bbfc79ab7b0/start.json","0a9ab7c263d16f378d1d40b535be4907fd69bcb2183222435ecc863f60b7cf35"),
            java.util.Map.entry("blind-lab-results/run-42-2861e323-eecc-4dcf-87c6-0bbfc79ab7b0/summary.json","aee06f9794f66aefac0ec17e0cf1d057a86045c89ecb220954018b342405060c"),
            java.util.Map.entry("blind-lab-results/run-42-2861e323-eecc-4dcf-87c6-0bbfc79ab7b0/trace.jsonl","40c3712655830bc2221e6c17f00d752562717d883b32a667f322e42c6b1c2a9d"),
            java.util.Map.entry("blind-lab-results/run-42-2861e323-eecc-4dcf-87c6-0bbfc79ab7b0/decisions.json","335d4833d70fca0286d795df909b28d90a71b3a765cbb7572eda9fbbc4a93ce4"),
            java.util.Map.entry("blind-lab-results/run-42-2861e323-eecc-4dcf-87c6-0bbfc79ab7b0/doors.jsonl","24dde2aa089df55093ca4e596653e77207b70e456a6f757e3971413af1fdabd7"),
            java.util.Map.entry("blind-lab-results/run-42-2861e323-eecc-4dcf-87c6-0bbfc79ab7b0/projectiles.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            java.util.Map.entry("blind-lab-results/run-42-2861e323-eecc-4dcf-87c6-0bbfc79ab7b0/impacts.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            java.util.Map.entry("blind-lab-input/probe-2e37a170-1ad0-49d6-9181-806f3b33f41c/start.json","ccb311ffbf10eef163e2a5b29c8a2999aad6367b40039009fbf62fe330b7bd71"),
            java.util.Map.entry("blind-lab-input/probe-2e37a170-1ad0-49d6-9181-806f3b33f41c/summary.json","acf98cd783653022a7fee1bbe4da6ff295ebbd2787853b4a3001c309d6e083bc"),
            java.util.Map.entry("blind-lab-input/probe-2e37a170-1ad0-49d6-9181-806f3b33f41c/server.jsonl","65eba229c9919dce61f8b2d9c4fbe8e1f1115d3f3f9b710898ebf082b1fa8695"),
            java.util.Map.entry("blind-lab-input/probe-2e37a170-1ad0-49d6-9181-806f3b33f41c/client.jsonl","16d0a19866263465132951ef9a42e3282442c4acd318ec62b5b1f5104e600982"),
            java.util.Map.entry("blind-lab-input/probe-2e37a170-1ad0-49d6-9181-806f3b33f41c/keys.jsonl","e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
            java.util.Map.entry("blind-lab-ground/check-7a78d4cc-6b22-4f20-b793-32729827e38b/start.json","eb38f65229c6df24bc27aaf0658de8dcbe830e5c9559a99b3de64f6aa8e54a85"),
            java.util.Map.entry("blind-lab-ground/check-7a78d4cc-6b22-4f20-b793-32729827e38b/summary.json","6efe8d4a6c0d17262cd42bd3e9d1ff45bb591759e10218a561a2ed11ad5e7f4a"),
            java.util.Map.entry("blind-lab-ground/check-7a78d4cc-6b22-4f20-b793-32729827e38b/server.jsonl","ee414866855b0aa464fa7fe302c17c44899d8f010eb26f9752ee26e0f4d09371"),
            java.util.Map.entry("blind-lab-ground/check-7a78d4cc-6b22-4f20-b793-32729827e38b/client.jsonl","38bbc0202dc8521f4b0be20299b1ac34e1b58e1c5e1a9f5e94d5759447e1507d"));
    public static boolean reviewedRetained(Path world) {
        try {
            for(var entry:REVIEWED.entrySet()) {
                var bytes=Files.readAllBytes(world.resolve(entry.getKey()));
                if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).equals(entry.getValue()))return false;
            }
            return true;
        }catch(Exception malformed){return false;}
    }
    static boolean ready(CommandSourceStack source,ServerPlayer player,ArchitectEntity actor) {
        if(!player.getUUID().equals(UUID.fromString("380df991-f603-344c-a090-369bad2a924a"))
                ||!actor.getUUID().equals(UUID.fromString("2861e323-eecc-4dcf-87c6-0bbfc79ab7b0"))
                ||!player.getPersistentData().hasUUID("BlindLabGroundCheck")
                ||!player.getPersistentData().getUUID("BlindLabGroundCheck").equals(UUID.fromString("7a78d4cc-6b22-4f20-b793-32729827e38b"))
                ||!player.getPersistentData().getUUID("BlindLabInputProbe").equals(UUID.fromString("2e37a170-1ad0-49d6-9181-806f3b33f41c")))return false;
        var world=source.getServer().getWorldPath(LevelResource.ROOT);
        return reviewedRetained(world)&&LabGroundCheck.retained(world.resolve("blind-lab-ground"),player.getPersistentData().getUUID("BlindLabGroundCheck"),player.getUUID(),actor.getUUID(),42);
    }
}
