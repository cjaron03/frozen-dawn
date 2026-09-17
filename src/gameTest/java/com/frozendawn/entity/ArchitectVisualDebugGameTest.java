package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.debug.architect.ArchitectDebugSnapshot;
import com.frozendawn.debug.architect.ArchitectLabRun;
import com.frozendawn.debug.architect.ArchitectLabScenario;
import com.frozendawn.debug.architect.ArchitectVisualDebug;
import com.frozendawn.network.ArchitectDebugPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectVisualDebugGameTest {
    private ArchitectVisualDebugGameTest() { }

    @GameTest(template = "lab/clear_corridor", timeoutTicks = 40)
    public static void visualSnapshotPreservesNavigationAndTerminalEvidence(GameTestHelper helper) {
        var run = ArchitectLabRun.prepare(helper.getLevel(), ArchitectLabScenario.CLEAR_CORRIDOR,
                ArchitectLabGameTest.frame(helper), 1337, false, false);
        run.begin();
        var actor = run.architect;
        helper.assertTrue(ArchitectVisualDebug.capture(actor, true) == null, "Visual capture must default off");
        actor.decisionJournal().visual().setEnabled(true);
        actor.setOnGround(true); // prepare() has not yet had a physics tick to establish support.
        var path = actor.getNavigation().createPath(run.target, 0);
        helper.assertTrue(path != null && path.getNodeCount() > 2, "Fixture must have a real vanilla path");
        actor.getNavigation().moveTo(path, 1);
        int next = path.getNextNodeIndex();
        actor.getRandom().setSeed(8128);
        var before = ArchitectVisualDebug.capture(actor, true, run.debugContext());
        for (int i = 0; i < 10; i++) ArchitectVisualDebug.capture(actor, true, run.debugContext());
        helper.assertTrue(actor.getNavigation().getPath() == path && path.getNextNodeIndex() == next,
                "Inspection must preserve the live path identity and cursor");
        helper.assertTrue(actor.getRandom().nextLong() == RandomSource.create(8128).nextLong(), "Inspection must not consume AI randomness");
        helper.assertTrue(!before.combat().commitKnown(), "Inspection must not invoke the production melee/path check");
        helper.assertTrue(before.actor().navigation().totalNodes() == path.getNodeCount(), "Must capture actual navigation nodes");
        helper.assertTrue(before.planner().equals(actor.decisionJournal().visual().latest().planner()), "Inspection must not advance D* search");
        run.finish(ArchitectLabRun.Status.ABORTED, "test terminal capture");
        var terminal = actor.decisionJournal().visual().latest();
        helper.assertTrue(actor.getNavigation().getPath() == null, "Lab cleanup must have stopped navigation");
        helper.assertTrue(terminal.actor().navigation().totalNodes() == path.getNodeCount(), "Terminal observation must precede navigation cleanup");
        helper.assertTrue(terminal.lab().status().equals("ABORTED"), "Terminal observation must carry the final lab outcome");
        helper.assertTrue(ArchitectVisualDebug.capture(actor, true) == terminal, "Later dumps must keep the terminal path");
        helper.assertTrue(before.lab().status().equals("RUNNING"), "Earlier snapshots must remain immutable");
        run.dispose();
        helper.succeed();
    }

    @GameTest(template = "lab/slab_low_roof", timeoutTicks = 40)
    public static void visualPacketPreservesFractionalCollisionAndTargetRoute(GameTestHelper helper) {
        var run = ArchitectLabRun.prepare(helper.getLevel(), ArchitectLabScenario.SLAB_LOW_ROOF,
                ArchitectLabGameTest.frame(helper), 2026, true, false);
        run.begin();
        run.architect.decisionJournal().visual().setEnabled(true);
        run.target.setOnGround(true);
        var targetPath = run.target.getNavigation().createPath(run.architect, 0);
        helper.assertTrue(targetPath != null, "Fixture must supply a target path");
        run.target.getNavigation().moveTo(targetPath, 1);
        var snapshot = ArchitectVisualDebug.capture(run.architect, true, run.debugContext());
        helper.assertTrue(snapshot.collisionShapes().stream().anyMatch(s -> s.block().equals("minecraft:stone_slab")
                        && Math.abs(s.bounds().maxY() - s.bounds().minY() - 0.5) < 0.00001),
                "Debug geometry must retain half-height collision, not full block substitutes");
        helper.assertTrue(snapshot.target().navigation().totalNodes() == targetPath.getNodeCount(), "Must retain target navigation separately");
        var view = new ArchitectDebugPayload.View(true, ArchitectDebugPayload.DEFAULT_LAYERS, false, true, snapshot, "");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ArchitectDebugPayload.STREAM_CODEC.encode(buffer, ArchitectDebugPayload.of(view));
            helper.assertTrue(buffer.readableBytes() < ArchitectDebugPayload.MAX_JSON_CHARS, "Real snapshot must fit the bounded packet");
            var decoded = ArchitectDebugPayload.STREAM_CODEC.decode(buffer).view();
            helper.assertTrue(view.equals(decoded), "Network view must exactly match dump schema, paths and fractional shapes");
            helper.assertTrue(snapshot.equals(ArchitectDebugSnapshot.JSON.fromJson(snapshot.json(), ArchitectDebugSnapshot.class)), "Dump must round trip");
        } finally { buffer.release(); }
        run.finish(ArchitectLabRun.Status.ABORTED, "test packet capture");
        run.dispose();
        helper.succeed();
    }
}
