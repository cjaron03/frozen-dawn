package com.frozendawn.shepherd;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdFace.Mood;
import com.frozendawn.world.ReadyChunks;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Unit nine, at the Blast Pit. Her saying where it is puts the husk there, out of sight, once the pit stands: it means
 * nothing in a world where she is not up, so it is story and not world generation.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ShepherdNine {
    /** She has given the ground ref. */
    static final String OPEN = "nine_open";
    /** She has stood at it, so it has something to give. */
    static final String SEEN = "nine_seen";
    /** A reply that lands on this means yes: take the lamp. */
    static final String HAND_OVER = "nine_handover";
    /** The lamp and the sheet have come out of it. */
    public static final String SHEET_TAKEN = "nine_sheet_taken";
    /** It rides in her handle. */
    static final String LAMP_WORN = "nine_lamp_worn";
    static final String REGISTER = "tithonus_register";
    private static final String THANKS = "shepherd.t2.thanks", CELL_LINE = "shepherd.t2.cell";
    private static final int CHECK_TICKS = 20;
    /** Nobody sees it arrive. */
    private static final int OUT_OF_SIGHT_BLOCKS = 32;
    /** One of these from the pit's center: a ring of open floor past the pad, the tower and the trench, three blocks down. */
    private static final int[][] RING = {{9, 7}, {7, 9}, {9, -7}, {7, -9}, {-7, 9}, {-9, 7}, {-7, -9}, {-9, -7}};
    /** The center is ground level and that floor is three below it, so this stands on it. */
    private static final int FLOOR_DROP = 2;

    private ShepherdNine() {}

    /** The first free place on the ring, or null. */
    static BlockPos spot(BlockPos center, Predicate<BlockPos> free) {
        for (int[] offset : RING) {
            var spot = center.offset(offset[0], -FLOOR_DROP, offset[1]);
            if (free.test(spot)) return spot;
        }
        return null;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return ReadyChunks.ready(level, pos) && level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.above()).canBeReplaced() && level.getBlockState(pos.below()).isSolid();
    }

    private static boolean outOfSight(ServerLevel level, BlockPos pos) {
        return level.players().stream().noneMatch(player -> player.blockPosition().closerThan(pos, OUT_OF_SIGHT_BLOCKS));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS != 0) return;
        var data = ShepherdSavedData.get(server);
        if (!data.flag(OPEN) || data.huskPos() != null) return;
        var pit = OrsaStructureState.get(server);
        if (!pit.isBlastPitPlaced() || pit.getBlastPitPos() == null) return;
        var level = server.overworld();
        var center = pit.getBlastPitPos();
        if (!ReadyChunks.ready(level, center)) return;
        var spot = spot(center, pos -> free(level, pos));
        if (spot == null || !outOfSight(level, spot)) return;
        var facing = Direction.getNearest(center.getX() - spot.getX(), 0, center.getZ() - spot.getZ());
        level.setBlockAndUpdate(spot, ModBlocks.CS1_HUSK.get().defaultBlockState().setValue(ShepherdHuskBlock.FACING, facing));
        data.huskPos(spot);
    }

    /** The folded sheet from behind the lamp housing. It only becomes the Frame Register once it is read. */
    public static ItemStack register() {
        var sheet = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        sheet.set(DataComponents.CUSTOM_NAME, Component.literal("Folded Sheet"));
        var tag = new CompoundTag();
        tag.putString("doc_type", REGISTER);
        sheet.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return sheet;
    }

    /** She has stood at it. It is the world's doing, not the speaker's, and the rest of the day is hers. */
    static void arrived(ServerPlayer player) {
        var server = player.getServer();
        ShepherdReactions.saw(player, Deed.NINE_FOUND);
        ShepherdReactions.quietFor(ShepherdSavedData.get(server), server.overworld().getGameTime());
    }

    /** Lamp and sheet, to this player, once. False if there is nothing left in it. */
    static boolean handOver(ServerPlayer player) {
        var server = player.getServer();
        var data = ShepherdSavedData.get(server);
        var level = server.overworld();
        var husk = data.huskPos();
        if (husk == null || data.flag(SHEET_TAKEN)) return false;
        data.flag(SHEET_TAKEN, true);
        var state = level.getBlockState(husk);
        if (state.is(ModBlocks.CS1_HUSK.get())) level.setBlockAndUpdate(husk, state.setValue(ShepherdHuskBlock.LAMP, false));
        give(player, new ItemStack(ModItems.NINE_LAMP.get()));
        give(player, register());
        return true;
    }

    /** Right-clicked with the lamp in hand. She takes it, for good, and it does not light. An unfinished conversation comes first: it is what lands the lamp in that hand. True if she did. */
    static boolean takeLamp(ShepherdEntity she, ServerPlayer player, ItemStack lamp) {
        var data = ShepherdSavedData.get(player.getServer());
        if (!lamp.is(ModItems.NINE_LAMP) || data.flag(LAMP_WORN) || she.offline() || ShepherdTalk.live() || data.resumeNode() != null) return false;
        data.flag(LAMP_WORN, true);
        lamp.shrink(1);
        return true;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static boolean ours(ShepherdSavedData data, BlockPos pos) {
        return pos.equals(data.huskPos()) && data.flag(SEEN);
    }

    /** A hand on it. Until she has stood here it is only a dead unit; afterwards what she asked for can still come out. */
    static void touched(ServerPlayer player, BlockPos pos) {
        var data = ShepherdSavedData.get(player.getServer());
        if (ours(data, pos) && !ShepherdTalk.live() && handOver(player)) ShepherdReactions.thanks(player.serverLevel(), THANKS, Mood.SAD);
    }

    /** The dock's dry click, and it stays in the hand. Only once she has seen it does she have anything to say about that. */
    static void offeredACell(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        level.playSound(null, pos, ModSounds.CS1_DOCK_REFUSE.get(), SoundSource.BLOCKS, 1, 1);
        if (ours(ShepherdSavedData.get(player.getServer()), pos)) ShepherdReactions.answer(level, CELL_LINE, Mood.SAD);
    }
}
