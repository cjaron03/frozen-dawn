package com.frozendawn.client.compat.curios;

import com.frozendawn.FrozenDawn;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.lang.reflect.InvocationTargetException;

public final class CuriosClientCompat {

    private static final CuriosClientAccess ACCESS = createAccess();

    private CuriosClientCompat() {
    }

    public static void registerRenderers() {
        ACCESS.registerRenderers();
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        ACCESS.registerLayerDefinitions(event);
    }

    private static CuriosClientAccess createAccess() {
        if (!ModList.get().isLoaded("curios")) {
            return new NoOpCuriosClientAccess();
        }

        try {
            return (CuriosClientAccess) Class.forName("com.frozendawn.client.compat.curios.CuriosLoadedClientAccess")
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                 | InvocationTargetException | NoSuchMethodException e) {
            FrozenDawn.LOGGER.error("Failed to initialize Curios client compatibility. Falling back to no-op mode.", e);
            return new NoOpCuriosClientAccess();
        }
    }
}
