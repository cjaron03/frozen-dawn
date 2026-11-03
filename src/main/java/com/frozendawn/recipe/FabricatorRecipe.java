package com.frozendawn.recipe;

import com.frozendawn.init.ModRecipeSerializers;
import com.frozendawn.init.ModRecipeTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** An ORSA Fabricator job: counted ingredients spread over any input slots, a blueprint the world must have learned,
 * and the power the machine draws while it works. */
public final class FabricatorRecipe implements Recipe<FabricatorRecipe.Input> {
    public record Counted(Ingredient ingredient, int count) {
        public static final Codec<Counted> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Counted::ingredient),
                ExtraCodecs.intRange(1, 64).optionalFieldOf("count", 1).forGetter(Counted::count)).apply(i, Counted::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Counted> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Counted::ingredient, ByteBufCodecs.VAR_INT, Counted::count, Counted::new);
    }
    public record Input(List<ItemStack> items) implements RecipeInput {
        @Override public ItemStack getItem(int index) { return items.get(index); }
        @Override public int size() { return items.size(); }
    }

    private final String blueprint;
    private final List<Counted> ingredients;
    private final ItemStack result;
    private final int watts, seconds;

    public FabricatorRecipe(String blueprint, List<Counted> ingredients, ItemStack result, int watts, int seconds) {
        this.blueprint = blueprint; this.ingredients = List.copyOf(ingredients); this.result = result; this.watts = watts; this.seconds = seconds;
    }
    public String blueprint() { return blueprint; }
    public List<Counted> counted() { return ingredients; }
    public ItemStack result() { return result; }
    public int watts() { return watts; }
    public int seconds() { return seconds; }

    /** Per-slot debits that satisfy every ingredient in order, or null when the inputs fall short. */
    public int[] consumption(Input input) {
        int[] taken = new int[input.size()];
        for (var need : ingredients) {
            int left = need.count();
            for (int i = 0; i < input.size() && left > 0; i++) {
                var stack = input.getItem(i);
                if (stack.isEmpty() || !need.ingredient().test(stack)) continue;
                int n = Math.min(left, stack.getCount() - taken[i]);
                taken[i] += n; left -= n;
            }
            if (left > 0) return null;
        }
        return taken;
    }

    @Override public boolean matches(Input input, Level level) { return consumption(input) != null; }
    @Override public ItemStack assemble(Input input, HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result; }
    @Override public NonNullList<Ingredient> getIngredients() {
        var list = NonNullList.<Ingredient>create(); for (var need : ingredients) list.add(need.ingredient()); return list;
    }
    /** Keeps the client recipe book from filing Fabricator jobs under an unknown category. */
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return ModRecipeSerializers.FABRICATING.get(); }
    @Override public RecipeType<?> getType() { return ModRecipeTypes.FABRICATING.get(); }

    public static final class Serializer implements RecipeSerializer<FabricatorRecipe> {
        private static final MapCodec<FabricatorRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("blueprint").forGetter(FabricatorRecipe::blueprint),
                ExtraCodecs.nonEmptyList(Counted.CODEC.listOf()).fieldOf("ingredients").forGetter(FabricatorRecipe::counted),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(FabricatorRecipe::result),
                ExtraCodecs.POSITIVE_INT.fieldOf("watts").forGetter(FabricatorRecipe::watts),
                ExtraCodecs.POSITIVE_INT.fieldOf("seconds").forGetter(FabricatorRecipe::seconds)).apply(i, FabricatorRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, FabricatorRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FabricatorRecipe::blueprint,
                Counted.STREAM_CODEC.apply(ByteBufCodecs.list()), FabricatorRecipe::counted,
                ItemStack.STREAM_CODEC, FabricatorRecipe::result,
                ByteBufCodecs.VAR_INT, FabricatorRecipe::watts,
                ByteBufCodecs.VAR_INT, FabricatorRecipe::seconds,
                FabricatorRecipe::new);
        @Override public MapCodec<FabricatorRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, FabricatorRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
