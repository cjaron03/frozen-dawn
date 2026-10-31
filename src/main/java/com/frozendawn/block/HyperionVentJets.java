package com.frozendawn.block;

import com.frozendawn.FrozenDawn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** A quench: the thump and puck drop at the Controller, a roar and jets of cloud out of each Quench Vent's face for
 * JET_TICKS, then the shell settling. Kept per level rather than on the Controller, so it all still runs when the
 * Controller itself was the part that broke. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class HyperionVentJets {
    public static final int JET_TICKS = 160;
    private record Jet(BlockPos controller, List<BlockPos> vents, long end) {}
    private static final Map<ServerLevel, List<Jet>> JETS = new WeakHashMap<>();
    private HyperionVentJets() {}

    static void start(ServerLevel l, BlockPos controller, List<BlockPos> vents) {
        l.playSound(null, controller, ModSounds.HYPERION_QUENCH.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        l.playSound(null, controller, ModSounds.HYPERION_PUCK_DROP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        for (var v : vents) l.playSound(null, v, ModSounds.HYPERION_VENT.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        JETS.computeIfAbsent(l, k -> new ArrayList<>()).add(new Jet(controller.immutable(), List.copyOf(vents), l.getGameTime() + JET_TICKS));
    }
    /** True while a jet from this vent is still running. */
    public static boolean venting(ServerLevel l, BlockPos vent) {
        long now = l.getGameTime();
        return JETS.getOrDefault(l, List.of()).stream().anyMatch(j -> now < j.end() && j.vents().contains(vent));
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel l)) return;
        var jets = JETS.get(l);
        if (jets == null) return;
        long now = l.getGameTime();
        jets.removeIf(j -> {
            if (now < j.end()) return false;
            if (l.isLoaded(j.controller())) l.playSound(null, j.controller(), ModSounds.HYPERION_AFTERMATH.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
            return true;
        });
        for (var j : jets) {
            // Full force at first, easing off over the last two seconds.
            double force = Math.min(1, (j.end() - now) / 40.0);
            for (var p : j.vents()) {
                if (!l.isLoaded(p)) continue;
                var s = l.getBlockState(p);
                if (!(s.getBlock() instanceof QuenchVentBlock)) continue;
                var d = s.getValue(HyperionFacingPartBlock.FACING);
                double x = p.getX() + 0.5 + d.getStepX() * 0.6, y = p.getY() + 0.5 + d.getStepY() * 0.6, z = p.getZ() + 0.5 + d.getStepZ() * 0.6;
                for (int i = 0; i < 4; i++) {
                    double vx = d.getStepX() + (l.random.nextDouble() - 0.5) * 0.3, vy = d.getStepY() + 0.15 + (l.random.nextDouble() - 0.5) * 0.3,
                            vz = d.getStepZ() + (l.random.nextDouble() - 0.5) * 0.3;
                    l.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, vx, vy, vz, 0.55 * force + 0.1);
                }
            }
        }
        if (jets.isEmpty()) JETS.remove(l);
    }
}
