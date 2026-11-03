package com.frozendawn.defense;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.*;
import com.frozendawn.item.TerminusItem;
import com.frozendawn.network.TerminusDischargePayload;
import com.frozendawn.radiation.ReactorAdvancements;
import java.util.ArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * ORSA TR-0 Terminus: after a two-second wind-up, one discharge stops every susceptible mind in a 12-block, 30-degree
 * cone, through blocks and armor, and costs its operator four max health for good. No beam, nothing burned.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class Terminus {
    public static final int WINDUP_TICKS = 40, TOLL_PER_DISCHARGE = 4, BACKLASH_TICKS = 200, GAZE_TICKS = 60;
    /** What a boss loses to a discharge, as a share of its maximum; it is never brought below 1. */
    public static final float BOSS_SHARE = .4f;
    public static final TagKey<EntityType<?>> SUSCEPTIBLE = tag("terminus_susceptible"), IMMUNE = tag("terminus_immune");
    private static final ResourceLocation TOLL = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "terminus_toll");

    private enum Response { STOP, WOUND, GAZE, NONE }

    /** Spends one discharge from {@code stack}, held in {@code hand}, on the cone ahead of the operator's eyes. */
    public static void discharge(ServerPlayer op, ItemStack stack, InteractionHand hand) {
        var log = stack.getOrDefault(ModDataComponents.TERMINUS_LOG.get(), TerminusLog.FOUND);
        if (log.spent()) return;
        var level = op.serverLevel();
        Vec3 eye = op.getEyePosition(), look = op.getLookAngle();
        var source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModDamageTypes.TERMINUS), op);
        boolean turned = TerminusLedger.turned(op.getServer());
        var minds = new ArrayList<Vector3f>();
        for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(TerminusCone.REACH),
                e -> e != op && e.isAlive())) {
            if (!TerminusCone.contains(eye, look, target.getBoundingBox().getCenter())
                    && !TerminusCone.contains(eye, look, target.getEyePosition())) continue;
            switch (response(target, source)) {
                case GAZE -> TerminusGaze.start((ArchitectEntity) target, op, hand);
                case WOUND -> target.setHealth(Math.max(1f, target.getHealth() - BOSS_SHARE * target.getMaxHealth()));
                case STOP -> {
                    var head = target.getEyePosition();
                    if (stop(target, source, op, turned)) minds.add(head.toVector3f());
                }
                case NONE -> { }
            }
        }
        stack.set(ModDataComponents.TERMINUS_LOG.get(), log.spend().log(minds.size()));
        op.setData(ModAttachments.TERMINUS_TOLL.get(), op.getData(ModAttachments.TERMINUS_TOLL.get()) + 1);
        applyToll(op);
        op.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BACKLASH_TICKS));
        op.addEffect(new MobEffectInstance(MobEffects.CONFUSION, BACKLASH_TICKS));
        ReactorAdvancements.grant(op, "deathwand");
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(op, new TerminusDischargePayload(op.getId(), minds));
    }

    private static Response response(LivingEntity target, DamageSource source) {
        if (target instanceof ArchitectEntity architect && architect.isMasterArchitectVisual()) return Response.GAZE;
        if (target.getType().is(IMMUNE) || target.isInvulnerableTo(source) || target.isSpectator()) return Response.NONE;
        if (target instanceof Player player && (player.getAbilities().invulnerable || !player.getServer().isPvpAllowed()))
            return Response.NONE;
        if (target.getType().is(Tags.EntityTypes.BOSSES)) return Response.WOUND;
        return target.getType().is(SUSCEPTIBLE) ? Response.STOP : Response.NONE;
    }

    /**
     * Before the CH-18 trigger a mind is taken: its death event fires, naming the operator, and it is removed with no
     * drops or experience. Players always die the ordinary way, since they come back. After it, everything does.
     */
    private static boolean stop(LivingEntity target, DamageSource source, ServerPlayer op, boolean turned) {
        if (turned || target instanceof Player) {
            target.setLastHurtByPlayer(op);
            target.getCombatTracker().recordDamage(source, target.getHealth());
            target.setHealth(0);
            target.die(source);
            return target.isDeadOrDying();
        }
        if (CommonHooks.onLivingDeath(target, source)) return false;
        target.remove(Entity.RemovalReason.KILLED);
        return true;
    }

    /** Where the wand is in the operator's hand, roughly: forward of the eyes, below them, to the holding side. */
    public static Vec3 wandPosition(Player op, InteractionHand hand) {
        boolean right = (hand == InteractionHand.MAIN_HAND) == (op.getMainArm() == HumanoidArm.RIGHT);
        float yaw = op.getYRot() * Mth.DEG_TO_RAD;
        var side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(right ? .35 : -.35);
        return op.getEyePosition().add(op.getLookAngle().scale(.6)).add(side).add(0, -.45, 0);
    }

    /** Sets the max-health modifier from the discharges on record, and keeps health under the new maximum. */
    public static void applyToll(ServerPlayer player) {
        int discharges = player.getData(ModAttachments.TERMINUS_TOLL.get());
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.removeModifier(TOLL);
        if (discharges > 0) maxHealth.addPermanentModifier(new AttributeModifier(TOLL,
                -TOLL_PER_DISCHARGE * discharges, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Any damage during the wind-up cancels it, and the discharge is spent anyway. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0 || !player.isUsingItem()) return;
        var stack = player.getUseItem();
        if (!(stack.getItem() instanceof TerminusItem)) return;
        stack.set(ModDataComponents.TERMINUS_LOG.get(),
                stack.getOrDefault(ModDataComponents.TERMINUS_LOG.get(), TerminusLog.FOUND).spend());
        player.stopUsingItem();
        silence(player);
    }

    /** Stops the wind-up tone for everyone who could hear it. */
    public static void silence(ServerPlayer player) {
        var packet = new ClientboundStopSoundPacket(ModSounds.TERMINUS_WINDUP.getId(), SoundSource.PLAYERS);
        for (var listener : player.serverLevel().players())
            if (listener.distanceToSqr(player) < 32 * 32) listener.connection.send(packet);
    }

    /** The toll outlives death: the respawned player takes it from the one who died. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer reborn)) return;
        reborn.setData(ModAttachments.TERMINUS_TOLL.get(), event.getOriginal().getData(ModAttachments.TERMINUS_TOLL.get()));
        applyToll(reborn);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) applyToll(player);
    }

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name));
    }

    private Terminus() { }
}
