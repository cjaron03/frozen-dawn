package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.cyclone.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Local prediction owns player motion. No matching server player impulse is applied. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID,value=Dist.CLIENT)
public final class CyclonePlayerWind {
    private CyclonePlayerWind() {}
    private static boolean active(LocalPlayer player) {
        var mc=Minecraft.getInstance();
        return !mc.isPaused()&&mc.player==player&&CycloneWind.eligiblePlayer(player);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void input(MovementInputUpdateEvent event) {
        if(!(event.getEntity() instanceof LocalPlayer player)||!active(player))return;
        var storm=CycloneClientState.storm().orElse(null);if(storm==null)return;
        var sample=CycloneClientState.sampleLocal(0);if(sample.intensity()<=0)return;
        float cover=CycloneWind.exposure(player.level(),player,storm.headingRad());if(cover<=0)return;
        var input=event.getInput();
        var original=CycloneWind.worldInput(input.leftImpulse,input.forwardImpulse,player.getYRot());
        if(CycloneWind.blocksSprint(original,sample,storm.severity(),storm.headingRad(),cover)) {
            player.setSprinting(false);
            // LocalPlayer checks sprint again after this hook; stay below its forward-input threshold.
            input.forwardImpulse=Math.min(input.forwardImpulse,.79f);
        }
    }
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof LocalPlayer player)||!active(player))return;
        var storm=CycloneClientState.storm().orElse(null);if(storm==null)return;
        var sample=CycloneClientState.sampleLocal(0);if(sample.intensity()<=0)return;
        float cover=CycloneWind.exposure(player.level(),player,storm.headingRad());
        var before=player.getDeltaMovement();
        var after=CycloneWind.adjust(before,sample,storm.severity(),storm.headingRad(),
                player.isCrouching()||Minecraft.getInstance().options.keyShift.isDown(),cover,
                CycloneWind.gust((long)CycloneClientState.time(0),storm.seed()),CycloneClientState.pushScale());
        if(!before.equals(after))player.setDeltaMovement(after);
    }
}
