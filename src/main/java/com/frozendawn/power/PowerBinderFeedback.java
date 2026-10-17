package com.frozendawn.power;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.network.PowerBinderPayload;
import com.frozendawn.network.PowerBinderPayload.Status;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** Target is derived on the server from the held tool and actual nearby aim, every five ticks. */
public final class PowerBinderFeedback {
    private record Cached(BlockPos source, BlockPos target, long topology, long geometry, long tick, PowerLinkRules.Result result) {}
    private static final Map<ServerPlayer, Cached> CACHE = new WeakHashMap<>();
    private PowerBinderFeedback() {}
    public static ItemStack held(ServerPlayer player) {
        if (player.getMainHandItem().is(ModItems.CONDUIT_BINDER)) return player.getMainHandItem();
        return player.getOffhandItem().is(ModItems.CONDUIT_BINDER) ? player.getOffhandItem() : ItemStack.EMPTY;
    }
    public static void send(ServerPlayer player) {
        if (held(player).isEmpty()) { CACHE.remove(player); return; }
        var hit = player.pick(Math.min(6, player.blockInteractionRange()), 0, false);
        var target = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos() : null;
        PacketDistributor.sendToPlayer(player, snapshot(player, target));
    }
    /** The caller supplies only a server-derived aim. Queries never change links or selection. */
    public static PowerBinderPayload snapshot(ServerPlayer player, BlockPos aimed) {
        var level = player.serverLevel(); var stack = held(player);
        if (stack.isEmpty()) return empty(level.dimension().location().toString());
        var anchor = stack.get(ModDataComponents.CONDUIT_ANCHOR);
        var source = anchor != null && anchor.dimension().equals(level.dimension()) ? anchor.pos() : null;
        var target = aimed != null && level.isLoaded(aimed) && PowerLinkRules.range(level.getBlockState(aimed)) > 0
                ? aimed.immutable() : null;
        int sourceRange = source != null && level.isLoaded(source) ? PowerLinkRules.range(level.getBlockState(source)) : 0;
        int targetRange = target != null ? PowerLinkRules.range(level.getBlockState(target)) : 0;
        int reach = source != null ? Math.min(sourceRange, targetRange) : targetRange;
        double distance = source != null && target != null ? Math.sqrt(source.distSqr(target)) : 0;
        Status status = source == null ? Status.CHOOSE : sourceRange == 0 ? Status.UNAVAILABLE
                : target == null ? Status.TARGET : source.equals(target) ? Status.CANCEL : null;
        if (status == null) {
            var data = PowerNetworkSavedData.get(level); long geometry = PowerNetworks.geometryRevision(level);
            var cached = CACHE.get(player); long tick = level.getGameTime();
            if (cached == null || !source.equals(cached.source()) || !target.equals(cached.target())
                    || cached.topology() != data.revision() || cached.geometry() != geometry
                    || tick < cached.tick() || tick - cached.tick() >= 20) {
                cached = new Cached(source, target, data.revision(), geometry, tick, PowerLinkRules.validate(level, source, target));
                CACHE.put(player, cached);
            }
            status = switch (cached.result()) {
                case OK -> data.links().contains(new PowerLink(source, target)) ? Status.ALREADY_LINKED
                        : data.links().size() >= PowerNetworkSavedData.MAX_LINKS
                        || data.degree(source) >= PowerNetworkSavedData.MAX_NODE_LINKS
                        || data.degree(target) >= PowerNetworkSavedData.MAX_NODE_LINKS ? Status.LIMIT : Status.READY;
                case DOCK_CONNECTION -> Status.DOCK_CONNECTION;
                case COOLANT -> Status.COOLANT;
                case RANGE -> Status.RANGE;
                case FEEDTHROUGH -> Status.FEEDTHROUGH;
                case UNKNOWN_SEAL -> Status.WAITING;
                default -> Status.UNAVAILABLE;
            };
        }
        var probe = target != null ? target : sourceRange > 0 ? source : null;
        var r = probe == null ? new PowerNetworks.Reading(0, 0, 0, 0, 0, 0) : PowerNetworks.reading(level, probe);
        return new PowerBinderPayload(level.dimension().location().toString(), source, target, status, reach,
                distance, r.nodes(), r.links(), r.supplyKw(), r.demandKw(), r.storedKj());
    }
    private static PowerBinderPayload empty(String dimension) {
        return new PowerBinderPayload(dimension, null, null, Status.CHOOSE, 0, 0, 0, 0, 0, 0, 0);
    }
    public static void reset() { CACHE.clear(); }
}
