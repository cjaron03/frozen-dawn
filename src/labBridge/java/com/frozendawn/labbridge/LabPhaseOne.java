package com.frozendawn.labbridge;

import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.PhaseOneWeatherState;
import com.frozendawn.phase.PhaseOneWeatherPolicy;
import com.frozendawn.barometer.PhaseBarometerForecasts;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Exact-world test staging only; absent from release jars. */
@EventBusSubscriber(modid="frozendawn")
public final class LabPhaseOne {
    private LabPhaseOne() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        if(FMLEnvironment.production)return;
        var command=Commands.literal("phase1");
        for(String name:java.util.List.of("early","middle","late","edge","status"))
            command.then(Commands.literal(name).executes(c->run(c.getSource(),name)));
        event.getDispatcher().register(Commands.literal("fdlab").requires(s->s.hasPermission(2)).then(command));
    }
    private static int run(CommandSourceStack source,String name) {
        if(!Boolean.getBoolean("frozendawn.labBridge")
                ||!source.getServer().getWorldData().getLevelName().equals("Phase 1 Weather Check")
                ||!(source.getEntity() instanceof ServerPlayer player)||!player.getTags().contains("phase1_lab"))return 0;
        var level=source.getLevel();var apocalypse=ApocalypseState.get(source.getServer());
        if(java.util.Set.of("early","middle","late").contains(name)) {
            float local=name.equals("early")?.10F:name.equals("middle")?.50F:.93F;
            long ticks=Math.round((.05D+.07D*local)*apocalypse.getTotalDays()*24000L);
            apocalypse.setApocalypseTicks(ticks,source.getServer());
            level.setDayTime((ticks/24000L)*24000L+6000);
        } else if(name.equals("edge")) {
            if(apocalypse.getPhase()!=1)return 0;
            var state=PhaseOneWeatherState.get(level);
            if(!state.active())return 0;
            var tag=state.save(new CompoundTag(),level.registryAccess());tag.putInt("remainingTicks",60);
            level.getDataStorage().set(PhaseOneWeatherState.NAME,PhaseOneWeatherState.load(tag,level.registryAccess()));
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true,source.getServer());
        }
        var state=PhaseOneWeatherState.get(level);var forecast=PhaseBarometerForecasts.evaluate(apocalypse.getPhase(),apocalypse.getPreciseProgress());
        player.sendSystemMessage(Component.literal("Phase1: phase="+apocalypse.getPhase()+" progress="+apocalypse.getPreciseProgress()
                +" dayTime="+level.getDayTime()+" gameTime="+level.getGameTime()+" weatherCycle="+level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE)
                +" rainFlag="+level.getLevelData().isRaining()+" thunderFlag="+level.getLevelData().isThundering()
                +" rain="+level.getRainLevel(1)+" thunder="+level.getThunderLevel(1)+" active="+state.active()+" remaining="+state.remainingTicks()
                +" sequence="+state.sequence()+" windNominal="+PhaseOneWeatherPolicy.windVolume(apocalypse.getPreciseProgress(),level.getRainLevel(1),level.getGameTime())
                +" forecast="+forecast.warning().displayName()+" advice="+forecast.warning().preparationAdvice()));
        return 1;
    }
}
