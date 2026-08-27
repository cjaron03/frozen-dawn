package com.frozendawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FrozenDawnCommandTreeTest {

    @Test
    void publicRootContainsOnlyDiagnosticsAndDebugGateway() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("frozendawn");

        assertEquals(Set.of("help", "status", "locate", "win", "debug"),
                childNames(root));
        assertNull(root.getChild("world"));
        assertNull(root.getChild("hearth"));
        assertNull(root.getChild("aggregate"));
        assertNull(root.getChild("suit"));
    }

    @Test
    void debugSurfaceIsGroupedByAuthorityDomain() {
        CommandNode<CommandSourceStack> debug = dispatcher().getRoot()
                .getChild("frozendawn").getChild("debug");

        assertEquals(Set.of("world", "hearth", "heart", "postmaeve",
                        "aggregate", "suit", "lore"), childNames(debug));
        assertNotNull(debug.getChild("postmaeve").getChild("bloom"));
        assertNull(debug.getChild("hearth").getChild("postmaeve"));
        assertNull(debug.getChild("hearth").getChild("heart"));
        assertNull(debug.getChild("hearth").getChild("lore"));
    }

    @Test
    void destructiveCommandsRequireExplicitConfirmation() {
        CommandNode<CommandSourceStack> debug = dispatcher().getRoot()
                .getChild("frozendawn").getChild("debug");

        assertNotNull(debug.getChild("world").getChild("reset").getChild("confirm"));
        assertNotNull(debug.getChild("heart").getChild("reset").getChild("confirm"));
        assertNotNull(debug.getChild("aggregate").getChild("reset").getChild("confirm"));
        assertNotNull(debug.getChild("lore").getChild("reset-world-semantic")
                .getChild("confirm"));
        assertNull(debug.getChild("world").getChild("reset").getCommand());
        assertNull(debug.getChild("aggregate").getChild("resolve").getCommand());
    }

    @Test
    void fdRedirectsToTheDebugGateway() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        CommandNode<CommandSourceStack> debug = dispatcher.getRoot()
                .getChild("frozendawn").getChild("debug");
        assertSame(debug, dispatcher.getRoot().getChild("fd").getRedirect());
    }

    @Test
    void primaryStatusesKeepDeepDiagnosticsBehindVerbose() {
        CommandNode<CommandSourceStack> debug = dispatcher().getRoot()
                .getChild("frozendawn").getChild("debug");

        assertVerbose(debug.getChild("world").getChild("status"));
        assertVerbose(debug.getChild("hearth").getChild("status"));
        assertNotNull(debug.getChild("hearth").getChild("list").getChild("verbose"));
        assertVerbose(debug.getChild("heart").getChild("status"));
        assertVerbose(debug.getChild("postmaeve").getChild("status"));
        assertVerbose(debug.getChild("aggregate").getChild("status"));
        assertVerbose(debug.getChild("aggregate").getChild("stillpoint")
                .getChild("status"));
        assertVerbose(debug.getChild("suit").getChild("status"));
        assertVerbose(debug.getChild("suit").getChild("hearthrot")
                .getChild("status"));
        assertVerbose(debug.getChild("lore").getChild("status"));
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        FrozenDawnCommand.register(dispatcher);
        return dispatcher;
    }

    private static Set<String> childNames(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName)
                .collect(Collectors.toSet());
    }

    private static void assertVerbose(CommandNode<CommandSourceStack> status) {
        assertNotNull(status);
        assertNotNull(status.getChild("verbose"));
    }
}
