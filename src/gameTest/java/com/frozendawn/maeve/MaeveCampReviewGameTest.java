package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaeveCampReviewGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void tacticalIceRejectsOccupiedCellsBeforeEviction(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 130, scene -> {
            var old = scene.origin.offset(8, 0, 8);
            var occupied = scene.origin.offset(5, 0, 4);
            scene.block(8, 0, 8, Blocks.PACKED_ICE.defaultBlockState());
            scene.block(5, 0, 4, Blocks.DANDELION.defaultBlockState());
            var pool = new java.util.ArrayList<net.minecraft.core.BlockPos>(); pool.add(old);
            var player = scene.player("ice_occupant", 5, 4);
            helper.assertTrue(!com.frozendawn.entity.architect.ArchitectIcePlacement.placeTacticalIce(scene.level, occupied, pool, 1),
                    "Tactical ice cannot be placed inside another player");
            helper.assertTrue(pool.equals(java.util.List.of(old)) && scene.level.getBlockState(old).is(Blocks.PACKED_ICE)
                            && scene.level.getBlockState(occupied).is(Blocks.DANDELION),
                    "Rejected placement must preserve vegetation and the bounded pool's previous wall");
            player.discard();
            var item = new net.minecraft.world.entity.item.ItemEntity(scene.level, occupied.getX() + .5,
                    occupied.getY(), occupied.getZ() + .5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
            scene.level.addFreshEntity(item); scene.entities.add(item);
            helper.assertTrue(com.frozendawn.entity.architect.ArchitectIcePlacement.placeTacticalIce(scene.level, occupied, pool, 1)
                            && pool.equals(java.util.List.of(occupied)) && scene.level.getBlockState(old).isAir(),
                    "Nonblocking dropped items must still permit construction and ordinary bounded eviction");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void retreatCoverCannotSuffocateItsBuilder(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 127, 2, scene -> {
            ground(scene);
            var actor = scene.architect(4, 4);
            actor.setPos(scene.position(4, 4).add(.4, 0, 0));
            var player = scene.player("retreat_wall", 24, 4);
            player.setInvulnerable(true);
            actor.setHealth(10); actor.tickCount = 80; actor.setOnGround(true);
            actor.debugForceApproach(player); actor.startDecisionRecording(1337L);
            actor.decisionJournal().useExtendedLabBuffer();
            float lowest = actor.getHealth();
            for (int i = 0; i < 70; i++) {
                scene.clock(scene.gameTime + i + 1); scene.level.tickNonPassenger(actor);
                lowest = Math.min(lowest, actor.getHealth());
            }
            var damage = actor.decisionJournal().entries().stream()
                    .filter(e -> e.event().equals("DAMAGE") && e.detail().contains("inWall")).toList();
            System.out.println("RETREAT_COVER_REVIEW health=" + actor.getHealth() + " damage=" + damage);
            helper.assertTrue(damage.isEmpty() && lowest == 10,
                    "A cover wall cannot intersect and suffocate its builder: " + damage);
            helper.assertTrue(actor.getHealth() > 10 && scene.level.getBlockState(scene.origin.offset(6, 0, 4)).is(Blocks.PACKED_ICE),
                    "Safe placement must still allow real cover and potion healing");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void pursuitReplansAroundItsNewCover(GameTestHelper helper) {
        coverPursuit(helper, false);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 300)
    public static void pursuitReplansAroundNewCoverTowardRaisedTarget(GameTestHelper helper) {
        coverPursuit(helper, true);
    }

    private static void coverPursuit(GameTestHelper helper, boolean raised) {
        MaeveObservationGameTest.withScene(helper, raised ? 129 : 128, 2, scene -> {
            ground(scene);
            var actor = scene.architect(8, 4);
            var player = scene.player("new_cover", 1, 4);
            if (raised) {
                scene.block(3, 0, 3, Blocks.STONE.defaultBlockState());
                player.setPos(scene.position(3, 3).add(0, 1, 0));
            }
            actor.setInvulnerable(true); player.setInvulnerable(true);
            actor.tickCount = 80; actor.setOnGround(true); actor.debugForceApproach(player);
            var path = actor.getDStarPathfinder();
            path.initialize(player.blockPosition(), actor.blockPosition(), scene.level);
            helper.assertTrue(path.computePartial(20000, scene.level), "The pre-cover ground route must finish");
            actor.startDecisionRecording(1337L); actor.decisionJournal().useExtendedLabBuffer();
            var wall = com.frozendawn.entity.architect.ArchitectCoverGeometry.find(actor, actor.position(), player.getEyePosition());
            helper.assertTrue(wall != null, "The visible lane must admit a defensive wall");
            // Run the real construction handler after caching the previous open-ground route.
            try {
                var field = ArchitectEntity.class.getDeclaredField("tacticsController"); field.setAccessible(true);
                var controller = field.get(actor);
                var method = controller.getClass().getDeclaredMethod("executeFortify", net.minecraft.world.entity.LivingEntity.class);
                method.setAccessible(true); method.invoke(controller, player);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            helper.assertTrue(scene.level.getBlockState(wall).is(Blocks.PACKED_ICE)
                    && scene.level.getBlockState(wall.above()).is(Blocks.PACKED_ICE), "Actual construction must place both blocks");
            for (int i = 0; i < 100; i++) {
                scene.clock(scene.gameTime + i + 1); scene.level.tickNonPassenger(actor);
            }
            System.out.println("NEW_COVER_REVIEW wall=" + wall + " pos=" + actor.blockPosition()
                    + " events=" + actor.decisionJournal().entries().stream().filter(e -> e.event().contains("BREAK")).toList());
            helper.assertTrue(scene.level.getBlockState(wall).is(Blocks.PACKED_ICE)
                            && scene.level.getBlockState(wall.above()).is(Blocks.PACKED_ICE),
                    "Pursuit must use the short clear route around its own new wall");
            helper.assertTrue(actor.decisionJournal().entries().stream().noneMatch(e -> e.event().equals("BREAK_START")
                            && e.choice() != null && (e.choice().pos().equals(wall) || e.choice().pos().equals(wall.above()))),
                    "Known new cover must update the route before a mining attempt");
            helper.assertTrue(actor.getX() < wall.getX() && actor.distanceTo(player) < 4,
                    "Preserving cover must still permit pressure beyond the wall");
        });
    }

    private static void ground(MaeveObservationGameTest.Scene scene) {
        for (int x = -5; x <= 30; x++) for (int z = -5; z <= 15; z++) {
            scene.block(x, -1, z, Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 4; y++) scene.block(x, y, z, Blocks.AIR.defaultBlockState());
        }
    }
}
