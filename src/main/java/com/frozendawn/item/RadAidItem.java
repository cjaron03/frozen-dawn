package com.frozendawn.item;

import com.frozendawn.init.ModAttachments;
import com.frozendawn.radiation.RadiationMath;
import com.frozendawn.radiation.ReactorAdvancements;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** ORSA RadAid: an orange IV drip bag of chelating solution. Hook it up (three seconds of dripping) and it
 * flushes 200 RU of dose over the next minute. Scarce medical salvage. */
public final class RadAidItem extends Item {
    public static final int SECONDS = 60, USE_TICKS = 60;
    public static final double DOSE = 200;
    public RadAidItem(Properties p) { super(p); }
    @Override public UseAnim getUseAnimation(ItemStack s) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack s, LivingEntity e) { return USE_TICKS; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) { return ItemUtils.startUsingInstantly(level, player, hand); }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (remaining % 12 == 0) level.playSound(null, entity.blockPosition(), SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.PLAYERS, 1f, 1.2f);
    }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof ServerPlayer p) {
            var state = p.getData(ModAttachments.RADIATION.get());
            if (RadiationMath.stage(state.felt) >= 3) ReactorAdvancements.grant(p, "war_never_changes");
            state.treatmentSeconds = SECONDS;
            level.playSound(null, p.blockPosition(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1f, 0.8f);
            if (!p.getAbilities().instabuild) stack.shrink(1);
        }
        return stack;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.frozendawn.rad_aid.hint").withStyle(ChatFormatting.GRAY));
    }
}
