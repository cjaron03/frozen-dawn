package com.frozendawn.homo;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.HeartSuccessorEntity;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Payoff branch 5 on a live server: node five reads the player's own Hearth record, and each
 * Successor says Kevin once, a reload included.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeartMemoryNodeGameTest {
    private static final long DAY = 24_000L;

    @BeforeBatch(batch = "heartmemorynode") public static void report(ServerLevel level) {
        GameTestReporting.installReporter(level);
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "heartmemorynode")
    public static void ledgerReadsTheHearthRecord(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ledger"));
        UUID id = player.getUUID();
        UUID hearthId = major(h).id();
        try {
            long arrived = 3 * DAY + 100L;
            data.recordPlayerContact(id, hearthId, arrived);
            data.recordPlayerContact(id, hearthId, arrived + ReturnedHearthSavedData.NEW_VISIT_GAP_TICKS);
            data.recordPlayerContact(id, hearthId, arrived + 2 * ReturnedHearthSavedData.NEW_VISIT_GAP_TICKS);
            data.recordHearthViolation(id, hearthId, 30 * DAY + 5L,
                    ReturnedHearthSavedData.HearthViolationReason.ENTITY_ATTACK);
            data.recordCongregationCasualty(id, hearthId, UUID.randomUUID(), 37 * DAY + 10L);
            data.recordCongregationCasualty(id, hearthId, UUID.randomUUID(), 37 * DAY + 20L);

            HeartMemoryLedger ledger = HeartMemoryLedger.of(player);
            h.assertTrue(ledger.contactDay() == 4, "First contact on day 4, got " + ledger.contactDay());
            h.assertTrue(ledger.cameBack() == 3,
                    "Three visits and the violation's own visit come back three times, got " + ledger.cameBack());
            h.assertTrue(ledger.violationDay() == 31, "The line crossed on day 31, got " + ledger.violationDay());
            h.assertTrue(ledger.casualties() == 2, "Two casualties, got " + ledger.casualties());
            h.assertTrue(ledger.casualtyDay() == 38, "Last casualty on day 38, got " + ledger.casualtyDay());
        } finally {
            data.erasePlayerFromHive(id);
        }
        h.assertTrue(HeartMemoryLedger.of(player).contactDay() == 0, "The record is cleared after");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "heartmemorynode", timeoutTicks = 120)
    public static void kevinOncePerSuccessor(GameTestHelper h) {
        HeartSuccessorEntity spoken = successor(h, new Vec3(1.5D, 3.0D, 1.5D));
        HeartSuccessorEntity unspoken = successor(h, new Vec3(3.5D, 3.0D, 3.5D));
        stagger(spoken);
        h.onEachTick(() -> {
            spoken.tickStagger();
        });
        h.runAfterDelay(31, () -> {
            h.assertTrue(spoken.kevinSpoken(), "Kevin is spoken 30 ticks into the first stagger");
            h.assertFalse(unspoken.kevinSpoken(), "A Successor that never staggered has not said it");

            HeartSuccessorEntity reloadedSpoken = reload(h, spoken);
            HeartSuccessorEntity reloadedUnspoken = reload(h, unspoken);
            h.assertTrue(reloadedSpoken.kevinSpoken(), "A reload keeps Kevin spoken");
            h.assertFalse(reloadedUnspoken.kevinSpoken(), "A reload keeps it unspoken");
            stagger(reloadedSpoken);
            stagger(reloadedUnspoken);
            h.onEachTick(() -> {
                reloadedSpoken.tickStagger();
                reloadedUnspoken.tickStagger();
            });
            h.runAfterDelay(31, () -> {
                h.assertTrue(reloadedSpoken.kevinSpoken(), "The spoken one stays spoken");
                h.assertTrue(reloadedUnspoken.kevinSpoken(),
                        "The reloaded one that had not spoken says it at its own stagger");
                h.succeed();
            });
        });
    }

    private static HeartSuccessorEntity successor(GameTestHelper h, Vec3 relative) {
        ServerLevel level = h.getLevel();
        HeartSuccessorEntity successor = ModEntities.HEART_SUCCESSOR.get().create(level);
        h.assertTrue(successor != null, "Successor created");
        Vec3 at = h.absoluteVec(relative);
        successor.configure(null, 3, 0, level.getGameTime() - HeartSuccessorPolicy.ASSEMBLY_TICKS - 1L,
                BlockPos.containing(at), 0L, 1.0F);
        successor.setMode(HeartSuccessorPolicy.Mode.CONDUCTING);
        successor.setPos(at.x, at.y, at.z);
        h.assertTrue(level.addFreshEntity(successor), "Successor added");
        return successor;
    }

    private static void stagger(HeartSuccessorEntity successor) {
        successor.setStaggerTicks(HeartSuccessorPolicy.STAGGER_TICKS);
        successor.setMode(HeartSuccessorPolicy.Mode.STAGGERED);
    }

    /** Save a Successor, remove it, and load its NBT into a new one, as a chunk reload does. */
    private static HeartSuccessorEntity reload(GameTestHelper h, HeartSuccessorEntity original) {
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);
        original.discard();
        HeartSuccessorEntity copy = ModEntities.HEART_SUCCESSOR.get().create(h.getLevel());
        h.assertTrue(copy != null, "Successor copy created");
        copy.load(tag);
        copy.setUUID(UUID.randomUUID());
        h.assertTrue(h.getLevel().addFreshEntity(copy), "Successor copy added");
        return copy;
    }

    /** The Major Hearth, planned here only if no earlier batch made one. */
    private static ReturnedHearthSavedData.HearthRecord major(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ReturnedHearthSavedData data = ReturnedHearthSavedData.get(level.getServer());
        if (data.hearth(HearthSelectionPolicy.HearthType.MAJOR).isEmpty()) {
            data.applySelectionPlan(HearthSelectionPolicy.createPlan(
                    level.getSeed(), h.absolutePos(BlockPos.ZERO)), level.getGameTime());
        }
        ReturnedHearthSavedData.HearthRecord hearth =
                data.hearth(HearthSelectionPolicy.HearthType.MAJOR).orElse(null);
        h.assertTrue(hearth != null, "No Major Hearth record to hold the contact");
        return hearth;
    }
}
