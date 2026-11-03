package com.frozendawn.defense;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The Master Architect does not flinch at a discharge. It turns its head to the wand, not the operator, for three
 * seconds, then goes back to what it was doing. The head is set after the Master's own tick, so its look control
 * cannot pull it away meanwhile.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class TerminusGaze {
    private record Gaze(UUID operator, InteractionHand hand, long until, Vec3 wand) { }
    private static final Map<ArchitectEntity, Gaze> GAZES = new WeakHashMap<>();

    static void start(ArchitectEntity master, ServerPlayer operator, InteractionHand hand) {
        GAZES.put(master, new Gaze(operator.getUUID(), hand, master.level().getGameTime() + Terminus.GAZE_TICKS,
                Terminus.wandPosition(operator, hand)));
    }

    public static boolean watching(ArchitectEntity master) {
        var gaze = GAZES.get(master);
        return gaze != null && master.level().getGameTime() < gaze.until();
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (GAZES.isEmpty() || !(event.getEntity() instanceof ArchitectEntity master) || master.level().isClientSide()) return;
        var gaze = GAZES.get(master);
        if (gaze == null) return;
        if (!watching(master) || master.isRemoved()) {
            GAZES.remove(master);
            return;
        }
        var wand = gaze.wand();
        if (master.level().getPlayerByUUID(gaze.operator()) instanceof ServerPlayer operator) {
            wand = Terminus.wandPosition(operator, gaze.hand());
            GAZES.put(master, new Gaze(gaze.operator(), gaze.hand(), gaze.until(), wand));
        }
        var to = wand.subtract(master.getEyePosition());
        master.setYHeadRot((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f);
        master.setXRot((float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    private TerminusGaze() { }
}
