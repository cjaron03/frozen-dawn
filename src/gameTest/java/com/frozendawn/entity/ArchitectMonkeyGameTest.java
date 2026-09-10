package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.debug.architect.ArchitectLabScenario;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Discovery suite. Its own failing gate preserves findings without weakening the baseline. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArchitectMonkeyGameTest {
    private ArchitectMonkeyGameTest() { }

    @GameTestGenerator
    public static Collection<TestFunction> stressCases() {
        if (!Boolean.getBoolean("frozendawn.architect.monkey")) return List.of();
        var cases = new ArrayList<TestFunction>();
        for (ArchitectLabScenario scenario : ArchitectLabScenario.values()) {
            if (!scenario.stressCase()) continue;
            long[] seeds = scenario == ArchitectLabScenario.SEEDED_MAZE || scenario == ArchitectLabScenario.CORRIDOR_SHUTTLE
                    || scenario == ArchitectLabScenario.CORRIDOR_SOAK ? new long[]{1, 7, 42, 1337} : new long[]{7, 1337};
            if (scenario.fieldCase()) seeds = new long[]{1, 7, 42, 1337, 2026, 65537, 314159, 8675309};
            for (long seed : seeds) for (Rotation rotation : scenario.holeCase() || scenario.expandedCase() ? Rotation.values()
                    : new Rotation[]{Rotation.NONE, Rotation.CLOCKWISE_90}) {
                String name = "architectmonkey." + scenario.id + "_s" + seed + "_r" + rotation.ordinal();
                cases.add(new TestFunction("defaultBatch", name, scenario.template().toString(), rotation,
                        scenario.timeout + 10, 0, true, h -> ArchitectLabGameTest.runScenario(h, scenario, seed, false)));
            }
        }
        return cases;
    }
}
