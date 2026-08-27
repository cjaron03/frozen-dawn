package com.frozendawn.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;

final class FrozenDawnCommandSuggestions {
    private FrozenDawnCommandSuggestions() {
    }

    static SuggestionProvider<CommandSourceStack> words(String... values) {
        return words(Arrays.asList(values));
    }

    static SuggestionProvider<CommandSourceStack> words(Collection<String> values) {
        return (context, builder) -> SharedSuggestionProvider.suggest(values, builder);
    }

    static <E extends Enum<E>> SuggestionProvider<CommandSourceStack> enums(
            Class<E> enumClass) {
        return enums(enumClass, value -> value.name().toLowerCase(Locale.ROOT));
    }

    static <E extends Enum<E>> SuggestionProvider<CommandSourceStack> enums(
            Class<E> enumClass, Function<E, String> name) {
        return (context, builder) -> SharedSuggestionProvider.suggest(
                Arrays.stream(enumClass.getEnumConstants()).map(name), builder);
    }
}
