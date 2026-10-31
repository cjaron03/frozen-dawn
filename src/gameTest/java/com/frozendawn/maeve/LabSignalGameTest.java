package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.LabSignalActor;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LabSignalGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 200)
    public static void signalReplayUsesProductionDepartureWithoutBeliefMutation(GameTestHelper h) {
        MaeveObservationGameTest.withScene(h, 391, 3, s -> {
            MaeveDirector.snapshot(s.server, null); // Stabilize the real late-phase activation latch first.
            var before = MaeveSavedData.get(s.server).save(new CompoundTag(), s.server.registryAccess());
            var actor = new LabSignalActor(s.level, s.origin.offset(4, 0, 4));
            s.entities.add(actor); s.level.addFreshEntity(actor); actor.begin();
            h.assertTrue(!actor.shouldBeSaved(), "A presentation fixture cannot persist as an ordinary pawn on reload");
            for (int age = 0; age <= 600; age++) {
                s.clock(s.gameTime + age); actor.aiStep();
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(actor));
                if (age < 200) h.assertTrue(actor.getReconnaissanceDissolve() == 0, "Real departure keeps its ten-second walk");
                if (age == 200) h.assertTrue(actor.getReconnaissanceDissolve() == 1 && actor.getReconnaissanceCloudAge() == 0, "Dissolve begins on the production boundary");
                if (age == 220) h.assertTrue(actor.getReconnaissanceDissolve() == 20 && actor.getReconnaissanceCloudAge() == 20, "Production outgoing pulse age is retained");
                if (age == 540) h.assertTrue(actor.getReconnaissanceCloudAge() == 340, "Production incoming pulse age is retained");
                if (age == 580) h.assertTrue(actor.getReconnaissanceDissolve() == -20, "Reform lasts the real twenty ticks");
                h.assertTrue(actor.getTarget() == null, "Presentation must not acquire a combat target");
            }
            h.assertTrue(!actor.hasReconnaissanceEyes() && actor.getReconnaissanceDissolve() == 0, "Departure returns to the ordinary presentation");
            actor.discard();
            try (var area = new LabSignalScene(s.level, s.origin, false)) {
                for (int age = 0; age < 600; age += 20) { s.clock(s.gameTime + 600 + age); area.tick(); }
            }
            h.assertTrue(before.equals(MaeveSavedData.get(s.server).save(new CompoundTag(), s.server.registryAccess())), "Presentation playback must not leave beliefs, death evidence, groups or outcomes: before=" + before + " after=" + MaeveSavedData.get(s.server).save(new CompoundTag(), s.server.registryAccess()));
            var player = s.player("signal_operator", 1, 1); player.setGameMode(GameType.CREATIVE); player.addTag("macs_signal_player");
            var source = s.server.createCommandSourceStack().withEntity(player).withPosition(player.position()).withPermission(2);
            int result;
            try { result = s.server.getCommands().getDispatcher().execute("fdlab signal_prepare", source); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalStateException(e); }
            h.assertTrue(result == 0, "Signal command must refuse a world outside the exact lab world");
            var resources = s.server.getResourceManager().listResources("function", id -> id.getNamespace().equals("macs_signal") && id.getPath().endsWith(".mcfunction"));
            h.assertTrue(resources.size() == 7, "All seven neutral replay functions are present");
            resources.forEach((file, resource) -> {
                try (var reader = resource.openAsReader()) {
                    net.minecraft.commands.functions.CommandFunction.fromLines(file, s.server.getCommands().getDispatcher(), source, reader.lines().toList());
                } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            });
        });
    }
}
