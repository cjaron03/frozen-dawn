package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.*;
import com.frozendawn.item.AntimatterCapsuleItem;
import com.frozendawn.recipe.FabricatorRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

/** ORSA AMR-1 Icarus: blueprints, fuel, structure and the running reactor. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IcarusGameTest {
    @BeforeBatch(batch = "icarus_archive")
    public static void reportArchive(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch = "icarus_structure")
    public static void reportStructure(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch = "icarus_foundry")
    public static void reportFoundry(ServerLevel level) { GameTestReporting.installReporter(level); }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "icarus_archive", timeoutTicks = 40)
    public static void icarusSlateTeachesAllFourBlueprints(GameTestHelper h) {
        var server = h.getLevel().getServer(); var archive = FabricatorBlueprints.get(server);
        boolean was = archive.knows("icarus");
        try {
            archive.forget("icarus");
            var recipes = server.getRecipeManager();
            for (var name : List.of("halsey_core", "icarus_stabilizer", "icarus_injector", "antiparticle_foundry")) {
                var holder = recipes.byKey(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "fabricator/" + name));
                h.assertTrue(holder.isPresent() && holder.get().value() instanceof FabricatorRecipe r && "icarus".equals(r.blueprint()), "The " + name + " recipe needs the Icarus blueprint");
            }
            h.assertTrue(!FabricatorBlueprints.knows(server, "icarus"), "Icarus parts are locked before the slate");
            h.assertTrue(archive.learn("icarus") && FabricatorBlueprints.knows(server, "icarus"), "The Project Icarus slate teaches all four parts");
            var tooltip = new ArrayList<Component>();
            FabricatorBlueprints.slate("icarus").getItem().appendHoverText(FabricatorBlueprints.slate("icarus"), Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
            h.assertTrue(tooltip.stream().anyMatch(IcarusGameTest::namesProjectIcarus), "The slate tooltip names Project Icarus: " + tooltip);
        } finally {
            if (was) archive.learn("icarus"); else archive.forget("icarus");
        }
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "icarus_archive", timeoutTicks = 40)
    public static void icarusSlateIsNotNeededForTheFusionArchive(GameTestHelper h) {
        var archive = new FabricatorBlueprints();
        for (var id : FabricatorBlueprints.SLATES) if (!id.equals("icarus")) archive.learn(id);
        h.assertTrue(archive.knowsAll(), "Full Archive counts the fusion blueprints; Icarus is optional");
        h.assertTrue("icarus".equals(archive.unlearned(net.minecraft.util.RandomSource.create(3))), "The Icarus slate is still offered while unlearned");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_foundry", timeoutTicks = 200)
    public static void icarusFoundryMakesACapsuleAfterAPaidHour(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel();
            var core = place(h, 2, 2, 4, ModBlocks.GEOTHERMAL_CORE.get()); ((GeothermalCoreBlockEntity) l.getBlockEntity(core)).setPowerShare(100);
            var bank = place(h, 4, 2, 4, ModBlocks.BATTERY_BANK.get()); var store = (PowerStorageBlockEntity) l.getBlockEntity(bank);
            for (int i = 0; i < 4; i++) store.setItem(i, com.frozendawn.item.PowerCellItem.charged(com.frozendawn.item.PowerCellItem.CAPACITY_JOULES));
            var p = place(h, 6, 2, 4, ModBlocks.ANTIPARTICLE_FOUNDRY.get()); var f = foundry(l, p);
            f.setItem(AntiparticleFoundryBlockEntity.INPUT, new ItemStack(ModItems.EMPTY_ANTIMATTER_CAPSULE.get()));
            link(h, core, bank); link(h, bank, p);
            h.assertTrue(f.wantsPower() && f.demandWatts() == AntiparticleFoundryBlockEntity.DEMAND_WATTS && f.demandWatts() == 400_000, "An empty capsule asks for 400 kW");
            f.setProgressForTest(AntiparticleFoundryBlockEntity.SECONDS_PER_CAPSULE - 1);
            h.succeedWhen(() -> {
                var out = f.getItem(AntiparticleFoundryBlockEntity.OUTPUT);
                h.assertTrue(out.is(ModItems.ANTIMATTER_CAPSULE.get()) && out.getCount() == 1, "One paid second finishes the hour: " + out);
                h.assertTrue(AntimatterCapsuleItem.charge(out) == IcarusPhysics.CAPSULE, "The capsule holds one full charge");
                h.assertTrue(f.getItem(AntiparticleFoundryBlockEntity.INPUT).isEmpty() && !f.wantsPower(), "The empty is used and the idle foundry draws nothing");
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_foundry", timeoutTicks = 80)
    public static void icarusFoundryPausesWithoutPower(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel();
            var p = place(h, 6, 2, 4, ModBlocks.ANTIPARTICLE_FOUNDRY.get()); var f = foundry(l, p);
            f.setItem(AntiparticleFoundryBlockEntity.INPUT, new ItemStack(ModItems.EMPTY_ANTIMATTER_CAPSULE.get()));
            f.setProgressForTest(AntiparticleFoundryBlockEntity.SECONDS_PER_CAPSULE - 1);
            int held = f.progress();
            h.runAfterDelay(40, () -> {
                h.assertTrue(f.getItem(AntiparticleFoundryBlockEntity.OUTPUT).isEmpty() && f.progress() == held && !f.working(), "Unpowered, the foundry makes nothing and keeps its progress");
                h.assertTrue(f.getItem(AntiparticleFoundryBlockEntity.INPUT).getCount() == 1, "The empty capsule is kept");
                h.succeed();
            });
        });
    }

    private static final BlockPos CENTER = new BlockPos(10, 7, 10);

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_structure", timeoutTicks = 100)
    public static void icarusStructureValidatesAndNamesTheWrongCell(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var controller = IcarusScenes.build(h, CENTER);
            var result = IcarusStructure.validate(l, controller, Direction.SOUTH, true);
            h.assertTrue(result.valid(), "The canonical build is valid: " + result.problem() + " at " + result.wrong());
            h.assertValueEqual(result.core(), h.absolutePos(CENTER), "core");
            h.assertValueEqual(result.parts().size(), 1 + 1 + 9 + 4 + 36 + 104 + 5, "part cells");
            h.assertValueEqual(result.powerPort(), IcarusScenes.cell(h, CENTER, Direction.SOUTH, 1, -5, 8), "power port");
            h.assertValueEqual(result.reserveBank(), IcarusScenes.cell(h, CENTER, Direction.SOUTH, 2, -5, 8), "reserve bank");
            var corner = IcarusScenes.cell(h, CENTER, Direction.SOUTH, 8, -6, 8);
            IcarusScenes.set(h, corner, Blocks.STONE.defaultBlockState());
            var broken = IcarusStructure.validate(l, controller, Direction.SOUTH, true);
            h.assertValueEqual(broken.problem(), IcarusStructure.Problem.FRAME, "stone in the frame");
            h.assertValueEqual(broken.wrong(), corner, "the stone cell");
            IcarusScenes.set(h, corner, ModBlocks.ICARUS_FRAME.get().defaultBlockState());
            var emitter = IcarusScenes.cell(h, CENTER, Direction.SOUTH, 7, 0, 0);
            IcarusScenes.set(h, emitter, ModBlocks.ICARUS_STABILIZER.get().defaultBlockState().setValue(IcarusStabilizerBlock.FACING, Direction.NORTH));
            h.assertValueEqual(IcarusStructure.validate(l, controller, Direction.SOUTH, true).problem(), IcarusStructure.Problem.STABILIZER, "an emitter facing away from the core");
            IcarusScenes.set(h, emitter, ModBlocks.ICARUS_STABILIZER.get().defaultBlockState().setValue(IcarusStabilizerBlock.FACING, Direction.WEST));
            var dirt = h.absolutePos(CENTER).above(2);
            IcarusScenes.set(h, dirt, Blocks.DIRT.defaultBlockState());
            var envelope = IcarusStructure.validate(l, controller, Direction.SOUTH, true);
            h.assertValueEqual(envelope.problem(), IcarusStructure.Problem.ENVELOPE, "dirt inside the shield");
            h.assertValueEqual(envelope.wrong(), dirt, "the dirt cell");
            h.assertTrue(IcarusStructure.validate(l, controller, Direction.SOUTH, false).valid(), "The envelope is only checked on request");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_structure", timeoutTicks = 100)
    public static void icarusStructureTurnsWithTheController(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var controller = IcarusScenes.build(h, CENTER, Direction.EAST);
            var result = IcarusStructure.validate(l, controller, Direction.EAST, true);
            h.assertTrue(result.valid(), "The east-facing build is valid: " + result.problem() + " at " + result.wrong());
            h.assertValueEqual(IcarusStructure.core(controller, Direction.EAST), h.absolutePos(CENTER), "core from an east-facing controller");
            h.assertFalse(IcarusStructure.validate(l, controller, Direction.SOUTH, false).valid(), "The same blocks do not read as a south-facing build");
            h.assertTrue(IcarusStructure.contains(controller, Direction.EAST, IcarusScenes.cell(h, CENTER, Direction.EAST, 0, -3, 8)), "a pillar cell is a part");
            h.assertFalse(IcarusStructure.contains(controller, Direction.EAST, h.absolutePos(CENTER).above(2)), "an envelope cell is not a part");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_structure", timeoutTicks = 100)
    public static void icarusGhostGuideShowsOneStageAtATime(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var controller = IcarusScenes.placeController(h, CENTER, Direction.SOUTH);
            var frame = IcarusStructure.wrongCells(l, controller, Direction.SOUTH, Integer.MAX_VALUE);
            h.assertValueEqual(frame.size(), 104, "frame ghosts around a lone controller");
            h.assertTrue(frame.stream().allMatch(c -> c.expected() == ModBlocks.ICARUS_FRAME.get()), "Only frame ghosts show first");
            h.assertValueEqual(IcarusStructure.wrongCells(l, controller, Direction.SOUTH, 3).size(), 3, "limit");
            var remaining = IcarusStructure.remaining(l, controller, Direction.SOUTH);
            var first = remaining.entrySet().iterator().next();
            h.assertTrue(first.getKey() == ModBlocks.ICARUS_FRAME.get() && first.getValue() == 104, "Frame counts first: " + remaining);
            h.assertValueEqual(remaining.get(ModBlocks.ICARUS_STABILIZER_HOUSING.get()), 36, "housings remaining");
            h.assertValueEqual(remaining.get(ModBlocks.HALSEY_CORE.get()), 1, "core remaining");
            h.assertFalse(remaining.containsKey(ModBlocks.ICARUS_CONTROLLER.get()), "The placed controller is not remaining");
            IcarusScenes.frame(h, CENTER, Direction.SOUTH);
            var injector = IcarusStructure.wrongCells(l, controller, Direction.SOUTH, Integer.MAX_VALUE);
            h.assertValueEqual(injector.size(), 10, "injector stage cells");
            h.assertTrue(injector.stream().allMatch(c -> c.expected() == ModBlocks.ICARUS_INJECTOR_CASING.get() || c.expected() == ModBlocks.ICARUS_INJECTOR.get()),
                    "After the frame, only the injector stage shows");
            h.assertFalse(IcarusStructure.remaining(l, controller, Direction.SOUTH).containsKey(ModBlocks.ICARUS_FRAME.get()), "No frame remains");
            IcarusScenes.injector(h, CENTER, Direction.SOUTH); IcarusScenes.stabilizers(h, CENTER, Direction.SOUTH);
            IcarusScenes.core(h, CENTER, Direction.SOUTH); IcarusScenes.support(h, CENTER, Direction.SOUTH);
            h.assertTrue(IcarusStructure.wrongCells(l, controller, Direction.SOUTH, Integer.MAX_VALUE).isEmpty(), "A finished reactor shows no ghosts");
            var dirt = h.absolutePos(CENTER).above(2);
            IcarusScenes.set(h, dirt, Blocks.DIRT.defaultBlockState());
            var intruders = IcarusStructure.wrongCells(l, controller, Direction.SOUTH, Integer.MAX_VALUE);
            h.assertTrue(intruders.size() == 1 && intruders.get(0).pos().equals(dirt) && intruders.get(0).ghost() == null, "A block in the envelope shows as a cell to clear: " + intruders);
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_structure", timeoutTicks = 100)
    public static void icarusPartsCannotBePushed(GameTestHelper h) {
        for (var b : List.of(ModBlocks.ICARUS_FRAME, ModBlocks.ICARUS_INJECTOR_CASING, ModBlocks.ICARUS_STABILIZER_HOUSING, ModBlocks.ICARUS_INJECTOR,
                ModBlocks.ICARUS_STABILIZER, ModBlocks.HALSEY_CORE, ModBlocks.ICARUS_CONTROLLER, ModBlocks.ICARUS_POWER_PORT, ModBlocks.ICARUS_MATTER_PORT,
                ModBlocks.ICARUS_CAPSULE_DOCK, ModBlocks.ICARUS_RESERVE_BANK)) {
            var state = b.get().defaultBlockState();
            h.assertTrue(state.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, b.getId() + " resists pistons");
            h.assertFalse(state.is(net.minecraft.tags.BlockTags.ENDERMAN_HOLDABLE), b.getId() + " is not enderman-holdable");
            h.assertTrue(b.get() instanceof IcarusPartBlock, b.getId() + " is an Icarus part");
        }
        h.succeed();
    }

    private static void scene(GameTestHelper h, Runnable body) { h.runAfterDelay(1, () -> { body.run(); h.succeed(); }); }
    private static boolean namesProjectIcarus(Component line) {
        if (line.getString().contains("Project Icarus")) return true;
        return line.getContents() instanceof TranslatableContents t && java.util.Arrays.stream(t.getArgs())
                .anyMatch(a -> a instanceof Component c && c.getContents() instanceof TranslatableContents k && k.getKey().equals("blueprint.frozendawn.icarus"));
    }
    private static AntiparticleFoundryBlockEntity foundry(ServerLevel l, BlockPos p) { return (AntiparticleFoundryBlockEntity) l.getBlockEntity(p); }
    private static BlockPos place(GameTestHelper h, int x, int y, int z, Block b) { var p = new BlockPos(x, y, z); h.setBlock(p, b); return h.absolutePos(p); }
    private static void link(GameTestHelper h, BlockPos a, BlockPos b) { var result = PowerNetworks.link(h.getLevel(), a, b); h.assertTrue(result == PowerLinkRules.Result.OK, "Expected link " + a + " -> " + b + ", got " + result); }
}
