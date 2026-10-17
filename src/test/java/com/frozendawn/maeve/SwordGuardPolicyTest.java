package com.frozendawn.maeve;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordGuardPolicyTest {
    private static final UUID PLAYER = new UUID(0, 31), ACTOR = new UUID(0, 32);
    private static final String DIM = "minecraft:overworld";
    private static StrategyPerformance start(long now) {
        var memory = new StrategyPerformance(); memory.begin(now);
        var event = new MaeveDirector.EvidenceSnapshot(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, now - 1, "WITNESSED_DAMAGE_SWORD", true);
        memory.start(new MaeveDirector.PositionDirective(PLAYER, ACTOR, UUID.randomUUID(), BeliefStore.SWORD, .8,
                event, BlockPos.ZERO, null, 1.5, now, -1, -1, -1, null, null));
        memory.arrived(now + 1); return memory;
    }
    @Test void preventedDamageIsSeparateAndSurvivesRoundTrip() {
        var memory = start(100);
        memory.block(ACTOR, PLAYER, DIM, BlockPos.ZERO, 110, 8);
        memory.damage(ACTOR, PLAYER, DIM, BlockPos.ZERO, 111, 2, false);
        memory.finish("TIME_COMPLETE");
        var row = memory.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(1, row.getInt("successes"));
        var result = row.getList("results", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(8, result.getFloat("blocked")); assertEquals(0, result.getFloat("dealt")); assertEquals(2, result.getFloat("received"));
        assertEquals(memory.save(), StrategyPerformance.load(memory.save()).save());
        var legacy = memory.save();
        legacy.getList("contexts", Tag.TAG_COMPOUND).getCompound(0).getList("results", Tag.TAG_COMPOUND).getCompound(0).remove("blocked");
        assertEquals(0, StrategyPerformance.load(legacy).save().getList("contexts", Tag.TAG_COMPOUND)
                .getCompound(0).getList("results", Tag.TAG_COMPOUND).getCompound(0).getFloat("blocked"));
    }
    @Test void wrongContactsSilenceAndUnobservedInterruptionsCannotEarnSuccess() {
        var memory = start(100);
        memory.block(UUID.randomUUID(), PLAYER, DIM, BlockPos.ZERO, 110, 8);
        memory.block(ACTOR, UUID.randomUUID(), DIM, BlockPos.ZERO, 110, 8);
        memory.block(ACTOR, PLAYER, "minecraft:the_nether", BlockPos.ZERO, 110, 8);
        memory.block(ACTOR, PLAYER, DIM, BlockPos.ZERO, 110, Float.NaN);
        memory.finish("TIME_COMPLETE");
        assertEquals(1, memory.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0).getInt("unknown"));
        for (String reason : new String[]{"ATTENTION_EVICTED", "RELOAD_RELEASED", "UNOBSERVED_DAMAGE", "ERASED"}) {
            memory = start(100); memory.block(ACTOR, PLAYER, DIM, BlockPos.ZERO, 110, 8); memory.finish(reason);
            assertEquals(1, memory.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0).getInt("unknown"), reason);
        }
    }
    @Test void DisabledBrokenOrDeadCannotWinFromTheDamageItPrevented() {
        for (String reason : new String[]{"SHIELD_DISABLED", "SHIELD_BROKEN", "OWNER_KILLED"}) {
            var memory = start(100); memory.block(ACTOR, PLAYER, DIM, BlockPos.ZERO, 110, 80); memory.finish(reason);
            assertEquals(1, memory.save().getList("contexts", Tag.TAG_COMPOUND).getCompound(0).getInt("failures"), reason);
            memory.begin(1000);
            assertTrue(memory.multiplier(BeliefStore.SWORD, DIM) < 1);
            assertFalse(memory.deferred(BeliefStore.SWORD, DIM));
        }
    }
}
