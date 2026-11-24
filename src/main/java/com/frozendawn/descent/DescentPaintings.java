package com.frozendawn.descent;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

/**
 * The descent's motifs (CH-44, CH-45), its sampler and its portrait as painting variants. None is in
 * {@code minecraft:placeable}, so a painting placed in survival never rolls one; the creative tab offers each.
 */
public final class DescentPaintings {
    /** Variant paths under {@code frozendawn:}, in descent order: each era's motif, the scratches, the portrait. */
    public static final List<String> IDS = List.of(
            "descent/house_1816", "descent/maunder", "descent/famine", "descent/late_antique", "descent/bronze_age",
            "descent/four_kiloyear", "descent/eight_kiloyear", "descent/younger_dryas", "descent/glacial_maximum",
            "descent/chamber_zero_scratches", "descent/portrait_1816");

    private DescentPaintings() {
    }

    /** One painting stack per variant, its variant set, as the vanilla creative tabs build them. */
    public static List<ItemStack> creativeStacks(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var variants = registries.lookupOrThrow(Registries.PAINTING_VARIANT);
        List<ItemStack> stacks = new ArrayList<>();
        for (String id : IDS) {
            var key = ResourceKey.create(Registries.PAINTING_VARIANT, ResourceLocation.fromNamespaceAndPath("frozendawn", id));
            variants.get(key).ifPresent(variant -> {
                CustomData data = CustomData.EMPTY.update(ops, Painting.VARIANT_MAP_CODEC, variant).getOrThrow()
                        .update(tag -> tag.putString("id", "minecraft:painting"));
                ItemStack stack = new ItemStack(Items.PAINTING);
                stack.set(DataComponents.ENTITY_DATA, data);
                stacks.add(stack);
            });
        }
        return stacks;
    }
}
