package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.client.particle.ColdBreathParticle;
import com.frozendawn.client.compat.curios.CuriosClientCompat;
import com.frozendawn.client.renderer.FrostbittenRenderer;
import com.frozendawn.client.renderer.AggregateRenderer;
import com.frozendawn.client.renderer.AggregateFragmentRenderer;
import com.frozendawn.client.renderer.AggregateShedChunkRenderer;
import com.frozendawn.client.renderer.RimeboundModel;
import com.frozendawn.client.renderer.RimeboundRenderer;
import com.frozendawn.client.renderer.RimeLanceRenderer;
import com.frozendawn.client.renderer.ResonantModel;
import com.frozendawn.client.renderer.ResonantRenderer;
import com.frozendawn.client.renderer.RemnantRenderer;
import com.frozendawn.client.renderer.FrostmiteRenderer;
import com.frozendawn.client.renderer.FrostwritheModel;
import com.frozendawn.client.renderer.FrostwritheRenderer;
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
import com.frozendawn.client.renderer.EmergencyEvaSuitLayer;
import com.frozendawn.client.particle.BloomSporeRootParticle;
import com.frozendawn.client.particle.BloomDriftParticle;
import com.frozendawn.client.particle.AggregateConvergenceParticle;
import com.frozendawn.client.particle.AggregateExpulsionParticle;
import com.frozendawn.client.particle.AggregatePressureSignalParticle;
import com.frozendawn.client.particle.UnthreadingMemoryParticle;
import com.frozendawn.client.particle.UnthreadingResidueParticle;
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
import com.frozendawn.item.MielHoneyItem;
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
        event.registerSpriteSet(ModParticles.COLD_BREATH.get(),
                com.frozendawn.client.particle.ColdBreathParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOM_SPORE_ROOTING.get(),
                BloomSporeRootParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOM_DRIFT.get(),
                BloomDriftParticle.Provider::new);
        event.registerSpriteSet(ModParticles.AGGREGATE_CONVERGENCE.get(),
                AggregateConvergenceParticle.Provider::new);
        event.registerSpriteSet(ModParticles.AGGREGATE_EXPULSION.get(),
                AggregateExpulsionParticle.Provider::new);
        event.registerSpriteSet(ModParticles.AGGREGATE_PRESSURE_SIGNAL.get(),
                AggregatePressureSignalParticle.Provider::new);
        event.registerSpriteSet(ModParticles.UNTHREADING_MEMORY.get(),
                UnthreadingMemoryParticle.Provider::new);
        event.registerSpriteSet(ModParticles.UNTHREADING_RESIDUE.get(),
                UnthreadingResidueParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "cold_breath"),
                        DefaultVertexFormat.PARTICLE), ColdBreathParticle::setShader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "aurora"),
                        DefaultVertexFormat.POSITION_TEX_COLOR), AuroraRenderer::setShader);
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(
                                FrozenDawn.MOD_ID, "master_architect_eye_volume"),
                        DefaultVertexFormat.POSITION),
                MasterArchitectEyeWallRenderer::setShader);
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(
                                FrozenDawn.MOD_ID, "stillpoint_field"),
                        DefaultVertexFormat.POSITION),
                StillpointFieldRenderer::setShader);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Visor material covers the world; every instrument is projected above it.
        event.registerAbove(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CAMERA_OVERLAYS,
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "eva_visor"),
                SuitIntegrityClient::renderVisor
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frost_overlay"),
                FrostOverlay::render
        );
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "radiation_soot"), RadiationClient::renderSoot);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_danger"), IcarusDangerHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_suit"), IcarusSuitHud::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_screen_flash"), IcarusScreenFlash::render);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "icarus_flash"), IcarusBlastEffects::renderFlash);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "radiation_flashes"), RadiationClient::renderFlashes);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "geiger_readout"), RadiationClient::renderReadout);
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(
                        FrozenDawn.MOD_ID, "frostwrithe_overrun"),
                FrostwritheOverrunOverlay::render
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
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "first_boot_epigraph"),
                FirstBootEpigraph::render
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
        event.register(ContinuityRecoveryHud.targetKey());
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
            ItemProperties.register(ModItems.GEIGER_COUNTER.get(), ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "reading"),
                    (stack, level, entity, seed) -> entity == net.minecraft.client.Minecraft.getInstance().player ? RadiationClient.needle() : 0);
            ItemProperties.register(ModItems.MIEL_HONEY.get(), ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "fill"),
                    (stack, level, entity, seed) -> stack.getOrDefault(ModDataComponents.MIEL_SPOONFULS, MielHoneyItem.SPOONFULS) / (float) MielHoneyItem.SPOONFULS);
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
                com.frozendawn.client.renderer.ArchitectModel.LAYER_LOCATION,
                com.frozendawn.client.renderer.ArchitectModel::createBodyLayer);
        event.registerLayerDefinition(
                MasterArchitectAdornmentModel.LAYER_LOCATION,
                MasterArchitectAdornmentModel::createBodyLayer);
        event.registerLayerDefinition(RocketLaunchModel.LAYER_LOCATION, RocketLaunchModel::createBodyLayer);
        event.registerLayerDefinition(
                RimeboundModel.LAYER_LOCATION, RimeboundModel::createBodyLayer);
        event.registerLayerDefinition(
                ResonantModel.LAYER_LOCATION, ResonantModel::createBodyLayer);
        event.registerLayerDefinition(
                FrostwritheModel.LAYER_LOCATION, FrostwritheModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new HearthrotSuitLayer(renderer));
                renderer.addLayer(new EmergencyEvaSuitLayer(renderer));
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.AGGREGATE.get(), AggregateRenderer::new);
        event.registerEntityRenderer(
                ModEntities.AGGREGATE_FRAGMENT.get(), AggregateFragmentRenderer::new);
        event.registerEntityRenderer(
                ModEntities.AGGREGATE_SHED_CHUNK.get(), AggregateShedChunkRenderer::new);
        event.registerEntityRenderer(ModEntities.SHADOW_FIGURE.get(), ShadowFigureRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTBITTEN.get(), FrostbittenRenderer::new);
        event.registerEntityRenderer(ModEntities.RIMEBOUND.get(), RimeboundRenderer::new);
        event.registerEntityRenderer(ModEntities.RIME_LANCE.get(), RimeLanceRenderer::new);
        event.registerEntityRenderer(ModEntities.RESONANT.get(), ResonantRenderer::new);
        event.registerEntityRenderer(ModEntities.REMNANT.get(), RemnantRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTMITE.get(), FrostmiteRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTWRITHE.get(), FrostwritheRenderer::new);
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
        event.registerBlockEntityRenderer(ModBlockEntities.UPLINK_DISH.get(), com.frozendawn.client.renderer.UplinkDishRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OXYGEN_RESERVOIR.get(),com.frozendawn.client.renderer.OxygenReservoirRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.OXYGEN_GAUGE.get(), com.frozendawn.client.renderer.OxygenGaugeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HALSEY_CORE.get(), HalseyCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ICARUS_STABILIZER.get(), IcarusStabilizerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ICARUS_INJECTOR.get(), IcarusInjectorRenderer::new);
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
    public static void onRegisterAdditionalModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        event.register(com.frozendawn.client.renderer.UplinkDishRenderer.REFLECTOR);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.RESISTIVE_HEATER.get(), ResistiveHeaterScreen::new);
        event.register(ModMenuTypes.REACTOR_CONTROLLER.get(), ReactorControllerScreen::new);
        event.register(ModMenuTypes.SFR_CONTROLLER.get(), SfrControllerScreen::new);
        event.register(ModMenuTypes.ICARUS_CONTROLLER.get(), IcarusControllerScreen::new);
        event.register(ModMenuTypes.PIXIE.get(), PixieScreen::new);
        event.register(ModMenuTypes.POWER_MONITOR.get(), PowerMonitorScreen::new);
        event.register(ModMenuTypes.UPLINK_CONSOLE.get(), UplinkConsoleScreen::new);
        event.register(ModMenuTypes.PLUMB_LINE.get(), PlumbLineScreen::new);
        event.register(ModMenuTypes.INDUCTION_FURNACE.get(), InductionFurnaceScreen::new);
        event.register(ModMenuTypes.FABRICATOR.get(), FabricatorScreen::new);
        event.register(ModMenuTypes.ANTIPARTICLE_FOUNDRY.get(), AntiparticleFoundryScreen::new);
        event.register(ModMenuTypes.ISOTOPE_COLUMN.get(), IsotopeColumnScreen::new);
        event.register(ModMenuTypes.POWER_STORAGE.get(), PowerStorageScreen::new);
        event.register(ModMenuTypes.GEOTHERMAL_CORE.get(), GeothermalCoreScreen::new);
        event.register(ModMenuTypes.FUEL_PROCESSING_SILO.get(), FuelProcessingSiloScreen::new);
        event.register(ModMenuTypes.THERMAL_CONTAINER.get(), ThermalContainerScreen::new);
        event.register(ModMenuTypes.ACHERON_FORGE.get(), AcheronForgeScreen::new);
        event.register(ModMenuTypes.TRANSPONDER.get(), TransponderScreen::new);
        event.register(ModMenuTypes.PHASE_BAROMETER.get(), PhaseBarometerScreen::new);
        event.register(ModMenuTypes.THERMAL_HEATER.get(), ThermalHeaterScreen::new);
        event.register(ModMenuTypes.THERMOSTAT.get(), ThermostatScreen::new);
    }

    /** Deep crimson glass palette; only the lens/highlight faces carry these tint indices. */
    private static int emergencyLensTint(boolean lit,int tintIndex) {
        return switch(tintIndex) {
            case 0 -> lit ? 0xFFD20B23 : 0xFF700B18;
            case 1 -> lit ? 0xFFE52236 : 0xFF8B1525;
            default -> -1;
        };
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state,level,pos,tintIndex) -> emergencyLensTint(
                state.getValue(com.frozendawn.block.OrsaEmergencyLightBlock.LIT),tintIndex),
                ModBlocks.ORSA_EMERGENCY_LIGHT.get());
    }

    /**
     * Tint food item textures based on frost level.
     * Affects rendering everywhere: inventory, hand, ground, item frames.
     */
    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> 0xFF000000 | (tintIndex == 0
                ? com.frozendawn.item.ScribeSpawnEggItem.BASE_COLOR
                : com.frozendawn.item.ScribeSpawnEggItem.SPOT_COLOR), ModItems.SCRIBE_SPAWN_EGG.get());
        event.register((stack, tintIndex) -> 0xFF000000 | (tintIndex == 0
                ? com.frozendawn.item.BlindSpawnEggItem.BASE_COLOR
                : com.frozendawn.item.BlindSpawnEggItem.SPOT_COLOR), ModItems.BLIND_SPAWN_EGG.get());
        event.register((stack, tintIndex) -> 0xFF000000 | (tintIndex == 0
                ? com.frozendawn.item.PyroSpawnEggItem.BASE_COLOR
                : com.frozendawn.item.PyroSpawnEggItem.SPOT_COLOR), ModItems.PYRO_SPAWN_EGG.get());
        event.register((stack,tintIndex) -> emergencyLensTint(false,tintIndex),ModBlocks.ORSA_EMERGENCY_LIGHT.get().asItem());
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
