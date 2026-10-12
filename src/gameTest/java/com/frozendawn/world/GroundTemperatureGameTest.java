package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.ConfigPresets;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.config.DifficultyPresetManager;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.gametest.GameTestReporting;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroundTemperatureGameTest {
    @BeforeBatch(batch="ground_temperature")
    public static void reporting(net.minecraft.server.level.ServerLevel level) {
        GameTestReporting.installReporter(level);
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="ground_temperature", timeoutTicks=40)
    public static void thermalGroundQueriesPreserveShelterAndLoadedOnlyPath(GameTestHelper h) {
        var level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(10, 2, 10));
        var pos = new BlockPos(origin.getX(), 0, origin.getZ());
        for (int dx=-3; dx<=3; dx++) for (int dy=-1; dy<=6; dy++) for (int dz=-3; dz<=3; dz++) {
            level.setBlock(pos.offset(dx,dy,dz), Blocks.AIR.defaultBlockState(), 2);
        }
        int day=100, total=100;
        float expected=TemperatureManager.getBackgroundTemperature(0,day,total);
        h.assertTrue(Math.abs(expected-TemperatureManager.getTemperatureAt(level,pos,day,total))<.01,
                "Unheated cave uses the new ground background");
        h.assertTrue(Math.abs(expected-TemperatureManager.getLoadedTemperatureAt(level,pos,day,total))<.01,
                "Catch-up samples the same loaded-only background");
        level.setBlock(pos.above(2),Blocks.STONE.defaultBlockState(),2);
        h.assertTrue(Math.abs(expected+5-TemperatureManager.getTemperatureAt(level,pos,day,total))<.01,
                "Existing roof shelter adds five degrees to the ground baseline");
        level.setBlock(pos.above(2),Blocks.AIR.defaultBlockState(),2);
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="ground_temperature", timeoutTicks=40)
    public static void thermalSurfaceAltitudeAndPhaseCurveRemainUnchanged(GameTestHelper h) {
        for (int y : new int[]{64,65,128,256}) for (int day : new int[]{0,46,60,85,100}) {
            float expected=PhaseManager.getTemperatureOffset(day,100)
                    + PhaseManager.getDepthModifier(y)*FrozenDawnConfig.GEOTHERMAL_STRENGTH.get().floatValue();
            h.assertTrue(Math.abs(expected-TemperatureManager.getBackgroundTemperature(y,day,100))<.0001,
                    "Surface/altitude behavior is unchanged at Y="+y+", day="+day);
        }
        h.assertTrue(PhaseManager.getTemperatureOffset(60,100)
                        == com.frozendawn.phase.SurfaceTemperatureCurve.temperature(.60f,
                        FrozenDawnConfig.BASE_PHASE5_TEMP.get()/-120f),
                "Public phase offset uses the canonical curve");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE, batch="ground_temperature", timeoutTicks=40)
    public static void thermalPresetDiffusivityIsAppliedAndPersisted(GameTestHelper h) {
        double oldDiffusivity=FrozenDawnConfig.GROUND_DIFFUSIVITY.get();
        int oldDays=FrozenDawnConfig.TOTAL_DAYS.get(),oldBase=FrozenDawnConfig.BASE_PHASE5_TEMP.get();
        double oldGeo=FrozenDawnConfig.GEOTHERMAL_STRENGTH.get(),oldHeat=FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.get();
        double oldSnow=FrozenDawnConfig.SNOW_ACCUMULATION_RATE.get(),oldSanity=FrozenDawnConfig.SANITY_SPEED_MULTIPLIER.get();
        int oldBroadcast=FrozenDawnConfig.BROADCAST_TICKS.get();
        double oldMobs=FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get();
        try {
            for (var preset : ConfigPresets.values()) {
                preset.apply();
                h.assertTrue(ConfigPresets.detectCurrentPreset()==preset,"Preset identification includes diffusivity");
                double expected=GroundTemperatureModel.temperature(-64,1,preset.geothermalStrength,
                        preset.basePhase5Temp/-120f,preset.groundDiffusivity);
                h.assertTrue(Math.abs(expected-TemperatureManager.getBackgroundTemperature(-64,100,100))<.001,
                        "Runtime ground query uses preset scale, geothermal strength and diffusivity");
            }
            DifficultyPresetManager.persistConfigOverrides();
            var path=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("frozendawn-common.toml");
            try(var config=com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(path).sync().build()) {
                config.load();
                Number saved=config.get("temperature.groundDiffusivity");
                h.assertTrue(saved!=null&&saved.doubleValue()==12000,"Brutal diffusivity survives config persistence");
            }
            h.succeed();
        } finally {
            FrozenDawnConfig.GROUND_DIFFUSIVITY.set(oldDiffusivity);
            FrozenDawnConfig.TOTAL_DAYS.set(oldDays);FrozenDawnConfig.BASE_PHASE5_TEMP.set(oldBase);
            FrozenDawnConfig.GEOTHERMAL_STRENGTH.set(oldGeo);FrozenDawnConfig.HEAT_SOURCE_MULTIPLIER.set(oldHeat);
            FrozenDawnConfig.SNOW_ACCUMULATION_RATE.set(oldSnow);FrozenDawnConfig.SANITY_SPEED_MULTIPLIER.set(oldSanity);
            FrozenDawnConfig.BROADCAST_TICKS.set(oldBroadcast);FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.set(oldMobs);
            DifficultyPresetManager.persistConfigOverrides();
        }
    }
}
