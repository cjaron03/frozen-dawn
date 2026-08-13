package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.compat.curios.CuriosClientCompat;
import com.frozendawn.client.renderer.FrostbittenRenderer;
import com.frozendawn.client.renderer.RimeboundModel;
import com.frozendawn.client.renderer.RimeboundRenderer;
import com.frozendawn.client.renderer.RimeLanceRenderer;
import com.frozendawn.client.renderer.ResonantModel;
import com.frozendawn.client.renderer.ResonantRenderer;
import com.frozendawn.client.renderer.RemnantRenderer;
import com.frozendawn.client.renderer.FrostmiteRenderer;
import com.frozendawn.client.renderer.HeavySnowballRenderer;
import com.frozendawn.client.renderer.HeartSuccessorRenderer;
import com.frozendawn.client.renderer.HollowRenderer;
import com.frozendawn.client.renderer.ArchitectRenderer;
import com.frozendawn.client.renderer.AlarmBeaconRenderer;
import com.frozendawn.client.renderer.MimicRenderer;
import com.frozendawn.client.renderer.MasterArchitectAdornmentModel;
import com.frozendawn.client.renderer.MasterArchitectLightningRenderer;
import com.frozendawn.client.renderer.ThaeIvenHeartRenderer;
import com.frozendawn.client.renderer.PhaseBarometerRenderer;
import com.frozendawn.client.renderer.ReturnedRenderer;
import com.frozendawn.client.renderer.UndoneRenderer;
import com.frozendawn.client.renderer.UndoneArchitectRenderer;
import com.frozendawn.client.renderer.BloomSporeRenderer;
import com.frozendawn.client.renderer.BloomSporeCorpseRenderer;
import com.frozendawn.client.renderer.ArchivistRenderer;
import com.frozendawn.client.renderer.ArchivistRelicRenderer;
import com.frozendawn.client.renderer.HearthrotSuitLayer;
import com.frozendawn.client.particle.BloomSporeRootParticle;
import com.frozendawn.client.particle.BloomDriftParticle;
import com.frozendawn.client.renderer.RocketLaunchModel;
import com.frozendawn.client.renderer.RocketLaunchRenderer;
import com.frozendawn.client.renderer.OrsaFlagRenderer;
import com.frozendawn.client.renderer.ShadowFigureRenderer;
import com.frozendawn.init.ModBlockEntities;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModFluids;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModMenuTypes;
import com.frozendawn.init.ModParticles;
import com.frozendawn.init.ModSkullTypes;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * Client-side MOD bus event handlers.
 * Registers GUI layers and menu screens.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    private static final ResourceLocation VENT_LAVA_STILL = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "block/vent_lava");
    private static final ResourceLocation VENT_LAVA_FLOW = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "block/vent_lava_flow");

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLOOM_SPORE_ROOTING.get(),
                BloomSporeRootParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOM_DRIFT.get(),
                BloomDriftParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(
                                FrozenDawn.MOD_ID, "master_architect_eye_volume"),
                        DefaultVertexFormat.POSITION),
                MasterArchitectEyeWallRenderer::setShader);
    }

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
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "suit_integrity"),
                SuitIntegrityClient::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "rocket_launch_overlay"),
                RocketLaunchClientController::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "orsa_awakening_intro"),
                OrsaAwakeningIntro::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "thaeven_transmission"),
                ThaevenTransmissionOverlay::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "hearth_boundary_effect"),
                HearthBoundaryEffects::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(
                        FrozenDawn.MOD_ID, "master_architect_fourth_wall"),
                MasterArchitectFourthWallMoment::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(
                        FrozenDawn.MOD_ID, "master_architect_flood"),
                MasterArchitectFloodClient::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(
                        FrozenDawn.MOD_ID, "cognitive_load"),
                CognitiveLoadClientState::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(
                        FrozenDawn.MOD_ID, "heart_memory_node"),
                HeartMemoryNodeClient::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(
                        FrozenDawn.MOD_ID, "hearthrot"),
                HearthrotClientState::render
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
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.THERMAL_VENT_POOL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.VENT_LAVA.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.VOLCANIC_ASH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BLOOM_MASS.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BLOOM_CRUST.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.BLOOM_TIP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.SOURCE_VENT_LAVA.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_VENT_LAVA.get(), RenderType.translucent());
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
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return VENT_LAVA_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return VENT_LAVA_FLOW;
            }
        }, ModFluids.VENT_LAVA_TYPE.get());
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        CuriosClientCompat.registerLayerDefinitions(event);
        event.registerLayerDefinition(
                MasterArchitectAdornmentModel.LAYER_LOCATION,
                MasterArchitectAdornmentModel::createBodyLayer);
        event.registerLayerDefinition(RocketLaunchModel.LAYER_LOCATION, RocketLaunchModel::createBodyLayer);
        event.registerLayerDefinition(
                RimeboundModel.LAYER_LOCATION, RimeboundModel::createBodyLayer);
        event.registerLayerDefinition(
                ResonantModel.LAYER_LOCATION, ResonantModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new HearthrotSuitLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHADOW_FIGURE.get(), ShadowFigureRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTBITTEN.get(), FrostbittenRenderer::new);
        event.registerEntityRenderer(ModEntities.RIMEBOUND.get(), RimeboundRenderer::new);
        event.registerEntityRenderer(ModEntities.RIME_LANCE.get(), RimeLanceRenderer::new);
        event.registerEntityRenderer(ModEntities.RESONANT.get(), ResonantRenderer::new);
        event.registerEntityRenderer(ModEntities.REMNANT.get(), RemnantRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTMITE.get(), FrostmiteRenderer::new);
        event.registerEntityRenderer(ModEntities.HOLLOW.get(), HollowRenderer::new);
        event.registerEntityRenderer(ModEntities.HEAVY_SNOWBALL.get(), HeavySnowballRenderer::new);
        event.registerEntityRenderer(ModEntities.RETURNED.get(), ReturnedRenderer::new);
        event.registerEntityRenderer(ModEntities.UNDONE.get(), UndoneRenderer::new);
        event.registerEntityRenderer(ModEntities.BLOOMBOUND_UNDONE.get(), UndoneRenderer::new);
        event.registerEntityRenderer(
                ModEntities.UNDONE_ARCHITECT.get(), UndoneArchitectRenderer::new);
        event.registerEntityRenderer(ModEntities.BLOOM_SPORE.get(), BloomSporeRenderer::new);
        event.registerEntityRenderer(
                ModEntities.BLOOM_SPORE_CORPSE.get(), BloomSporeCorpseRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCHIVIST.get(), ArchivistRenderer::new);
        event.registerEntityRenderer(
                ModEntities.ARCHIVIST_RELIC.get(), ArchivistRelicRenderer::new);
        event.registerEntityRenderer(ModEntities.MIMIC.get(), MimicRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCHITECT.get(), ArchitectRenderer::new);
        event.registerEntityRenderer(
                ModEntities.MASTER_ARCHITECT_LIGHTNING.get(),
                MasterArchitectLightningRenderer::new);
        event.registerEntityRenderer(
                ModEntities.THAE_IVEN_HEART.get(),
                ThaeIvenHeartRenderer::new);
        event.registerEntityRenderer(
                ModEntities.HEART_SUCCESSOR.get(),
                HeartSuccessorRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCKET_LAUNCH.get(), RocketLaunchRenderer::new);
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
        event.register(ModMenuTypes.FUEL_PROCESSING_SILO.get(), FuelProcessingSiloScreen::new);
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
