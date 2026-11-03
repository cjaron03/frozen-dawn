package com.frozendawn.defense;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.*;
import com.frozendawn.item.TerminusItem;
import com.frozendawn.network.TerminusDischargePayload;
import com.frozendawn.radiation.ReactorAdvancements;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * ORSA TR-0 Terminus: after a two-second wind-up, one discharge stops every susceptible mind in a 24-block, 30-degree
 * cone, through blocks and armor, and takes two of its operator's hearts, which come back a heart a Minecraft day.
 * No beam, nothing burned.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class Terminus {
    public static final int WINDUP_TICKS = 40, BACKLASH_TICKS = 200, GAZE_TICKS = 60;
    /** Minds one discharge must stop for the hidden Reduced to Atoms (the owner's, after "Gone. Reduced to atoms."). */
    public static final int ATOMS = 3;
    /** After a discharge or an interrupted wind-up, so a held use key cannot wind straight into the next one. */
    public static final int COOLDOWN_TICKS = 20;
    /**
     * How long a stopped body stands before it is gone: still and greying for half a second, then a second crumbling
     * from the head down (see TerminusCrumble). Vanilla takes a body at 20 ticks.
     */
    public static final int DEATH_TICKS = 32;
    /** Marks a mind taken before the CH-18 trigger: it dies, but leaves nothing behind. */
    private static final String TAKEN = FrozenDawn.MOD_ID + ".terminus_taken";
    /** Stopped bodies on the server, held for the crumble. Players keep vanilla's death clock. */
    private static final Set<LivingEntity> STOPPED = Collections.newSetFromMap(new WeakHashMap<>());
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
        var minds = new ArrayList<Integer>();
        for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(TerminusCone.REACH),
                e -> e != op && e.isAlive())) {
            if (!TerminusCone.contains(eye, look, target.getBoundingBox().getCenter())
                    && !TerminusCone.contains(eye, look, target.getEyePosition())) continue;
            switch (response(target, source, op)) {
                case GAZE -> TerminusGaze.start((ArchitectEntity) target, op, hand);
                case WOUND -> target.setHealth(Math.max(1f, target.getHealth() - BOSS_SHARE * target.getMaxHealth()));
                case STOP -> {
                    if (stop(target, source, op, turned)) minds.add(target.getId());
                }
                case NONE -> { }
            }
        }
        stack.set(ModDataComponents.TERMINUS_LOG.get(), log.spend().log(minds.size()));
        op.setData(ModAttachments.TERMINUS_TAKEN.get(), op.getData(ModAttachments.TERMINUS_TAKEN.get()).discharge(now(op)));
        applyToll(op);
        op.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BACKLASH_TICKS));
        op.addEffect(new MobEffectInstance(MobEffects.CONFUSION, BACKLASH_TICKS));
        ReactorAdvancements.grant(op, "deathwand");
        if (minds.size() >= ATOMS) ReactorAdvancements.grant(op, "reduced_to_atoms");
        op.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(op, TerminusDischargePayload.of(op.getId(), minds));
    }

    private static Response response(LivingEntity target, DamageSource source, ServerPlayer op) {
        if (target instanceof ArchitectEntity architect && architect.isMasterArchitectVisual()) return Response.GAZE;
        // isInvulnerableTo lets a creative player's damage through /data-invulnerable mobs; the Terminus does not.
        if (target.getType().is(IMMUNE) || target.isInvulnerable() || target.isInvulnerableTo(source) || target.isSpectator())
            return Response.NONE;
        // Creative players, a server with PvP off, and teammates where friendly fire is off are spared.
        if (target instanceof Player player && (player.getAbilities().invulnerable || !op.canHarmPlayer(player)))
            return Response.NONE;
        if (target.getType().is(Tags.EntityTypes.BOSSES)) return Response.WOUND;
        return target.getType().is(SUSCEPTIBLE) ? Response.STOP : Response.NONE;
    }

    /**
     * Every mind dies the ordinary way, credited to the operator, so whatever keeps a roll of the living hears of it.
     * Before the CH-18 trigger it is taken: nothing drops and no experience is left. Players always drop, since they
     * come back. Returns whether it died; a mind with its own way of dying may put that off.
     */
    private static boolean stop(LivingEntity target, DamageSource source, ServerPlayer op, boolean turned) {
        boolean taken = !turned && !(target instanceof Player);
        if (taken) target.addTag(TAKEN);
        if (!(target instanceof Player)) STOPPED.add(target);
        target.setLastHurtByPlayer(op);
        target.getCombatTracker().recordDamage(source, target.getHealth());
        target.setHealth(0);
        target.die(source);
        if (!target.isDeadOrDying()) {
            target.removeTag(TAKEN);
            STOPPED.remove(target);
        }
        return target.isDeadOrDying();
    }

    /**
     * In place of vanilla's death clock for a stopped body: it stands until the crumble is done, then is gone with no
     * poof of smoke. Returns false for every other body.
     */
    public static boolean holdDeath(LivingEntity body) {
        if (body.level().isClientSide() || !STOPPED.contains(body)) return false;
        if (++body.deathTime >= DEATH_TICKS && !body.isRemoved()) {
            STOPPED.remove(body);
            body.remove(Entity.RemovalReason.KILLED);
        }
        return true;
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(TAKEN)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(TAKEN)) event.setCanceled(true);
    }

    /** Where the wand is in the operator's hand, roughly: forward of the eyes, below them, to the holding side. */
    public static Vec3 wandPosition(Player op, InteractionHand hand) {
        boolean right = (hand == InteractionHand.MAIN_HAND) == (op.getMainArm() == HumanoidArm.RIGHT);
        float yaw = op.getYRot() * Mth.DEG_TO_RAD;
        var side = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(right ? .35 : -.35);
        return op.getEyePosition().add(op.getLookAngle().scale(.6)).add(side).add(0, -.45, 0);
    }

    /**
     * Sets the max-health modifier from the hearts taken, and keeps health under the new maximum. The client reads the
     * modifier to draw the taken hearts (TerminusHearts).
     */
    public static void applyToll(ServerPlayer player) {
        int taken = player.getData(ModAttachments.TERMINUS_TAKEN.get()).taken();
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;
        maxHealth.removeModifier(TOLL);
        if (taken > 0) maxHealth.addPermanentModifier(new AttributeModifier(TOLL, -taken, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Points taken, as the operator's own client sees them: the size of the toll modifier. */
    public static int taken(Player player) {
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        var modifier = maxHealth == null ? null : maxHealth.getModifier(TOLL);
        return modifier == null ? 0 : (int) -modifier.amount();
    }

    /** Taken hearts come back on the overworld's clock, which every dimension shares. */
    private static long now(ServerPlayer player) {
        return player.getServer().overworld().getGameTime();
    }

    /** Half a heart back when one is due. Players in every level, the GameTest's included, tick here. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        long now = level.getServer().overworld().getGameTime();
        for (var player : level.players()) {
            var toll = player.getData(ModAttachments.TERMINUS_TAKEN.get());
            var next = toll.tick(now);
            if (next == toll) continue;
            player.setData(ModAttachments.TERMINUS_TAKEN.get(), next);
            applyToll(player);
        }
    }

    /** Any damage during the wind-up cancels it, and the discharge is spent anyway. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0 || !player.isUsingItem()) return;
        var stack = player.getUseItem();
        if (!(stack.getItem() instanceof TerminusItem)) return;
        stack.set(ModDataComponents.TERMINUS_LOG.get(),
                stack.getOrDefault(ModDataComponents.TERMINUS_LOG.get(), TerminusLog.FOUND).spend());
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN_TICKS);
        player.stopUsingItem();
    }

    /**
     * Stops the wind-up tone for everyone who could hear it. The packet stops the sound by name, so another operator
     * winding up within earshot is cut off too.
     */
    public static void silence(ServerPlayer player) {
        var packet = new ClientboundStopSoundPacket(ModSounds.TERMINUS_WINDUP.getId(), SoundSource.PLAYERS);
        for (var listener : player.serverLevel().players())
            if (listener.distanceToSqr(player) < 32 * 32) listener.connection.send(packet);
    }

    /** The toll outlives death: the respawned player takes it from the one who died. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer reborn)) return;
        reborn.setData(ModAttachments.TERMINUS_TAKEN.get(), event.getOriginal().getData(ModAttachments.TERMINUS_TAKEN.get()));
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
