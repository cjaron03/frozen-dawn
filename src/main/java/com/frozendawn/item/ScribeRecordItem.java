package com.frozendawn.item;

import com.frozendawn.init.ModDataComponents;
import com.frozendawn.lore.ThaevenLoreManager;
import com.frozendawn.network.OpenScribeRecordPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Salvaged ORSA slate the Scribe carries. Its notes exist only once written at the Scribe's death. */
public final class ScribeRecordItem extends Item {
    public ScribeRecordItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ScribeRecordContents contents = stack.get(ModDataComponents.SCRIBE_RECORD.get());
        if (contents == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            // Readable with the Thaeven Translator; without it, the raw lines and the translator recipe.
            boolean translated = ThaevenLoreManager.hasTranslator(serverPlayer);
            if (!translated) ThaevenLoreManager.discoverTranslatorRecipe(serverPlayer);
            PacketDistributor.sendToPlayer(serverPlayer, new OpenScribeRecordPayload(contents, translated));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.has(ModDataComponents.SCRIBE_RECORD.get())
                        ? "item.frozendawn.scribe_record.hint" : "item.frozendawn.scribe_record.blank")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
