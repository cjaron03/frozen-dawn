package com.frozendawn.labbridge;

import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.blind.BlindVibrationPropagation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Fixed, guarded sensor bench. No arbitrary coordinates, signals, resets or release-jar entry point. */
public final class LabBlindVibration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private LabBlindVibration() { }
    public static int sample(CommandSourceStack source) {
        boolean allowed = LabBlindReplay.allowed(source,"vibration_connected") || LabBlindReplay.allowed(source,"vibration_disconnected");
        if (!allowed || !(source.getEntity() instanceof ServerPlayer player) || !player.isCreative()
                || !player.getTags().contains("blind_lab_player")) return 0;
        var board=source.getServer().getScoreboard();var objective=board.getObjective("blind_lab");
        if(objective==null) return 0;
        var stage=board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#stage"),objective);
        var timer=board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#timer"),objective);
        var last=board.getPlayerScoreInfo(ScoreHolder.forNameOnly("#last_impulse"),objective);
        if(stage==null||stage.value()!=2||timer==null||last==null||last.value()>=timer.value()
                ||(timer.value()!=100&&timer.value()!=220&&timer.value()!=340)) return 0;
        var level=player.serverLevel();var sourceBlock=new BlockPos(21,-45,5);
        if(!level.hasChunkAt(sourceBlock)||!level.hasChunkAt(new BlockPos(9,-45,5))) return 0;
        var actors=level.getEntitiesOfClass(ArchitectEntity.class,new AABB(0,-49,0,31,-37,11));
        if(actors.size()!=1) return 0;
        var actor=actors.getFirst();
        if(!actor.isBlindArchitect()||!actor.isNoAi()||!actor.getTags().contains("blind_lab_actor")
                ||actor.position().distanceToSqr(new Vec3(9.5,-44,5.5))>.0001
                ||!level.getBlockState(sourceBlock).is(Blocks.STONE)) return 0;
        // Consume the one-shot timer before effects. A partial failure must never be replayed.
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#last_impulse"),objective).set(timer.value());
        var impulse=BlindVibrationPropagation.block(BlindVibrationPropagation.Kind.TOOL,sourceBlock,
                level.getBlockState(sourceBlock),sourceBlock.getCenter(),12,level.getGameTime());
        long start=System.nanoTime();
        var result=BlindVibrationPropagation.receive(level,impulse,actor,512);
        long elapsed=System.nanoTime()-start;
        boolean validNegative=result.outcome()==BlindVibrationPropagation.Outcome.NO_PATH;
        int outcome=result.detected()?1:validNegative?0:2;
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#samples"),objective).add(1);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#detected"),objective).add(result.detected()?1:0);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#inconclusive"),objective).add(outcome==2?1:0);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#outcome"),objective).set(outcome);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#strength_milli"),objective).set((int)Math.round(result.received()*1000));
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#work"),objective).set(result.work());
        level.setBlock(new BlockPos(21,-40,5),Blocks.SEA_LANTERN.defaultBlockState(),3);
        level.setBlock(new BlockPos(9,-40,5),(outcome==1?Blocks.LIME_CONCRETE:outcome==0?Blocks.BLACK_CONCRETE:Blocks.ORANGE_CONCRETE).defaultBlockState(),3);
        // Presentation only. The held actor runs no hearing/echo/controller acquisition here.
        level.playSound(null,sourceBlock,SoundEvents.ANVIL_HIT,SoundSource.BLOCKS,.6F,1.2F);
        int pulse=timer.value()==100?1:timer.value()==220?2:3;
        String message="Impulse "+pulse+"/3: "+(outcome==1?"BA received vibration.":outcome==0?"No vibration reached BA.":"Test inconclusive.");
        var notice=Component.literal(message).withStyle(outcome==1?net.minecraft.ChatFormatting.GREEN:
                outcome==0?net.minecraft.ChatFormatting.WHITE:net.minecraft.ChatFormatting.GOLD);
        player.displayClientMessage(notice,false);
        player.displayClientMessage(notice,true);
        var record=new com.google.gson.JsonObject();
        record.addProperty("world",source.getServer().getWorldData().getLevelName());record.addProperty("actor",actor.getUUID().toString());
        record.addProperty("gameTime",level.getGameTime());record.addProperty("timer",timer.value());record.addProperty("nanos",elapsed);
        record.addProperty("receiverOnGround",actor.onGround());
        record.add("measurement",new com.google.gson.Gson().toJsonTree(result));
        LOGGER.info("[BlindVibrationBench] {}",record);
        return 1;
    }
}
