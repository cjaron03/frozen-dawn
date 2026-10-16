package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReactorGameTest {
    private static final int CX = 5, CY = 3, CZ = 5;
    @BeforeBatch(batch = "reactor")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void reactorShellValidatesAndNamesTheWrongCell(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = build(h);
            var facing = Direction.NORTH;
            var ok = ReactorMultiblock.validate(level, controller, facing);
            h.assertTrue(ok.valid() && ok.ports().size() == 2, "Complete 3x3x3 shell with two ports is valid");
            h.setBlock(new BlockPos(CX + 1, CY - 1, CZ + 1), Blocks.STONE);
            var missing = ReactorMultiblock.validate(level, controller, facing);
            h.assertTrue(missing.problem() == ReactorMultiblock.Problem.CASING
                    && ReactorMultiblock.offsetPos(missing.core(), missing.offset()).equals(h.absolutePos(new BlockPos(CX + 1, CY - 1, CZ + 1))),
                    "A missing casing names its exact cell");
            h.setBlock(new BlockPos(CX + 1, CY - 1, CZ + 1), ModBlocks.REACTOR_CASING.get());
            h.setBlock(new BlockPos(CX + 1, CY, CZ), ModBlocks.REACTOR_CASING.get());
            h.assertTrue(ReactorMultiblock.validate(level, controller, facing).problem() == ReactorMultiblock.Problem.PORTS, "Two Coolant Ports are required");
            h.setBlock(new BlockPos(CX + 1, CY, CZ), ModBlocks.COOLANT_PORT.get());
            h.setBlock(new BlockPos(CX, CY + 1, CZ), ModBlocks.REACTOR_CASING.get());
            h.assertTrue(ReactorMultiblock.validate(level, controller, facing).problem() == ReactorMultiblock.Problem.ROD_DRIVE, "Rod drive sits above the core");
            h.setBlock(new BlockPos(CX, CY + 1, CZ), ModBlocks.CONTROL_ROD_DRIVE.get());
            var scorched = ModBlocks.REACTOR_CASING.get().defaultBlockState().setValue(ReactorCasingBlock.SCORCHED, true);
            h.setBlock(new BlockPos(CX - 1, CY + 1, CZ - 1), scorched);
            h.assertTrue(ReactorMultiblock.validate(level, controller, facing).valid(), "Scorched casing still counts as shell");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void reactorCoolantLinksJoinOnlyPortsAndRadiators(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = build(h);
            var port = h.absolutePos(new BlockPos(CX + 1, CY, CZ));
            var radiators = radiators(h, 2);
            var relay = place(h, 1, 1, 12, ModBlocks.POWER_RELAY.get());
            h.assertTrue(PowerNetworks.link(level, port, radiators.get(0)) == PowerLinkRules.Result.OK, "Port to radiator links");
            h.assertTrue(PowerNetworks.link(level, port, relay) == PowerLinkRules.Result.COOLANT, "Port refuses an electrical relay");
            h.assertTrue(PowerNetworks.link(level, radiators.get(1), controller) == PowerLinkRules.Result.COOLANT, "Radiator refuses the Controller");
            h.assertTrue(PowerNetworks.link(level, radiators.get(0), radiators.get(1)) == PowerLinkRules.Result.COOLANT, "Radiators do not chain");
            h.assertTrue(PowerNetworks.link(level, controller, relay) == PowerLinkRules.Result.OK, "Controller is an electrical node");
            h.assertTrue(PowerNetworks.coolantRadiators(level, port).equals(List.of(radiators.get(0))), "Port sees its linked radiator");
            h.assertTrue(PowerLinkRules.range(level.getBlockState(port)) == 16 && PowerLinkRules.range(level.getBlockState(radiators.get(0))) == 16, "Coolant reach is 16");
        });
    }

    /** Radiators need open sky; without skyAccess the framework roofs the structure with barriers. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100, skyAccess = true)
    public static void reactorRadiatorsMakePowerForTheNetwork(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = build(h); var be = controller(h, controller);
            be.physics().setCoreC(300);
            be.second(level);
            h.assertTrue(be.powerWatts() == 0 && be.radiatorCount() == 0, "No radiators, no power");
            var port = h.absolutePos(new BlockPos(CX + 1, CY, CZ));
            for (var r : radiators(h, 6)) link(h, port, r);
            be.physics().setCoreC(300);
            be.second(level);
            h.assertTrue(be.radiatorCount() == 6 && be.powerWatts() > 1000, "Six radiators carry heat out as power: " + be.powerWatts());
            h.assertTrue(level.getBlockState(radiatorsAt(h).get(0)).getValue(ReactorRadiatorBlock.HOT), "Working radiators glow");
            var lamp = place(h, CX, 1, CZ - 4, ModBlocks.PANEL_LIGHT.get());
            link(h, controller, lamp);
            allocate(level);
            h.assertTrue(level.getBlockState(lamp).getValue(PanelLightBlock.LIT), "Reactor supply lights a linked panel");
            be.physics().setLoadFollow(true);
            h.assertTrue(PowerNetworks.loadFollowTargetWatts(level, controller, be.powerWatts()) == PanelLightBlock.DEMAND_WATTS,
                    "Load-follow asks for exactly the network demand");
            for (int x = 1; x <= 3; x++) for (int z = 14; z <= 16; z++) h.setBlock(new BlockPos(x, CY + 6, z), Blocks.STONE);
            be.physics().setCoreC(300); be.second(level);
            h.assertTrue(be.radiatorCount() == 4, "Roofed radiators stop counting: " + be.radiatorCount());
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void reactorMenuLocksHotRodsAndControlsServerSide(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = build(h); var be = controller(h, controller);
            be.items().set(0, new ItemStack(ModItems.FUEL_ROD.get()));
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "reactor_operator")); player.setPos(controller.north(2).getCenter());
            var menu = new ReactorControllerMenu(1, player.getInventory(), be);
            h.assertTrue(menu.slots.get(0).mayPickup(player), "Cold core: rods can be pulled");
            be.physics().setCoreC(460);
            h.assertTrue(!menu.slots.get(0).mayPickup(player) && !menu.slots.get(1).mayPlace(new ItemStack(ModItems.FUEL_ROD.get())), "Rod slots lock above 350C");
            h.assertTrue(!menu.slots.get(ReactorControllerBlockEntity.OUTPUT).mayPlace(new ItemStack(ModItems.SPENT_FUEL_ROD.get())), "Output is take-only");
            be.physics().setLoadFollow(true);
            h.assertTrue(menu.clickMenuButton(player, ReactorControllerMenu.INSERTION + 40) && Math.abs(be.physics().insertion() - 0.4) < 1e-9
                    && !be.physics().loadFollow(), "Manual insertion hands control back from load-follow");
            h.assertTrue(menu.clickMenuButton(player, ReactorControllerMenu.SCRAM) && be.physics().scrammed(), "SCRAM button trips");
            h.assertTrue(!menu.clickMenuButton(player, ReactorControllerMenu.RESET), "Reset refused while hot");
            h.assertTrue(menu.clickMenuButton(player, ReactorControllerMenu.AUTO_OFF) && !be.physics().autoScram(), "Confirmed disable reaches the server");
            h.assertTrue(menu.clickMenuButton(player, ReactorControllerMenu.AUTO_ON) && be.physics().autoScram(), "Re-enabling is one click");
            h.assertTrue(!menu.clickMenuButton(player, 99) && !menu.clickMenuButton(player, 201), "Unknown ids are refused");
            player.setPos(controller.north(20).getCenter());
            h.assertTrue(!menu.clickMenuButton(player, ReactorControllerMenu.SCRAM), "Distant players cannot operate it");
            level.setBlock(controller.north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            be.physics().setCoreC(20); be.physics().reset(false);
            be.second(level);
            h.assertTrue(be.physics().scrammed(), "A redstone signal trips SCRAM");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void reactorBurnsRodsAndEjectsSpentFuel(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = build(h); var be = controller(h, controller);
            var rod = new ItemStack(ModItems.FUEL_ROD.get()); rod.setDamageValue(rod.getMaxDamage() - 1);
            be.items().set(0, rod); be.items().set(1, new ItemStack(ModItems.FUEL_ROD.get()));
            be.physics().setInsertion(1);
            be.second(level);
            h.assertTrue(be.items().get(0).isEmpty() && be.items().get(ReactorControllerBlockEntity.OUTPUT).is(ModItems.SPENT_FUEL_ROD.get()), "Exhausted rod drops into the output");
            h.assertTrue(be.items().get(1).getDamageValue() == 1, "Full insertion burns one second of life");
            var saved = be.saveWithFullMetadata(level.registryAccess());
            var copy = new ReactorControllerBlockEntity(controller, be.getBlockState()); copy.loadWithComponents(saved, level.registryAccess());
            h.assertTrue(copy.items().get(1).getDamageValue() == 1 && Math.abs(copy.physics().insertion() - 1) < 1e-9
                    && Math.abs(copy.physics().coreC() - be.physics().coreC()) < 1e-9, "Rods and core state survive a reload");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void reactorMeltdownLeavesCoriumAndScorchedCasing(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var controller = build(h); var be = controller(h, controller);
            for (int i = 0; i < 4; i++) be.items().set(i, new ItemStack(ModItems.FUEL_ROD.get()));
            be.physics().setAutoScram(true); be.physics().setInsertion(1); be.physics().setCoreC(ReactorPhysics.MELTDOWN_C - 0.5);
            be.second(level);
            h.assertTrue(!be.physics().meltdown() && be.physics().scrammed(), "With automatic SCRAM on, the core never melts");
            // Clear the latch the way an operator cannot: disabled auto-SCRAM, core just under 800C.
            var tag = new net.minecraft.nbt.CompoundTag(); be.physics().save(tag);
            tag.putBoolean("Scram", false); tag.putBoolean("AutoScram", false); tag.putDouble("CoreC", ReactorPhysics.MELTDOWN_C - 0.5);
            be.physics().load(tag);
            var live = be;
            live.second(level);
            h.assertTrue(live.physics().meltdown(), "Disabled auto-SCRAM lets the core reach 800C");
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(CX, CY, CZ))).is(ModBlocks.MOLTEN_CORIUM.get()), "Core becomes molten Corium");
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(CX - 1, CY - 1, CZ - 1))).getValue(ReactorCasingBlock.SCORCHED), "Casing survives, scorched");
            h.assertTrue(live.items().subList(0, 4).stream().allMatch(ItemStack::isEmpty), "Fuel is lost in the melt");
            h.assertTrue(level.getBlockState(controller).getValue(ReactorControllerBlock.ALARM) == 3, "Controller shows red");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void moltenCoriumMeltsThroughCasingThenPoolsAndCools(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            for (int x = 3; x <= 7; x++) for (int z = 3; z <= 7; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.OBSIDIAN);
            for (int x = 3; x <= 7; x++) for (int y = 2; y <= 4; y++) for (int z = 3; z <= 7; z++) h.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            h.setBlock(new BlockPos(5, 5, 5), ModBlocks.REACTOR_CASING.get());
            var start = h.absolutePos(new BlockPos(5, 7, 5));
            level.setBlock(start, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3);
            var pos = start;
            for (int i = 0; i < 12; i++) {
                MoltenCoriumBlock.step(level, pos);
                if (!(level.getBlockState(pos).getBlock() instanceof MoltenCoriumBlock)) pos = pos.below();
            }
            var floor = h.absolutePos(new BlockPos(5, 2, 5));
            h.assertTrue(level.getBlockState(floor).is(ModBlocks.MOLTEN_CORIUM.get()), "Falls through air, melts casing and stone, stops on obsidian");
            h.assertTrue(level.getBlockState(start).isAir() && level.getBlockState(h.absolutePos(new BlockPos(5, 5, 5))).isAir(), "Leaves a melted channel");
            h.assertTrue(level.getBlockState(h.absolutePos(new BlockPos(5, 1, 5))).is(Blocks.OBSIDIAN), "Obsidian holds");
            h.assertTrue(level.getBlockState(floor.east()).is(Blocks.STONE), "Buried in its channel, it cannot spread");
            for (int i = 0; i < MoltenCoriumBlock.MAX_AGE + 2; i++) MoltenCoriumBlock.step(level, floor);
            h.assertTrue(level.getBlockState(floor).is(ModBlocks.CORIUM.get()), "Cools into solid Corium");
            var open = h.absolutePos(new BlockPos(9, 2, 9));
            h.setBlock(new BlockPos(9, 1, 9), Blocks.OBSIDIAN); for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) h.getLevel().setBlock(open.below().relative(d), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(open, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3);
            MoltenCoriumBlock.step(level, open);
            h.assertTrue(level.getBlockState(open.east()).is(ModBlocks.MOLTEN_CORIUM.get()) && !level.getBlockState(open.east()).getValue(MoltenCoriumBlock.HEAD), "On an open floor it pools");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void moltenCoriumMakesSteamAndExplodesInWater(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel();
            var a = h.absolutePos(new BlockPos(3, 3, 3));
            level.setBlock(a.below(), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(a, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3);
            level.setBlock(a.east(), Blocks.ICE.defaultBlockState(), 3);
            var wall = h.absolutePos(new BlockPos(3, 3, 6)); level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            MoltenCoriumBlock.step(level, a);
            h.assertTrue(level.getBlockState(a.east()).isAir() && level.getBlockState(wall).is(Blocks.STONE), "A little ice flashes to steam, no explosion");
            var b = h.absolutePos(new BlockPos(14, 4, 14));
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setBlock(b.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) level.setBlock(b.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 3);
            level.setBlock(b, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3);
            MoltenCoriumBlock.step(level, b);
            boolean broke = false;
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) broke |= level.getBlockState(b.offset(x, -1, z)).isAir();
            h.assertTrue(broke, "A pool of water around molten Corium is a block-breaking steam explosion");
            boolean survived = false;
            for (int dy = 0; dy >= -3; dy--) survived |= level.getBlockState(b.offset(0, dy, 0)).is(ModBlocks.MOLTEN_CORIUM.get());
            h.assertTrue(survived, "The melt survives the blast and keeps sinking into the crater");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void moltenCoriumIgnitesFuelWhenAirIsPresent(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var p = h.absolutePos(new BlockPos(5, 3, 5));
            level.setBlock(p.below(), Blocks.OBSIDIAN.defaultBlockState(), 3);
            level.setBlock(p.north(2), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            level.setBlock(p, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3);
            h.assertTrue(com.frozendawn.world.CombustionAtmosphere.canBurnAt(level, p), "Test world has air");
            MoltenCoriumBlock.step(level, p);
            boolean fire = false;
            for (var q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 2, 2))) fire |= level.getBlockState(q).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock;
            h.assertTrue(fire, "With oxygen, it sets nearby fuel alight");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "reactor", timeoutTicks = 100)
    public static void uraniumRefinesOnlyInTheInductionFurnace(GameTestHelper h) {
        scene(h, () -> {
            var level = h.getLevel(); var raw = new ItemStack(ModItems.RAW_URANIUM.get());
            h.assertTrue(level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(raw), level).isEmpty(), "Fire-fed furnaces cannot refine uranium");
            h.assertTrue(level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BLASTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(raw), level).isEmpty(), "Blast furnaces cannot either");
            h.assertTrue(InductionFurnaceBlockEntity.smeltable(level, raw), "The Induction Furnace accepts Raw Uranium");
            var pos = place(h, 5, 2, 5, ModBlocks.INDUCTION_FURNACE.get());
            var furnace = (InductionFurnaceBlockEntity) level.getBlockEntity(pos);
            furnace.setItem(0, new ItemStack(ModItems.RAW_URANIUM.get(), 4));
            h.assertTrue(furnace.status() == 1 && furnace.wantsPower(), "Unpowered, it waits for the grid rather than rejecting the recipe");
            h.assertTrue(furnace.demandWatts() == com.frozendawn.recipe.InductionRecipes.REFINING_WATTS, "Refining is a hotter, 8 kW process");
            h.assertTrue(PowerNetworks.loadFollowTargetWatts(level, pos, 0) == 0, "Unlinked furnace asks nothing of a reactor");
            furnace.setItem(0, new ItemStack(net.minecraft.world.item.Items.RAW_IRON, 4));
            h.assertTrue(furnace.demandWatts() == InductionFurnaceBlockEntity.DEMAND_WATTS, "Ordinary smelting stays at 2 kW");
            var recipe = com.frozendawn.recipe.InductionRecipes.find(raw).orElseThrow().value();
            h.assertTrue(recipe.getResultItem(level.registryAccess()).is(ModItems.URANIUM_CONCENTRATE.get()), "Refines into Uranium Concentrate");
        });
    }

    /** Core at (CX,CY,CZ); Controller on the north face, ports east and south, casing west. */
    private static BlockPos build(GameTestHelper h) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            var rel = new BlockPos(CX + dx, CY + dy, CZ + dz);
            Block block = ModBlocks.REACTOR_CASING.get();
            if (dx == 0 && dz == 0 && dy == 1) block = ModBlocks.CONTROL_ROD_DRIVE.get();
            else if (dx == 0 && dy == 0 && dz == 0) block = ModBlocks.REACTOR_CORE.get();
            else if (dy == 0 && (dx == 1 && dz == 0 || dx == 0 && dz == 1)) block = ModBlocks.COOLANT_PORT.get();
            else if (dy == 0 && dx == 0 && dz == -1) {
                h.setBlock(rel, ModBlocks.REACTOR_CONTROLLER.get().defaultBlockState().setValue(ReactorControllerBlock.FACING, Direction.NORTH));
                continue;
            }
            h.setBlock(rel, block);
        }
        return h.absolutePos(new BlockPos(CX, CY, CZ - 1));
    }
    private static List<BlockPos> radiatorsAt(GameTestHelper h) {
        var list = new ArrayList<BlockPos>();
        for (int x = 2; x <= 13; x++) list.add(h.absolutePos(new BlockPos(x, CY + 4, 15)));
        return list;
    }
    private static List<BlockPos> radiators(GameTestHelper h, int count) {
        var list = radiatorsAt(h).subList(0, count);
        for (var p : list) h.getLevel().setBlock(p, ModBlocks.REACTOR_RADIATOR.get().defaultBlockState(), 3);
        return List.copyOf(list);
    }
    private static ReactorControllerBlockEntity controller(GameTestHelper h, BlockPos pos) {
        if (!(h.getLevel().getBlockEntity(pos) instanceof ReactorControllerBlockEntity be)) throw new GameTestAssertException("Controller block entity missing");
        return be;
    }
    private static BlockPos place(GameTestHelper h, int x, int y, int z, Block block) { var pos = new BlockPos(x, y, z); h.setBlock(pos, block); return h.absolutePos(pos); }
    private static void link(GameTestHelper h, BlockPos a, BlockPos b) { h.assertTrue(PowerNetworks.link(h.getLevel(), a, b) == PowerLinkRules.Result.OK, "Expected link " + a + " -> " + b); }
    private static void allocate(ServerLevel level) { for (int i = 0; i < 128; i++) PowerNetworks.update(level); PowerNetworks.account(level); }
    private static void scene(GameTestHelper h, Runnable body) { h.runAfterDelay(1, () -> { body.run(); h.succeed(); }); }
}
