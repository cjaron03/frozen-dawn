package com.frozendawn.phase;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PhaseOneWeatherState;
import com.frozendawn.event.WeatherHandler;
import com.frozendawn.gametest.GameTestTemplates;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhaseOneWeatherGameTest {
    private static void check(GameTestHelper h, Runnable body) {
        var l=h.getLevel();var data=(ServerLevelData)l.getLevelData();
        var old=PhaseOneWeatherState.get(l);var saved=old.save(new CompoundTag(),l.registryAccess());
        long clock=l.getGameTime();int clear=data.getClearWeatherTime(),rain=data.getRainTime(),thunder=data.getThunderTime();
        boolean raining=data.isRaining(),thundering=data.isThundering();float rainLevel=l.getRainLevel(1),thunderLevel=l.getThunderLevel(1);
        boolean cycling=l.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        l.getDataStorage().set(PhaseOneWeatherState.NAME,new PhaseOneWeatherState());
        l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true,l.getServer());
        l.setWeatherParameters(100000,0,false,false);l.setRainLevel(0);l.setThunderLevel(0);
        try { body.run();h.succeed(); }
        catch(Exception e){h.fail(e.toString());}
        finally {
            l.getDataStorage().set(PhaseOneWeatherState.NAME,PhaseOneWeatherState.load(saved,l.registryAccess()));
            data.setGameTime(clock);data.setClearWeatherTime(clear);data.setRainTime(rain);data.setThunderTime(thunder);
            data.setRaining(raining);data.setThundering(thundering);l.setRainLevel(rainLevel);l.setThunderLevel(thunderLevel);
            l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(cycling,l.getServer());
        }
    }
    @GameTest(template=GameTestTemplates.EMPTY,timeoutTicks=100)
    public static void phaseOneWeatherReloadKeepsRemainingWindowAndNoDuplicateTick(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var data=(ServerLevelData)l.getLevelData();
        WeatherHandler.tick(l,1,.057F);var state=PhaseOneWeatherState.get(l);int initial=state.remainingTicks();
        h.assertTrue(initial>=9000&&initial<=14400,"Early clear window lasts many minutes");
        WeatherHandler.tick(l,1,.057F);h.assertTrue(state.remainingTicks()==initial,"Same-tick call cannot shorten the window");
        data.setGameTime(l.getGameTime()+1);WeatherHandler.tick(l,1,.057F);
        var nbt=state.save(new CompoundTag(),l.registryAccess());int remaining=state.remainingTicks();long sequence=state.sequence();
        l.getDataStorage().set(PhaseOneWeatherState.NAME,PhaseOneWeatherState.load(nbt,l.registryAccess()));
        data.setGameTime(l.getGameTime()+1);WeatherHandler.tick(l,1,.057F);state=PhaseOneWeatherState.get(l);
        h.assertTrue(state.remainingTicks()==remaining-1&&state.sequence()==sequence,"Reload resumes exactly, without a new lottery or offline time");
    });}
    @GameTest(template=GameTestTemplates.EMPTY,timeoutTicks=100)
    public static void phaseOneWeatherUsesFlagsThroughRainFadeAndReturnsToClear(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var data=(ServerLevelData)l.getLevelData();
        WeatherHandler.tick(l,1,.115F);var nbt=PhaseOneWeatherState.get(l).save(new CompoundTag(),l.registryAccess());
        nbt.putInt("remainingTicks",1);l.getDataStorage().set(PhaseOneWeatherState.NAME,PhaseOneWeatherState.load(nbt,l.registryAccess()));
        data.setGameTime(l.getGameTime()+1);WeatherHandler.tick(l,1,.115F);long sequence=PhaseOneWeatherState.get(l).sequence();
        h.assertTrue(data.isRaining()&&l.getRainLevel(1)==0,"Rain is requested before the visual fade starts");
        for(int i=0;i<30;i++){data.setGameTime(l.getGameTime()+1);WeatherHandler.tick(l,1,.115F);}
        h.assertTrue(data.isRaining()&&PhaseOneWeatherState.get(l).sequence()==sequence,"A visual fade cannot cancel or resample rain");
        nbt=PhaseOneWeatherState.get(l).save(new CompoundTag(),l.registryAccess());nbt.putInt("remainingTicks",1);
        l.getDataStorage().set(PhaseOneWeatherState.NAME,PhaseOneWeatherState.load(nbt,l.registryAccess()));
        data.setGameTime(l.getGameTime()+1);WeatherHandler.tick(l,1,.115F);
        h.assertTrue(!data.isRaining()&&!data.isThundering(),"Rain window finishes with a clear interval");
    });}
    @GameTest(template=GameTestTemplates.EMPTY,timeoutTicks=100)
    public static void phaseOneWeatherHonorsManualHoldAndLaterPhaseLocks(GameTestHelper h){check(h,()->{
        var l=h.getLevel();var data=(ServerLevelData)l.getLevelData();
        WeatherHandler.tick(l,1,.057F);int remaining=PhaseOneWeatherState.get(l).remainingTicks();
        l.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,l.getServer());
        data.setGameTime(l.getGameTime()+1);WeatherHandler.tick(l,1,.057F);
        h.assertTrue(PhaseOneWeatherState.get(l).remainingTicks()==remaining,"Manual weather hold pauses the cycle");
        l.setWeatherParameters(0,10000,true,true);l.setRainLevel(1);l.setThunderLevel(1);
        WeatherHandler.tick(l,2,.15F);
        h.assertTrue(data.isRaining()&&!data.isThundering()&&!PhaseOneWeatherState.get(l).active(),"Phase 2 keeps rain and removes leftover rare thunder");
        WeatherHandler.tick(l,3,.25F);h.assertTrue(data.isRaining()&&data.isThundering(),"Phase 3 storm policy still applies");
        WeatherHandler.tick(l,6,.75F);h.assertTrue(!data.isRaining()&&!data.isThundering(),"Atmospheric thinning still clears weather");
    });}
    @GameTest(template=GameTestTemplates.EMPTY,timeoutTicks=100)
    public static void phaseOneReviewFunctionsParseAndBreathResourcesExist(GameTestHelper h){
        var server=h.getLevel().getServer();
        for(String name:java.util.List.of("controls","setup","read","early","middle","late","clear","rain","edge","natural","breath_warm","breath_cold"))
            h.assertTrue(server.getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("phase1_check",name)).isPresent(),"Parser loads review control "+name);
        h.assertTrue(com.frozendawn.init.ModParticles.COLD_BREATH.get()!=null,"Breath type registers on a dedicated server without client class loading");
        h.succeed();
    }
}
