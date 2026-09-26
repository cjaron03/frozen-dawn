package com.frozendawn.entity;

import com.frozendawn.maeve.MaeveDirector;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/** A finite local execution of one historical sword bet. Never reads equipment or suit state. */
final class ArchitectArcherController {
    static final int ARROWS = 16, DRAW_TICKS = 20, SHOT_GAP = 20, EMPTY_CUE = 20;
    private static final String GENERATED = "macs_keep_away_bow";
    private final ArchitectEntity actor;
    private final ArchitectArcherCover cover = new ArchitectArcherCover();
    private UUID encounter;
    private int arrows;
    private long drawStarted = -1, nextDraw, emptyUntil = -1, lastSeenAt;
    private Vec3 lastSeen;
    private boolean suspended;

    ArchitectArcherController(ArchitectEntity actor) { this.actor = actor; }
    boolean active() { return encounter != null; }
    String describe() { return active() ? "archer=" + encounter + " arrows=" + arrows + " drawing=" + (drawStarted >= 0) + " suspended=" + suspended : "archer=none"; }
    static boolean generated(ItemStack stack) {
        return stack.is(Items.BOW) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(GENERATED);
    }
    boolean candidate(LivingEntity target) {
        return !actor.isDrinkingPotion() && actor.getBrainAction() != ArchitectEntity.ACTION_RETREAT
                && actor.getOffhandItem().isEmpty() && actor.distanceToSqr(target) > 64 && actor.hasLineOfSight(target);
    }
    boolean tick(MaeveDirector.PositionDirective directive, LivingEntity target, long now) {
        if (!(target instanceof ServerPlayer player) || !player.isAlive() || player.isCreative() || player.isSpectator()
                || player.level() != actor.level() || !player.getUUID().equals(directive.player()) || actor.distanceToSqr(player) > 48 * 48) {
            stop("TARGET_UNAVAILABLE"); return false;
        }
        if (!active()) {
            encounter = directive.encounter(); arrows = ARROWS; nextDraw = now;
            lastSeen = target.position(); lastSeenAt = now;
            MaeveDirector.commitmentArrived(actor);
            MaeveDirector.beginLocalCombat(actor, player);
            actor.recordDecision("MAEVE_ARCHER_EQUIPPED", null, "encounter=" + encounter + " arrows=" + arrows + " confidence=" + directive.confidence());
        }
        if (actor.isDrinkingPotion() || actor.getBrainAction() == ArchitectEntity.ACTION_RETREAT
                || actor.getHealth() <= actor.getMaxHealth() * .3F) {
            suspend(); return false;
        }
        if (suspended) {
            suspended = false; nextDraw = now + SHOT_GAP;
            actor.recordDecision("MAEVE_ARCHER_RESUMED", null, "arrows=" + arrows);
        }
        equip();
        actor.setMaeveHolding(false); actor.setCommitmentAction(false); actor.setSprinting(false);
        boolean visible = actor.hasLineOfSight(target);
        if (visible) { lastSeen = target.position(); lastSeenAt = now; }
        if (visible && actor.distanceToSqr(target) <= 9) { stop("ARCHER_RUSHED_TO_MELEE"); return false; }
        if (arrows == 0) {
            lower(); actor.setArcherPose(2); actor.getNavigation().stop();
            actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
            if (now >= emptyUntil) { stop("ARCHER_QUIVER_EMPTY"); return false; }
            return true;
        }
        if (!visible) {
            lower();
            // Follow only the last point witnessed locally, never a hidden player's coordinates.
            if (lastSeen == null || now - lastSeenAt >= 100) { stop("ARCHER_LOST_SIGHT"); return false; }
            if (cover.side() != 0) actor.executeArcherMotion(lastSeen, -cover.side());
            else if (now % 10 == 0) actor.getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, .8);
            return true;
        }
        actor.getLookControl().setLookAt(target, 30, 30);
        cover.update(actor, target, now);
        actor.executeArcherMotion(target.position(), drawStarted >= 0 || now >= nextDraw ? -cover.side() : cover.side());
        if (actor.distanceToSqr(target) > 24 * 24) { lower(); return true; }
        if (drawStarted < 0 && now >= nextDraw) {
            drawStarted = now; actor.startUsingItem(InteractionHand.MAIN_HAND); actor.setArcherPose(1);
        }
        if (drawStarted >= 0 && now - drawStarted >= DRAW_TICKS) {
            shoot(target); lower(); nextDraw = now + SHOT_GAP;
            if (arrows == 0) {
                emptyUntil = now + EMPTY_CUE; actor.setArcherPose(2);
                actor.recordDecision("MAEVE_ARCHER_EMPTY", null, "cueTicks=" + EMPTY_CUE + " encounter=" + encounter);
            }
        }
        return true;
    }
    private void shoot(LivingEntity target) {
        var arrow = new Arrow(actor.level(), actor, new ItemStack(Items.ARROW), actor.getMainHandItem());
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        double x = target.getX() - actor.getX(), z = target.getZ() - actor.getZ();
        double y = target.getY(.3333333333333333) - arrow.getY();
        // Vanilla physical arrows: cover, shields and normal suit puncture events all apply.
        // Faster flight needs less loft: .2 * (1.6 / 2.0)^2. Keep the existing spread.
        // Offset velocity-scaled impact damage so this tuning is primarily a flight-speed change.
        arrow.setBaseDamage(arrow.getBaseDamage() * .8);
        arrow.shoot(x, y + Math.sqrt(x * x + z * z) * .128, z, 2.0F, 6F);
        if (actor.level().addFreshEntity(arrow)) {
            arrows--;
            actor.playSound(SoundEvents.SKELETON_SHOOT, 1, 1);
            actor.recordDecision("MAEVE_ARCHER_SHOT", null, "remaining=" + arrows + " target=" + target.getUUID() + " projectile=" + arrow.getUUID());
        }
    }
    boolean equip() {
        if (!active() || suspended || actor.isDrinkingPotion()) return false;
        if (!generated(actor.getMainHandItem())) {
            var bow = new ItemStack(Items.BOW); var tag = new CompoundTag(); tag.putBoolean(GENERATED, true);
            bow.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); actor.setItemSlot(EquipmentSlot.MAINHAND, bow);
        }
        return true;
    }
    private void lower() {
        if (actor.isUsingItem() && generated(actor.getUseItem())) actor.stopUsingItem();
        drawStarted = -1; actor.setArcherPose(0);
    }
    void suspend() {
        if (!active()) return;
        lower();
        if (!suspended) actor.recordDecision("MAEVE_ARCHER_SUSPENDED", null, "arrows=" + arrows);
        suspended = true;
    }
    void stop(String reason) {
        if (active()) actor.recordDecision("MAEVE_ARCHER_FINISHED", null, reason + " remaining=" + arrows);
        MaeveDirector.releaseCommitment(actor, reason); clear(); actor.triggerReeval();
    }
    void clear() {
        lower();
        if (generated(actor.getMainHandItem())) actor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        encounter = null; arrows = 0; emptyUntil = -1; lastSeen = null; suspended = false; cover.clear();
    }
}
