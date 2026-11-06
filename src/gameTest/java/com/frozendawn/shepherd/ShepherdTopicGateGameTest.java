package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.WinConditionState;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What a topic's gate reads, and whose. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShepherdTopicGateGameTest {
    private static final BlockPos SPOT = new BlockPos(10, 4, 10);
    private static final int WARY = -300, TRUSTED = 600;
    private static final String PLUSH = "carrying:stuffed_penguin";

    @BeforeBatch(batch = "shepherd_gate_join") public static void joinBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_gate_join") public static void joinAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_gate_carrying") public static void carryingBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_gate_carrying") public static void carryingAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }
    @BeforeBatch(batch = "shepherd_gate_conspiracy") public static void conspiracyBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @AfterBatch(batch = "shepherd_gate_conspiracy") public static void conspiracyAfter(ServerLevel l) { ShepherdTestWorld.clear(l); WinConditionState.get(l.getServer()).setConspiracyDiscovered(false); }
    @BeforeBatch(batch = "shepherd_gate_find") public static void findBefore(ServerLevel l) { ShepherdTestWorld.fresh(l); }
    @AfterBatch(batch = "shepherd_gate_find") public static void findAfter(ServerLevel l) { ShepherdTestWorld.clear(l); }

    private static ShepherdSavedData data(GameTestHelper h) {
        return ShepherdSavedData.get(h.getLevel().getServer());
    }

    private static ServerPlayer player(GameTestHelper h) {
        return ShepherdTestWorld.onlinePlayer(h, h.absoluteVec(Vec3.atCenterOf(SPOT)).add(1, 0, 0));
    }

    private static void find(GameTestHelper h, ServerPlayer player, String doc) {
        var filed = h.getLevel().getServer().getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_" + doc));
        player.getAdvancements().award(filed, "impossible");
    }

    private static void assertHolds(GameTestHelper h, ServerPlayer player, String condition, boolean holds) {
        h.assertTrue(ShepherdTalk.holds(player, condition) == holds, "'" + condition + "' should be " + holds);
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_gate_join", timeoutTicks = 400)
    public static void joinedConditionsNeedEveryPart(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            data(h).flag("gate_a", true);
            data(h).flag("gate_b", true);
            assertHolds(h, player, "gate_a&gate_b", true);
            assertHolds(h, player, "gate_a&gate_off", false);
            assertHolds(h, player, "gate_off&gate_a", false);
            assertHolds(h, player, "gate_a&", false);
            assertHolds(h, player, "&gate_a", false);
            assertHolds(h, player, "gate_a&&gate_b", false);
            assertHolds(h, player, "gate_a&not:", false);
            assertHolds(h, player, "gate_a&not:gate_off", true);
            assertHolds(h, player, "not:gate_off&gate_a", true);
            assertHolds(h, player, "not:gate_off&gate_off", false);
            assertHolds(h, player, "not:gate_a&gate_b", false);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_gate_carrying", timeoutTicks = 400)
    public static void shepherdCarryingReadsTheSpeaker(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var carrier = player(h);
            var other = player(h);
            assertHolds(h, carrier, PLUSH, false);
            carrier.getInventory().add(new ItemStack(ModItems.STUFFED_PENGUIN.get()));
            assertHolds(h, carrier, PLUSH, true);
            assertHolds(h, other, PLUSH, false);
            carrier.getInventory().clearContent();
            carrier.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.STUFFED_PENGUIN.get()));
            assertHolds(h, carrier, PLUSH, true);
            carrier.getInventory().clearContent();
            assertHolds(h, carrier, PLUSH, false);
            // An item that does not exist reads as air, and every empty slot is air.
            assertHolds(h, carrier, "carrying:no_such_item", false);
            assertHolds(h, carrier, "carrying:", false);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_gate_conspiracy", timeoutTicks = 400)
    public static void shepherdConspiracyOpensItsTopic(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var player = player(h);
            String gate = "tier_above_wary&conspiracy";
            data(h).affinity(TRUSTED);
            assertHolds(h, player, gate, false);
            WinConditionState.get(h.getLevel().getServer()).setConspiracyDiscovered(true);
            assertHolds(h, player, gate, true);
            data(h).affinity(WARY);
            assertHolds(h, player, gate, false);
            h.succeed();
        });
    }

    @GameTest(template = GameTestTemplates.EMPTY_LARGE, batch = "shepherd_gate_find", timeoutTicks = 400)
    public static void shepherdAFindBelongsToItsSpeaker(GameTestHelper h) {
        ShepherdTestWorld.whenTicking(h, () -> {
            var finder = player(h);
            var other = player(h);
            String gate = "tier_trusted&found:terminus_log_maintenance";
            data(h).affinity(TRUSTED);
            assertHolds(h, finder, gate, false);
            find(h, finder, "terminus_log_maintenance");
            assertHolds(h, finder, gate, true);
            assertHolds(h, other, gate, false);
            h.succeed();
        });
    }
}
