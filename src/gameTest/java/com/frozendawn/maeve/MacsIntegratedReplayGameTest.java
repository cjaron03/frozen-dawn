package com.frozendawn.maeve;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MacsIntegratedReplayGameTest {
    @GameTest(template = GameTestTemplates.EMPTY_LARGE, timeoutTicks = 80)
    public static void macsIntegratedCampFunctionsParseAtClientPermission(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var functions = server.getResourceManager().listResources("function", id ->
                id.getNamespace().equals("macs_trial") && id.getPath().endsWith(".mcfunction"));
        helper.assertTrue(!functions.isEmpty(), "The generated camp functions must be in the actual test resources");
        var calls = java.util.regex.Pattern.compile("(?:^| )function (macs_trial:[a-z0-9_/]+)");
        functions.forEach((file, resource) -> {
            var id = ResourceLocation.fromNamespaceAndPath("macs_trial",
                    file.getPath().substring("function/".length()).replace(".mcfunction", ""));
            helper.assertTrue(server.getFunctions().get(id).isPresent(), "Native function loaded: " + id);
            try (var reader = resource.openAsReader()) {
                var lines = reader.lines().toList();
                CommandFunction.fromLines(id, server.getCommands().getDispatcher(),
                        server.createCommandSourceStack().withPermission(2), lines);
                for (String line : lines) {
                    var matcher = calls.matcher(line);
                    while (matcher.find()) helper.assertTrue(server.getFunctions().get(ResourceLocation.parse(matcher.group(1))).isPresent(),
                            "Referenced function exists: " + matcher.group(1));
                    // Brigadier parses fill coordinates but checks its volume only at execution.
                    if (line.startsWith("fill ")) {
                        String[] fields = line.split(" ");
                        long volume = 1;
                        for (int axis = 1; axis <= 3; axis++) volume *= 1L + Math.abs(
                                Integer.parseInt(fields[axis]) - Integer.parseInt(fields[axis + 3]));
                        helper.assertTrue(volume <= 32768, "Camp fill fits the vanilla command limit: " + line);
                    }
                }
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        });
        helper.succeed();
    }
}
