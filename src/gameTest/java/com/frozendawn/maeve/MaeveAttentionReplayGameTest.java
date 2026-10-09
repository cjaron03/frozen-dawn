package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import java.util.ArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveAttentionReplayGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveTwoPlayerAttentionControlRetainsStalker(GameTestHelper helper) { replay(helper, false); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveTwoPlayerAttentionDoorPressureShedsStalker(GameTestHelper helper) { replay(helper, true); }

    private static void replay(GameTestHelper helper, boolean open) {
        MaeveObservationGameTest.withScene(helper, open ? 26 : 25, 2, scene -> {
            for (String name : java.util.List.of("setup", "setup_host", "training", "potion_finished", "consumed", "gap",
                    "ready", "practice", "top_up", "prepare_top_up", "control", "tactic", "start", "start_round", "others", "open", "tick", "finish", "abort")) {
                helper.assertTrue(scene.server.getFunctions().get(net.minecraft.resources.ResourceLocation.parse("maeve_focus:" + name)).isPresent(),
                        "Native parser must accept the live two-player command: " + name);
            }
            helper.assertTrue(scene.server.getAdvancements().get(net.minecraft.resources.ResourceLocation.parse("maeve_focus:practice_potion")) != null,
                    "The live completed-consumption advancement must load");
            for (int offset : new int[]{0, 7, 19}) {
                try (var attempt = new MaeveObservationGameTest.Scene(helper, scene.origin)) {
                    replayOnce(helper, attempt, open, offset);
                }
            }
        });
    }

    private static void replayOnce(GameTestHelper helper, MaeveObservationGameTest.Scene scene, boolean open, int offset) {
        scene.phase.setPresetName("cinematic");
        for (int x = -15; x <= 29; x++) for (int z = -2; z <= 45; z++) scene.block(x, -1, z, Blocks.BEDROCK.defaultBlockState());
        for (int x = 18; x <= 22; x++) for (int z = 2; z <= 6; z++) scene.block(x, 4, z, Blocks.STONE.defaultBlockState());
        scene.settleLight();
        var guest = scene.player("focus_guest", 20, 4);
        for (int x = 13; x <= 15; x++) for (int y = 0; y <= 3; y++) for (int z = 3; z <= 5; z++) scene.block(x, y, z, Blocks.BEDROCK.defaultBlockState());
        scene.block(14, 0, 4, Blocks.AIR.defaultBlockState()); scene.block(14, 1, 4, Blocks.AIR.defaultBlockState());
        scene.block(15, 1, 4, Blocks.AIR.defaultBlockState());
        var trainer = scene.architect(14, 4);
        long start = (scene.gameTime / 20 + 1) * 20 + offset;
        for (int i = 0; i < 140; i++) {
            scene.clock(start + i); trainer.tickCount++; trainer.tick(); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(trainer));
        }
        helper.assertTrue(trainer.hasLineOfSight(guest) && trainer.distanceTo(guest) > 4 && guest.getHealth() == guest.getMaxHealth(),
                "The actual training booth must preserve sight while keeping the AI away from the drinking player");
        start += 200;
        for (int i = 0; i < 5; i++) { scene.clock(start + i * 640); guest.finish(scene.potion()); }
        helper.assertTrue(scene.beliefs(guest).stream().anyMatch(b -> b.pattern().equals(BeliefStore.RECOVERY) && b.confidence() >= .99),
                "Five actual completed restorative uses must supply the historical belief: sky="
                        + scene.level.canSeeSky(guest.blockPosition()) + " beliefs=" + scene.beliefs(guest));
        trainer.discard();
        for (int x = 13; x <= 15; x++) for (int y = 0; y <= 3; y++) for (int z = 3; z <= 5; z++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
        long now = start + 5 * 640;
        scene.clock(now); MaeveDirector.tick(scene.server);
        var host = scene.player("focus_host", -10, 4);
        // Same booth and shutter as the live two-client fixture. Only sight changes.
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z <= 8; z++) { scene.block(12, y, z, Blocks.BEDROCK.defaultBlockState()); scene.block(17, y, z, Blocks.BEDROCK.defaultBlockState()); }
            for (int x = 12; x <= 17; x++) { scene.block(x, y, 0, Blocks.BEDROCK.defaultBlockState()); scene.block(x, y, 8, Blocks.BEDROCK.defaultBlockState()); }
        }
        var stalker = scene.architect(-10, 30);
        var actors = new ArrayList<ArchitectEntity>(); actors.add(stalker);
        for (int i = 0; i < 320; i++) {
            scene.clock(now + i);
            if (i == 20) { actors.add(scene.architect(20, 30)); actors.add(scene.architect(14, 4)); }
            if (i == 160) {
                var before = MaeveDirector.attentionSnapshot(scene.server);
                helper.assertTrue(before.slots().size() == 2 && before.slots().stream().allMatch(s -> s.kind().equals("PASSIVE_TRACKING")),
                        "Before the shutter opens, two real players occupy two ordinary tracking concerns: " + before);
            }
            // Earliest, middle, and last accepted lever timing; arbitrary coarse-tick phase.
            if (open && i == 160 + (offset == 0 ? 0 : offset == 7 ? 20 : 40)) {
                for (int y = 0; y < 4; y++) for (int z = 1; z < 8; z++) scene.block(17, y, z, Blocks.AIR.defaultBlockState());
            }
            for (var actor : actors) {
                actor.tickCount++; actor.tick(); NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(actor));
            }
            MaeveDirector.tick(scene.server);
            if (i == 230) {
                var focus = MaeveDirector.attentionSnapshot(scene.server);
                helper.assertTrue(focus.slots().size() == 2 && stalker.isMaeveDisengaging() == open,
                        "Only opening real sight to the second player's commitment should evict the host: " + focus);
                helper.assertTrue((MaeveDirector.positionDirective(actors.get(2)) != null) == open,
                        "Pressure must come from a locally chosen historical commitment");
            }
        }
        helper.assertTrue(open ? stalker.getZ() > scene.position(-10, 30).z + 8 : stalker.getZ() < scene.position(-10, 30).z,
                "Actual AI must visibly depart in the tactic and pursue in the control: " + stalker.position());
        helper.assertTrue(MaeveDirector.attentionSnapshot(scene.server).events().stream()
                        .anyMatch(e -> e.contains("EVICTED") && e.contains(host.getUUID().toString())) == open,
                "Departure must be explained by eviction, not an ordinary pose or the final scripted pause");
    }
}
