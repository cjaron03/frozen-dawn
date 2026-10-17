package com.frozendawn.dev.fdbot;

import com.frozendawn.FrozenDawn;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The only registration hook for {@code /fdbot}.
 *
 * <p>The command is registered when {@link FMLEnvironment#production} is false, which is the
 * Gradle dev client, the dev server, and the GameTest server. A shipped production jar still
 * contains this class, but the event returns before the command is added to the dispatcher, so
 * a normal player cannot run it. There is no config switch: one that defaulted off would also
 * hide the command from {@code ./gradlew runClient}.
 *
 * <p>The node itself requires permission level 2. This class does not attach anything to
 * {@code /frozendawn} or {@code /fd}.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class FdBotCommands {
    private FdBotCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (!enabled()) {
            return;
        }
        register(event.getDispatcher());
        FrozenDawn.LOGGER.info("[FDBOT] registered dev-only /fdbot (permission 2, not production)");
    }

    /** True when the command should be registered for this environment. */
    public static boolean enabled() {
        return shouldRegister(FMLEnvironment.production);
    }

    static boolean shouldRegister(boolean production) {
        return !production;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(FdBotCommand.command());
    }
}
