package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.loot.LoreBookLootModifier;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, FrozenDawn.MOD_ID);

    public static final Supplier<MapCodec<LoreBookLootModifier>> LORE_BOOK =
            LOOT_MODIFIERS.register("lore_book", () -> LoreBookLootModifier.CODEC);
}
