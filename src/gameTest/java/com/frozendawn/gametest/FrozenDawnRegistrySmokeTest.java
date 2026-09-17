package com.frozendawn.gametest;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModArmorMaterials;
import com.frozendawn.init.ModAttachments;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModEffects;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModFluids;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModLootModifiers;
import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.init.ModParticles;
import com.frozendawn.init.ModRecipeSerializers;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Load-time integrity checks that only a booted server can make.
 *
 * <p>The JUnit suite under {@code src/test} exercises policy logic against plain objects and
 * never starts Minecraft, so it cannot see a deferred registration that failed to bind or a
 * datapack file that no longer parses. These tests cover exactly that gap, and they are the
 * reason the release gate has something to run.
 */
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public class FrozenDawnRegistrySmokeTest {

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void deferredRegistrationsAreBound(GameTestHelper helper) {
        assertAllBound(helper, "armor materials", ModArmorMaterials.ARMOR_MATERIALS);
        assertAllBound(helper, "attachments", ModAttachments.ATTACHMENTS);
        assertAllBound(helper, "data components", ModDataComponents.DATA_COMPONENTS);
        assertAllBound(helper, "fluid types", ModFluids.FLUID_TYPES);
        assertAllBound(helper, "fluids", ModFluids.FLUIDS);
        assertAllBound(helper, "blocks", ModBlocks.BLOCKS);
        assertAllBound(helper, "items", ModItems.ITEMS);
        assertAllBound(helper, "creative tabs", ModItems.CREATIVE_TABS);
        assertAllBound(helper, "block entities", ModBlockEntities.BLOCK_ENTITIES);
        assertAllBound(helper, "menu types", ModMenuTypes.MENU_TYPES);
        assertAllBound(helper, "recipe serializers", ModRecipeSerializers.RECIPE_SERIALIZERS);
        assertAllBound(helper, "loot modifiers", ModLootModifiers.LOOT_MODIFIERS);
        assertAllBound(helper, "sounds", ModSounds.SOUNDS);
        assertAllBound(helper, "particles", ModParticles.PARTICLES);
        assertAllBound(helper, "entities", ModEntities.ENTITIES);
        assertAllBound(helper, "effects", ModEffects.EFFECTS);
        helper.succeed();
    }

    @GameTest(template = GameTestTemplates.EMPTY)
    public static void datapackContentLoaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();

        long recipes = server.getRecipeManager().getRecipes().stream()
                .filter(holder -> isOurs(holder.id()))
                .count();
        helper.assertTrue(recipes > 0, "no " + FrozenDawn.MOD_ID + " recipes were loaded");

        long lootTables = server.reloadableRegistries().getKeys(Registries.LOOT_TABLE).stream()
                .filter(FrozenDawnRegistrySmokeTest::isOurs)
                .count();
        helper.assertTrue(lootTables > 0, "no " + FrozenDawn.MOD_ID + " loot tables were loaded");

        helper.succeed();
    }

    /**
     * Walks every entry the register declared and confirms the game actually bound it. An
     * unbound holder means the registration silently dropped out — the failure mode that
     * turns into a missing block or a hard crash the first time a player touches it.
     */
    private static void assertAllBound(GameTestHelper helper, String label, DeferredRegister<?> register) {
        int count = 0;
        for (DeferredHolder<?, ?> holder : register.getEntries()) {
            helper.assertTrue(holder.isBound(), "unbound " + label + " entry: " + holder.getId());
            helper.assertTrue(isOurs(holder.getId()),
                    label + " entry registered outside the mod namespace: " + holder.getId());
            count++;
        }
        helper.assertTrue(count > 0, "no " + label + " were registered at all");
    }

    private static boolean isOurs(ResourceLocation id) {
        return FrozenDawn.MOD_ID.equals(id.getNamespace());
    }
}
