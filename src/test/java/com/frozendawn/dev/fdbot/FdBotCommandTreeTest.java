package com.frozendawn.dev.fdbot;

import com.frozendawn.command.FrozenDawnCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdBotCommandTreeTest {

    @Test
    void productionBuildsDoNotRegisterTheCommand() {
        assertFalse(FdBotCommands.shouldRegister(true));
        assertTrue(FdBotCommands.shouldRegister(false));
    }

    @Test
    void commandIsSeparateFromThePublicTreeAndRequiresOp() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        FrozenDawnCommand.register(dispatcher);
        FdBotCommands.register(dispatcher);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("fdbot");
        CommandNode<CommandSourceStack> frozenDawn = dispatcher.getRoot().getChild("frozendawn");
        assertNotNull(root);
        assertNull(frozenDawn.getChild("fdbot"));
        assertNull(frozenDawn.getChild("debug").getChild("fdbot"));
        assertEquals(Set.of("gather", "craft", "place", "use", "goto", "face", "status"), childNames(root));

        assertFalse(root.getRequirement().test(source(1)));
        assertTrue(root.getRequirement().test(source(2)));
        assertNotNull(root.getChild("gather").getChild("target").getChild("count").getChild("radius"));
        assertNotNull(root.getChild("craft").getChild("item").getChild("count"));
        assertNotNull(root.getChild("place").getChild("item").getChild("front"));
        assertNotNull(root.getChild("place").getChild("item").getChild("pos").getChild("against").getChild("dir"));
        assertNotNull(root.getChild("use").getChild("here"));
        assertNotNull(root.getChild("goto").getChild("nearest").getChild("target"));
        assertNotNull(root.getChild("face").getChild("pos"));
    }

    @Test
    void commandTreeSerializesForAnOp() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        FdBotCommands.register(dispatcher);
        RootCommandNode<SharedSuggestionProvider> clientRoot = copyForClient(dispatcher.getRoot());
        // The same call Open to LAN makes inside ClientboundCommandsPacket. Unregistered
        // argument types throw IllegalArgumentException from ArgumentTypeInfos.byClass.
        ClientboundCommandsPacket packet = new ClientboundCommandsPacket(clientRoot);
        assertNotNull(packet);
        assertNotNull(clientRoot.getChild("fdbot"));
    }

    @Test
    void namespacedIdsAndTagsParseAsOneToken() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        FdBotCommands.register(dispatcher);
        CommandSourceStack op = source(2);
        assertParsed(dispatcher, op, "fdbot gather #minecraft:logs 2 4");
        assertParsed(dispatcher, op, "fdbot gather oak_log 1");
        assertParsed(dispatcher, op, "fdbot craft minecraft:oak_planks 4");
        assertParsed(dispatcher, op, "fdbot place minecraft:oak_door front");
        assertParsed(dispatcher, op, "fdbot place minecraft:stone 1 2 3");
        assertParsed(dispatcher, op, "fdbot place minecraft:stone 1 2 3 against north");
        assertParsed(dispatcher, op, "fdbot place minecraft:stone front against down");
        assertParsed(dispatcher, op, "fdbot face nearest minecraft:emerald_block");
        assertParsed(dispatcher, op, "fdbot use 1 2 3");
    }

    @Test
    void idsDefaultToMinecraftAndTheAuditFlagIsPerPlayer() {
        assertEquals(ResourceLocation.parse("minecraft:oak_log"), FdBotIds.parseId("oak_log"));
        assertEquals(ResourceLocation.parse("frozendawn:thermal_heater"),
                FdBotIds.parseId("frozendawn:thermal_heater"));
        assertNull(FdBotIds.parseId("  "));

        UUID player = UUID.randomUUID();
        assertFalse(FdBotAudit.isBotAssisted(player));
        FdBotAudit.mark(player);
        assertTrue(FdBotAudit.isBotAssisted(player));
        assertFalse(FdBotAudit.isBotAssisted(UUID.randomUUID()));
    }

    private static void assertParsed(
            CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String command) {
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(command, source);
        assertTrue(parsed.getExceptions().isEmpty(), command + " " + parsed.getExceptions());
        assertFalse(parsed.getReader().canRead(), command + " leftover: " + parsed.getReader().getRemaining());
    }

    /**
     * Same shape as {@code Commands#fillUsableCommands} for a source that can use every node.
     * Suggestion providers that are not in the vanilla registry are swapped to ask-server,
     * which is what the packet writer does.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RootCommandNode<SharedSuggestionProvider> copyForClient(RootCommandNode<CommandSourceStack> root) {
        Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> map = new HashMap<>();
        RootCommandNode<SharedSuggestionProvider> copy = new RootCommandNode<>();
        map.put(root, copy);
        copyChildren(root, copy, map);
        return copy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void copyChildren(
            CommandNode<CommandSourceStack> source,
            CommandNode<SharedSuggestionProvider> dest,
            Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> map) {
        for (CommandNode<CommandSourceStack> child : source.getChildren()) {
            if (child instanceof ArgumentCommandNode<?, ?> argument) {
                ArgumentTypeInfos.byClass((ArgumentType<?>) argument.getType());
            }
            ArgumentBuilder<SharedSuggestionProvider, ?> builder =
                    (ArgumentBuilder<SharedSuggestionProvider, ?>) (ArgumentBuilder) child.createBuilder();
            builder.requires(ignored -> true);
            if (builder.getCommand() != null) {
                builder.executes(ctx -> 0);
            }
            if (builder instanceof RequiredArgumentBuilder<?, ?> required && required.getSuggestionsProvider() != null) {
                SuggestionProvider<SharedSuggestionProvider> provider =
                        (SuggestionProvider<SharedSuggestionProvider>) required.getSuggestionsProvider();
                ((RequiredArgumentBuilder<SharedSuggestionProvider, ?>) required)
                        .suggests(SuggestionProviders.safelySwap(provider));
            }
            CommandNode<SharedSuggestionProvider> built = builder.build();
            map.put(child, built);
            dest.addChild(built);
            if (!child.getChildren().isEmpty()) {
                copyChildren(child, built, map);
            }
        }
    }

    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "test", Component.literal("test"), null, null);
    }
}
