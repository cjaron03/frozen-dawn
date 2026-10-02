package com.frozendawn.dev.fdbot;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Parses {@code oak_log}, {@code minecraft:oak_log}, and {@code #minecraft:logs}. */
final class FdBotIds {
    private FdBotIds() {
    }

    record Result<T>(T value, String error) {
        boolean ok() {
            return error == null;
        }

        static <T> Result<T> ok(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> fail(String error) {
            return new Result<>(null, error);
        }
    }

    record BlockMatch(Block block, TagKey<Block> tag, String label) {
        boolean test(BlockState state) {
            return tag != null ? state.is(tag) : state.is(block);
        }
    }

    static Result<BlockMatch> block(RegistryAccess access, String raw) {
        boolean tag = raw.startsWith("#");
        ResourceLocation id;
        try {
            id = parseId(tag ? raw.substring(1) : raw);
        } catch (IllegalArgumentException ex) {
            return Result.fail("invalid block id '" + raw + "'");
        }
        if (id == null) {
            return Result.fail("invalid block id '" + raw + "'");
        }
        Registry<Block> registry = access.registryOrThrow(Registries.BLOCK);
        if (tag) {
            TagKey<Block> key = TagKey.create(Registries.BLOCK, id);
            if (registry.getTag(key).isEmpty()) {
                return Result.fail("unknown block tag '#" + id + "'");
            }
            return Result.ok(new BlockMatch(null, key, "#" + id));
        }
        Block block = registry.getOptional(id).orElse(null);
        if (block == null) {
            return Result.fail("unknown block '" + id + "'");
        }
        return Result.ok(new BlockMatch(block, null, BuiltInRegistries.BLOCK.getKey(block).toString()));
    }

    static Result<Item> item(RegistryAccess access, String raw) {
        if (raw.startsWith("#")) {
            return Result.fail("expected an item id, not a tag");
        }
        ResourceLocation id;
        try {
            id = parseId(raw);
        } catch (IllegalArgumentException ex) {
            return Result.fail("invalid item id '" + raw + "'");
        }
        if (id == null) {
            return Result.fail("invalid item id '" + raw + "'");
        }
        Item item = access.registryOrThrow(Registries.ITEM).getOptional(id).orElse(null);
        if (item == null) {
            return Result.fail("unknown item '" + id + "'");
        }
        return Result.ok(item);
    }

    static String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /**
     * A missing namespace means {@code minecraft}. Returns null when the id is blank.
     * {@link ResourceLocation#parse} throws on a malformed id.
     */
    static ResourceLocation parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String body = raw.contains(":") ? raw : "minecraft:" + raw;
        return ResourceLocation.parse(body);
    }
}
