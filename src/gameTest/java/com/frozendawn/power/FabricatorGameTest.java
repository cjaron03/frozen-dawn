package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.FabricatorBlueprints;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModDataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.*;

/** ORSA Fabricator: per-world blueprints learned from Data Slates, and the powered machine that prints parts from them. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FabricatorGameTest {
    @BeforeBatch(batch = "fabricator")
    public static void report(ServerLevel level) { GameTestReporting.installReporter(level); }

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
}
