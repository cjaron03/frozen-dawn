package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.compat.curios.CuriosClientCompat;
import com.frozendawn.client.renderer.FrostbittenRenderer;
import com.frozendawn.client.renderer.FrostmiteRenderer;
import com.frozendawn.client.renderer.HeavySnowballRenderer;
import com.frozendawn.client.renderer.HollowRenderer;
import com.frozendawn.client.renderer.ArchitectRenderer;
import com.frozendawn.client.renderer.AlarmBeaconRenderer;
import com.frozendawn.client.renderer.MimicRenderer;
import com.frozendawn.client.renderer.PhaseBarometerRenderer;
import com.frozendawn.client.renderer.ReturnedRenderer;
import com.frozendawn.client.renderer.OrsaFlagRenderer;
import com.frozendawn.client.renderer.ShadowFigureRenderer;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.init.ModSkullTypes;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "surveyor_lens_overlay"),
                SurveyorLensOverlay::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "temperature_hud"),
                TemperatureHud::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "air_status_hud"),
                AirStatusHud::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "o2_bubble_hud"),
                O2BubbleHud::render
        );
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SurveyorLensVision.thermalModeKey());
        event.register(SurveyorLensVision.blizzardModeKey());
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
            CuriosClientCompat.registerRenderers();
        });
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        CuriosClientCompat.registerLayerDefinitions(event);
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHADOW_FIGURE.get(), ShadowFigureRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTBITTEN.get(), FrostbittenRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTMITE.get(), FrostmiteRenderer::new);
        event.registerEntityRenderer(ModEntities.HOLLOW.get(), HollowRenderer::new);
        event.registerEntityRenderer(ModEntities.HEAVY_SNOWBALL.get(), HeavySnowballRenderer::new);
        event.registerEntityRenderer(ModEntities.RETURNED.get(), ReturnedRenderer::new);
        event.registerEntityRenderer(ModEntities.MIMIC.get(), MimicRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCHITECT.get(), ArchitectRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ORSA_FLAG.get(), OrsaFlagRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ALARM_BEACON.get(), AlarmBeaconRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.PHASE_BAROMETER.get(), PhaseBarometerRenderer::new);
    }

    @SubscribeEvent
    public static void onCreateSkullModels(EntityRenderersEvent.CreateSkullModels event) {
        event.registerSkullModel(
                ModSkullTypes.ARCHITECT,
                new SkullModel(event.getEntityModelSet().bakeLayer(ModelLayers.ZOMBIE_HEAD))
        );
        SkullBlockRenderer.SKIN_BY_TYPE.put(
                ModSkullTypes.ARCHITECT,
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "textures/entity/architect_head.png")
        );
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.GEOTHERMAL_CORE.get(), GeothermalCoreScreen::new);
        event.register(ModMenuTypes.THERMAL_CONTAINER.get(), ThermalContainerScreen::new);
        event.register(ModMenuTypes.ACHERON_FORGE.get(), AcheronForgeScreen::new);
        event.register(ModMenuTypes.TRANSPONDER.get(), TransponderScreen::new);
        event.register(ModMenuTypes.PHASE_BAROMETER.get(), PhaseBarometerScreen::new);
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
