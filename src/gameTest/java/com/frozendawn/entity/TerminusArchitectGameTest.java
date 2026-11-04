package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.Terminus;
import com.frozendawn.defense.TerminusGameTest;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Here for the Architect's own package: its ice is tracked privately. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TerminusArchitectGameTest {

    /** A stopped Architect leaves as its own deaths do: what it built comes down with it. */
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "terminus", timeoutTicks = 100)
    public static void terminusStoppedArchitectTakesItsIceWithIt(GameTestHelper h) {
        var op = TerminusGameTest.operator(h);
        var architect = TerminusGameTest.target(h, ModEntities.ARCHITECT.get(), 0, 8);
        var ice = architect.blockPosition().east(2);
        h.assertTrue(architect.placeTacticalIce(ice), "The Architect places its ice");
        var placed = h.getLevel().getBlockState(ice);
        h.runAfterDelay(1, () -> {
            Terminus.discharge(op, op.getMainHandItem(), InteractionHand.MAIN_HAND);
            h.runAfterDelay(Terminus.DEATH_TICKS + 2, () -> {
                h.assertTrue(architect.isRemoved(), "Gone after the crumble");
                h.assertTrue(!h.getLevel().getBlockState(ice).is(placed.getBlock()) && !placed.is(Blocks.AIR),
                        "Its ice went with it: " + h.getLevel().getBlockState(ice));
                h.succeed();
            });
        });
    }
}
