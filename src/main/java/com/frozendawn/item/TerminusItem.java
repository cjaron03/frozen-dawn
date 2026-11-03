package com.frozendawn.item;

import com.frozendawn.defense.*;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/**
 * ORSA TR-0 Terminus, the deathwand: hold use for two seconds to discharge. Three discharges, ever; at none it is an
 * inert trophy. It cannot burn, wear or despawn.
 */
public final class TerminusItem extends Item {
    public TerminusItem(Properties p) {
        super(p.stacksTo(1).fireResistant().rarity(Rarity.EPIC).component(ModDataComponents.TERMINUS_LOG.get(), TerminusLog.FOUND));
    }

    private static TerminusLog log(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TERMINUS_LOG.get(), TerminusLog.FOUND);
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (log(stack).spent()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        if (level instanceof ServerLevel)
            level.playSound(null, player, ModSounds.TERMINUS_WINDUP.get(), SoundSource.PLAYERS, 1f, 1f);
        return InteractionResultHolder.consume(stack);
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return Terminus.WINDUP_TICKS; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer operator)
            Terminus.discharge(operator, stack, operator.getUsedItemHand());
        return stack;
    }
    /** Letting go before the end of the wind-up costs nothing; only a hit spends the discharge. */
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (entity instanceof ServerPlayer operator) Terminus.silence(operator);
    }
    @Override public boolean canBeHurtBy(ItemStack stack, DamageSource source) { return false; }
    @Override public int getEntityLifespan(ItemStack stack, Level level) { return Integer.MAX_VALUE; }
    /** The log changes as it discharges; only another change re-equips the wand in hand. */
    @Override public boolean shouldCauseReequipAnimation(ItemStack from, ItemStack to, boolean slotChanged) {
        return slotChanged || !to.is(this) || !from.is(this);
    }

    /** Whether {@code holder} is winding up this very stack, for the hold, the specks and the model. */
    public static boolean winding(LivingEntity holder, ItemStack stack) {
        return stack.getItem() instanceof TerminusItem && holder.isUsingItem() && holder.getItemInHand(holder.getUsedItemHand()) == stack;
    }

    /** The tooltip, given whether this world's log has turned from transfers to terminations. */
    public static List<Component> lines(TerminusLog log, boolean turned) {
        return List.of(
                Component.translatable("item.frozendawn.terminus.discharges", log.discharges()).withStyle(ChatFormatting.GRAY),
                Component.translatable(turned ? "item.frozendawn.terminus.terminations" : "item.frozendawn.terminus.transfers",
                        log.logged()).withStyle(ChatFormatting.GRAY),
                Component.translatable("item.frozendawn.terminus.exempt").withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("item.frozendawn.terminus.warning").withStyle(ChatFormatting.RED));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> text, TooltipFlag flag) {
        text.addAll(lines(log(stack), TerminusLedger.clientTurned()));
    }
}
