package com.frozendawn.labbridge.mixin;

import com.frozendawn.cyclone.CycloneDrift;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.world.BlockFreezer;
import com.frozendawn.world.SnowAccumulator;
import com.frozendawn.world.VegetationDecay;
import java.nio.file.Files;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Isolates the explicitly launched drift fixture; this entire source set is excluded from releases. */
@Mixin(value = WorldTickHandler.class, remap = false)
public final class LabDriftSnowMixin {
    @Unique private static boolean frozendawn$announced;
    @Unique private static boolean frozendawn$isolated(ServerLevel level) {
        if (!Boolean.getBoolean("frozendawn.labCycloneDrift") || net.neoforged.fml.loading.FMLEnvironment.production
                || !level.getServer().getWorldData().getLevelName().equals("Cyclone Drift Check")) return false;
        var root = level.getServer().getWorldPath(LevelResource.ROOT).normalize();
        boolean active = root.getFileName().toString().equals("Cyclone Drift Check")
                && Files.isRegularFile(root.resolve("cyclone-drift-lab.json"));
        if (active && !frozendawn$announced) {
            System.out.println("[CycloneDriftLab] Dedicated fixture: ordinary snowfall, freezing and vegetation decay paused; production cyclone drift active.");
            frozendawn$announced = true;
        }
        return active;
    }
    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lcom/frozendawn/world/SnowAccumulator;tick(Lnet/minecraft/server/level/ServerLevel;IF)V"), remap = false)
    private static void frozendawn$snow(ServerLevel level, int phase, float progress) {
        if (frozendawn$isolated(level)) CycloneDrift.tick(level, phase, progress);
        else SnowAccumulator.tick(level, phase, progress);
    }
    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lcom/frozendawn/world/BlockFreezer;tick(Lnet/minecraft/server/level/ServerLevel;IF)V"), remap = false)
    private static void frozendawn$freezing(ServerLevel level, int phase, float progress) {
        if (!frozendawn$isolated(level)) BlockFreezer.tick(level, phase, progress);
    }
    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lcom/frozendawn/world/VegetationDecay;tick(Lnet/minecraft/server/level/ServerLevel;I)V"), remap = false)
    private static void frozendawn$plants(ServerLevel level, int phase) {
        if (!frozendawn$isolated(level)) VegetationDecay.tick(level, phase);
    }
}
