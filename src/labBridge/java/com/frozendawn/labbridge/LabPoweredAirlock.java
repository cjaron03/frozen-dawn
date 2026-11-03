package com.frozendawn.labbridge;

import com.frozendawn.block.*;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.airlock.AirlockManager;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** One-use construction in a fresh default-terrain lab only; never shipped. */
@EventBusSubscriber(modid="frozendawn")
public final class LabPoweredAirlock {
    public static final String WORLD="Powered Airlock Check";
    private static Path receipt(CommandSourceStack s){return s.getServer().getWorldPath(LevelResource.ROOT).resolve("powered-airlock-fixture.properties");}
    private static boolean allowed(CommandSourceStack s){return !FMLEnvironment.production&&s.getEntity() instanceof ServerPlayer p&&p.isCreative()&&s.getServer().getWorldData().getLevelName().equals(WORLD);}
    @SubscribeEvent public static void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("fdlab").requires(s->s.hasPermission(2))
            .then(Commands.literal("powered_airlock_setup").executes(c->prepare(c.getSource())))
            .then(Commands.literal("powered_airlock_cave").executes(c->travel(c.getSource(),"cave")))
            .then(Commands.literal("powered_airlock_surface").executes(c->travel(c.getSource(),"surface"))));
    }
    private static boolean caveSite(ServerLevel l,BlockPos c){
        if(!l.getBlockState(c.below()).isSolidRender(l,c.below()))return false;
        // Check the entire construction envelope before editing any generated block.
        for(int x=-3;x<=8;x++)for(int y=0;y<=3;y++)for(int z=-3;z<=3;z++)if(!l.getBlockState(c.offset(x,y,z)).isAir())return false;
        return !l.canSeeSky(c);
    }
    public static int prepare(CommandSourceStack s){
        if(!allowed(s)||Files.exists(receipt(s)))return 0;
        try {
            var l=s.getLevel();BlockPos cave=null;
            search:for(int x=-96;x<=96;x+=4)for(int z=-96;z<=96;z+=4){
                l.getChunk(x>>4,z>>4); // New disposable world generation; never production discovery.
                for(int y=-50;y<=-8;y+=2){var p=new BlockPos(x,y,z);if(l.getBlockState(p).isAir()&&caveSite(l,p)){cave=p;break search;}}
            }
            if(cave==null){s.sendFailure(Component.literal("No fitting generated cave found; preserve this world, do not carve a replacement."));return 0;}
            // Populate the scanner's bounded neighborhood in this fresh lab only.
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)l.getChunk((cave.getX()>>4)+dx,(cave.getZ()>>4)+dz);
            int surfaceY=0;
            for(int x=61;x<=73;x++)for(int z=-3;z<=3;z++)surfaceY=Math.max(surfaceY,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1);
            var surface=new BlockPos(64,surfaceY,0);
            var p=new Properties();p.setProperty("cave",Long.toString(cave.asLong()));p.setProperty("surface",Long.toString(surface.asLong()));
            p.setProperty("undergroundEnvelope","336 pre-existing generated air cells; no cave carved");
            p.setProperty("state","construction started; never replay setup");
            try(var out=Files.newOutputStream(receipt(s),StandardOpenOption.CREATE_NEW)){p.store(out,"Powered Airlock Check - natural terrain fixture");}
            // Explicit lab reservoir state: late-phase ambient cave vacuum, independent of scan budgets.
            com.frozendawn.world.RoomAtmosphere.recordExterior(l,cave.east(8));
            com.frozendawn.data.RoomAirState.get(l).evacuate(Set.of(cave.east(8)));
            build(l,cave);
            for(int x=-3;x<=7;x++)for(int z=-3;z<=3;z++){
                int ground=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,surface.getX()+x,surface.getZ()+z);
                for(int y=ground;y<surface.getY()-1;y++)l.setBlock(new BlockPos(surface.getX()+x,y,surface.getZ()+z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
            }
            build(l,surface);p.setProperty("state","ready");
            try(var out=Files.newOutputStream(receipt(s),StandardOpenOption.TRUNCATE_EXISTING)){p.store(out,"Both bases Phase 6 late");}
            return travel(s,"cave");
        }catch(Exception e){s.sendFailure(Component.literal("Fixture stopped: "+e.getMessage()+". Preserve this attempt."));return 0;}
    }
    private static void room(ServerLevel l,BlockPos c,int radius){
        for(int x=-radius;x<=radius;x++)for(int y=-1;y<=3;y++)for(int z=-radius;z<=radius;z++){
            boolean shell=Math.abs(x)==radius||Math.abs(z)==radius||y==-1||y==3;
            l.setBlock(c.offset(x,y,z),(shell?ModBlocks.INSULATED_GLASS.get():Blocks.AIR).defaultBlockState(),3);
        }
    }
    private static void build(ServerLevel l,BlockPos base){
        var c=base.east(5);room(l,base,3);room(l,c,2);
        for(var pos:List.of(base.east(3),base.east(7))){
            var door=ModBlocks.AIRLOCK_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST);
            l.setBlock(pos,door,3);l.setBlock(pos.above(),door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),3);
        }
        for(int x=7;x<=9;x++)for(int z=-1;z<=1;z++)l.setBlock(base.offset(x,-1,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
        var panel=c.north(2).above();l.setBlock(panel,ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.SOUTH),3);
        l.setBlock(base.offset(3,1,-1),ModBlocks.AIRLOCK_CONTROLLER.get().defaultBlockState().setValue(AirlockControllerBlock.FACING,Direction.EAST),3);
        l.setBlock(c.south(2).above(),ModBlocks.MANUAL_VENT_VALVE.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH),3);
        var core=base.offset(-1,0,1);l.setBlock(core,ModBlocks.GEOTHERMAL_CORE.get().defaultBlockState(),3);
        var be=(GeothermalCoreBlockEntity)l.getBlockEntity(core);be.setPowerShare(20);be.setHeatShare(0);
        l.setBlock(base.offset(-1,0,-1),ModBlocks.BATTERY_BANK.get().defaultBlockState(),3);
        l.setBlock(base.offset(0,1,-1),ModBlocks.POWER_RELAY.get().defaultBlockState(),3);
        l.setBlock(base.offset(1,0,1),ModBlocks.RESISTIVE_HEATER.get().defaultBlockState(),3);
        l.setBlock(base.offset(0,1,2),ModBlocks.POWER_MONITOR.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH),3);
        l.setBlock(base.offset(-1,0,2),ModBlocks.INDUCTION_FURNACE.get().defaultBlockState(),3);
        l.setBlock(base.offset(1,2,2),ModBlocks.PANEL_LIGHT.get().defaultBlockState(),3);
        l.setBlock(base.offset(-2,1,0),ModBlocks.THERMOSTAT.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,Direction.EAST),3);
        var chamber=AirlockManager.resolve(l,(AirlockControllerBlockEntity)l.getBlockEntity(panel));
        if(chamber==null||chamber.cells.size()!=27)throw new IllegalStateException("New fixture did not validate a 27-cell airlock");
    }
    public static int travel(CommandSourceStack s,String name){
        if(!allowed(s)||!Files.exists(receipt(s)))return 0;
        try{var p=new Properties();try(var in=Files.newInputStream(receipt(s))){p.load(in);}if(!"ready".equals(p.getProperty("state")))return 0;
            var pos=BlockPos.of(Long.parseLong(p.getProperty(name)));var player=(ServerPlayer)s.getEntity();player.teleportTo(s.getLevel(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),-90,0);
            s.sendSuccess(()->Component.literal((name.equals("cave")?"Natural cave":"Surface")+" base: bind Core -> Bank -> Relay -> controller beside inner door. Cycling: 2 kW; idle: 0."),false);return 1;
        }catch(Exception e){return 0;}
    }
}
