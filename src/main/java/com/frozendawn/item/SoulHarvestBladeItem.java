package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Tier;

import java.util.List;

public class SoulHarvestBladeItem extends AcheroniteSwordItem {
    private static final float SOUL_HARVEST_DAMAGE = 3.0f;
    private static final TagKey<EntityType<?>> SOUL_HARVEST_TARGETS = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "soul_harvest_targets"));

    public SoulHarvestBladeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.frozendawn.soul_harvest_blade")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.frozendawn.soul_harvest_blade.flavor")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource damageSource) {
        return target.getType().is(SOUL_HARVEST_TARGETS) ? SOUL_HARVEST_DAMAGE : 0.0f;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.getType().is(SOUL_HARVEST_TARGETS) && target.level() instanceof ServerLevel serverLevel) {
            spawnSoulHarvestParticles(serverLevel, target, attacker);
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    private static void spawnSoulHarvestParticles(ServerLevel serverLevel, LivingEntity target, LivingEntity attacker) {
        double width = target.getBbWidth();
        double height = target.getBbHeight();
        double x = target.getX();
        double y = target.getY() + height * 0.6;
        double z = target.getZ();
        int count = 20;
        double xOffset = Math.max(0.2, width * 0.4);
        double yOffset = Math.max(0.25, height * 0.35);
        double zOffset = Math.max(0.2, width * 0.4);
        double speed = 0.02;

        if (attacker instanceof ServerPlayer serverPlayer) {
            serverLevel.sendParticles(
                    serverPlayer,
                    ParticleTypes.SOUL_FIRE_FLAME,
                    true,
                    x,
                    y,
                    z,
                    count,
                    xOffset,
                    yOffset,
                    zOffset,
                    speed
            );
            return;
        }

        serverLevel.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                x,
                y,
                z,
                count,
                xOffset,
                yOffset,
                zOffset,
                speed
        );
    }
}
