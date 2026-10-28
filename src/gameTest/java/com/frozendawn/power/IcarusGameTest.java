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
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;

/** ORSA AMR-1 Icarus: blueprints, fuel, structure and the running reactor. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IcarusGameTest {
    @BeforeBatch(batch = "icarus_archive")
    public static void reportArchive(ServerLevel level) { GameTestReporting.installReporter(level); }
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

    private static boolean namesProjectIcarus(Component line) {
        if (line.getString().contains("Project Icarus")) return true;
        return line.getContents() instanceof TranslatableContents t && java.util.Arrays.stream(t.getArgs())
                .anyMatch(a -> a instanceof Component c && c.getContents() instanceof TranslatableContents k && k.getKey().equals("blueprint.frozendawn.icarus"));
    }
    private static AntiparticleFoundryBlockEntity foundry(ServerLevel l, BlockPos p) { return (AntiparticleFoundryBlockEntity) l.getBlockEntity(p); }
    private static BlockPos place(GameTestHelper h, int x, int y, int z, Block b) { var p = new BlockPos(x, y, z); h.setBlock(p, b); return h.absolutePos(p); }
    private static void link(GameTestHelper h, BlockPos a, BlockPos b) { var result = PowerNetworks.link(h.getLevel(), a, b); h.assertTrue(result == PowerLinkRules.Result.OK, "Expected link " + a + " -> " + b + ", got " + result); }
}
