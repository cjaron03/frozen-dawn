package com.frozendawn.loot;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModItems;
import com.frozendawn.phase.PhaseManager;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Replaces Overworld fishing loot in phase 6. Early phase 6 is the die-off:
 * the old fish are gone and the line brings up bones and junk. Once vacuum
 * sets in, the cold-adapted late fish bite everywhere under the ice.
 * Phases before 6 keep vanilla catches; {@code FishingHookMixin} slows bites.
 */
public class IceFishingLootModifier extends LootModifier {

    public static final MapCodec<IceFishingLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    IGlobalLootModifier.LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(glm -> glm.conditions)
            ).apply(instance, IceFishingLootModifier::new));

    public static final ResourceKey<LootTable> DIE_OFF = table("gameplay/ice_fishing/die_off");
    public static final ResourceKey<LootTable> LATE = table("gameplay/ice_fishing/late");

    /** Hagfish come up dripping; this is the chance one brings a slimeball with it. */
    private static final float HAGFISH_SLIME_CHANCE = 0.25f;

    public IceFishingLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ServerLevel level = context.getLevel();
        if (level.dimension() != Level.OVERWORLD) return generatedLoot;
        ApocalypseState state = ApocalypseState.get(level.getServer());
        int phase = state.getPhase();
        if (!PhaseManager.isPhase6Active(phase)) return generatedLoot;

        ResourceKey<LootTable> key = PhaseManager.isVacuumActive(phase, state.getProgress()) ? LATE : DIE_OFF;
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        ObjectArrayList<ItemStack> replaced = new ObjectArrayList<>();
        table.getRandomItemsRaw(context, LootTable.createStackSplitter(level, replaced::add));

        boolean hagfish = replaced.stream().anyMatch(stack -> stack.is(ModItems.RIME_HAGFISH.get()));
        if (hagfish && context.getRandom().nextFloat() < HAGFISH_SLIME_CHANCE) {
            replaced.add(new ItemStack(Items.SLIME_BALL));
        }
        return replaced;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    private static ResourceKey<LootTable> table(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, path));
    }
}
