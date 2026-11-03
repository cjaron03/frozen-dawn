package com.frozendawn.world;

import com.frozendawn.data.RoomIdentityState;
import com.frozendawn.event.WorldTickHandler;
import net.minecraft.core.*;
import net.minecraft.server.level.*;

/** Credits actual participants without discovering geometry or altering any gas/power state. */
public final class PowerAirAdvancements {
    public static final String BROWNOUT="in_space_no_one_can_hear_you_brown_out";
    public static final String UNLIMITED="unlimited_power";
    public static final String CANARY="canary_in_the_furnace";
    public static final String COW="holy_cow";
    private PowerAirAdvancements(){}
    public static void grant(ServerPlayer player,String advancement){
        if(!player.isSpectator())WorldTickHandler.grantAdvancement(player,advancement);
    }
    public static void emergencyLighting(ServerLevel level,BlockPos lamp){
        var identity=RoomIdentityState.get(level);
        for(var player:level.players()){
            if(player.isSpectator()||RoomAtmosphere.cachedSealAt(level,player.blockPosition())!=RoomAtmosphere.Seal.SEALED)continue;
            var room=identity.at(player.blockPosition());if(room==null)continue;
            for(var direction:Direction.values()){
                var adjacent=identity.at(lamp.relative(direction));
                if(adjacent!=null&&adjacent.id()==room.id()){grant(player,BROWNOUT);break;}
            }
        }
    }
}
