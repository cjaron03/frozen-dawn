package com.frozendawn.cyclone;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ArchitectEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Loaded mobs within 64 blocks of a participant; no entity discovery, chunk loads or AI target changes. */
@EventBusSubscriber(modid=FrozenDawn.MOD_ID)
public final class CycloneMobWind {
    public static final TagKey<EntityType<?>> ANCHORED=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("frozendawn","storm_anchored"));
    public static final ResourceLocation DRAG=ResourceLocation.fromNamespaceAndPath("frozendawn","cyclone_wind_drag");
    private CycloneMobWind() {}
    @SubscribeEvent public static void tick(EntityTickEvent.Post event) {
        if(event.getEntity() instanceof Mob mob&&mob.level() instanceof ServerLevel level)apply(level,mob);
    }
    public static boolean anchored(Mob mob) {
        return mob.getType().is(ANCHORED)||mob instanceof ArchitectEntity architect&&architect.isMasterArchitectVisual();
    }
    public static void apply(ServerLevel level,Mob mob) {
        var attribute=mob.getAttribute(Attributes.MOVEMENT_SPEED);
        var active=level.dimension()==Level.OVERWORLD?CycloneSavedData.get(level.getServer()).active().orElse(null):null;
        boolean eligible=active!=null&&mob.isAlive()&&!mob.isPassenger()&&!mob.isVehicle()
                &&!mob.noPhysics&&!anchored(mob)&&level.players().stream().anyMatch(p->!p.isSpectator()&&p.distanceToSqr(mob)<=64*64);
        var storm=eligible?active:null;
        var sample=storm==null?CycloneSample.NONE:CycloneController.sampleAt(level,mob.getX(),mob.getZ());
        float cover=storm==null||sample.intensity()<=0?0:CycloneWind.exposure(level,mob,storm.headingRad());
        if(attribute!=null) {
            if(cover>0&&sample.intensity()>.5f)attribute.addOrUpdateTransientModifier(new AttributeModifier(DRAG,
                    -.15*sample.intensity()*cover,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else attribute.removeModifier(DRAG);
        }
        if(cover<=0)return;
        var before=mob.getDeltaMovement();var after=CycloneWind.adjust(before,sample,storm.severity(),storm.headingRad(),mob.isCrouching(),cover,
                CycloneWind.gust(level.getGameTime(),storm.seed()),FrozenDawnConfig.CYCLONE_PUSH_SCALE.get().floatValue());
        if(!before.equals(after)){mob.setDeltaMovement(after);mob.hasImpulse=true;}
    }
}
