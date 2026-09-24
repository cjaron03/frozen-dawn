package com.frozendawn.entity;

import com.frozendawn.maeve.MaeveDirector;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** One lagged sword bet; local guard cadence never samples a player's attack timing. */
final class ArchitectShieldController {
    static final String PATTERN = "PLAYER_PREFERS_SWORD";
    static final int DISABLE_TICKS = 100;
    private static final String GENERATED = "macs_sword_guard";
    private final ArchitectEntity actor;
    private final ArchitectShieldCadence cadence = new ArchitectShieldCadence();
    private UUID encounter;
    private long disabledUntil;
    private Boolean raised;
    private boolean suspended;

    ArchitectShieldController(ArchitectEntity actor) { this.actor = actor; }

    static boolean generated(ItemStack stack) {
        return stack.is(Items.SHIELD) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(GENERATED);
    }

    boolean canEquip(long now) { return now >= disabledUntil && actor.getOffhandItem().isEmpty(); }
    boolean active() { return encounter != null; }

    boolean tick(MaeveDirector.PositionDirective directive, LivingEntity target, long now) {
        if (!(target instanceof ServerPlayer subject) || !subject.isAlive() || subject.isCreative() || subject.isSpectator()
                || subject.level() != actor.level() || !subject.getUUID().equals(directive.player())) {
            stop("TARGET_UNAVAILABLE"); return false;
        }
        if (encounter == null) {
            if (!canEquip(now)) { stop("SHIELD_UNAVAILABLE"); return false; }
            encounter = directive.encounter();
            var shield = new ItemStack(Items.SHIELD);
            var tag = new CompoundTag(); tag.putBoolean(GENERATED, true);
            shield.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            actor.setItemSlot(EquipmentSlot.OFFHAND, shield);
            MaeveDirector.commitmentArrived(actor);
            if (target instanceof ServerPlayer player) MaeveDirector.beginLocalCombat(actor, player);
            actor.recordDecision("MAEVE_SHIELD_EQUIPPED", null, "encounter=" + encounter + " confidence=" + directive.confidence());
        }
        if (!generated(actor.getOffhandItem())) { stop("SHIELD_BROKEN"); return false; }
        if (actor.isDrinkingPotion() || actor.getBrainAction() == ArchitectEntity.ACTION_RETREAT
                || actor.getHealth() <= actor.getMaxHealth() * .3F) {
            suspend(actor.isDrinkingPotion() ? "DRINKING" : "RECOVERING"); return false;
        }
        if (suspended) {
            suspended = false;
            actor.recordDecision("MAEVE_SHIELD_RESUMED", null, "encounter=" + encounter
                    + " durabilityUsed=" + actor.getOffhandItem().getDamageValue());
        }
        long arrived = directive.arrivedAt() < 0 ? now : directive.arrivedAt();
        boolean visible = actor.hasLineOfSight(target);
        boolean guard = cadence.tick(now, visible, visible ? actor.distanceToSqr(target) : Double.POSITIVE_INFINITY,
                actor::nextRandomFloat);
        if (raised == null || raised != guard) {
            raised = guard;
            actor.recordDecision(guard ? "MAEVE_SHIELD_RAISE" : "MAEVE_SHIELD_LOWER", null,
                    "elapsed=" + (now - arrived) + " visible=" + visible
                            + " close=" + (visible && actor.distanceToSqr(target) <= ArchitectShieldCadence.RAISE_RANGE * ArchitectShieldCadence.RAISE_RANGE));
        }
        actor.setMaeveHolding(false);
        if (!guard) { lower(); return false; }
        if (!actor.isUsingItem()) actor.startUsingItem(InteractionHand.OFF_HAND);
        actor.getNavigation().stop();
        actor.getMoveControl().setWantedPosition(actor.getX(), actor.getY(), actor.getZ(), 0);
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        actor.setSpeed(0); actor.setZza(0); actor.setXxa(0); actor.setSprinting(false);
        if (visible) {
            float toward = (float) (Mth.atan2(target.getZ() - actor.getZ(), target.getX() - actor.getX()) * 180 / Math.PI) - 90;
            float yaw = Mth.approachDegrees(actor.getYRot(), toward, 10);
            actor.setYRot(yaw); actor.setYBodyRot(yaw); actor.setYHeadRot(yaw);
            actor.getLookControl().setLookAt(target, 10, 10);
        }
        return true;
    }

    /** Recovery keeps the same equipment, attention and result, with no active protection. */
    void suspend(String reason) {
        if (!active()) return;
        lower(); raised = false;
        if (!suspended) {
            cadence.stagger(actor.getServer().overworld().getGameTime());
            actor.recordDecision("MAEVE_SHIELD_SUSPENDED", null, reason + " encounter=" + encounter);
        }
        suspended = true;
        actor.setMaeveHolding(false);
    }

    /** Damage remains part of the same bet and result, including during recovery. */
    boolean staggerAfterDamage(DamageSource source) {
        if (!active() || !actor.isAlive() || !generated(actor.getOffhandItem())) return false;
        long now = actor.getServer().overworld().getGameTime();
        var directive = MaeveDirector.positionDirective(actor);
        if (directive == null || !encounter.equals(directive.encounter())) return false;
        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            contact(attacker);
            if (!active()) return false;
        }
        cadence.stagger(now);
        lower(); raised = false;
        actor.recordDecision("MAEVE_SHIELD_STAGGER", null, "exposedTicks=" + ArchitectShieldCadence.STAGGER_TICKS
                + " encounter=" + encounter + " arrived=" + directive.arrivedAt());
        return true;
    }

    void blocked(DamageSource source, float blocked) {
        if (!active() || !(blocked > 0)) return;
        MaeveDirector.observeShieldBlock(actor, source, blocked);
        actor.recordDecision("MAEVE_SHIELD_BLOCK", null, "type=" + source.getMsgId() + " prevented=" + blocked);
    }

    void wear(float amount) {
        if (!active() || !generated(actor.getUseItem()) || !(amount >= 3)) return;
        actor.getUseItem().hurtAndBreak(1 + Mth.floor(amount), actor, EquipmentSlot.OFFHAND);
        if (actor.getOffhandItem().isEmpty()) stop("SHIELD_BROKEN");
    }

    void contact(LivingEntity attacker) {
        if (!active()) return;
        if (attacker.canDisableShield() || attacker.getMainHandItem().is(ItemTags.AXES)) {
            disabledUntil = actor.getServer().overworld().getGameTime() + DISABLE_TICKS;
            actor.level().broadcastEntityEvent(actor, (byte) 30);
            actor.recordDecision("MAEVE_SHIELD_DISABLED", null, "until=" + disabledUntil);
            stop("SHIELD_DISABLED");
        }
    }

    private void lower() {
        if (actor.isUsingItem() && actor.getUsedItemHand() == InteractionHand.OFF_HAND
                && (active() || generated(actor.getUseItem()))) actor.stopUsingItem();
    }
    void stop(String reason) {
        if (active()) actor.recordDecision("MAEVE_SHIELD_FINISHED", null, reason);
        MaeveDirector.releaseCommitment(actor, reason);
        clear();
    }
    void clear() {
        lower();
        if (generated(actor.getOffhandItem())) actor.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        encounter = null; raised = null; suspended = false; cadence.clear();
    }
}
