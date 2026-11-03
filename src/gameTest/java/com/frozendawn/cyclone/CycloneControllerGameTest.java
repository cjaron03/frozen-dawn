package com.frozendawn.cyclone;
import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.GameTestTemplates;
import com.frozendawn.network.CycloneSyncPayload;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CycloneControllerGameTest {
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone",timeoutTicks=100)
    public static void cycloneThermalReplayFunctionsLoadWithNativeParser(GameTestHelper h) {
        for(var name:java.util.List.of("load","bootstrap","tick","setup","controls","more","peak","end","read","outside","stone","insulated","metal","refill","survival","creative","moderate","severe","catastrophic","eva","heavy"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cyclone_thermal",name)).isPresent(),"Native parser loaded thermal replay function "+name);
        h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone",timeoutTicks=100)
    public static void barometerCycloneAlarmCooldownSurvivesBlockEntityReload(GameTestHelper h) {
        var registries=h.getLevel().registryAccess();var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));
        var state=com.frozendawn.init.ModBlocks.PHASE_BAROMETER.get().defaultBlockState();
        var alarm=new com.frozendawn.barometer.CycloneBarometerAlarm();
        var forecast=new CycloneForecast.Reading(true,990,-10,CycloneSeverity.CATASTROPHIC,120,CycloneStage.BOMBOGENESIS);
        alarm.poll(forecast,42,1000);var seed=new CompoundTag();alarm.save(seed);
        var instrument=new com.frozendawn.block.PhaseBarometerBlockEntity(pos,state);instrument.loadWithComponents(seed,registries);
        var saved=instrument.saveWithFullMetadata(registries);
        var copy=new com.frozendawn.block.PhaseBarometerBlockEntity(pos,state);copy.loadWithComponents(saved,registries);
        var finalTag=copy.saveWithFullMetadata(registries);var restored=new com.frozendawn.barometer.CycloneBarometerAlarm();restored.load(finalTag);
        h.assertTrue(restored.poll(forecast,42,1199)==com.frozendawn.barometer.CycloneBarometerAlarm.Cue.NONE,"Block entity reload must not replay alarm early");
        h.assertTrue(restored.poll(forecast,42,1200)==com.frozendawn.barometer.CycloneBarometerAlarm.Cue.CATASTROPHIC,"Exact catastrophic reminder survives real block entity persistence");
        h.assertTrue(com.frozendawn.init.ModSounds.BAROMETER_CYCLONE_WARNING.get().getLocation().getPath().equals("block.barometer.cyclone_warning"),"Normal warning registered");
        h.assertTrue(com.frozendawn.init.ModSounds.BAROMETER_CYCLONE_CATASTROPHIC.get().getLocation().getPath().equals("block.barometer.cyclone_catastrophic"),"Catastrophic warning registered");
        h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone",timeoutTicks=100)
    public static void cycloneSavedTrackTimelineWarningsAndPlanSurviveReload(GameTestHelper h) {
        var data=new CycloneSavedData();var id=UUID.randomUUID();
        data.setPlan(56*24000L,CyclonePlanner.plan(56*24000L,72*24000L,42,4,true,.05));
        var s=CycloneStorm.roll(43,CycloneSeverity.SEVERE,12,-30,1000,id,1f);
        data.startPlanned(s,60*24000L);h.assertTrue(data.warnOnce(id),"first warning");
        var restored=CycloneSavedData.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.active().orElseThrow().equals(s),"Exact track, target and time origin survive NBT");
        h.assertTrue(restored.plan().equals(data.plan())&&restored.nextIndex()==1,"Postponed schedule and consumed index survive");
        h.assertTrue(!restored.warnOnce(id),"Reload must not repeat an ear-pop warning");
        long now=s.startGameTime()+s.offset(CycloneStage.PEAK)+500;
        h.assertTrue(CycloneField.sample(s,12,-30,now).equals(CycloneField.sample(restored.active().orElseThrow(),12,-30,now)),"Stage and pressure resume exactly");
        h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone",timeoutTicks=100)
    public static void cycloneWireSnapshotAndCollapseRoundTrip(GameTestHelper h) {
        var s=CycloneStorm.roll(123,CycloneSeverity.CATASTROPHIC,4,8,100,UUID.randomUUID(),2).withCollapse(200);
        var b=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            var payload=new CycloneSyncPayload(Optional.of(s),true,210,.35f,new CycloneVisuals.Peaks(27,18,9));
            CycloneSyncPayload.STREAM_CODEC.encode(b,payload);
            h.assertTrue(payload.equals(CycloneSyncPayload.STREAM_CODEC.decode(b)),"Non-default server visibility, suppression, time and active storm survive real wire codec");
            CycloneSyncPayload.STREAM_CODEC.encode(b,new CycloneSyncPayload(Optional.empty(),false,300));
            var empty=CycloneSyncPayload.STREAM_CODEC.decode(b);
            h.assertTrue(empty.storm().isEmpty() && empty.visibility().equals(CycloneVisuals.Peaks.DEFAULT),"Empty default snapshot clears stale storm and custom visibility");
        } finally {b.release();}
        h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone",timeoutTicks=100)
    public static void cycloneConfiguredBalanceReachesTemperatureAndVisibilitySnapshot(GameTestHelper h) {
        var level=h.getLevel().getServer().overworld();
        var registries=level.registryAccess();
        var saved=CycloneSavedData.get(level.getServer()).save(new CompoundTag(),registries);
        var settings=new net.neoforged.neoforge.common.ModConfigSpec.DoubleValue[]{
                com.frozendawn.config.FrozenDawnConfig.CYCLONE_PEAK_OFFSET_MODERATE,
                com.frozendawn.config.FrozenDawnConfig.CYCLONE_PEAK_OFFSET_SEVERE,
                com.frozendawn.config.FrozenDawnConfig.CYCLONE_PEAK_OFFSET_CATASTROPHIC,
                com.frozendawn.config.FrozenDawnConfig.CYCLONE_VISIBILITY_MODERATE,
                com.frozendawn.config.FrozenDawnConfig.CYCLONE_VISIBILITY_SEVERE,
                com.frozendawn.config.FrozenDawnConfig.CYCLONE_VISIBILITY_CATASTROPHIC};
        double[] previous=java.util.Arrays.stream(settings).mapToDouble(value->value.get()).toArray();
        double[] custom={-3,-11,-42,27,18,9};
        var data=new CycloneSavedData();level.getDataStorage().set("frozendawn_cyclones",data);
        try {
            for(int i=0;i<settings.length;i++)settings[i].set(custom[i]);
            var peaks=CycloneConfigValues.visibility();
            var pos=new net.minecraft.core.BlockPos(20,64,30);
            var phase=com.frozendawn.data.ApocalypseState.get(level.getServer());
            float baseline=com.frozendawn.world.TemperatureManager.getBackgroundTemperature(64,phase.getCurrentDay(),phase.getTotalDays());
            for(var severity:CycloneSeverity.values()) {
                var storm=CycloneStorm.roll(321,severity,20.5,30.5,level.getGameTime(),UUID.randomUUID(),0)
                        .atStage(CycloneStage.PEAK,level.getGameTime(),.5f);
                data.startForced(storm);
                var sample=CycloneController.sampleAt(level,20.5,30.5);
                h.assertTrue(Math.abs(sample.temperatureOffset()-custom[severity.ordinal()])<1e-5,"Configured surface cold reaches actual server sample: "+severity);
                float temperature=com.frozendawn.world.TemperatureManager.getBackgroundTemperature(level,pos,phase.getCurrentDay(),phase.getTotalDays());
                h.assertTrue(Math.abs(temperature-baseline-custom[severity.ordinal()])<1e-4,"Configured cold reaches production temperature path: "+severity);
                h.assertTrue(peaks.forSeverity(severity)==custom[3+severity.ordinal()],"All three non-default visibility values reach server snapshot");
                h.assertTrue(CycloneController.sampleAt(level,20.5,1000)==CycloneSample.NONE,"Outside track remains exact NONE");
                var deep=pos.atY(20);
                h.assertTrue(com.frozendawn.world.TemperatureManager.getBackgroundTemperature(level,deep,phase.getCurrentDay(),phase.getTotalDays())
                        ==com.frozendawn.world.TemperatureManager.getBackgroundTemperature(20,phase.getCurrentDay(),phase.getTotalDays()),"Balance overrides do not alter deep ground");
                data.replaceActive(storm.atStage(CycloneStage.FALSE_CALM,level.getGameTime(),.5f));
                h.assertTrue(CycloneController.sampleAt(level,20.5,30.5).temperatureOffset()==15,"Peak overrides preserve false-calm warmth");
            }
        } finally {
            for(int i=0;i<settings.length;i++)settings[i].set(previous[i]);
            level.getDataStorage().set("frozendawn_cyclones",CycloneSavedData.load(saved,registries));
        }
        h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="cyclone",timeoutTicks=100)
    public static void cycloneCommandsFunctionsAndForecastIsolation(GameTestHelper h) {
        var server=h.getLevel().getServer();var dispatcher=server.getCommands().getDispatcher();
        var source=server.createCommandSourceStack().withPermission(2);
        for(String command:List.of("fd cyclone status","fd cyclone plan","fd cyclone stage peak",
                "fd cyclone start moderate @p 90","fd cyclone stage-index","fd cyclone end")) {
            var parsed=dispatcher.parse(command,source);
            h.assertTrue(parsed.getReader().getRemaining().isEmpty()&&parsed.getExceptions().isEmpty(),"Production command parses: "+command);
        }
        for(var name:List.of("load","bootstrap","tick","setup","controls","display","read","moderate","severe","catastrophic",
                "false_calm","bombogenesis","arrival","peak","dissipation","aftermath","end","center","downstream","outside","night_on","night_off"))
            h.assertTrue(server.getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cyclone_check",name)).isPresent(),"Native parser loaded replay function "+name);
        var s=CycloneStorm.roll(2,CycloneSeverity.SEVERE,0,0,0,UUID.randomUUID(),0);
        h.assertTrue(!CycloneForecast.read(s,0,900,100).visible(),"No warning outside the storm track");
        var data=new CycloneSavedData();data.setPlan(0,CyclonePlanner.plan(0,1000000,9,4,true,.05));data.startForced(s);
        h.assertTrue(data.nextIndex()==0,"Manual replay never consumes the natural schedule");
        data.end();h.assertTrue(data.active().isEmpty(),"Manual end clears active storm");
        h.succeed();
    }
}
