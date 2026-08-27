package com.frozendawn.lore;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.data.ThaevenLoreSavedData;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.UndoneArchitectEntity;
import com.frozendawn.init.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Guaranteed thematic drops and atomic Pattern Residue assembly. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class ThaevenLoreAcquisitionHandler {
    private static final int ASSEMBLY_TICKS = 5 * 20;
    private static final int FORMATION_TICKS = 3 * 20;
    private static final double HEART_SCAR_RADIUS_SQR = 24.0D * 24.0D;
    private static final Map<UUID, Integer> ASSEMBLY_PROGRESS = new HashMap<>();

    private ThaevenLoreAcquisitionHandler() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ServerPlayer player = responsiblePlayer(event.getSource().getEntity());
        if (player == null || player.getServer() == null) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof UndoneArchitectEntity) {
            dropRecordFour(player, victim);
            return;
        }
        Item fragment = fragmentFor(victim);
        if (fragment != null) {
            dropFragment(player, victim, fragment);
        }
    }

    /** Frostwrithe resolves through disassembly and never emits a death event. */
    public static void onFrostwritheDisassembled(
            ServerPlayer player, FrostwritheEntity frostwrithe) {
        dropFragment(player, frostwrithe,
                ModItems.FROSTWRITHE_PATTERN_FRAGMENT.get());
    }

    public static void tickPlayer(ServerPlayer player) {
        if (player.getServer() == null
                || ThaevenLoreSavedData.get(player.getServer()).hasRecord(
                player.getUUID(), ThaevenRecordId.PATTERN_RESIDUE)
                || !hasAllFragments(player)) {
            ASSEMBLY_PROGRESS.remove(player.getUUID());
            return;
        }
        ThaevenLoreSavedData lore = ThaevenLoreSavedData.get(player.getServer());
        ThaevenLoreSavedData.HeartScarAnchor anchor =
                lore.heartScarAnchor().orElse(null);
        if (anchor == null || anchor.dimension() != player.level().dimension()
                || player.distanceToSqr(anchor.pos().getX() + 0.5D,
                anchor.pos().getY() + 0.5D, anchor.pos().getZ() + 0.5D)
                > HEART_SCAR_RADIUS_SQR) {
            ASSEMBLY_PROGRESS.remove(player.getUUID());
            return;
        }
        int progress = ASSEMBLY_PROGRESS.merge(
                player.getUUID(), 1, Integer::sum);
        if (progress < ASSEMBLY_TICKS) {
            return;
        }
        int formationTick = progress - ASSEMBLY_TICKS;
        tickRecordThreeFormation(player, formationTick);
        if (formationTick < FORMATION_TICKS) {
            return;
        }
        ItemStack output = new ItemStack(ModItems.PATTERN_RESIDUE_RECORD.get());
        Vec3 look = player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (look.lengthSqr() > 0.001D) look = look.normalize().scale(0.65D);
        ItemEntity formed = new ItemEntity(player.level(),
                player.getX() + look.x, player.getY() + 1.4D,
                player.getZ() + look.z, output);
        formed.setPickUpDelay(10);
        formed.setDeltaMovement(0.0D, 0.045D, 0.0D);
        if (!player.level().addFreshEntity(formed)) {
            ASSEMBLY_PROGRESS.put(player.getUUID(), ASSEMBLY_TICKS
                    + FORMATION_TICKS);
            return;
        }
        consumeOne(player, ModItems.RIMEBOUND_PATTERN_FRAGMENT.get());
        consumeOne(player, ModItems.RESONANT_PATTERN_FRAGMENT.get());
        consumeOne(player, ModItems.REMNANT_PATTERN_FRAGMENT.get());
        consumeOne(player, ModItems.FROSTWRITHE_PATTERN_FRAGMENT.get());
        ASSEMBLY_PROGRESS.remove(player.getUUID());
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS,
                0.8F, 0.72F);
    }

    private static void tickRecordThreeFormation(
            ServerPlayer player, int formationTick) {
        ServerLevel level = player.serverLevel();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                5, 10, false, false, false));
        double lift = formationTick < 34 ? 0.045D : 0.0D;
        player.setDeltaMovement(0.0D, lift, 0.0D);
        player.hurtMarked = true;
        player.resetFallDistance();

        if (formationTick == 0) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 1.0F, 0.62F);
        }
        double radius = Mth.lerp(Math.min(1.0D,
                formationTick / (double) FORMATION_TICKS), 1.2D, 0.24D);
        Item[] fragments = {
                ModItems.RIMEBOUND_PATTERN_FRAGMENT.get(),
                ModItems.RESONANT_PATTERN_FRAGMENT.get(),
                ModItems.REMNANT_PATTERN_FRAGMENT.get(),
                ModItems.FROSTWRITHE_PATTERN_FRAGMENT.get()
        };
        for (int index = 0; index < fragments.length; index++) {
            double angle = formationTick * 0.19D
                    + Math.PI * 2.0D * index / fragments.length;
            double x = player.getX() + Math.cos(angle) * radius;
            double y = player.getY() + 1.1D
                    + Math.sin(formationTick * 0.11D + index) * 0.28D;
            double z = player.getZ() + Math.sin(angle) * radius;
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,
                            new ItemStack(fragments[index])), x, y, z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        level.sendParticles(ParticleTypes.GLOW,
                player.getX(), player.getY() + 1.15D, player.getZ(),
                2, radius * 0.35D, 0.22D, radius * 0.35D, 0.01D);
    }

    public static void clearPlayer(ServerPlayer player) {
        ASSEMBLY_PROGRESS.remove(player.getUUID());
    }

    private static void dropRecordFour(ServerPlayer player, Entity victim) {
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(player.getServer());
        boolean heartExposed = ReturnedHearthSavedData.get(player.getServer())
                .hearth(com.frozendawn.homo.HearthSelectionPolicy.HearthType.MAJOR)
                .map(hearth -> hearth.heartLive()
                        || hearth.heartCollapseStartGameTime() >= 0L
                        || hearth.heartCollapseComplete())
                .orElse(false);
        if (!heartExposed
                || data.hasRecord(player.getUUID(),
                ThaevenRecordId.THE_HEART_BENEATH)
                || player.getInventory().contains(
                new ItemStack(ModItems.ACCRETED_REMNANT.get()))) {
            return;
        }
        spawnForPlayer(victim, new ItemStack(ModItems.ACCRETED_REMNANT.get()));
    }

    private static void dropFragment(
            ServerPlayer player, Entity victim, Item fragment) {
        ThaevenLoreSavedData data = ThaevenLoreSavedData.get(player.getServer());
        if (data.hasRecord(player.getUUID(), ThaevenRecordId.PATTERN_RESIDUE)
                || player.getInventory().contains(new ItemStack(fragment))) {
            return;
        }
        spawnForPlayer(victim, new ItemStack(fragment));
    }

    private static void spawnForPlayer(Entity victim, ItemStack stack) {
        ItemEntity item = new ItemEntity(victim.level(), victim.getX(),
                victim.getY() + 0.4D, victim.getZ(), stack);
        item.setDefaultPickUpDelay();
        victim.level().addFreshEntity(item);
    }

    private static ServerPlayer responsiblePlayer(Entity source) {
        if (source instanceof ServerPlayer player) {
            return player;
        }
        if (source instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    private static Item fragmentFor(Entity entity) {
        if (entity instanceof RimeboundEntity) {
            return ModItems.RIMEBOUND_PATTERN_FRAGMENT.get();
        }
        if (entity instanceof ResonantEntity) {
            return ModItems.RESONANT_PATTERN_FRAGMENT.get();
        }
        if (entity instanceof RemnantEntity) {
            return ModItems.REMNANT_PATTERN_FRAGMENT.get();
        }
        return null;
    }

    private static boolean hasAllFragments(ServerPlayer player) {
        return has(player, ModItems.RIMEBOUND_PATTERN_FRAGMENT.get())
                && has(player, ModItems.RESONANT_PATTERN_FRAGMENT.get())
                && has(player, ModItems.REMNANT_PATTERN_FRAGMENT.get())
                && has(player, ModItems.FROSTWRITHE_PATTERN_FRAGMENT.get());
    }

    private static boolean has(ServerPlayer player, Item item) {
        return player.getInventory().contains(new ItemStack(item));
    }

    private static void consumeOne(ServerPlayer player, Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
    }
}
