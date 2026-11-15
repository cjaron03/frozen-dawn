package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.init.ModItems;
import com.frozendawn.lore.PayoffState;
import com.frozendawn.shepherd.ShepherdPolicy;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** CH-38's blanket: +5 from the player's own inventory, nowhere else, and still after the Heart. */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermalBlanketGameTest {
    private static boolean firedBefore, erasedBefore;

    @BeforeBatch(batch = "kevinsblanket") public static void record(ServerLevel level) {
        GameTestReporting.installReporter(level);
        MinecraftServer server = level.getServer();
        firedBefore = PayoffState.fired(server);
        erasedBefore = ReturnedHearthSavedData.get(server).maeveErased();
    }

    @AfterBatch(batch = "kevinsblanket") public static void restore(ServerLevel level) {
        MinecraftServer server = level.getServer();
        PayoffState.setForTest(server, firedBefore);
        ReturnedHearthSavedData.get(server).setMaeveErasedForDebug(erasedBefore, server.overworld().getGameTime());
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "kevinsblanket")
    public static void aBlanketInTheBagWarms(GameTestHelper h) {
        ServerPlayer player = player(h);
        h.assertTrue(ThermalBlanketItem.warmth(player) == 0f, "An empty bag gives nothing");
        player.getInventory().setItem(0, blanket());
        h.assertTrue(ThermalBlanketItem.warmth(player) == ShepherdPolicy.WARMTH_C, "Hotbar");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.getInventory().setItem(20, blanket());
        h.assertTrue(ThermalBlanketItem.warmth(player) == ShepherdPolicy.WARMTH_C, "Main inventory");
        player.getInventory().setItem(20, ItemStack.EMPTY);
        player.getInventory().offhand.set(0, blanket());
        h.assertTrue(ThermalBlanketItem.warmth(player) == ShepherdPolicy.WARMTH_C, "Offhand");
        h.assertTrue(ShepherdPolicy.WARMTH_C == 5f, "Aurora's value, five degrees");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "kevinsblanket")
    public static void aBlanketElsewhereDoesNot(GameTestHelper h) {
        ServerPlayer player = player(h);
        BlockPos chest = new BlockPos(5, 1, 4);
        h.setBlock(chest, Blocks.CHEST);
        if (!(h.getBlockEntity(chest) instanceof ChestBlockEntity box)) {
            h.fail("No chest");
            return;
        }
        box.setItem(0, blanket());
        h.assertTrue(ThermalBlanketItem.warmth(player) == 0f, "A blanket in a chest beside them gives nothing");
        ItemEntity dropped = new ItemEntity(h.getLevel(), player.getX(), player.getY(), player.getZ(), blanket());
        h.getLevel().addFreshEntity(dropped);
        h.assertTrue(ThermalBlanketItem.warmth(player) == 0f, "Nor one dropped at their feet");
        dropped.discard();
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "kevinsblanket")
    public static void twoBlanketsAreOne(GameTestHelper h) {
        ServerPlayer player = player(h);
        player.getInventory().setItem(0, blanket());
        player.getInventory().setItem(1, blanket());
        h.assertTrue(ThermalBlanketItem.warmth(player) == ShepherdPolicy.WARMTH_C, "Two blankets still give five");
        h.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY, batch = "kevinsblanket")
    public static void itOutlastsTheHeart(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        PayoffState.setForTest(server, true);
        ReturnedHearthSavedData.get(server).setMaeveErasedForDebug(true, server.overworld().getGameTime());
        ServerPlayer player = player(h);
        player.getInventory().setItem(0, blanket());
        h.assertTrue(ThermalBlanketItem.warmth(player) == ShepherdPolicy.WARMTH_C, "Still five after the Heart and the payoff");
        h.succeed();
    }

    private static ItemStack blanket() {
        return new ItemStack(ModItems.ORSA_THERMAL_BLANKET.get());
    }

    /** A server player standing in the test structure, never added to the level: warmth reads only the inventory. */
    private static ServerPlayer player(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "blanket"), ClientInformation.createDefault());
        player.setPos(h.absoluteVec(new Vec3(4.5, 1, 4.5)));
        return player;
    }
}
