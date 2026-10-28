package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

/** ORSA Fabricator: per-world blueprints learned from Data Slates, and the powered machine that prints parts from them. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FabricatorGameTest {
    @BeforeBatch(batch = "fabricator")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }
    // Machine tests share the world's archive, so tests that learn and forget the same blueprint run in separate batches.
    @BeforeBatch(batch = "fabricator_machine")
    public static void reportMachine(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch = "fabricator_uplink")
    public static void reportUplink(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch = "fabricator_live")
    public static void reportLive(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "fabricator", timeoutTicks = 40)
    public static void slateTeachesItsBlueprintOncePerWorld(GameTestHelper h) {
        var archive = new FabricatorBlueprints();
        h.assertTrue(!archive.knows("magnet_coil") && archive.learn("magnet_coil") && archive.knows("magnet_coil"), "A slate teaches its blueprint");
        h.assertTrue(!archive.learn("magnet_coil"), "A learned blueprint is not learned twice");
        h.assertTrue(!archive.learn("rocket") && !archive.learn("sweep_turret"), "Only slate blueprints can be learned from slates");
        var slate = FabricatorBlueprints.slate("first_wall");
        h.assertTrue("first_wall".equals(slate.get(ModDataComponents.BLUEPRINT.get())), "A slate names its blueprint");
        for (var id : FabricatorBlueprints.SLATES) if (!id.equals("lithium_blanket")) archive.learn(id);
        h.assertTrue("lithium_blanket".equals(archive.unlearned(RandomSource.create(7))) && !archive.knowsAll(), "Only the missing blueprint is offered");
        archive.learn("lithium_blanket");
        h.assertTrue(archive.unlearned(RandomSource.create(7)) == null && archive.knowsAll(), "A full archive offers nothing");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "fabricator", timeoutTicks = 40)
    public static void rocketBlueprintFollowsTheStoryFlag(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var win = WinConditionState.get(server);
        boolean was = win.isRocketBlueprintUnlocked();
        win.setRocketBlueprintUnlocked(false);
        h.assertTrue(!FabricatorBlueprints.knows(server, "rocket"), "No rocket blueprint before the Martian reply");
        win.setRocketBlueprintUnlocked(true);
        h.assertTrue(FabricatorBlueprints.knows(server, "rocket"), "The recovered reply grants the rocket blueprint, old saves included");
        win.setRocketBlueprintUnlocked(was);
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "fabricator_machine", timeoutTicks = 60)
    public static void fabricatorRefusesUnlearnedBlueprintsAndDrawsRecipeWattage(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel(); var archive = FabricatorBlueprints.get(l.getServer());
            archive.forget("lithium_blanket"); archive.learn("magnet_coil");
            var p = place(h, 3, 2, 3, ModBlocks.FABRICATOR.get()); var f = fab(l, p);
            f.setItem(0, new ItemStack(ModItems.LITHIUM.get(), 4)); f.setItem(1, new ItemStack(ModItems.LEAD_INGOT.get(), 2)); f.setItem(2, new ItemStack(Items.IRON_INGOT, 2));
            h.assertTrue(!f.wantsPower() && f.status() == FabricatorBlockEntity.STATUS_LOCKED, "An unlearned blueprint draws no power and says so");
            archive.learn("lithium_blanket");
            h.assertTrue(f.wantsPower() && f.status() == 1 && f.demandWatts() == 10000, "A learned Lithium Blanket waits for its 10 kW");
            var q = place(h, 7, 2, 3, ModBlocks.FABRICATOR.get()); var g = fab(l, q);
            g.setItem(0, new ItemStack(ModItems.SUPERCONDUCTING_COIL_SCRAP.get(), 2)); g.setItem(1, new ItemStack(Items.COPPER_INGOT, 4)); g.setItem(2, new ItemStack(ModItems.REFINED_ACHERONITE.get()));
            h.assertTrue(g.wantsPower() && g.demandWatts() == 15000, "Magnet Coils draw 15 kW");
            var slate = FabricatorBlueprints.slate("first_wall");
            h.assertTrue(!f.canPlaceItem(3, slate) && !f.canPlaceItemThroughFace(3, slate, Direction.UP) && f.canPlaceItemThroughFace(3, new ItemStack(Items.IRON_INGOT), Direction.UP), "Hoppers feed inputs but never slates");
            h.assertTrue(java.util.Arrays.equals(f.getSlotsForFace(Direction.DOWN), new int[]{FabricatorBlockEntity.OUTPUT}) && !f.canPlaceItem(FabricatorBlockEntity.OUTPUT, new ItemStack(Items.IRON_INGOT)), "Output is extract-only from below");
            var core = place(h, 3, 2, 7, ModBlocks.GEOTHERMAL_CORE.get()); var source = (GeothermalCoreBlockEntity) l.getBlockEntity(core); source.setPowerShare(75);
            link(h, core, p); account(l); f.serverTick();
            h.assertTrue(f.progress() == 1 && f.working() && f.total() == 45 * 20, "Powered, it works a 45 s job");
            var saved = f.saveWithFullMetadata(l.registryAccess());
            var reloaded = (FabricatorBlockEntity) BlockEntity.loadStatic(p, l.getBlockState(p), saved, l.registryAccess());
            h.assertTrue(reloaded != null && reloaded.progress() == 1 && reloaded.total() == 45 * 20, "A saved job keeps its progress");
            f.setItem(1, new ItemStack(ModItems.LEAD_INGOT.get(), 1)); account(l); f.serverTick();
            h.assertTrue(f.progress() == 0 && f.status() == 3 && !f.working(), "Taking a needed ingredient resets the job");
            var player = new FakePlayer(l, new GameProfile(UUID.randomUUID(), "fabricator_menu")); player.setPos(p.getCenter());
            var menu = new FabricatorMenu(1, player.getInventory(), f, f.data(), p);
            h.assertTrue(menu.stillValid(player) && menu.slots.size() == 43 && !menu.slots.get(0).mayPlace(slate) && !menu.slots.get(FabricatorBlockEntity.OUTPUT).mayPlace(new ItemStack(Items.IRON_INGOT)), "Menu inputs refuse slates and output refuses insertion");
            l.destroyBlock(p, false);
            int dropped = l.getEntitiesOfClass(ItemEntity.class, new AABB(p).inflate(2)).stream().filter(e -> e.getItem().is(ModItems.LITHIUM.get())).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(dropped == 4, "Breaking the Fabricator drops its inputs");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "fabricator_machine", timeoutTicks = 40)
    public static void slateRightClickLearnsAndConsumes(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel(); var archive = FabricatorBlueprints.get(l.getServer()); archive.forget("first_wall");
            var p = place(h, 3, 2, 3, ModBlocks.FABRICATOR.get()); var state = l.getBlockState(p);
            var player = new FakePlayer(l, new GameProfile(UUID.randomUUID(), "fabricator_slate")); player.setPos(p.east().getCenter());
            var hit = new BlockHitResult(p.getCenter(), Direction.EAST, p, false);
            var stack = FabricatorBlueprints.slate("first_wall"); stack.setCount(2); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            state.useItemOn(stack, l, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(archive.knows("first_wall") && stack.getCount() == 1, "Inserting a slate learns it and consumes one slate");
            state.useItemOn(stack, l, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(stack.getCount() == 1, "A slate for a known blueprint stays in hand");
            var forged = new ItemStack(ModItems.DATA_SLATE.get()); forged.set(ModDataComponents.BLUEPRINT.get(), "sweep_turret");
            state.useItemOn(forged, l, player, InteractionHand.MAIN_HAND, hit);
            var blank = new ItemStack(ModItems.DATA_SLATE.get());
            state.useItemOn(blank, l, player, InteractionHand.MAIN_HAND, hit);
            h.assertTrue(forged.getCount() == 1 && blank.getCount() == 1 && !archive.knows("sweep_turret"), "Blank and unknown slates teach nothing and are kept");
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "fabricator_live", timeoutTicks = 700)
    public static void fabricatorBuildsALearnedPartAndPausesWithoutPower(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            // Own the pressure geometry: links must not depend on the surrounding batch's terrain or scans.
            for (int x = 0; x <= 8; x++) for (int y = 0; y <= 4; y++) for (int z = 1; z <= 7; z++)
                h.setBlock(new BlockPos(x, y, z), x == 0 || x == 8 || y == 0 || y == 4 || z == 1 || z == 7 ? Blocks.GLASS : Blocks.AIR);
            var l = h.getLevel(); FabricatorBlueprints.get(l.getServer()).learn("first_wall");
            var core = place(h, 2, 2, 4, ModBlocks.GEOTHERMAL_CORE.get()); var source = (GeothermalCoreBlockEntity) l.getBlockEntity(core); source.setPowerShare(50);
            var p = place(h, 6, 2, 4, ModBlocks.FABRICATOR.get()); fab(l, p).setItem(0, new ItemStack(ModItems.FIRST_WALL_TILE.get(), 4)); link(h, core, p);
            int[] held = new int[1];
            h.runAfterDelay(100, () -> { h.assertTrue(fab(l, p).progress() > 0, "The learned job makes progress"); source.setPowerShare(0); });
            h.runAfterDelay(160, () -> held[0] = fab(l, p).progress());
            h.runAfterDelay(200, () -> {
                h.assertTrue(fab(l, p).progress() == held[0] && held[0] > 0 && !fab(l, p).working(), "Without power the job pauses and keeps its progress");
                source.setPowerShare(50);
            });
            h.runAfterDelay(640, () -> {
                var f = fab(l, p);
                h.assertTrue(f.getItem(FabricatorBlockEntity.OUTPUT).is(ModItems.FIRST_WALL.get()) && f.getItem(FabricatorBlockEntity.OUTPUT).getCount() == 1, "Restored power finishes exactly one First Wall");
                h.assertTrue(f.getItem(0).isEmpty() && !f.wantsPower(), "The tiles are used up and the idle machine draws nothing");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "fabricator_uplink", timeoutTicks = 40)
    public static void uplinkResupplyOffersOnlyUnlearnedSlates(GameTestHelper h) {
        var l = h.getLevel(); var archive = FabricatorBlueprints.get(l.getServer());
        var known = FabricatorBlueprints.SLATES.stream().filter(archive::knows).toList();
        try {
            for (var id : FabricatorBlueprints.SLATES) if (!id.equals("isotope_column")) archive.learn(id);
            archive.forget("isotope_column");
            var crate = place(h, 1, 1, 1, Blocks.BARREL);
            var barrel = (net.minecraft.world.level.block.entity.BarrelBlockEntity) l.getBlockEntity(crate);
            barrel.setItem(0, new ItemStack(Items.PAPER));
            h.assertTrue(com.frozendawn.uplink.UplinkEvents.stockSlate(l, crate), "A crate gets a slate while the archive has gaps");
            h.assertTrue(barrel.getItem(0).is(Items.PAPER) && barrel.getItem(1).is(ModItems.DATA_SLATE.get())
                    && "isotope_column".equals(barrel.getItem(1).get(ModDataComponents.BLUEPRINT.get())), "The only unlearned slate goes in the first free slot: " + barrel.getItem(1));
            archive.learn("isotope_column"); barrel.setItem(1, ItemStack.EMPTY);
            h.assertTrue(!com.frozendawn.uplink.UplinkEvents.stockSlate(l, crate) && barrel.getItem(1).isEmpty(), "A full archive gets no slates");
        } finally {
            for (var id : FabricatorBlueprints.SLATES) if (!known.contains(id)) archive.forget(id);
        }
        h.succeed();
    }

    private static FabricatorBlockEntity fab(ServerLevel l, BlockPos p) { return (FabricatorBlockEntity) l.getBlockEntity(p); }
    private static BlockPos place(GameTestHelper h, int x, int y, int z, Block b) { var p = new BlockPos(x, y, z); h.setBlock(p, b); return h.absolutePos(p); }
    private static void link(GameTestHelper h, BlockPos a, BlockPos b) { var result = PowerNetworks.link(h.getLevel(), a, b); h.assertTrue(result == PowerLinkRules.Result.OK, "Expected fabricator link " + a + " -> " + b + ", got " + result); }
    private static void account(ServerLevel l) { for (int i = 0; i < 128; i++) PowerNetworks.update(l); PowerNetworks.account(l); }
}
