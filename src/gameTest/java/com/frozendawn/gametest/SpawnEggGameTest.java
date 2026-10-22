package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.BloomSporeEntity;
import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.entity.RemnantState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercise real egg use, entity initialization, ticking and standalone save/load state. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpawnEggGameTest {

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void rimeboundEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.RIMEBOUND_SPAWN_EGG.get(), ModEntities.RIMEBOUND.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void resonantEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.RESONANT_SPAWN_EGG.get(), ModEntities.RESONANT.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void remnantEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.REMNANT_SPAWN_EGG.get(), ModEntities.REMNANT.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void frostwritheEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.FROSTWRITHE_SPAWN_EGG.get(), ModEntities.FROSTWRITHE.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void undoneEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.UNDONE_SPAWN_EGG.get(), ModEntities.UNDONE.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void bloomboundEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.BLOOMBOUND_UNDONE_SPAWN_EGG.get(), ModEntities.BLOOMBOUND_UNDONE.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void undoneArchitectEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.UNDONE_ARCHITECT_SPAWN_EGG.get(), ModEntities.UNDONE_ARCHITECT.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void sporeEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.BLOOM_SPORE_SPAWN_EGG.get(), ModEntities.BLOOM_SPORE.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY, timeoutTicks = 80)
    public static void archivistEggHatches(GameTestHelper helper) {
        hatch(helper, ModItems.ARCHIVIST_SPAWN_EGG.get(), ModEntities.ARCHIVIST.get());
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void eventRemnantRetainsLureSetup(GameTestHelper helper) {
        var mob = ModEntities.REMNANT.get().create(helper.getLevel());
        var lure = UUID.randomUUID();
        var anchor = helper.absolutePos(new BlockPos(4, 1, 4));
        mob.bind(lure, anchor, RemnantState.OBSERVING);
        mob.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(anchor),
                MobSpawnType.EVENT, null);
        helper.assertTrue(lure.equals(mob.lureId()) && anchor.equals(mob.lureAnchor())
                        && mob.state() == RemnantState.OBSERVING && mob.isInvisible() && mob.isNoAi(),
                "event-spawned Remnant lost its authored lure setup");
        var restored = ModEntities.REMNANT.get().create(helper.getLevel());
        restored.load(mob.saveWithoutId(new CompoundTag()));
        helper.assertTrue(lure.equals(restored.lureId()) && anchor.equals(restored.lureAnchor())
                        && restored.state() == RemnantState.OBSERVING && restored.isInvisible()
                        && restored.isNoAi(),
                "bound Remnant lost its lure setup after save/load");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void eventSporeRetainsSourceSetup(GameTestHelper helper) {
        var mob = ModEntities.BLOOM_SPORE.get().create(helper.getLevel());
        var source = UUID.randomUUID();
        var lineage = UUID.randomUUID();
        var anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        mob.bindSource(source, lineage, anchor, 12.0D, new Vec3(1, 0, 0));
        mob.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(anchor),
                MobSpawnType.EVENT, null);
        var tag = mob.saveWithoutId(new CompoundTag());
        helper.assertTrue(source.equals(mob.getSourceId()) && lineage.equals(mob.getLineageId())
                        && anchor.asLong() == tag.getLong("sourceAnchor")
                        && mob.getOutwardHeading().equals(new Vec3(1, 0, 0)),
                "event-spawned Spore lost its authored source setup");
        helper.succeed();
    }

    private static void hatch(GameTestHelper helper, Item egg, EntityType<? extends Mob> expected) {
        GameTestTemplates.placeFloor(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(1.5D, 1, 1.5D)));
        var stack = new ItemStack(egg, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var floor = helper.absolutePos(new BlockPos(4, 0, 4));
        var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5D, 0),
                Direction.UP, floor, false);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(result.consumesAction() && stack.getCount() == 1,
                "egg use failed or consumed the wrong quantity: " + egg);
        var spawned = helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(floor.above()).inflate(1),
                mob -> mob.getType() == expected);
        helper.assertTrue(spawned.size() == 1, "egg did not hatch exactly one expected mob: " + expected);
        var mob = spawned.getFirst();
        assertStandalone(helper, mob, floor.above());
        var restored = expected.create(helper.getLevel());
        restored.load(mob.saveWithoutId(new CompoundTag()));
        assertStandalone(helper, restored, floor.above());
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(mob.isAlive() && !mob.isRemoved() && mob.tickCount >= 30
                            && mob.getMaxHealth() > 0 && mob.getHealth() > 0,
                    "hatched mob failed to survive and tick normally: " + expected);
            assertStandalone(helper, mob, floor.above());
            helper.succeed();
        });
    }

    private static void assertStandalone(GameTestHelper helper, Mob mob, BlockPos spawn) {
        if (mob instanceof RemnantEntity remnant) {
            helper.assertTrue(remnant.state() == RemnantState.HUNTING && !remnant.isInvisible()
                            && !remnant.isNoAi() && !remnant.insideLure() && remnant.lureId() == null,
                    "egg Remnant is dormant, hidden or incorrectly bound to a lure");
        }
        if (mob instanceof BloomSporeEntity spore) {
            var tag = spore.saveWithoutId(new CompoundTag());
            helper.assertTrue(!spore.isRooting() && spawn.asLong() == tag.getLong("sourceAnchor")
                            && spore.getUUID().equals(spore.getSourceId())
                            && spore.getUUID().equals(spore.getLineageId()),
                    "egg Spore rooted early or lost its local source anchor");
        }
    }
}
