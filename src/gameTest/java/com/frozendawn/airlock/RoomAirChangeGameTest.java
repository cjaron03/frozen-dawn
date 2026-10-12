package com.frozendawn.airlock;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.RoomAirState;
import com.frozendawn.gametest.*;
import com.frozendawn.world.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoomAirChangeGameTest {
    @BeforeBatch(batch="room_air_change")
    public static void report(net.minecraft.server.level.ServerLevel level) { GameTestReporting.installReporter(level); }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="room_air_change",timeoutTicks=100)
    public static void roomChangeRealControllerCyclesAndEmergencyValvePublishAirOnly(GameTestHelper h) {
        AirlockGameTest.check(h,()->{
            var l=h.getLevel();var s=AirlockGameTest.scene(h);var c=s.chamber();
            var initial=RoomAtmosphere.view(l,s.center());
            var events=new ArrayList<RoomChangeEvent>();Consumer<RoomChangeEvent> capture=e->{if(e.level()==l)events.add(e);};
            NeoForge.EVENT_BUS.addListener(capture);
            try {
                h.assertTrue(AirlockManager.start(l,c).equals("depressurizing"),"Real controller starts closed-chamber evacuation");
                assertAir(h,events,true,c.cells,initial.id());
                h.assertTrue(RoomAirState.get(l).isDepleted(c.cells),"Authority is depleted before subscribers run");
                AirlockGameTest.finish(l,c);events.clear();c.gas.fill(c.gas.capacity());
                h.assertTrue(AirlockManager.start(l,c).equals("pressurizing"),"Measured reserve starts refill");
                h.assertTrue(events.isEmpty(),"Starting pump does not invent completed air recovery");
                AirlockGameTest.finish(l,c);assertAir(h,events,false,c.cells,initial.id());
                h.assertTrue(RoomAtmosphere.hasAir(l,s.center()),"Completed pump restores actual air");
                events.clear();h.assertTrue(AirlockManager.emergencyVent(l,s.panel(),Direction.SOUTH),"Actual emergency valve resolves chamber");
                assertAir(h,events,true,c.cells,initial.id());
                h.assertTrue(RoomAtmosphere.view(l,s.center()).id()==initial.id(),"Airlock air changes do not recreate room identity");
            } finally {NeoForge.EVENT_BUS.unregister(capture);}
        });
    }
    private static void assertAir(GameTestHelper h,List<RoomChangeEvent> events,boolean depleted,
            Set<net.minecraft.core.BlockPos> cells,long id) {
        h.assertTrue(events.size()==1&&events.getFirst().change() instanceof RoomChangeEvent.AirStateChange air
                &&air.depleted()==depleted&&air.cells().equals(cells)&&air.roomIds().equals(Set.of(id)),
                "Exactly one typed air transition, without geometry or wall-material notifications: " + events.stream().map(e->e.type().name()).toList());
    }
}
