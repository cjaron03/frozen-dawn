package com.frozendawn.dev.fdbot;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpList;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec2;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Survival actions through {@code /fdbot}. The command is registered by the dev gate, which is
 * on for the GameTest server.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FdBotGameTest {
    private static final UUID TEST_ID = UUID.fromString("6b1d1b2e-7c3a-4f2e-9a11-fdb07fdb0701");
    private static final BlockPos FEET = new BlockPos(4, 1, 2);
    private static final BlockPos FRONT = new BlockPos(4, 1, 3);

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void fdbotIsRegisteredForDevGameTests(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher()
                .getRoot().getChild("fdbot") != null, "/fdbot was not registered");
        helper.succeed();
    }

    /**
     * Open to LAN with cheats calls {@code Commands#sendCommands} for every player. A custom
     * argument type crashes there, in {@code ArgumentTypeInfos.byClass}, before any command runs.
     * The GameTest server's op level is 0, so the fake player is added to the op list at level 2,
     * which is the permission that includes {@code /fdbot}.
     */
    @GameTest(template = GameTestTemplates.EMPTY)
    public static void sendCommandsForAnOpDoesNotCrash(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        ServerOpList ops = helper.getLevel().getServer().getPlayerList().getOps();
        ops.add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        try {
            helper.assertTrue(player.createCommandSourceStack().hasPermission(2),
                    "op level 2 was not visible to the command source");
            helper.getLevel().getServer().getCommands().sendCommands(player);
        } finally {
            ops.remove(player.getGameProfile());
        }
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void gatherLogsByTag(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.OAK_LOG);

        int result = exec(player, "fdbot gather #minecraft:logs 2 4");

        helper.assertTrue(result == 2, "gather result was " + result);
        helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).isAir(), "first log remains");
        helper.assertTrue(helper.getBlockState(new BlockPos(3, 1, 2)).isAir(), "second log remains");
        helper.assertTrue(count(player, Items.OAK_LOG) == 2, "expected 2 oak logs, found " + count(player, Items.OAK_LOG));
        helper.assertTrue(FdBotAudit.isBotAssisted(player.getUUID()), "gather did not mark the player bot-assisted");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void gatherStoneRespectsTheHeldTool(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        // The shared floor is stone, so the target has to be a different block or gather
        // mines the floor under the player's feet first.
        BlockPos ore = new BlockPos(2, 1, 2);
        helper.setBlock(ore, Blocks.COBBLESTONE);

        int bare = exec(player, "fdbot gather minecraft:cobblestone 1 4");
        helper.assertTrue(bare == 0, "bare hands harvested cobblestone");
        helper.assertTrue(helper.getBlockState(ore).is(Blocks.COBBLESTONE), "cobblestone was removed without a pickaxe");

        player.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        player.getInventory().selected = 0;
        int mined = exec(player, "fdbot gather minecraft:cobblestone 1 4");

        helper.assertTrue(mined == 1, "pickaxe gather result was " + mined);
        helper.assertTrue(helper.getBlockState(ore).isAir(), "cobblestone was not mined");
        helper.assertTrue(count(player, Items.COBBLESTONE) == 1, "cobblestone was not collected");
        helper.assertTrue(player.getInventory().getItem(0).getDamageValue() > 0, "pickaxe took no damage");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void craftPlanksFromInventory(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        player.getInventory().add(new ItemStack(Items.OAK_LOG));

        int crafted = exec(player, "fdbot craft minecraft:oak_planks 4");

        helper.assertTrue(crafted == 4, "craft result was " + crafted);
        helper.assertTrue(count(player, Items.OAK_PLANKS) == 4, "expected 4 planks");
        helper.assertTrue(count(player, Items.OAK_LOG) == 0, "log was not consumed");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void craftChestRequiresANearbyTable(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 8));
        player.getInventory().add(new ItemStack(Items.CRAFTING_TABLE));

        FdBotActions.Outcome refused = FdBotActions.craft(player, Items.CHEST, 1);
        helper.assertFalse(refused.success(), "chest crafted without a placed table");
        helper.assertTrue(refused.message().contains("crafting table"), refused.message());
        helper.assertTrue(count(player, Items.OAK_PLANKS) == 8, "planks were consumed by the failed craft");

        int placed = exec(player, "fdbot place minecraft:crafting_table front");
        helper.assertTrue(placed > 0, "could not place the crafting table");
        helper.assertTrue(helper.getBlockState(FRONT).is(Blocks.CRAFTING_TABLE), "table is not in front");

        int crafted = exec(player, "fdbot craft minecraft:chest 1");
        helper.assertTrue(crafted == 1, "chest craft result was " + crafted);
        helper.assertTrue(count(player, Items.CHEST) == 1, "chest was not crafted");
        helper.assertTrue(count(player, Items.OAK_PLANKS) == 0, "planks were not consumed");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void placeAndUseDoor(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        player.getInventory().add(new ItemStack(Items.OAK_DOOR));

        int placed = exec(player, "fdbot place minecraft:oak_door front");
        helper.assertTrue(placed > 0, "could not place the door");
        helper.assertTrue(helper.getBlockState(FRONT).is(Blocks.OAK_DOOR), "door is not in front");
        helper.assertFalse(helper.getBlockState(FRONT).getValue(BlockStateProperties.OPEN), "door started open");

        int used = exec(player, "fdbot use front");
        helper.assertTrue(used > 0, "could not use the door");
        helper.assertTrue(helper.getBlockState(FRONT).getValue(BlockStateProperties.OPEN), "door did not open");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void gotoRefusesASolidTarget(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        BlockPos start = player.blockPosition();
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.DIRT);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.GRASS_BLOCK);
        BlockPos solid = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos safe = helper.absolutePos(new BlockPos(1, 1, 2));

        int result = exec(player, "fdbot goto " + solid.getX() + " " + solid.getY() + " " + solid.getZ());

        helper.assertTrue(result == 0, "solid goto returned " + result);
        helper.assertTrue(start.equals(player.blockPosition()),
                "solid goto moved the player to " + player.blockPosition().toShortString());
        FdBotActions.Outcome outcome = FdBotActions.goTo(player, solid);
        helper.assertFalse(outcome.success(), outcome.message());
        helper.assertTrue(outcome.message().contains("feet=minecraft:dirt"), outcome.message());
        helper.assertTrue(outcome.message().contains("head=minecraft:grass_block"), outcome.message());
        helper.assertTrue(outcome.message().contains(safe.toShortString()), outcome.message());
        helper.assertTrue(start.equals(player.blockPosition()), "the suggestion teleported the player");
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void gotoAcceptsAnOpenTarget(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        BlockPos dest = helper.absolutePos(new BlockPos(6, 1, 2));

        int result = exec(player, "fdbot goto " + dest.getX() + " " + dest.getY() + " " + dest.getZ());

        helper.assertTrue(result > 0, "open goto returned " + result);
        helper.assertTrue(dest.equals(player.blockPosition()),
                "expected " + dest.toShortString() + " but was " + player.blockPosition().toShortString());
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void gotoFaceAndStatus(GameTestHelper helper) throws CommandSyntaxException {
        GameTestTemplates.placeFloor(helper);
        ServerPlayer player = player(helper);
        BlockPos dest = helper.absolutePos(new BlockPos(6, 1, 2));
        int moved = exec(player, "fdbot goto " + dest.getX() + " " + dest.getY() + " " + dest.getZ());
        helper.assertTrue(moved > 0, "goto failed");
        helper.assertTrue(dest.equals(player.blockPosition()),
                "expected " + dest.toShortString() + " but was " + player.blockPosition().toShortString());

        player(helper);
        helper.setBlock(new BlockPos(7, 1, 2), Blocks.EMERALD_BLOCK);
        int faced = exec(player, "fdbot face nearest minecraft:emerald_block");
        helper.assertTrue(faced > 0, "face failed");
        helper.assertTrue(player.getDirection() == net.minecraft.core.Direction.EAST,
                "faced " + player.getDirection());

        FdBotActions.Outcome status = FdBotActions.status(player);
        helper.assertTrue(status.success() && status.message().contains("phase=")
                && status.message().contains("botAssisted=true"), status.message());
        int reported = exec(player, "fdbot status");
        helper.assertTrue(reported > 0, "status command failed");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(TEST_ID, "fdbot"));
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        BlockPos feet = helper.absolutePos(FEET);
        player.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
        player.setYHeadRot(0.0F);
        player.setYBodyRot(0.0F);
        return player;
    }

    private static int exec(ServerPlayer player, String command) throws CommandSyntaxException {
        var source = new net.minecraft.commands.CommandSourceStack(
                player,
                player.position(),
                new Vec2(player.getXRot(), player.getYRot()),
                player.serverLevel(),
                2,
                player.getGameProfile().getName(),
                player.getName(),
                player.serverLevel().getServer(),
                player);
        return player.serverLevel().getServer().getCommands().getDispatcher().execute(command, source);
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
