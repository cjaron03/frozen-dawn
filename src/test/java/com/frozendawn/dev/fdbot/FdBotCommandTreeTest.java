package com.frozendawn.dev.fdbot;

import com.frozendawn.command.FrozenDawnCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
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
        assertNotNull(root.getChild("use").getChild("here"));
        assertNotNull(root.getChild("goto").getChild("nearest").getChild("target"));
        assertNotNull(root.getChild("face").getChild("pos"));
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

    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "test", Component.literal("test"), null, null);
    }
}
