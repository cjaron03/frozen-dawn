package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The west camp crossing: path feet at y-1, full plank floor inside at y. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveCampReconGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void campWestDoorwaySurveyResolvesMixedFloor(GameTestHelper helper) {
        crossing(helper, 107, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void campWestDoorwaySurveyDetectsSeal(GameTestHelper helper) {
        crossing(helper, 108, true);
    }

    private static void crossing(GameTestHelper helper, int lane, boolean sealed) {
        MaeveObservationGameTest.withScene(helper, lane, 2, scene -> {
            for (int x = 0; x <= 32; x++) for (int z = 0; z <= 18; z++) {
                scene.block(x, -2, z, Blocks.STONE.defaultBlockState());
                scene.block(x, -1, z, (x >= 14 ? Blocks.SPRUCE_PLANKS : Blocks.DIRT_PATH).defaultBlockState());
                for (int y = 0; y <= 5; y++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
                if (x >= 14) scene.block(x, 4, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            for (int z = 0; z <= 18; z++) for (int y = 0; y < 4; y++)
                if (z < 7 || z > 9 || y == 3) scene.block(14, y, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            scene.settleLight();
            var player = scene.player("west_crossing_" + lane, 15, 8);
            var witness = scene.architect(8, 8);
            long now = (scene.gameTime / 20 + 1) * 20;
            scene.clock(now); witness.setTarget(player);
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(witness));
            scene.clock(now + 10); player.setPos(scene.position(12, 8).add(0, -.0625, 0));
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(witness));
            var point = MaeveDirector.worldSnapshot(scene.server, player.getUUID()).stream()
                    .filter(p -> p.label().equals("ACCESS_POINT")).findFirst().orElseThrow();
            helper.assertTrue(point.position().equals(scene.origin.offset(12, -1, 8))
                            && point.inside().equals(scene.origin.offset(15, 0, 8)),
                    "Actual observation hooks must reproduce the camp's saved crossing");
            witness.discard(); player.setPos(scene.position(5, 16));
            if (sealed) for (int z = 7; z <= 9; z++) for (int y = 0; y < 3; y++)
                scene.block(14, y, z, Blocks.STONE.defaultBlockState());
            scene.clock(now + 650); MaeveDirector.tick(scene.server);
            var scout = scene.architect(5, 8); scout.setPos(scene.position(5, 8).add(0, -.0625, 0));
            scout.tickCount = 80; scout.setOnGround(true); scout.startDecisionRecording(1337L);
            for (int i = 0; i < 300; i++) {
                scene.clock(now + 650 + i); scout.tickCount++; scout.tick();
                NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(scout)); MaeveDirector.tick(scene.server);
                if (MaeveDirector.missionSnapshots(scene.server, player.getUUID()).stream()
                        .anyMatch(m -> !m.outcome().equals("ACTIVE"))) break;
            }
            var mission = MaeveDirector.missionSnapshots(scene.server, player.getUUID()).getFirst();
            var floor = scene.origin.offset(14, -1, 8);
            var floorRay = scene.level.clip(new ClipContext(scout.getEyePosition(), floor.getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, scout));
            System.out.println("CAMP_WEST_SURVEY sealed=" + sealed + " report=" + mission.report()
                    + " outcome=" + mission.outcome() + " floorRay=" + floorRay.getBlockPos().subtract(scene.origin)
                    + " actor=" + scout.position().subtract(scene.position(0, 0)));
            helper.assertTrue(mission.outcome().equals("SURVEY_COMPLETE")
                            && mission.report().equals(sealed ? "BLOCKED" : "OPEN"),
                    "West mixed-floor survey must resolve its visible passage: " + mission.outcome() + "/" + mission.report());
            helper.assertTrue(mission.packet().access().outside().equals(point.position())
                            && mission.packet().access().inside().equals(point.inside()),
                    "Sensing cannot rewrite the witnessed coordinates");
            helper.assertTrue(scout.decisionJournal().breaks() == 0 && player.getHealth() == player.getMaxHealth(),
                    "The report must come from inspection without excavation or combat");
            if (!sealed) {
                var access = mission.packet().access();
                scout.setPos(scene.position(18, 8));
                var reversed = new MaeveDirector.AccessHint(access.inside(), access.outside(),
                        access.state(), access.confidence(), access.source());
                helper.assertTrue(MissionSensing.inspect(scout, reversed).equals("OPEN"),
                        "Descending from the full floor onto the path must also remain open");
                scout.setPos(scene.position(9, 8).add(0, -.0625, 0));
                scene.block(14, 0, 8, Blocks.STONE.defaultBlockState());
                helper.assertTrue(MissionSensing.inspect(scout, access).equals("BLOCKED"),
                        "A single visible block above the floor must not be treated as support");
                scene.block(14, 0, 8, Blocks.AIR.defaultBlockState());
            }
            // Occluding the entire segment must not allow a report through the nearer wall.
            scout.setPos(scene.position(9, 8).add(0, -.0625, 0));
            for (int z = 4; z <= 12; z++) for (int y = 0; y < 4; y++)
                scene.block(11, y, z, Blocks.STONE.defaultBlockState());
            helper.assertTrue(MissionSensing.inspect(scout, mission.packet().access()).equals("UNSEEN"),
                    "A nearer wall must hide the remembered crossing");
        });
    }
}
