package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModMenuTypes;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side MOD bus event handlers.
 * Registers GUI layers and menu screens.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frost_overlay"),
                FrostOverlay::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "heat_overlay"),
                HeatOverlay::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "temperature_hud"),
                TemperatureHud::render
        );
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Register compass needle property for Acheronite Compass
        // Uses LodestoneTracker data component — same as vanilla lodestone compass
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.ACHERONITE_COMPASS.get(),
                    ResourceLocation.withDefaultNamespace("angle"),
                    new CompassItemPropertyFunction((level, stack, entity) -> {
                        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                        return tracker != null ? tracker.target().orElse(null) : null;
                    })
            );
        });
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.GEOTHERMAL_CORE.get(), GeothermalCoreScreen::new);
        event.register(ModMenuTypes.THERMAL_CONTAINER.get(), ThermalContainerScreen::new);
        event.register(ModMenuTypes.ACHERON_FORGE.get(), AcheronForgeScreen::new);
        event.register(ModMenuTypes.TRANSPONDER.get(), TransponderScreen::new);
        event.register(ModMenuTypes.THERMAL_HEATER.get(), ThermalHeaterScreen::new);
    }

    /**
     * Tint food item textures based on frost level.
     * Affects rendering everywhere: inventory, hand, ground, item frames.
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor frostTint = (stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            Integer frost = stack.get(ModDataComponents.FROST_TICKS.get());
            if (frost == null || frost <= 0) return -1;
            // Progressive blue shift via color multiplication
            if (frost >= 6000) return 0xFF8899D9; // heavy blue-grey (frost-ruined)
            if (frost >= 2400) return 0xFFAABBEE; // noticeable blue (frozen)
            if (frost >= 600) return 0xFFDDE5FF;  // subtle cool tint (chilled)
            return -1;
        };

        // Register for all items that have food properties
        BuiltInRegistries.ITEM.forEach(item -> {
            if (item.getDefaultInstance().has(DataComponents.FOOD)) {
                event.register(frostTint, item);
            }
        });
    }
}
