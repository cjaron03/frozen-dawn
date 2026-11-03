package com.frozendawn.event;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.item.EmergencyEvaArmorItem;
import com.frozendawn.network.ContinuityRecoveryPayload;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ContinuityRecoveryHandler {
    public static final int RECOVERY_DROP_LIFETIME = 15 * 60 * 20;
    public static final String RECOVERY_DROP_TAG = "frozendawn:continuity_drop";
    private ContinuityRecoveryHandler() {}

    /** Called after vanilla successfully updates the spawn, including daytime bed use. */
    public static void onSpawnRecorded(ServerPlayer player, ResourceKey<Level> dimension, BlockPos position,
            boolean forced) {
        if (position == null || forced || !position.equals(player.getRespawnPosition())
                || !dimension.equals(player.getRespawnDimension())) return;
        var level = player.getServer().getLevel(dimension);
        if (level == null || !level.hasChunkAt(position)) return;
        var block = level.getBlockState(position);
        if (!(block.getBlock() instanceof BedBlock)
                && !(block.getBlock() instanceof RespawnAnchorBlock
                    && block.getValue(RespawnAnchorBlock.CHARGE) > 0)) return;
        if (player.getData(ModAttachments.CONTINUITY_RECOVERY)
                .recordShelter(GlobalPos.of(dimension, position), player.getUUID())) sync(player);
    }

    /** Existing saved bed coordinates are sufficient; never force-load or inspect a destination. */
    public static void initialize(ServerPlayer player) {
        var record = player.getData(ModAttachments.CONTINUITY_RECOVERY);
        if (record.shelter() == null && player.getRespawnPosition() != null && !player.isRespawnForced()) {
            record.recordShelter(GlobalPos.of(player.getRespawnDimension(), player.getRespawnPosition()), player.getUUID());
        }
        sync(player);
    }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) initialize(player);
    }

    @SubscribeEvent public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) initialize(player);
    }

    @SubscribeEvent public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        var apocalypse = ApocalypseState.get(player.getServer());
        if (!PhaseManager.isVacuumActive(apocalypse.getPhase(), apocalypse.getProgress())) return;
        boolean hasOrdinaryDrops = false;
        for (var drop : event.getDrops()) {
            if (drop.getItem().isEmpty() || drop.getItem().getItem() instanceof EmergencyEvaArmorItem) continue;
            hasOrdinaryDrops = true;
            // One extension on a newly created death drop. Saved age/lifespan continue normally.
            if (!isRecoveryDrop(drop)) {
                drop.lifespan = RECOVERY_DROP_LIFETIME;
                drop.getPersistentData().putBoolean(RECOVERY_DROP_TAG, true);
            }
        }
        player.getData(ModAttachments.CONTINUITY_RECOVERY)
                .recordLoss(GlobalPos.of(player.level().dimension(), player.blockPosition()), hasOrdinaryDrops);
    }

    public static boolean isRecoveryDrop(ItemEntity item) {
        return item.getPersistentData().getBoolean(RECOVERY_DROP_TAG);
    }

    public static void selectTarget(ServerPlayer player, java.util.UUID issue, boolean shelter) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || !EmergencyEvaHandler.isWearingIssuedPiece(player) || EmergencyEvaHandler.remainingTicks(player) == 0
                || !player.getData(ModAttachments.EMERGENCY_EVA).issue().equals(issue)) return;
        player.getData(ModAttachments.CONTINUITY_RECOVERY).selectShelter(shelter);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ContinuityRecoveryPayload(
                player.getData(ModAttachments.CONTINUITY_RECOVERY).clientRecord()));
    }
}
