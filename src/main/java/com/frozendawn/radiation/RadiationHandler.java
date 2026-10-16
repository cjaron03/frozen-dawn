package com.frozendawn.radiation;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.FalloutBlock;
import com.frozendawn.event.SuitIntegrityHandler;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModEffects;
import com.frozendawn.item.RadAidItem;
import com.frozendawn.network.RadiationPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Once a second per player: dose rate, accumulated dose, contamination, symptoms and the Geiger feed. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class RadiationHandler {
    public static final ResourceLocation MAX_HEALTH_ID = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "radiation_sickness");
    public static final double CONTAMINATION_STEP = 0.2, CONTAMINATION_MAX = 2;
    /** Fallout layers within three blocks at which dust in the air contaminates an unsealed player. */
    public static final int AIRBORNE_LAYERS = 10;
    private RadiationHandler() {}
    private static int airborneFallout(ServerPlayer p) {
        int[] n = {0};
        RadiationSourceData.get(p.serverLevel()).near(p.blockPosition(), 3, (pos, kind) -> {
            if (kind == RadiationSourceData.Kind.FALLOUT || kind == RadiationSourceData.Kind.FALLOUT_SEVERE) n[0]++; });
        return n[0];
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && (p.tickCount + p.getId()) % 20 == 0 && !p.isSpectator()) second(p);
    }

    /** One second of exposure. Public for GameTests. */
    public static void second(ServerPlayer p) {
        var state = p.getData(ModAttachments.RADIATION.get());
        boolean onFallout = FalloutBlock.at(p.level(), p.blockPosition()) || FalloutBlock.at(p.level(), p.blockPosition().below())
                || airborneFallout(p) >= AIRBORNE_LAYERS;
        if (p.isInWater() || com.frozendawn.block.DeconShowerBlock.washing(p)) { state.contamination = 0; state.contaminationWarned = false; }
        else if (onFallout && !SuitIntegrityHandler.isWearingSealedSuit(p)) {
            state.contamination = Math.min(CONTAMINATION_MAX, state.contamination + CONTAMINATION_STEP);
            if (!state.contaminationWarned) { state.contaminationWarned = true;
                p.displayClientMessage(Component.translatable("message.frozendawn.radiation.contaminated").withStyle(ChatFormatting.GOLD), true); }
        }
        // Visible and infinite until washed: the effect icon, and ash flaking off for everyone to see.
        if (state.contamination > 0) {
            var current = p.getEffect(ModEffects.CONTAMINATED);
            if (current == null || !current.isInfiniteDuration())
                p.addEffect(new MobEffectInstance(ModEffects.CONTAMINATED, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, p.getX(), p.getY() + 1, p.getZ(),
                    (int) Math.ceil(4 * state.contamination), 0.3, 0.5, 0.3, 0);
        } else p.removeEffect(ModEffects.CONTAMINATED);
        state.rate = RadiationField.rate(p) + state.contamination;
        double treatment = 0;
        if (state.treatmentSeconds > 0) { state.treatmentSeconds--; treatment = RadAidItem.DOSE / RadAidItem.SECONDS; }
        state.dose = RadiationMath.nextDose(state.dose, state.rate, treatment);
        if (state.dose >= 1) ReactorAdvancements.grant(p, "three_point_six_roentgen");
        state.felt = Math.min(state.dose, RadiationMath.nextFelt(state.felt, state.dose));
        symptoms(p, RadiationMath.stage(state.felt));
        PacketDistributor.sendToPlayer(p, new RadiationPayload((float) state.rate, (float) state.dose, (byte) RadiationMath.stage(state.felt), (float) state.contamination));
    }

    private static void symptoms(ServerPlayer p, int stage) {
        var maxHealth = p.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            if (stage >= 3 && maxHealth.getModifier(MAX_HEALTH_ID) == null)
                maxHealth.addTransientModifier(new AttributeModifier(MAX_HEALTH_ID, -8, AttributeModifier.Operation.ADD_VALUE));
            else if (stage < 3) maxHealth.removeModifier(MAX_HEALTH_ID);
            if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        }
        if (stage == 0) { p.removeEffect(ModEffects.RADIATION_SICKNESS); return; }
        p.addEffect(new MobEffectInstance(ModEffects.RADIATION_SICKNESS, 60, Math.min(3, stage - 1), false, false, true));
        var random = p.getRandom();
        p.causeFoodExhaustion(0.2f * stage);
        if (stage >= 1 && random.nextFloat() < 0.03f * stage) p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false, false));
        if (stage >= 2) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, stage >= 3 ? 1 : 0, false, false, false));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 0, false, false, false));
        }
        if (stage == 3 && random.nextFloat() < 0.15f) p.hurt(source(p), 1);
        if (stage >= 4) p.hurt(source(p), 2);
    }
    private static DamageSource source(ServerPlayer p) {
        return new DamageSource(p.serverLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ModDamageTypes.RADIATION));
    }

    /** Sickness II halves healing; III and lethal stop it. */
    @SubscribeEvent public static void heal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        var effect = p.getEffect(ModEffects.RADIATION_SICKNESS);
        if (effect == null) return;
        if (effect.getAmplifier() >= 2) event.setCanceled(true);
        else if (effect.getAmplifier() >= 1) event.setAmount(event.getAmount() * 0.5f);
    }
}
