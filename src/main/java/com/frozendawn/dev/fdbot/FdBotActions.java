package com.frozendawn.dev.fdbot;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.SuitIntegrity;
import com.frozendawn.event.SuitIntegrityHandler;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.item.O2TankItem;
import com.frozendawn.world.TemperatureManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Survival actions for {@code /fdbot}. Mining, placement, and use go through
 * {@link ServerPlayer#gameMode} so drops, durability, containers, and block entities stay on
 * the vanilla path. Breaks are instant: durability is applied once per block, the same way a
 * finished mine is, without waiting out the break time.
 */
public final class FdBotActions {
    public static final int DEFAULT_GATHER_RADIUS = 24;
    public static final int MAX_RADIUS = 48;
    public static final int MAX_COUNT = 64;
    public static final int CRAFTING_TABLE_RADIUS = 6;
    public static final int NEAREST_RADIUS = 48;
    private static final int GOTO_UP_SEARCH = 4;
    private static final int GOTO_NEAR_RADIUS = 2;

    private FdBotActions() {
    }

    public record Outcome(boolean success, String message, int count) {
        public static Outcome ok(String message) {
            return new Outcome(true, message, 1);
        }

        public static Outcome ok(String message, int count) {
            return new Outcome(true, message, count);
        }

        public static Outcome fail(String message) {
            return new Outcome(false, message, 0);
        }
    }

    static Outcome help() {
        return Outcome.ok("dev-only: gather <block|#tag> <count> [radius] | craft <item> [count]"
                + " | place <item> [here|front|x y z] | use <here|front|x y z>"
                + " | goto <x y z|nearest <block>> | face <x y z|nearest <block>> | status."
                + " Smelt is not implemented. See docs/fdbot.md.");
    }

    public static Outcome gather(ServerPlayer player, FdBotIds.BlockMatch match, int count, int radius) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (mode != GameType.SURVIVAL) {
            return Outcome.fail("gather requires survival mode (player is " + mode.getName() + ")");
        }
        if (match.block() != null && match.block().defaultBlockState().isAir()) {
            return Outcome.fail("refusing to gather air");
        }
        ServerLevel level = player.serverLevel();
        List<BlockPos> found = findBlocks(level, player.blockPosition(), match, radius);
        if (found.isEmpty()) {
            return Outcome.fail("no " + match.label() + " within " + radius + " loaded blocks");
        }
        ItemStack tool = player.getMainHandItem().copy();
        int broken = 0;
        int picked = 0;
        int leftOnGround = 0;
        int skippedTool = 0;
        int skippedSolid = 0;
        int rejected = 0;
        for (BlockPos pos : found) {
            if (broken >= count) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!match.test(state)) {
                continue;
            }
            if (state.getDestroySpeed(level, pos) < 0.0F) {
                skippedSolid++;
                continue;
            }
            if (!state.canHarvestBlock(level, pos, player)) {
                skippedTool++;
                continue;
            }
            AABB box = new AABB(pos).inflate(1.0);
            Set<UUID> before = new HashSet<>();
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
                before.add(entity.getUUID());
            }
            if (!player.gameMode.destroyBlock(pos) || level.getBlockState(pos).is(state.getBlock())) {
                rejected++;
                continue;
            }
            broken++;
            Pickup pickup = collectNewDrops(player, level, box, before);
            picked += pickup.picked;
            leftOnGround += pickup.left;
        }
        String toolName = tool.isEmpty() ? "empty hand" : FdBotIds.itemId(tool);
        if (broken == 0) {
            return Outcome.fail("gathered 0 " + match.label() + " with " + toolName
                    + describeSkips(skippedTool, skippedSolid, rejected));
        }
        String message = "gathered " + broken + "/" + count + " " + match.label()
                + " within " + radius + " with " + toolName
                + "; picked up " + picked + " items";
        if (leftOnGround > 0) {
            message += "; left " + leftOnGround + " items on the ground";
        }
        message += describeSkips(skippedTool, skippedSolid, rejected);
        return Outcome.ok(message, broken);
    }

    public static Outcome craft(ServerPlayer player, Item item, int count) {
        return FdBotCrafting.craft(player, item, count);
    }

    /**
     * Places {@code item} into {@code target} by right-clicking the supporting face while
     * sneaking, which is how a player places against a block that would otherwise open.
     * {@code front} and {@code here} are feet-level cells from {@link #feetOrFront}, not the
     * crosshair. The target has to be inside vanilla block reach.
     */
    public static Outcome place(ServerPlayer player, Item item, BlockPos target) {
        if (!(item instanceof BlockItem)) {
            return Outcome.fail(BuiltInRegistries.ITEM.getKey(item) + " is not a placeable block");
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(target)) {
            return Outcome.fail("chunk not loaded at " + target.toShortString());
        }
        BlockState occupying = level.getBlockState(target);
        if (!occupying.canBeReplaced()) {
            return Outcome.fail(target.toShortString() + " is occupied by " + FdBotIds.blockId(occupying));
        }
        if (!inReach(player, target)) {
            return Outcome.fail("out of reach of " + target.toShortString());
        }
        if (!moveToSelectedHotbar(player, item)) {
            return Outcome.fail("no " + BuiltInRegistries.ITEM.getKey(item) + " in the inventory");
        }
        BlockPos support = target.below();
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(support).relative(net.minecraft.core.Direction.UP, 0.5),
                net.minecraft.core.Direction.UP,
                support,
                false);
        boolean wasSneaking = player.isShiftKeyDown();
        player.setShiftKeyDown(true);
        InteractionResult result;
        try {
            result = player.gameMode.useItemOn(
                    player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        } finally {
            player.setShiftKeyDown(wasSneaking);
        }
        BlockState placed = level.getBlockState(target);
        if (!placed.is(item instanceof BlockItem blockItem ? blockItem.getBlock() : null)
                && (result == null || !result.consumesAction())) {
            return Outcome.fail("could not place " + BuiltInRegistries.ITEM.getKey(item)
                    + " at " + target.toShortString());
        }
        if (!placed.is(((BlockItem) item).getBlock())) {
            return Outcome.fail("could not place " + BuiltInRegistries.ITEM.getKey(item)
                    + " at " + target.toShortString() + " (cell is " + FdBotIds.blockId(placed) + ")");
        }
        return Outcome.ok("placed " + FdBotIds.blockId(placed) + " at " + target.toShortString());
    }

    /**
     * Right-clicks {@code pos} with the held item, through {@code ServerPlayerGameMode#useItemOn}.
     * An empty hand toggles doors and opens containers. A held item may be inserted or placed
     * instead, because that is what the same click would do.
     */
    public static Outcome use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return Outcome.fail("chunk not loaded at " + pos.toShortString());
        }
        BlockState before = level.getBlockState(pos);
        if (before.isAir()) {
            return Outcome.fail("nothing to use at " + pos.toShortString() + " (air)");
        }
        if (!inReach(player, pos)) {
            return Outcome.fail("out of reach of " + pos.toShortString());
        }
        ItemStack held = player.getMainHandItem().copy();
        var menuBefore = player.containerMenu;
        net.minecraft.core.Direction face = player.getDirection().getOpposite();
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        InteractionResult result = player.gameMode.useItemOn(
                player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        BlockState after = level.getBlockState(pos);
        boolean opened = player.containerMenu != menuBefore;
        boolean changed = !after.equals(before);
        if ((result == null || !result.consumesAction()) && !changed && !opened) {
            return Outcome.fail("no interaction with " + FdBotIds.blockId(before)
                    + " at " + pos.toShortString());
        }
        StringBuilder message = new StringBuilder("used ")
                .append(FdBotIds.blockId(after))
                .append(" at ")
                .append(pos.toShortString());
        if (after.hasProperty(BlockStateProperties.OPEN)) {
            message.append(" open=").append(after.getValue(BlockStateProperties.OPEN));
        }
        if (opened) {
            message.append(" menu=").append(player.containerMenu.getClass().getSimpleName());
        }
        if (!held.isEmpty()) {
            message.append(" holding=").append(FdBotIds.itemId(held));
        }
        return Outcome.ok(message.toString());
    }

    /**
     * Teleports. This does not pathfind. Feet land at the block coordinates, centered on x/z, only
     * when that cell and the head cell are non-solid and a solid or supporting block is below.
     * An unsafe target is refused and the nearest open spot is named instead.
     */
    public static Outcome goTo(ServerPlayer player, BlockPos feet) {
        ServerLevel level = player.serverLevel();
        if (!canStand(level, feet)) {
            return Outcome.fail(unsafeGoto(level, feet));
        }
        double x = feet.getX() + 0.5;
        double y = feet.getY();
        double z = feet.getZ() + 0.5;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.teleportTo(level, x, y, z, yaw, pitch);
        // A real player's connection applies the position before telling the client.
        // FakePlayer's handler drops that packet, which would leave tests unmoved.
        if (player.isFakePlayer()) {
            player.absMoveTo(x, y, z, yaw, pitch);
        }
        BlockState atFeet = level.getBlockState(feet);
        BlockState atHead = level.getBlockState(feet.above());
        String message = "teleported to " + feet.toShortString();
        if (!atFeet.isAir() || !atHead.isAir()) {
            message += " (feet=" + FdBotIds.blockId(atFeet) + " head=" + FdBotIds.blockId(atHead) + ")";
        }
        return Outcome.ok(message);
    }

    public static Outcome goToNearest(ServerPlayer player, FdBotIds.BlockMatch match) {
        BlockPos found = nearest(player, match);
        if (found == null) {
            return Outcome.fail("no " + match.label() + " within " + NEAREST_RADIUS + " loaded blocks");
        }
        BlockPos stand = standNear(player.serverLevel(), found, player.blockPosition());
        if (stand == null) {
            return Outcome.fail("found " + match.label() + " at " + found.toShortString()
                    + " but no safe place to stand beside it");
        }
        Outcome moved = goTo(player, stand);
        if (!moved.success()) {
            return moved;
        }
        return Outcome.ok(moved.message() + " beside " + match.label() + " at " + found.toShortString());
    }

    public static Outcome face(ServerPlayer player, BlockPos pos) {
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(pos));
        player.setYBodyRot(player.getYRot());
        return Outcome.ok("facing " + pos.toShortString());
    }

    public static Outcome faceNearest(ServerPlayer player, FdBotIds.BlockMatch match) {
        BlockPos found = nearest(player, match);
        if (found == null) {
            return Outcome.fail("no " + match.label() + " within " + NEAREST_RADIUS + " loaded blocks");
        }
        Outcome looked = face(player, found);
        return Outcome.ok(looked.message() + " (" + match.label() + ")");
    }

    public static Outcome status(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ApocalypseState apocalypse = ApocalypseState.get(player.getServer());
        BlockPos pos = player.blockPosition();
        float temp = TemperatureManager.getTemperatureAt(
                level, pos, apocalypse.getCurrentDay(), apocalypse.getTotalDays());
        SuitIntegrity suit = SuitIntegrityHandler.getState(player);
        BlockPos front = pos.relative(player.getDirection());
        String message = String.format(Locale.ROOT,
                "status pos=%s dim=%s phase=%d day=%d temp=%.1f vacuum=%s sealedSuit=%s"
                        + " suitO2=%d tankO2=%d slot=%d front=%s:%s hotbar=%s botAssisted=%s",
                pos.toShortString(),
                level.dimension().location(),
                apocalypse.getPhase(),
                apocalypse.getCurrentDay(),
                temp,
                SuitIntegrityHandler.isVacuumExposure(player),
                SuitIntegrityHandler.isWearingSealedSuit(player),
                suit.o2Ticks(),
                tankO2(player),
                player.getInventory().selected,
                front.toShortString(),
                FdBotIds.blockId(level.getBlockState(front)),
                hotbar(player),
                FdBotAudit.isBotAssisted(player.getUUID()));
        return Outcome.ok(message);
    }

    static BlockPos feetOrFront(ServerPlayer player, String where) {
        if ("here".equals(where)) {
            return player.blockPosition();
        }
        return player.blockPosition().relative(player.getDirection());
    }

    private static List<BlockPos> findBlocks(ServerLevel level, BlockPos origin, FdBotIds.BlockMatch match, int radius) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos min = origin.offset(-radius, -radius, -radius);
        BlockPos max = origin.offset(radius, radius, radius);
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            if (cursor.distSqr(origin) > (double) radius * radius) {
                continue;
            }
            if (!level.hasChunkAt(cursor)) {
                continue;
            }
            if (!match.test(level.getBlockState(cursor))) {
                continue;
            }
            found.add(cursor.immutable());
        }
        found.sort(Comparator
                .comparingDouble((BlockPos pos) -> pos.distSqr(origin))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return found;
    }

    private static BlockPos nearest(ServerPlayer player, FdBotIds.BlockMatch match) {
        List<BlockPos> found = findBlocks(player.serverLevel(), player.blockPosition(), match, NEAREST_RADIUS);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static BlockPos standNear(ServerLevel level, BlockPos target, BlockPos from) {
        List<BlockPos> options = new ArrayList<>();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos feet = target.relative(direction);
            if (canStand(level, feet)) {
                options.add(feet);
            }
        }
        if (canStand(level, target.above())) {
            options.add(target.above());
        }
        options.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
        return options.isEmpty() ? null : options.getFirst();
    }

    private static String unsafeGoto(ServerLevel level, BlockPos feet) {
        String where = "refused teleport to " + feet.toShortString();
        if (!columnLoaded(level, feet)) {
            return where + ": destination is not loaded";
        }
        String occupied = where + ": feet=" + FdBotIds.blockId(level.getBlockState(feet))
                + " head=" + FdBotIds.blockId(level.getBlockState(feet.above()));
        BlockPos safe = nearestSafe(level, feet);
        if (safe == null) {
            return occupied + "; no safe spot nearby";
        }
        return occupied + "; nearest safe spot is " + safe.toShortString();
    }

    /** Feet and head are non-solid, and the block below is solid or has a collision to stand on. */
    private static boolean canStand(ServerLevel level, BlockPos feet) {
        if (!columnLoaded(level, feet)) {
            return false;
        }
        return !level.getBlockState(feet).isSolid()
                && !level.getBlockState(feet.above()).isSolid()
                && supportsStanding(level, feet.below());
    }

    private static boolean columnLoaded(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        BlockPos below = feet.below();
        return !level.isOutsideBuildHeight(feet)
                && !level.isOutsideBuildHeight(head)
                && !level.isOutsideBuildHeight(below)
                && level.hasChunkAt(feet)
                && level.hasChunkAt(head)
                && level.hasChunkAt(below);
    }

    private static boolean supportsStanding(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isSolid() || !state.getCollisionShape(level, pos).isEmpty();
    }

    private static BlockPos nearestSafe(ServerLevel level, BlockPos feet) {
        List<BlockPos> options = new ArrayList<>();
        for (int dy = -1; dy <= GOTO_UP_SEARCH; dy++) {
            if (dy != 0) {
                consider(level, feet.above(dy), options);
            }
        }
        for (int dy = -1; dy <= GOTO_UP_SEARCH; dy++) {
            for (int dx = -GOTO_NEAR_RADIUS; dx <= GOTO_NEAR_RADIUS; dx++) {
                for (int dz = -GOTO_NEAR_RADIUS; dz <= GOTO_NEAR_RADIUS; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    consider(level, feet.offset(dx, dy, dz), options);
                }
            }
        }
        options.sort(Comparator
                .comparingDouble((BlockPos pos) -> pos.distSqr(feet))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return options.isEmpty() ? null : options.getFirst();
    }

    private static void consider(ServerLevel level, BlockPos candidate, List<BlockPos> options) {
        if (canStand(level, candidate)) {
            options.add(candidate);
        }
    }

    private static boolean inReach(ServerPlayer player, BlockPos pos) {
        double reach = player.blockInteractionRange();
        double limit = reach + 1.0;
        return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= limit * limit;
    }

    private static boolean moveToSelectedHotbar(ServerPlayer player, Item item) {
        var inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            if (inventory.getItem(i).is(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return false;
        }
        if (slot < 9) {
            inventory.selected = slot;
            return true;
        }
        int selected = inventory.selected;
        ItemStack held = inventory.getItem(selected);
        inventory.setItem(selected, inventory.getItem(slot));
        inventory.setItem(slot, held);
        return true;
    }

    private static Pickup collectNewDrops(ServerPlayer player, ServerLevel level, AABB box, Set<UUID> before) {
        int picked = 0;
        int left = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (before.contains(entity.getUUID()) || entity.isRemoved()) {
                continue;
            }
            int count = entity.getItem().getCount();
            try {
                entity.setPickUpDelay(0);
                entity.playerTouch(player);
            } catch (RuntimeException ex) {
                com.frozendawn.FrozenDawn.LOGGER.warn("[FDBOT] playerTouch failed: {}", ex.toString());
            }
            int remain = entity.isRemoved() || entity.getItem().isEmpty() ? 0 : entity.getItem().getCount();
            if (remain == count) {
                ItemStack stack = entity.getItem().copy();
                if (player.getInventory().add(stack)) {
                    if (stack.isEmpty()) {
                        entity.discard();
                        remain = 0;
                    } else {
                        entity.setItem(stack);
                        remain = stack.getCount();
                    }
                }
            }
            picked += count - remain;
            left += remain;
        }
        return new Pickup(picked, left);
    }

    private static String describeSkips(int tool, int unbreakable, int rejected) {
        StringBuilder text = new StringBuilder();
        if (tool > 0) {
            text.append("; skipped ").append(tool).append(" that need a better tool");
        }
        if (unbreakable > 0) {
            text.append("; skipped ").append(unbreakable).append(" unbreakable");
        }
        if (rejected > 0) {
            text.append("; mining rejected ").append(rejected);
        }
        return text.toString();
    }

    private static int tankO2(ServerPlayer player) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof O2TankItem) {
                total += stack.getOrDefault(ModDataComponents.O2_LEVEL.get(), 0);
            }
        }
        return total;
    }

    private static String hotbar(ServerPlayer player) {
        StringBuilder text = new StringBuilder();
        var inventory = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (i > 0) {
                text.append(',');
            }
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                text.append('-');
            } else {
                text.append(FdBotIds.itemId(stack)).append('x').append(stack.getCount());
            }
        }
        return text.toString();
    }

    private record Pickup(int picked, int left) {
    }
}
