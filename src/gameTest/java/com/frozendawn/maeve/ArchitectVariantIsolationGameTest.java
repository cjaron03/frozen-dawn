package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Combined BA/Scribe gate: special variants remain separate from ordinary visual learning. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectVariantIsolationGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 100)
    public static void architectVariantsKeepScribeClaimsAndObservationSeparate(GameTestHelper helper) {
        MaeveObservationGameTest.withScene(helper, 920, scene -> {
            MaeveScribeGameTest.floor(scene, 16);
            var player = scene.player("variant_subject", 3, 3);
            MaeveScribeGameTest.gate(scene, player.getUUID());
            var blind = scene.architect(7, 3);
            blind.setBlindArchitect(true);
            var scribe = scene.architect(9, 3);
            var ordinary = scene.architect(11, 3);

            helper.assertFalse(MaeveDirector.designateScribe(blind, player),
                    "A BA cannot consume the eligible subject's Scribe claim");
            helper.assertTrue(MaeveDirector.designateScribe(scribe, player),
                    "Rejecting the BA preserves the claim for an ordinary Architect");
            scribe.becomeScribe();
            helper.assertFalse(MaeveDirector.designateScribe(ordinary, player),
                    "A subject still has at most one active Scribe");

            helper.assertTrue(ObservationCollector.canObserve(ordinary, player, false),
                    "Ordinary visual learning still works in the same open scene");
            helper.assertFalse(ObservationCollector.canObserve(blind, player, false),
                    "BA sensing cannot enter ordinary visual learning");
            helper.assertFalse(ObservationCollector.canObserve(scribe, player, false),
                    "The note-carrying Scribe cannot enter ordinary visual learning");
            var before = MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs();
            helper.assertTrue(scene.hit(blind, player, false, 1), "Actual BA hit is accepted");
            helper.assertTrue(scene.hit(scribe, player, false, 1), "Actual Scribe hit is accepted");
            helper.assertTrue(MaeveDirector.snapshot(scene.server, player.getUUID()).beliefs().equals(before),
                    "Neither special variant reports combat evidence through the shared damage hooks");
        });
    }
}
