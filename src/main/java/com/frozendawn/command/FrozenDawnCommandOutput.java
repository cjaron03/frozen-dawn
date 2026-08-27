package com.frozendawn.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;

/** Shared, restrained presentation for operator diagnostics. */
final class FrozenDawnCommandOutput {
    private FrozenDawnCommandOutput() {
    }

    static void heading(CommandSourceStack source, String title) {
        source.sendSuccess(() -> Component.literal("Frozen Dawn / " + title)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
    }

    static void line(CommandSourceStack source, String label, Object value) {
        MutableComponent message = Component.literal(label + ": ")
                .withStyle(ChatFormatting.GRAY);
        message.append(Component.literal(String.valueOf(value))
                .withStyle(ChatFormatting.WHITE));
        source.sendSuccess(() -> message, false);
    }

    static void detail(CommandSourceStack source, String label, Object value) {
        MutableComponent message = Component.literal("  " + label + ": ")
                .withStyle(ChatFormatting.DARK_GRAY);
        message.append(Component.literal(String.valueOf(value))
                .withStyle(ChatFormatting.GRAY));
        source.sendSuccess(() -> message, false);
    }

    static void metrics(CommandSourceStack source, String values, int valuesPerLine) {
        String[] tokens = values.trim().split("\\s+");
        for (int start = 0; start < tokens.length; start += valuesPerLine) {
            int end = Math.min(tokens.length, start + valuesPerLine);
            String line = String.join("  ", Arrays.copyOfRange(tokens, start, end));
            source.sendSuccess(() -> Component.literal("  " + line)
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    static void item(CommandSourceStack source, String value) {
        source.sendSuccess(() -> Component.literal("- ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }

    static void hint(CommandSourceStack source, String command) {
        source.sendSuccess(() -> Component.literal("Details: " + command)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false);
    }

    static void success(CommandSourceStack source, String message, boolean broadcast) {
        source.sendSuccess(() -> Component.literal(message)
                .withStyle(ChatFormatting.GREEN), broadcast);
    }

    static void warning(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message)
                .withStyle(ChatFormatting.YELLOW), false);
    }
}
