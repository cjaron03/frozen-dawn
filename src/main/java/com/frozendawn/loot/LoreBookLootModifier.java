package com.frozendawn.loot;

import com.frozendawn.config.FrozenDawnConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Global Loot Modifier that injects ORSA lore books into structure loot tables.
 * Each instance targets a specific loot table via LootTableIdCondition.
 */
public class LoreBookLootModifier extends LootModifier {

    public static final MapCodec<LoreBookLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    IGlobalLootModifier.LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(glm -> glm.conditions),
                    com.mojang.serialization.Codec.STRING.fieldOf("title").forGetter(m -> m.title),
                    com.mojang.serialization.Codec.STRING.fieldOf("author").forGetter(m -> m.author),
                    com.mojang.serialization.Codec.STRING.listOf().fieldOf("pages").forGetter(m -> m.pages),
                    com.mojang.serialization.Codec.FLOAT.fieldOf("chance").forGetter(m -> m.chance)
            ).apply(instance, LoreBookLootModifier::new));

    private final String title;
    private final String author;
    private final List<String> pages;
    private final float chance;

    public LoreBookLootModifier(LootItemCondition[] conditions, String title, String author,
                                List<String> pages, float chance) {
        super(conditions);
        this.title = title;
        this.author = author;
        this.pages = pages;
        this.chance = chance;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!FrozenDawnConfig.ENABLE_LORE_BOOKS.get()) return generatedLoot;
        if (context.getRandom().nextFloat() > chance) return generatedLoot;

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);

        List<Filterable<Component>> bookPages = new ArrayList<>();
        for (String pageText : pages) {
            bookPages.add(Filterable.passThrough(Component.literal(pageText)));
        }

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                bookPages,
                true
        );
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        generatedLoot.add(book);
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
