package com.frozendawn.loot;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModItems;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Global Loot Modifier that injects ORSA Document items into structure loot tables.
 * Each document has a type (for advancement matching) and a display name.
 * Right-clicking the document archives it into the Patchouli guidebook.
 */
public class LoreBookLootModifier extends LootModifier {

    public static final MapCodec<LoreBookLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    IGlobalLootModifier.LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(glm -> glm.conditions),
                    com.mojang.serialization.Codec.STRING.fieldOf("doc_type").forGetter(m -> m.docType),
                    com.mojang.serialization.Codec.STRING.fieldOf("display_name").forGetter(m -> m.displayName),
                    com.mojang.serialization.Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(instance, LoreBookLootModifier::new));

    private final String docType;
    private final String displayName;
    private final float chance;

    public LoreBookLootModifier(LootItemCondition[] conditions, String docType, String displayName, float chance) {
        super(conditions);
        this.docType = docType;
        this.displayName = displayName;
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!FrozenDawnConfig.ENABLE_LORE_BOOKS.get()) return generatedLoot;
        if (context.getRandom().nextFloat() > chance) return generatedLoot;

        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME,
                Component.literal(displayName).withStyle(ChatFormatting.GOLD));

        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", docType);
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        generatedLoot.add(doc);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
