package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.gametest.GameTestPacing;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.*;
import com.frozendawn.item.AntimatterCapsuleItem;
import com.frozendawn.recipe.FabricatorRecipe;
import com.frozendawn.world.IcarusCraterData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    @BeforeBatch(batch = "icarus_reactor")
    public static void reportReactor(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch = "icarus_detonation")
    public static void reportDetonation(ServerLevel level) { GameTestReporting.installReporter(level); }
    @BeforeBatch(batch = "icarus_lab")
    public static void reportLab(ServerLevel level) { GameTestReporting.installReporter(level); }

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

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_reactor", timeoutTicks = 200)
    public static void icarusColdStartExportsPowerAfterTwoMinutes(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var l = h.getLevel(); var c = icarus(h, IcarusScenes.build(h, CENTER));
            var banks = banks(h, 3);
            c.physics().setCommissionedForTest(true);
            dock(h).setItem(0, AntimatterCapsuleItem.sealed(IcarusPhysics.CAPSULE).copyWithCount(2));
            matter(h).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
            var core = h.absolutePos(CENTER);
            h.assertFalse(l.getBlockState(core).getValue(HalseyCoreBlock.HIDDEN), "a cold core block is solid");
            h.assertTrue(c.press(IcarusControllerBlockEntity.BUTTON_START), "Start is accepted from COLD");
            c.second(l);
            long full = stored(banks);
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(stored(banks) < full, "The banks pay the 1 MW startup draw through the power port"))
                    .thenExecute(() -> {
                        for (int s = 0; s < 121; s++) c.second(l);
                        h.assertValueEqual(c.physics().state(), IcarusPhysics.State.RUNNING, "state after two minutes");
                        h.assertTrue(port(h).supplyWatts() > 0, "A running reactor exports net power, got " + port(h).supplyWatts() + " W");
                        h.assertValueEqual(dock(h).empties(), 2, "empty casings left in the dock");
                        h.assertTrue(l.getBlockState(core).getValue(HalseyCoreBlock.HIDDEN), "the plasma replaces the core block");
                        h.assertValueEqual(l.getBlockState(core).getLightEmission(l, core), 15, "the running core lights its chamber");
                        h.assertTrue(l.getBlockEntity(core) instanceof HalseyCoreBlockEntity, "the core keeps its block entity");
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_reactor", timeoutTicks = 200)
    public static void icarusStartupHoldsWithoutGridPower(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var l = h.getLevel(); var c = icarus(h, IcarusScenes.build(h, CENTER));
            c.physics().setCommissionedForTest(true);
            dock(h).setItem(0, AntimatterCapsuleItem.sealed(IcarusPhysics.CAPSULE).copyWithCount(2));
            h.assertTrue(c.press(IcarusControllerBlockEntity.BUTTON_START), "Start is accepted from COLD");
            c.second(l);
            // Real ticks let the port find nothing to pay its request from. The loop runs ten ticks clear of the
            // controller's own second, so the port has counted several unpaid ticks, not the one it forgives.
            h.runAfterDelay(21 + Math.floorMod(10 - (l.getGameTime() + 21), 20), () -> {
                for (int s = 0; s < 40; s++) c.second(l);
                h.assertValueEqual(c.physics().state(), IcarusPhysics.State.POWER_CHECK, "an unpowered start stays on the power check");
                h.assertValueEqual(c.readout().status(), IcarusPhysics.Status.HOLD_POWER, "hold reason");
                h.assertValueEqual(c.physics().coreCharge(), 0L, "nothing is injected without power");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "icarus_reactor", timeoutTicks = 40)
    public static void icarusMatterPortRejectsBlockEntityItems(GameTestHelper h) {
        h.assertTrue(IcarusMatterPortBlockEntity.accepts(new ItemStack(Items.COBBLESTONE)), "Plain blocks are matter");
        h.assertFalse(IcarusMatterPortBlockEntity.accepts(new ItemStack(Items.CHEST)), "A block with a block entity is refused");
        h.assertFalse(IcarusMatterPortBlockEntity.accepts(new ItemStack(Items.STICK)), "Items that are not blocks are refused");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_reactor", timeoutTicks = 100)
    public static void icarusShieldVaporizesIntrudingBlocks(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var c = running(h); var core = h.absolutePos(CENTER);
            l.setBlock(core.east(2), Blocks.DIRT.defaultBlockState(), 3);
            l.setBlock(core.above(3), Blocks.CHEST.defaultBlockState(), 3);
            ((net.minecraft.world.level.block.entity.ChestBlockEntity) l.getBlockEntity(core.above(3))).setItem(0, new ItemStack(Items.DIAMOND, 5));
            c.second(l);
            h.assertTrue(l.getBlockState(core.east(2)).isAir(), "Dirt inside the shield is vaporized");
            h.assertTrue(l.getBlockState(core.above(3)).isAir(), "A chest inside the shield is vaporized");
            h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(core).inflate(8)).isEmpty(),
                    "Vaporized blocks and their contents leave no items");
            h.assertValueEqual(c.physics().state(), IcarusPhysics.State.RUNNING, "state after an intrusion");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_reactor", timeoutTicks = 100)
    public static void icarusColeProtocolReturnsAntimatterToTheDock(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var c = running(h);
            h.assertTrue(c.press(IcarusControllerBlockEntity.BUTTON_COLE), "Cole Protocol is accepted while running");
            long atRecovery = -1;
            for (int s = 0; s < 181; s++) {
                c.second(l);
                if (atRecovery < 0 && c.physics().state() == IcarusPhysics.State.COLE_RECOVER) atRecovery = c.physics().coreCharge();
            }
            h.assertTrue(atRecovery > 0, "The core still held antimatter at recovery");
            h.assertValueEqual(c.readout().status(), IcarusPhysics.Status.SAFE_TO_DISMANTLE, "status after the Cole Protocol");
            h.assertValueEqual(c.physics().coreCharge(), 0L, "core charge after the Cole Protocol");
            h.assertValueEqual(dock(h).charge(), atRecovery, "sealed charge returned to the dock");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_reactor", timeoutTicks = 100)
    public static void icarusRedstoneClips(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var c = running(h);
            l.setBlock(c.getBlockPos().above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            c.second(l);
            h.assertValueEqual(c.physics().state(), IcarusPhysics.State.CLIPPED, "state after a redstone signal");
        });
    }

    // Detonation tests run in their own batch: a blast kills and pushes entities out to twice its radius, which must
    // not reach another batch's scene. A core charge of 512 gives radius 8, which stays inside the template.
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 100)
    public static void icarusBreakingAFrameBlockWithChargeDetonates(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var c = icarus(h, IcarusScenes.build(h, CENTER)); var core = h.absolutePos(CENTER);
            c.physics().setCoreChargeForTest(512);
            l.destroyBlock(IcarusScenes.cell(h, CENTER, Direction.SOUTH, 8, -6, 0), true);
            var jobs = jobsAt(l, core);
            h.assertValueEqual(jobs.size(), 1, "crater jobs at the core after a charged frame block breaks");
            h.assertTrue(Math.abs(jobs.get(0).radius() - 8) < 0.01, "512 charge gives radius 8, got " + jobs.get(0).radius());
            h.assertValueEqual(c.physics().coreCharge(), 0L, "core charge after the detonation");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 100)
    public static void icarusPartsAreArmedOnlyWhileTheCoreHoldsCharge(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var c = icarus(h, IcarusScenes.build(h, CENTER));
            var frame = IcarusScenes.cell(h, CENTER, Direction.SOUTH, 8, -6, 0);
            h.assertFalse(com.frozendawn.world.IcarusDetonation.armed(l, frame), "an empty reactor's frame is not armed");
            c.physics().setCoreChargeForTest(512);
            h.assertTrue(com.frozendawn.world.IcarusDetonation.armed(l, frame), "a charged reactor's frame is armed");
            h.assertFalse(com.frozendawn.world.IcarusDetonation.armed(l, frame.east(3)), "a block outside the reactor is not armed");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 100)
    public static void icarusBreakingAnEmptyReactorOnlyInvalidates(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var controller = IcarusScenes.build(h, CENTER); var c = icarus(h, controller);
            l.destroyBlock(IcarusScenes.cell(h, CENTER, Direction.SOUTH, 8, -6, 0), true);
            h.assertValueEqual(jobsAt(l, h.absolutePos(CENTER)).size(), 0, "crater jobs after an empty reactor loses a part");
            h.assertTrue(l.getBlockState(controller).is(ModBlocks.ICARUS_CONTROLLER.get()), "The controller is still there");
            c.second(l);
            h.assertValueEqual(c.problem(), IcarusStructure.Problem.FRAME, "the controller reports the missing frame block");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 100)
    public static void icarusExplosionNextToFrameDetonates(GameTestHelper h) {
        h.runAfterDelay(1, () -> {
            var l = h.getLevel(); var c = icarus(h, IcarusScenes.build(h, CENTER));
            var housing = IcarusScenes.cell(h, CENTER, Direction.SOUTH, -8, 0, 0);
            var piston = housing.west();
            l.setBlock(piston, Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.EAST), 3);
            l.setBlock(piston.west(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            h.runAfterDelay(6, () -> {
                h.assertTrue(l.getBlockState(housing).is(ModBlocks.ICARUS_STABILIZER_HOUSING.get()), "A piston cannot push a stabilizer housing");
                h.assertTrue(jobsAt(l, h.absolutePos(CENTER)).isEmpty(), "The piston did not set the reactor off");
                c.physics().setCoreChargeForTest(512);
                var beside = IcarusScenes.cell(h, CENTER, Direction.SOUTH, 9, -3, 0).getCenter();
                l.explode(null, beside.x, beside.y, beside.z, 4f, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
                h.assertValueEqual(jobsAt(l, h.absolutePos(CENTER)).size(), 1, "crater jobs after TNT beside the frame");
                h.succeed();
            });
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 100)
    public static void icarusDetonationDoesNotRetriggerItself(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var c = icarus(h, IcarusScenes.build(h, CENTER));
            c.physics().setCoreChargeForTest(512);
            l.destroyBlock(IcarusScenes.cell(h, CENTER, Direction.SOUTH, 8, -6, 0), true);
            l.destroyBlock(IcarusScenes.cell(h, CENTER, Direction.SOUTH, 0, 0, 0), true);
            l.destroyBlock(c.getBlockPos(), true);
            h.assertValueEqual(jobsAt(l, h.absolutePos(CENTER)).size(), 1, "crater jobs after three parts break");
        });
    }

    /** A second reactor's own part inside the first's crater: the carve removes it the way the crater does, without
     * drops, and that charged reactor goes up too. Only its controller and one support part fit beside the first. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 100)
    public static void icarusCraterChainsIntoASecondReactor(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var first = icarus(h, IcarusScenes.build(h, CENTER));
            var secondCore = new BlockPos(2, 7, 10);
            var controller = IcarusScenes.placeController(h, secondCore, Direction.NORTH);
            var port = IcarusScenes.cell(h, secondCore, Direction.NORTH, -3, -5, 8);
            IcarusScenes.set(h, port, ModBlocks.ICARUS_MATTER_PORT.get().defaultBlockState());
            var second = icarus(h, controller);
            first.physics().setCoreChargeForTest(512); second.physics().setCoreChargeForTest(1);
            l.destroyBlock(IcarusScenes.cell(h, CENTER, Direction.SOUTH, 8, -6, 0), true);
            l.setBlock(port, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            h.assertValueEqual(jobsAt(l, h.absolutePos(CENTER)).size(), 1, "crater jobs at the first core");
            h.assertValueEqual(jobsAt(l, h.absolutePos(secondCore)).size(), 1, "crater jobs at the second core");
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 200)
    public static void icarusShieldFailureDetonates(GameTestHelper h) {
        GameTestPacing.whenTicking(h, () -> {
            var l = h.getLevel(); var c = running(h); var core = h.absolutePos(CENTER);
            h.assertTrue(c.press(IcarusControllerBlockEntity.BUTTON_CLIP), "CLIP is accepted while running");
            c.physics().setCoreChargeForTest(512); c.physics().setReserveForTest(0);
            c.second(l);
            // As in icarusStartupHoldsWithoutGridPower: real ticks let the port find nothing to pay the shield's draw from.
            h.runAfterDelay(21 + Math.floorMod(10 - (l.getGameTime() + 21), 20), () -> {
                c.second(l);
                h.assertValueEqual(c.physics().state(), IcarusPhysics.State.CLIPPED, "state with the shield on nothing");
                h.assertTrue(Double.isFinite(c.readout().shieldEtaS()), "An unpowered shield with an empty reserve is falling");
                // The ETA is a straight-line projection and the core cools once CLIPped, so the shield outlasts the first
                // ETA; what must hold is that the core goes up on the second the shield runs out, not before.
                var last = c.readout();
                for (int s = 0; s < 2000 && jobsAt(l, core).isEmpty(); s++) { last = c.readout(); c.second(l); }
                h.assertValueEqual(jobsAt(l, core).size(), 1, "crater jobs once the shield runs out");
                h.assertTrue(last.shieldPct() > 0 && last.shieldEtaS() <= 1.5, "The second before the blast the shield was nearly out: "
                        + last.shieldPct() + "%, ETA " + last.shieldEtaS() + " s");
                h.assertValueEqual(c.physics().coreCharge(), 0L, "core charge after the detonation");
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

    /** The lab command's reactor: valid, cold and uncommissioned, with matter in a chest and enough stored charge on the
     * power port's grid for a commissioning start and three cold starts after it. Its own batch: the banks reach
     * past the template. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_lab", timeoutTicks = 100)
    public static void icarusLabBuildsAValidChargedReactor(GameTestHelper h) {
        var l = h.getLevel();
        var lab = com.frozendawn.world.IcarusLabPlacement.build(l, h.absolutePos(CENTER), Direction.SOUTH);
        h.assertValueEqual(lab.failedLinks(), 0, "failed lab links");
        var result = IcarusStructure.validate(l, lab.controller(), Direction.SOUTH, true);
        h.assertTrue(result.valid(), "Expected the lab reactor to validate, got " + result.problem() + " at " + result.wrong());
        var c = icarus(h, lab.controller());
        h.assertValueEqual(c.physics().state(), IcarusPhysics.State.COLD, "lab reactor state");
        h.assertFalse(c.physics().commissioned(), "the lab reactor shows commissioning on its first start");
        if (!(l.getBlockEntity(lab.matterChest()) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest))
            throw new GameTestAssertException("Expected a matter chest at " + lab.matterChest());
        int matter = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(Items.COBBLESTONE)) matter += chest.getItem(i).getCount();
        h.assertTrue(matter >= 64, "Expected at least a stack of matter in the chest, got " + matter);
        long first = IcarusPhysics.POWER_CHECK_S + IcarusPhysics.RESERVE_CHARGE_S + IcarusPhysics.SPINUP_S + IcarusPhysics.COMMISSIONING_S
                + IcarusPhysics.INJECTION_S + IcarusPhysics.PRIMING_S + IcarusPhysics.IGNITION_S;
        // A commissioning start, then three cold starts after Cole Protocols; only the reserve and stabilizer steps draw.
        long needed = (long) ((4 * (IcarusPhysics.RESERVE_CHARGE_S + IcarusPhysics.SPINUP_S) + IcarusPhysics.COMMISSIONING_S) * IcarusPhysics.STARTUP_DRAW_KW * 1000);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(PowerNetworks.priorityBudget(l, result.powerPort()) >= needed,
                        "Expected " + needed + " J on the power port's grid, got " + PowerNetworks.priorityBudget(l, result.powerPort())))
                .thenSucceed();
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_lab", timeoutTicks = 100)
    public static void icarusLabRechargeRefillsDrainedBanks(GameTestHelper h) {
        var l = h.getLevel();
        var lab = com.frozendawn.world.IcarusLabPlacement.build(l, h.absolutePos(CENTER), Direction.SOUTH);
        var banks = new java.util.ArrayList<PowerStorageBlockEntity>();
        for (int y = 0; y < com.frozendawn.world.IcarusLabPlacement.BANKS_Y; y++) for (int z = 0; z < com.frozendawn.world.IcarusLabPlacement.BANKS_Z; z++)
            for (int x = 0; x < com.frozendawn.world.IcarusLabPlacement.BANKS_X; x++)
                if (l.getBlockEntity(IcarusStructure.cell(h.absolutePos(CENTER), Direction.SOUTH, 10 + x, IcarusStructure.BOTTOM + 1 + y, 2 - z)) instanceof PowerStorageBlockEntity store) banks.add(store);
        h.assertValueEqual(banks.size(), com.frozendawn.world.IcarusLabPlacement.BANKS_X * com.frozendawn.world.IcarusLabPlacement.BANKS_Z * com.frozendawn.world.IcarusLabPlacement.BANKS_Y, "lab banks found");
        long full = stored(banks);
        for (var store : banks) for (int cell = 0; cell < store.getContainerSize(); cell++) store.setItem(cell, com.frozendawn.item.PowerCellItem.charged(0));
        h.assertValueEqual(stored(banks), 0L, "drained banks");
        int refilled = com.frozendawn.world.IcarusLabPlacement.recharge(l, lab.controller());
        h.assertTrue(refilled >= banks.size(), "Expected every lab bank recharged, got " + refilled);
        h.assertValueEqual(stored(banks), full, "stored after recharge");
        h.succeed();
    }

    private static IcarusControllerBlockEntity icarus(GameTestHelper h, BlockPos controller) {
        if (!(h.getLevel().getBlockEntity(controller) instanceof IcarusControllerBlockEntity c)) throw new GameTestAssertException("Icarus controller block entity missing");
        return c;
    }
    private static IcarusPowerPortBlockEntity port(GameTestHelper h) { return (IcarusPowerPortBlockEntity) h.getLevel().getBlockEntity(IcarusScenes.cell(h, CENTER, Direction.SOUTH, 1, -5, 8)); }
    private static IcarusCapsuleDockBlockEntity dock(GameTestHelper h) { return (IcarusCapsuleDockBlockEntity) h.getLevel().getBlockEntity(IcarusScenes.cell(h, CENTER, Direction.SOUTH, -2, -5, 8)); }
    private static IcarusMatterPortBlockEntity matter(GameTestHelper h) { return (IcarusMatterPortBlockEntity) h.getLevel().getBlockEntity(IcarusScenes.cell(h, CENTER, Direction.SOUTH, -3, -5, 8)); }
    /** A commissioned reactor with two capsules and a stack of cobblestone, started and driven straight to RUNNING. */
    private static IcarusControllerBlockEntity running(GameTestHelper h) {
        var l = h.getLevel(); var c = icarus(h, IcarusScenes.build(h, CENTER));
        c.physics().setCommissionedForTest(true);
        dock(h).setItem(0, AntimatterCapsuleItem.sealed(IcarusPhysics.CAPSULE).copyWithCount(2));
        matter(h).setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        h.assertTrue(c.press(IcarusControllerBlockEntity.BUTTON_START), "Start is accepted from COLD");
        for (int s = 0; s < 121; s++) c.second(l);
        h.assertValueEqual(c.physics().state(), IcarusPhysics.State.RUNNING, "state after two minutes");
        return c;
    }
    /** Full battery banks on the row behind the support blocks, each linked to the power port. */
    private static List<PowerStorageBlockEntity> banks(GameTestHelper h, int count) {
        var banks = new ArrayList<PowerStorageBlockEntity>(); var target = IcarusScenes.cell(h, CENTER, Direction.SOUTH, 1, -5, 8);
        for (int i = 0; i < count; i++) {
            var p = place(h, 9 + 2 * i, 2, 20, ModBlocks.BATTERY_BANK.get()); var store = (PowerStorageBlockEntity) h.getLevel().getBlockEntity(p);
            for (int cell = 0; cell < store.getContainerSize(); cell++) store.setItem(cell, com.frozendawn.item.PowerCellItem.charged(com.frozendawn.item.PowerCellItem.CAPACITY_JOULES));
            link(h, p, target); banks.add(store);
        }
        return banks;
    }
    private static long stored(List<PowerStorageBlockEntity> banks) { return banks.stream().mapToLong(PowerStorageBlockEntity::storedJoules).sum(); }
    /** Radius 8: a survival player at 1.75r (10 damage, so alive) takes an eighth of the burst, a creative player at the same range and a survival
     * player past 2r take nothing. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "icarus_detonation", timeoutTicks = 100)
    public static void icarusDetonationDosesNearbySurvivalPlayers(GameTestHelper h) {
        scene(h, () -> {
            var l = h.getLevel(); var core = h.absolutePos(CENTER);
            var near = player(h, core.offset(14, 0, 0), GameType.SURVIVAL);
            var creative = player(h, core.offset(-14, 0, 0), GameType.CREATIVE);
            var far = player(h, core.offset(0, 0, 17), GameType.SURVIVAL);
            try {
                com.frozendawn.world.IcarusDetonation.detonate(l, core, 8, java.util.Set.of());
                h.assertTrue(Math.abs(dose(near) - IcarusBlast.BURST_DOSE / 8) < 1e-6, "Burst dose at 1.75r, got " + dose(near));
                h.assertValueEqual(dose(creative), 0.0, "creative dose");
                h.assertValueEqual(dose(far), 0.0, "dose past 2r");
            } finally { near.discard(); creative.discard(); far.discard(); }
        });
    }
    private static double dose(ServerPlayer p) { return p.getData(ModAttachments.RADIATION.get()).dose; }
    private static ServerPlayer player(GameTestHelper h, BlockPos pos, GameType mode) {
        var level = h.getLevel(); var profile = new GameProfile(UUID.randomUUID(), "icarus_" + mode.getName());
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new net.neoforged.neoforge.common.util.FakePlayer(level, profile).connection;
        player.setPos(pos.getCenter()); player.setGameMode(mode); level.addNewPlayer(player);
        return player;
    }

    private static List<IcarusCraterData.Job> jobsAt(ServerLevel l, BlockPos core) {
        return IcarusCraterData.get(l).jobs().stream().filter(j -> j.center().equals(core)).toList();
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
