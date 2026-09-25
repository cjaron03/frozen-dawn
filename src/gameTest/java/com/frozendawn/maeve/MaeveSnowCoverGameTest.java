package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.architect.ArchitectCoverGeometry;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveSnowCoverGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void fortifyScreensArrowsAcrossEverySnowDepth(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 132, 2, scene -> {
            for (int full = 0; full <= 2; full++) for (int layers = 1; layers <= 8; layers++) {
                terrain(scene, full, layers);
                double height = full + (layers - 1) / 8.0;
                var actor = scene.architect(4, 4);
                var player = scene.player("snow_fortify", 16, 4);
                actor.setPos(scene.position(4, 4).add(0, height, 0));
                player.setPos(scene.position(16, 4).add(0, height, 0));
                actor.tickCount = 80; actor.setOnGround(true);
                var wall = ArchitectCoverGeometry.find(actor, actor.position(), player.getEyePosition());
                helper.assertTrue(wall != null, "Visible cover must fit full snow=" + full + " layers=" + layers);
                invoke(actor, "tacticsController", "executeFortify", player);
                var ice = actor.saveWithoutId(new CompoundTag()).getList("TacticalIce", Tag.TAG_LONG);
                helper.assertTrue(ice.size() >= 2 && ice.size() <= 3,
                        "One supported pillar consumes two or three actual budgeted blocks: " + ice);
                for (int y = 0; y < ice.size(); y++) helper.assertTrue(
                        scene.level.getBlockState(wall.above(y)).is(Blocks.PACKED_ICE), "Pillar is continuous at " + wall.above(y));
                helper.assertTrue(scene.level.getBlockState(wall.below()).isFaceSturdy(scene.level, wall.below(), net.minecraft.core.Direction.UP),
                        "The pillar has actual supporting terrain");
                for (double torso : new double[]{.9, 1.6}) {
                    float hp = actor.getHealth();
                    var arrow = new Arrow(scene.level, player, new ItemStack(Items.ARROW), null);
                    arrow.setPos(player.getEyePosition());
                    arrow.setDeltaMovement(actor.position().add(0, torso, 0).subtract(arrow.position()).normalize().scale(2));
                    scene.level.addFreshEntity(arrow); scene.entities.add(arrow);
                    for (int tick = 0; tick < 12; tick++) scene.level.tickNonPassenger(arrow);
                    var saved = arrow.saveWithoutId(new CompoundTag());
                    helper.assertTrue(actor.getHealth() == hp && saved.getBoolean("inGround")
                                    && saved.getCompound("inBlockState").getString("Name").equals("minecraft:packed_ice"),
                            "Actual cover intercepts a real torso arrow at snow depth " + full + "/" + layers + " height=" + torso
                                    + " hp=" + actor.getHealth() + " hit=" + saved.getCompound("inBlockState"));
                    arrow.discard();
                }
                actor.discard(); player.discard();
            }
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void snowBowCoverPreservesObstaclesOccupantsAndBudget(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 133, 2, scene -> {
            terrain(scene, 0, 7);
            var actor = scene.architect(4, 4);
            actor.setPos(scene.position(4, 4).add(0, .75, 0));
            var base = scene.origin.offset(6, 0, 4);
            helper.assertTrue(ArchitectCoverGeometry.canPlacePillar(actor, actor.position(), base), "Deep snow admits supported cover");
            for (var obstacle : java.util.List.of(Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                    Blocks.POWDER_SNOW.defaultBlockState(), ModBlocks.ACHERONITE_CRYSTAL.get().defaultBlockState())) {
                scene.block(6, 0, 4, obstacle);
                helper.assertTrue(placePillar(actor, base) == 0 && scene.level.getBlockState(base).equals(obstacle),
                        "Cover must not erase a solid obstacle, fluid, powder snow or crystal: " + obstacle);
            }
            scene.block(6, 0, 4, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 7));
            var blocker = scene.player("snow_upper_occupant", 6, 4);
            blocker.setPos(scene.position(6, 4).add(0, 2, 0));
            helper.assertTrue(placePillar(actor, base) == 0 && scene.level.getBlockState(base).is(Blocks.SNOW),
                    "An occupant in the third block must reject the whole pillar before replacing snow");
            blocker.discard();
            for (int x : new int[]{6, 8, 10}) {
                var next = scene.origin.offset(x, 0, 4);
                helper.assertTrue(placePillar(actor, next) == 3, "Deep snow consumes three real tactical blocks");
                helper.assertTrue(actor.saveWithoutId(new CompoundTag()).getList("TacticalIce", Tag.TAG_LONG).size() <= 6,
                        "Additional foundation blocks cannot exceed the existing six-block cap");
            }
            helper.assertTrue(scene.level.getBlockState(base).isAir(), "The third pillar recycles the old bounded cover");
            var unsupported = scene.origin.offset(12, 1, 4);
            scene.block(12, 0, 4, Blocks.AIR.defaultBlockState());
            helper.assertTrue(placePillar(actor, unsupported) == 0, "Cover cannot float above an unsupported gap");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void maeveRangedHoldBuildsOnEverySnowLayer(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 134, 2, scene -> {
            for (int layers = 1; layers <= 8; layers++) {
                terrain(scene, 2, layers);
                scene.storage(new MaeveSavedData());
                var actor = scene.architect(4, 4);
                var player = scene.player("snow_commit", 16, 4);
                double height = 2 + (layers - 1) / 8.0;
                actor.setPos(scene.position(4, 4).add(0, height, 0));
                player.setPos(scene.position(16, 4).add(0, height, 0));
                long start = scene.server.overworld().getGameTime() + 1;
                for (int i = 0; i < 4; i++) {
                    scene.clock(start + i * 610L); scene.hit(actor, player, true, 1);
                }
                long now = start + 4 * 610L;
                actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
                actor.setDeltaMovement(Vec3.ZERO);
                actor.startDecisionRecording(1337L); actor.decisionJournal().useExtendedLabBuffer();
                for (int i = 0; i < 30; i++) { scene.clock(now + i); scene.level.tickNonPassenger(actor); }
                var held = MaeveDirector.positionDirective(actor);
                helper.assertTrue(held != null && held.cover() != null && held.arrivedAt() >= 0 && actor.isHoldingMaevePosition(),
                        "Past witnessed bow history must permit an actual hold on snow layers=" + layers
                                + " pos=" + actor.position() + " action=" + actor.getBrainAction()
                                + " state=" + MaeveDirector.commitmentSnapshot(scene.server, player.getUUID())
                                + " beliefs=" + scene.beliefs(player) + " journal=" + actor.decisionJournal().entries().stream()
                                .filter(e -> e.event().contains("MAEVE")).toList());
                helper.assertTrue(scene.level.getBlockState(held.cover()).is(Blocks.PACKED_ICE)
                                && scene.level.getBlockState(held.cover().above()).is(Blocks.PACKED_ICE),
                        "The real directive constructs cover through the budgeted placement path");
                helper.assertTrue(actor.getDeltaMovement().lengthSqr() < .01 && Double.isFinite(actor.getX()),
                        "Fractional snow height must not cause repeated vertical arrival corrections");
                actor.discard(); player.discard();
            }
        });
    }

    private static void terrain(MaeveObservationGameTest.Scene scene, int full, int layers) {
        for (int x = -2; x <= 26; x++) for (int z = -2; z <= 10; z++) {
            scene.block(x, -1, z, ModBlocks.FROZEN_DIRT.get().defaultBlockState());
            for (int y = 0; y <= 6; y++) scene.block(x, y, z, y < full ? Blocks.SNOW_BLOCK.defaultBlockState()
                    : y == full ? Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers) : Blocks.AIR.defaultBlockState());
        }
    }

    private static void invoke(ArchitectEntity actor, String field, String method, LivingEntity player) {
        try {
            var f = ArchitectEntity.class.getDeclaredField(field); f.setAccessible(true);
            var controller = f.get(actor);
            var m = controller.getClass().getDeclaredMethod(method, LivingEntity.class); m.setAccessible(true);
            m.invoke(controller, player);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static int placePillar(ArchitectEntity actor, BlockPos base) {
        try {
            var method = ArchitectEntity.class.getDeclaredMethod("placeCoverPillar", BlockPos.class);
            method.setAccessible(true);
            return (int) method.invoke(actor, base);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
