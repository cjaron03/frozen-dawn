package com.frozendawn.radiation;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FalloutBlock;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.*;
import com.frozendawn.item.ShieldedCaskItem;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RadiationGameTest {
    @BeforeBatch(batch = "radiation")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "radiation", timeoutTicks = 100)
    public static void radiationShieldingFollowsMaterial(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            var a = h.absolutePos(new BlockPos(2, 3, 5)); var b = h.absolutePos(new BlockPos(8, 3, 5));
            var from = Vec3.atCenterOf(a); var to = Vec3.atCenterOf(b);
            near(h, RadiationField.shielding(level, from, to, a, b), 1, "Open air does not shield");
            level.setBlock(a.east(3), Blocks.STONE.defaultBlockState(), 3);
            near(h, RadiationField.shielding(level, from, to, a, b), 0.5, "One stone block halves it");
            level.setBlock(a.east(3), Blocks.OBSIDIAN.defaultBlockState(), 3);
            near(h, RadiationField.shielding(level, from, to, a, b), 0.4, "Dense blocks shield more");
            level.setBlock(a.east(2), Blocks.WATER.defaultBlockState(), 3);
            near(h, RadiationField.shielding(level, from, to, a, b), 0.2, "Water stacks");
            var cask = new ItemStack(ModItems.SHIELDED_CASK.get());
            for (int i = 0; i < 5; i++) ShieldedCaskItem.add(cask);
            h.assertTrue(ShieldedCaskItem.rods(cask) == 4, "A cask holds four rods");
            near(h, RadiationField.strength(cask), 4 * RadiationMath.SPENT_ROD * RadiationMath.CASK_SHIELD, "The cask blocks 99%");
            near(h, RadiationField.strength(new ItemStack(ModItems.SPENT_FUEL_ROD.get(), 2)), 2 * RadiationMath.SPENT_ROD, "Bare rods do not");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "radiation", timeoutTicks = 100)
    public static void spentRodCausesSicknessMilkCannotCure(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var p = player(h, "rod_carrier");
            p.getInventory().add(new ItemStack(ModItems.SPENT_FUEL_ROD.get()));
            for (int i = 0; i < 12; i++) RadiationHandler.second(p);
            var state = p.getData(ModAttachments.RADIATION.get());
            h.assertTrue(state.dose >= RadiationMath.STAGE_III, "A bare spent rod carries Sickness III dose within about ten seconds: " + state.dose);
            h.assertTrue(state.felt < state.dose, "Symptoms lag the dose");
            state.felt = state.dose; RadiationHandler.second(p);
            var effect = p.getEffect(ModEffects.RADIATION_SICKNESS);
            h.assertTrue(effect != null && effect.getAmplifier() >= 2, "Sickness III shows the trefoil effect");
            h.assertTrue(p.getMaxHealth() <= 12.01f, "Sickness III takes four hearts");
            p.removeAllEffects(); p.getInventory().clearContent(); RadiationHandler.second(p);
            h.assertTrue(p.hasEffect(ModEffects.RADIATION_SICKNESS), "Clearing effects (milk) is undone from stored dose");
            p.getData(ModAttachments.RADIATION.get()).dose = 0; p.getData(ModAttachments.RADIATION.get()).felt = 0; RadiationHandler.second(p);
            h.assertTrue(!p.hasEffect(ModEffects.RADIATION_SICKNESS) && p.getMaxHealth() >= 19.99f, "Recovery removes symptoms and restores health");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "radiation", timeoutTicks = 100)
    public static void contaminationFillsOpenSpaceButNotSealedRooms(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            for (int x = 0; x <= 20; x++) for (int z = 0; z <= 20; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            for (int x = 13; x <= 17; x++) for (int y = 2; y <= 5; y++) for (int z = 13; z <= 17; z++)
                h.setBlock(new BlockPos(x, y, z), x == 13 || x == 17 || y == 5 || z == 13 || z == 17 ? Blocks.STONE : Blocks.AIR);
            var center = h.absolutePos(new BlockPos(6, 2, 6));
            int placed = Contamination.spread(level, List.of(center), center, Contamination.AIR_RADIUS);
            h.assertTrue(placed > 50, "Fallout settles across the open floor: " + placed);
            h.assertTrue(FalloutBlock.at(level, center) && FalloutBlock.at(level, center.east(5)), "Floor cells carry fallout");
            h.assertTrue(!FalloutBlock.at(level, h.absolutePos(new BlockPos(15, 2, 15))), "A sealed room stays clean");
            h.assertTrue(RadiationSourceData.get(level).kind(center) == RadiationSourceData.Kind.FALLOUT, "Fallout registers as a source");
            level.destroyBlock(center, false);
            h.assertTrue(RadiationSourceData.get(level).kind(center) == null, "Cleaning it up removes the source");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "radiation", timeoutTicks = 100)
    public static void falloutContaminatesUntilWashed(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            for (int x = 3; x <= 7; x++) for (int z = 3; z <= 7; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            h.setBlock(new BlockPos(5, 2, 5), ModBlocks.FALLOUT.get());
            var p = player(h, "fallout_walker"); p.setPos(h.absolutePos(new BlockPos(5, 2, 5)).getBottomCenter());
            RadiationHandler.second(p); RadiationHandler.second(p);
            var state = p.getData(ModAttachments.RADIATION.get());
            h.assertTrue(state.contamination > 0 && state.rate > state.contamination, "Walking through fallout contaminates and it emits");
            var effect = p.getEffect(ModEffects.CONTAMINATED);
            h.assertTrue(effect != null && effect.isInfiniteDuration(), "Contaminated shows as an effect that lasts until washed");
            p.removeAllEffects(); RadiationHandler.second(p);
            h.assertTrue(p.hasEffect(ModEffects.CONTAMINATED), "Clearing effects (milk) does not wash it off");
            level.setBlock(h.absolutePos(new BlockPos(5, 2, 5)), Blocks.AIR.defaultBlockState(), 3);
            p.setPos(h.absolutePos(new BlockPos(12, 2, 12)).getBottomCenter());
            RadiationHandler.second(p);
            h.assertTrue(state.contamination > 0, "Contamination stays on the player after leaving");
            level.setBlock(h.absolutePos(new BlockPos(12, 2, 12)), Blocks.WATER.defaultBlockState(), 3);
            p.setPos(h.absolutePos(new BlockPos(12, 2, 12)).getCenter());
            p.updateFluidHeightAndDoFluidPushing(net.minecraft.tags.FluidTags.WATER, 0.014); p.baseTick();
            RadiationHandler.second(p);
            h.assertTrue(state.contamination == 0 && !p.hasEffect(ModEffects.CONTAMINATED), "Water washes it off");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "radiation", timeoutTicks = 100)
    public static void radAidFlushesDoseAndAdvancementsLoad(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var p = player(h, "rad_aid_patient");
            var state = p.getData(ModAttachments.RADIATION.get()); state.dose = 300; state.felt = 300;
            var bag = new ItemStack(ModItems.RAD_AID.get(), 2);
            bag.finishUsingItem(level, p);
            h.assertTrue(state.treatmentSeconds == com.frozendawn.item.RadAidItem.SECONDS && bag.getCount() == 1, "RadAid starts a one-minute flush and is used up");
            for (int i = 0; i < 60; i++) RadiationHandler.second(p);
            h.assertTrue(state.dose < 300 - com.frozendawn.item.RadAidItem.DOSE + 1, "It removes about 200 RU over the minute: " + state.dose);
            for (var key : List.of("three_point_six_roentgen", "az_5", "elephants_foot", "carnot_would_be_proud",
                    "even_the_reactor_gets_cold", "war_never_changes", "was_that_the_geiger_counter"))
                h.assertTrue(level.getServer().getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, key)) != null, "Advancement loads: " + key);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "radiation", timeoutTicks = 100)
    public static void leadShieldsAndLiningHalvesSealedSuitDose(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            var a = h.absolutePos(new BlockPos(2, 3, 2)); var b = h.absolutePos(new BlockPos(8, 3, 2));
            level.setBlock(a.east(3), ModBlocks.LEAD_BLOCK.get().defaultBlockState(), 3);
            near(h, RadiationField.shielding(level, Vec3.atCenterOf(a), Vec3.atCenterOf(b), a, b), 0.1, "One lead block stops 90%");
            var p = player(h, "lead_lined");
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModItems.EVA_HELMET.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModItems.EVA_CHESTPLATE.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModItems.EVA_LEGGINGS.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModItems.EVA_BOOTS.get()));
            h.assertTrue(com.frozendawn.event.SuitIntegrityHandler.isWearingSealedSuit(p), "Full EVA is a sealed suit");
            var corium = BlockPos.containing(p.getEyePosition()).east(3);
            level.setBlock(corium, ModBlocks.CORIUM.get().defaultBlockState(), 3);
            double bare = RadiationField.rate(p);
            h.assertTrue(bare > 1, "Corium three blocks away is hot: " + bare);
            p.getInventory().add(new ItemStack(ModItems.LEAD_LINING_MODULE.get()));
            h.assertTrue(Math.abs(RadiationField.rate(p) - bare * 0.5) < bare * 1e-6, "The lining halves it under a sealed suit");
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
            h.assertTrue(Math.abs(RadiationField.rate(p) - bare) < bare * 1e-6, "Without the full suit the lining does nothing");
            level.setBlock(corium, Blocks.AIR.defaultBlockState(), 3);
            var smelted = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ModItems.RAW_GALENA.get())), level);
            h.assertTrue(smelted.isPresent() && smelted.get().value().getResultItem(level.registryAccess()).is(ModItems.LEAD_INGOT.get()), "Raw galena smelts to lead");
            h.assertTrue(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                    .containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "galena_ore")), "Galena generates");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "radiation", timeoutTicks = 100)
    public static void deconShowerNeedsPowerAndWaterThenWashes(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            h.setBlock(new BlockPos(3, 5, 3), Blocks.STONE);
            var shower = h.absolutePos(new BlockPos(3, 4, 3));
            level.setBlock(shower, ModBlocks.DECON_SHOWER.get().defaultBlockState(), 3);
            var bank = h.absolutePos(new BlockPos(1, 2, 1));
            level.setBlock(bank, ModBlocks.BATTERY_BANK.get().defaultBlockState(), 3);
            var store = (com.frozendawn.block.PowerStorageBlockEntity) level.getBlockEntity(bank);
            for (int i = 0; i < 4; i++) store.setItem(i, com.frozendawn.item.PowerCellItem.charged(500_000));
            var p = player(h, "showering"); p.setPos(h.absolutePos(new BlockPos(3, 2, 3)).getBottomCenter());
            p.getData(ModAttachments.RADIATION.get()).contamination = 1;
            h.assertTrue(com.frozendawn.block.DeconShowerBlock.spray(shower).intersects(p.getBoundingBox()), "Standing under the head is in its spray");
            var tank = new BlockPos(4, 4, 3); h.setBlock(tank, Blocks.PACKED_ICE);
            com.frozendawn.block.DeconShowerBlock.update(level, shower, true);
            h.assertTrue(com.frozendawn.block.DeconShowerBlock.demandWatts(level.getBlockState(shower)) == com.frozendawn.block.DeconShowerBlock.DEMAND_WATTS
                    && !level.getBlockState(shower).getValue(com.frozendawn.block.DeconShowerBlock.RUNNING), "Occupied, it asks for power; unlinked, no water runs");
            h.assertTrue(com.frozendawn.power.PowerNetworks.link(level, bank, shower) == com.frozendawn.power.PowerLinkRules.Result.OK, "The Binder links a shower");
            allocate(level);
            com.frozendawn.block.DeconShowerBlock.update(level, shower, true);
            h.assertTrue(level.getBlockState(shower).getValue(com.frozendawn.block.DeconShowerBlock.RUNNING) && com.frozendawn.block.DeconShowerBlock.washing(p),
                    "With an ice tank and power it runs and sprays the player");
            RadiationHandler.second(p);
            h.assertTrue(p.getData(ModAttachments.RADIATION.get()).contamination == 0 && !p.hasEffect(ModEffects.CONTAMINATED), "The shower washes contamination off");
            h.setBlock(tank, Blocks.AIR);
            com.frozendawn.block.DeconShowerBlock.update(level, shower, true);
            h.assertTrue(!level.getBlockState(shower).getValue(com.frozendawn.block.DeconShowerBlock.RUNNING) && !com.frozendawn.block.DeconShowerBlock.washing(p),
                    "Powered but with no tank, it stays dry");
            com.frozendawn.block.DeconShowerBlock.update(level, shower, false);
            h.assertTrue(com.frozendawn.block.DeconShowerBlock.demandWatts(level.getBlockState(shower)) == 0, "Empty, it draws nothing");
        });
    }
    private static void allocate(ServerLevel level) { for (int i = 0; i < 128; i++) com.frozendawn.power.PowerNetworks.update(level); com.frozendawn.power.PowerNetworks.account(level); }

    private static FakePlayer player(GameTestHelper h, String name) {
        var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.setPos(h.absolutePos(new BlockPos(10, 2, 10)).getBottomCenter());
        return p;
    }
    private static void near(GameTestHelper h, double a, double b, String why) { h.assertTrue(Math.abs(a - b) < 1e-6, why + ": " + a + " != " + b); }
    private static void scene(GameTestHelper h, Runnable body) { h.runAfterDelay(1, () -> { body.run(); h.succeed(); }); }
}
