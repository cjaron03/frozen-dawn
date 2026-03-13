package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.AcheroniteCrystalBlock;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.item.SurveyorLensScanner;
import com.frozendawn.mixin.GameRendererAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public final class SurveyorLensVision {

    private static final int SCAN_INTERVAL = 8;
    private static final int MAX_SHADER_FIELDS = 6;
    private static final int MAX_COLD_FIELDS = 6;
    private static final int THERMAL_BOOT_TICKS = 42;
    private static final float THERMAL_FADE_IN_STEP = 0.035F;
    private static final float THERMAL_FADE_OUT_STEP = 0.028F;
    private static final ResourceLocation THERMAL_POST_EFFECT =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "shaders/post/orsa_thermal_v8a.json");
    private static final KeyMapping THERMAL_MODE_KEY = new KeyMapping(
            "key.frozendawn.toggle_thermal_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.frozendawn"
    );

    private static final List<SurveyorLensScanner.HeatSignature> cachedSignatures = new ArrayList<>();
    private static final List<ColdAnchor> cachedColdAnchors = new ArrayList<>();
    private static float overlayStrength = 0.0F;
    private static float thermalModeStrength = 0.0F;
    private static boolean thermalModeEnabled = false;
    private static int thermalBootTicksRemaining = 0;
    private static int thermalShutdownTicksRemaining = 0;

    private SurveyorLensVision() {}

    public static KeyMapping thermalModeKey() {
        return THERMAL_MODE_KEY;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) {
            syncThermalPostEffect(mc, false);
            fadeOut();
            fadeThermal();
            return;
        }

        ItemStack headArmor = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        boolean visorEquipped = headArmor.is(ModItems.ORSA_THERMAL_VISOR.get());
        SurveyorLensScanner.LensProfile heldProfile = SurveyorLensScanner.heldProfile(
                mc.player.getMainHandItem(),
                mc.player.getOffhandItem()
        );
        while (THERMAL_MODE_KEY.consumeClick()) {
            if (visorEquipped) {
                thermalModeEnabled = !thermalModeEnabled;
                if (thermalModeEnabled) {
                    thermalBootTicksRemaining = THERMAL_BOOT_TICKS;
                    thermalShutdownTicksRemaining = 0;
                } else {
                    thermalBootTicksRemaining = 0;
                    thermalShutdownTicksRemaining = THERMAL_BOOT_TICKS;
                }
            }
        }

        boolean visorThermalActive = visorEquipped && (
                thermalModeEnabled
                        || thermalBootTicksRemaining > 0
                        || thermalShutdownTicksRemaining > 0
                        || thermalModeStrength > 0.01F
        );
        SurveyorLensScanner.LensProfile activeProfile = visorThermalActive
                ? SurveyorLensScanner.LensProfile.VISOR
                : heldProfile;

        if (activeProfile == null) {
            cachedSignatures.clear();
            cachedColdAnchors.clear();
            if (!visorEquipped) {
                thermalModeEnabled = false;
                thermalBootTicksRemaining = 0;
                thermalShutdownTicksRemaining = 0;
            }
            syncThermalPostEffect(mc, false);
            fadeOut();
            fadeThermal();
            return;
        }

        overlayStrength = Math.min(1.0F, overlayStrength + 0.08F);
        if (!visorEquipped) {
            thermalModeEnabled = false;
            thermalBootTicksRemaining = 0;
            thermalShutdownTicksRemaining = 0;
        }

        if (thermalModeEnabled) {
            thermalModeStrength = Math.min(1.0F, thermalModeStrength + THERMAL_FADE_IN_STEP);
            if (thermalBootTicksRemaining > 0) {
                thermalBootTicksRemaining--;
            }
            thermalShutdownTicksRemaining = 0;
        } else if (thermalShutdownTicksRemaining > 0) {
            thermalModeStrength = Math.max(0.0F, thermalModeStrength - (THERMAL_FADE_OUT_STEP * 0.78F));
            thermalShutdownTicksRemaining--;
        } else {
            fadeThermal();
        }

        syncThermalPostEffect(mc, thermalModeStrength > 0.01F);

        long gameTime = mc.level.getGameTime();
        if (gameTime % SCAN_INTERVAL != 0) {
            return;
        }

        cachedSignatures.clear();
        cachedSignatures.addAll(SurveyorLensScanner.collectHeatSignatures(
                mc.level,
                mc.player.position(),
                mc.player.blockPosition(),
                activeProfile
        ));
        cachedColdAnchors.clear();
        if (visorEquipped) {
            cachedColdAnchors.addAll(collectColdAnchors(mc));
        }

        int markers = Math.min(activeProfile.maxMarkers(), cachedSignatures.size());
        for (int i = 0; i < markers; i++) {
            SurveyorLensScanner.HeatSignature signature = cachedSignatures.get(i);
            double x = signature.pos().getX() + 0.5D;
            double y = signature.pos().getY() + 1.05D;
            double z = signature.pos().getZ() + 0.5D;
            if (activeProfile != SurveyorLensScanner.LensProfile.VISOR && thermalModeStrength <= 0.05F) {
                mc.level.addParticle(signature.sourceType().markerParticle(), x, y, z, 0.0D, 0.01D, 0.0D);
            }
        }
    }

    public static boolean isActive() {
        return overlayStrength > 0.01F;
    }

    public static float getOverlayStrength() {
        return overlayStrength;
    }

    public static boolean isThermalModeVisible() {
        return thermalModeStrength > 0.01F || thermalBootTicksRemaining > 0 || thermalShutdownTicksRemaining > 0;
    }

    public static float getThermalModeStrength() {
        return easedThermalStrength();
    }

    public static boolean isThermalBooting() {
        return thermalBootTicksRemaining > 0;
    }

    public static boolean isThermalShuttingDown() {
        return thermalShutdownTicksRemaining > 0;
    }

    public static float getThermalBootProgress() {
        if (!isThermalBooting()) {
            return 0.0F;
        }
        return 1.0F - (thermalBootTicksRemaining / (float) THERMAL_BOOT_TICKS);
    }

    public static float getThermalShutdownProgress() {
        if (!isThermalShuttingDown()) {
            return 0.0F;
        }
        return 1.0F - (thermalShutdownTicksRemaining / (float) THERMAL_BOOT_TICKS);
    }

    public static List<SurveyorLensScanner.HeatSignature> getCachedSignatures() {
        return Collections.unmodifiableList(cachedSignatures);
    }

    public static void syncThermalShaderUniforms(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        if (currentEffect == null || !THERMAL_POST_EFFECT.toString().equals(currentEffect.getName())) {
            return;
        }

        currentEffect.setUniform("ThermalAmount", easedThermalStrength());
        currentEffect.setUniform("AmbientBaseline", ambientBaselineFromTemp(TemperatureHud.getDisplayedTemp()));

        if (mc.player == null || mc.level == null || thermalModeStrength <= 0.01F) {
            clearThermalShaderFields(currentEffect);
            return;
        }

        List<ShaderField> fields = collectShaderFields(mc, partialTick);
        List<ShaderField> coldFields = collectColdShaderFields(mc, partialTick);
        for (int i = 0; i < MAX_SHADER_FIELDS; i++) {
            if (i < fields.size()) {
                ShaderField field = fields.get(i);
                currentEffect.setUniform("HeatField" + i + "X", field.x());
                currentEffect.setUniform("HeatField" + i + "Y", field.y());
                currentEffect.setUniform("HeatField" + i + "Radius", field.radius());
                currentEffect.setUniform("HeatField" + i + "Intensity", field.intensity());
            } else {
                currentEffect.setUniform("HeatField" + i + "X", 0.0F);
                currentEffect.setUniform("HeatField" + i + "Y", 0.0F);
                currentEffect.setUniform("HeatField" + i + "Radius", 0.0F);
                currentEffect.setUniform("HeatField" + i + "Intensity", 0.0F);
            }
        }
        for (int i = 0; i < MAX_COLD_FIELDS; i++) {
            if (i < coldFields.size()) {
                ShaderField field = coldFields.get(i);
                currentEffect.setUniform("ColdField" + i + "X", field.x());
                currentEffect.setUniform("ColdField" + i + "Y", field.y());
                currentEffect.setUniform("ColdField" + i + "Radius", field.radius());
                currentEffect.setUniform("ColdField" + i + "Intensity", field.intensity());
            } else {
                currentEffect.setUniform("ColdField" + i + "X", 0.0F);
                currentEffect.setUniform("ColdField" + i + "Y", 0.0F);
                currentEffect.setUniform("ColdField" + i + "Radius", 0.0F);
                currentEffect.setUniform("ColdField" + i + "Intensity", 0.0F);
            }
        }
    }

    private static void syncThermalPostEffect(Minecraft mc, boolean shouldEnable) {
        GameRendererAccessor accessor = (GameRendererAccessor) mc.gameRenderer;
        PostChain currentEffect = accessor.frozendawn$getPostEffect();
        boolean thermalEffectActive = currentEffect != null && THERMAL_POST_EFFECT.toString().equals(currentEffect.getName());

        if (shouldEnable) {
            if (!thermalEffectActive) {
                mc.gameRenderer.loadEffect(THERMAL_POST_EFFECT);
                currentEffect = accessor.frozendawn$getPostEffect();
                thermalEffectActive = currentEffect != null && THERMAL_POST_EFFECT.toString().equals(currentEffect.getName());
            }

            if (thermalEffectActive && currentEffect != null) {
                currentEffect.setUniform("ThermalAmount", easedThermalStrength());
                clearThermalShaderFields(currentEffect);
            }
            return;
        }

        if (thermalEffectActive) {
            accessor.frozendawn$shutdownEffect();
        }
    }

    private static void fadeOut() {
        overlayStrength = Math.max(0.0F, overlayStrength - 0.08F);
    }

    private static void fadeThermal() {
        thermalModeStrength = Math.max(0.0F, thermalModeStrength - THERMAL_FADE_OUT_STEP);
    }

    private static float easedThermalStrength() {
        float t = Mth.clamp(thermalModeStrength, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float ambientBaselineFromTemp(float ambientTemp) {
        float normalized = Mth.clamp((ambientTemp + 200.0F) / 260.0F, 0.0F, 1.0F);
        return Mth.lerp(normalized, 0.03F, 0.16F);
    }

    private static List<ShaderField> collectShaderFields(Minecraft mc, float partialTick) {
        List<ShaderField> fields = new ArrayList<>();

        Vec3 eyePos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 forward = mc.player.getViewVector(partialTick).normalize();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        if (right.lengthSqr() < 1.0E-5D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
        float tanHalfFov = (float) Math.tan(Math.toRadians(mc.options.fov().get() * 0.5D));

        for (SurveyorLensScanner.HeatSignature signature : cachedSignatures) {
            float normalizedHeat = normalizeHeat(signature);
            if (normalizedHeat <= 0.0F) {
                continue;
            }

            Vec3 toSource = new Vec3(
                    signature.pos().getX() + 0.5D,
                    signature.pos().getY() + 0.65D,
                    signature.pos().getZ() + 0.5D
            ).subtract(eyePos);

            double forwardDist = toSource.dot(forward);
            if (forwardDist <= 0.12D) {
                continue;
            }

            double sideDist = toSource.dot(right);
            double upDist = toSource.dot(up);
            float xNdc = (float) (sideDist / (forwardDist * tanHalfFov * aspect));
            float yNdc = (float) (upDist / (forwardDist * tanHalfFov));

            if (Math.abs(xNdc) > 1.18F || Math.abs(yNdc) > 1.18F) {
                continue;
            }

            float uvX = xNdc * 0.5F + 0.5F;
            float uvY = 0.5F - yNdc * 0.5F;
            float distanceAttenuation = Mth.clamp(1.10F - signature.distanceBlocks() / 84.0F, 0.44F, 1.0F);
            float radius = Mth.lerp(normalizedHeat, 0.052F, 0.096F) * distanceAttenuation;
            float intensity = Mth.lerp(normalizedHeat, 0.014F, 0.055F) * distanceAttenuation;
            float score = (normalizedHeat * 1.35F) / Math.max(0.55F, signature.distanceBlocks() / 18.0F);

            fields.add(new ShaderField(uvX, uvY, radius, intensity, score));
        }

        fields.sort((left, rightField) -> Float.compare(rightField.score(), left.score()));
        if (fields.size() > MAX_SHADER_FIELDS) {
            return new ArrayList<>(fields.subList(0, MAX_SHADER_FIELDS));
        }
        return fields;
    }

    private static float normalizeHeat(SurveyorLensScanner.HeatSignature signature) {
        float heatValue = signature.heatValue();
        if (heatValue <= 0.0F) {
            return 0.0F;
        }

        float normalized = Mth.clamp((heatValue - 8.0F) / 92.0F, 0.0F, 1.0F);
        return switch (signature.sourceType()) {
            case GEOTHERMAL_CORE -> Mth.clamp(normalized * 1.18F, 0.0F, 1.0F);
            case THERMAL_HEATER -> Mth.clamp(normalized * 1.34F, 0.0F, 1.0F);
            case LAVA -> Mth.clamp(normalized * 1.02F, 0.0F, 1.0F);
            case SOUL_CAMPFIRE, CAMPFIRE -> normalized * 0.78F;
            case SOUL_FIRE, FIRE -> normalized * 0.72F;
            case ACHERONITE_BLOCK -> normalized * 0.58F;
            default -> normalized * 0.45F;
        };
    }

    private static void clearThermalShaderFields(PostChain currentEffect) {
        for (int i = 0; i < MAX_SHADER_FIELDS; i++) {
            currentEffect.setUniform("HeatField" + i + "X", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Y", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Radius", 0.0F);
            currentEffect.setUniform("HeatField" + i + "Intensity", 0.0F);
        }
        for (int i = 0; i < MAX_COLD_FIELDS; i++) {
            currentEffect.setUniform("ColdField" + i + "X", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Y", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Radius", 0.0F);
            currentEffect.setUniform("ColdField" + i + "Intensity", 0.0F);
        }
    }

    private static List<ColdAnchor> collectColdAnchors(Minecraft mc) {
        List<ColdAnchor> anchors = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int dy = -12; dy <= 12; dy++) {
            int y = playerPos.getY() + dy;
            if (y < mc.level.getMinBuildHeight() || y >= mc.level.getMaxBuildHeight()) {
                continue;
            }

            for (int dz = -24; dz <= 24; dz++) {
                for (int dx = -24; dx <= 24; dx++) {
                    mutablePos.set(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    if (!mc.level.hasChunkAt(mutablePos)) {
                        continue;
                    }

                    var state = mc.level.getBlockState(mutablePos);
                    if (!state.is(ModBlocks.ACHERONITE_CRYSTAL.get())) {
                        continue;
                    }

                    float strength = switch (state.getValue(AcheroniteCrystalBlock.AGE)) {
                        case 0 -> 0.30F;
                        case 1 -> 0.48F;
                        case 2 -> 0.72F;
                        default -> 1.0F;
                    };
                    addColdAnchor(anchors, mutablePos, strength);
                }
            }
        }

        anchors.sort((left, right) -> Float.compare(right.strength(), left.strength()));
        if (anchors.size() > MAX_COLD_FIELDS) {
            return new ArrayList<>(anchors.subList(0, MAX_COLD_FIELDS));
        }
        return anchors;
    }

    private static void addColdAnchor(List<ColdAnchor> anchors, BlockPos pos, float strength) {
        int clusterRadius = 6;
        int clusterRadiusSqr = clusterRadius * clusterRadius;
        ColdAnchor candidate = new ColdAnchor(pos.immutable(), strength);

        for (int i = 0; i < anchors.size(); i++) {
            ColdAnchor existing = anchors.get(i);
            if (existing.pos().distSqr(pos) <= clusterRadiusSqr) {
                if (candidate.strength() > existing.strength()) {
                    anchors.set(i, candidate);
                }
                return;
            }
        }

        anchors.add(candidate);
    }

    private static List<ShaderField> collectColdShaderFields(Minecraft mc, float partialTick) {
        List<ShaderField> fields = new ArrayList<>();

        Vec3 eyePos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 forward = mc.player.getViewVector(partialTick).normalize();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        if (right.lengthSqr() < 1.0E-5D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
        float tanHalfFov = (float) Math.tan(Math.toRadians(mc.options.fov().get() * 0.5D));

        for (ColdAnchor anchor : cachedColdAnchors) {
            Vec3 toSource = new Vec3(
                    anchor.pos().getX() + 0.5D,
                    anchor.pos().getY() + 0.8D,
                    anchor.pos().getZ() + 0.5D
            ).subtract(eyePos);

            double forwardDist = toSource.dot(forward);
            if (forwardDist <= 0.12D) {
                continue;
            }

            double sideDist = toSource.dot(right);
            double upDist = toSource.dot(up);
            float xNdc = (float) (sideDist / (forwardDist * tanHalfFov * aspect));
            float yNdc = (float) (upDist / (forwardDist * tanHalfFov));
            if (Math.abs(xNdc) > 1.18F || Math.abs(yNdc) > 1.18F) {
                continue;
            }

            float uvX = xNdc * 0.5F + 0.5F;
            float uvY = 0.5F - yNdc * 0.5F;
            float distanceBlocks = (float) Math.sqrt(mc.player.distanceToSqr(
                    anchor.pos().getX() + 0.5D,
                    anchor.pos().getY() + 0.5D,
                    anchor.pos().getZ() + 0.5D
            ));
            float distanceAttenuation = Mth.clamp(1.08F - distanceBlocks / 42.0F, 0.34F, 1.0F);
            float radius = Mth.lerp(anchor.strength(), 0.050F, 0.094F) * distanceAttenuation;
            float intensity = Mth.lerp(anchor.strength(), 0.016F, 0.050F) * distanceAttenuation;
            float score = anchor.strength() / Math.max(0.6F, distanceBlocks / 10.0F);
            fields.add(new ShaderField(uvX, uvY, radius, intensity, score));
        }

        fields.sort((left, rightField) -> Float.compare(rightField.score(), left.score()));
        if (fields.size() > MAX_COLD_FIELDS) {
            return new ArrayList<>(fields.subList(0, MAX_COLD_FIELDS));
        }
        return fields;
    }

    private record ShaderField(float x, float y, float radius, float intensity, float score) {}
    private record ColdAnchor(BlockPos pos, float strength) {}
}
