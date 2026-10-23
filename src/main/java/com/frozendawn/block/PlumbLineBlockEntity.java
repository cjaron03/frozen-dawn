package com.frozendawn.block;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.BlindArchitectEvents;
import com.frozendawn.entity.blind.BlindVibrationPropagation;
import com.frozendawn.gpr.*;
import com.frozendawn.gpr.PlumbLinePolicy.Setting;
import com.frozendawn.gpr.PlumbLineScan.Kind;
import com.frozendawn.init.*;
import com.frozendawn.network.PlumbLineReturnsPayload;
import com.frozendawn.power.PowerNetworks;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pulse state machine. A pulse draws its setting's power for ten seconds while the scan runs in bounded slices; losing
 * the full draw aborts it with no scan. The thump is heard at the start. Results go to players within 16 blocks.
 */
public final class PlumbLineBlockEntity extends BlockEntity implements MenuProvider {
    public static final int RESULT_NONE=0, RESULT_DONE=1, RESULT_ABORTED=2, RESULT_NO_POWER=3, RESULT_COOLING=4;
    /** Print outcome, shown beside the Print button; separate from the pulse result so the finds stay listed. */
    public static final int PRINT_NONE=0, PRINT_DONE=1, PRINT_NO_MATERIALS=2, PRINT_NO_DATA=3;
    private Setting setting=Setting.SURVEY;
    private PlumbLineOres.Target target=PlumbLineOres.Target.ALL;
    private boolean pulsing, powered;
    private int elapsed, result;
    private long started, nextPulse;
    private int ores, voids, structures, metals, printNote;
    /** The last completed pulse, for printing; transient, like the scan, so a reload or the next pulse clears it. */
    private com.frozendawn.gpr.PlumbLineSurvey lastSurvey;
    private PlumbLineScan scan;

    public PlumbLineBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.PLUMB_LINE.get(),p,s);}
    public Setting setting(){return setting;}
    public PlumbLineOres.Target target(){return target;}
    public boolean pulsing(){return pulsing;}
    public boolean powered(){return powered;}
    public int elapsedTicks(){return elapsed;}
    public int result(){return result;}
    public int[] counts(){return new int[]{ores,voids,structures,metals};}
    public int printNote(){return printNote;}
    public boolean hasSurvey(){return lastSurvey!=null;}
    /** Grid request in watts: the setting's draw while pulsing, nothing at rest. */
    public int demandWatts(){return pulsing?setting.watts:0;}
    public long cooldownTicks(){return level==null?0:Math.max(0,nextPulse-level.getGameTime());}
    public long availableWatts(){
        if(!(level instanceof ServerLevel l))return 0;
        var r=PowerNetworks.monitorReading(l,worldPosition);
        if(!r.connected())return 0;
        return Math.max(0,(long)r.generationWatts()+r.storedJoules()-Math.max(0,(long)r.demandWatts()-demandWatts()));
    }

    /** Menu buttons: 0 Survey, 1 Deep, 2 Pulse, 3 next target, 4 print a Survey Sheet. */
    public boolean press(ServerPlayer player,int id){
        if(!(level instanceof ServerLevel l)||pulsing)return false;
        switch(id){
            case 0,1->setting=Setting.byId(id);
            case 2->start(l);
            case 3->target=target.next();
            case 4->print(player);
            default->{return false;}
        }
        setChanged();return true;
    }

    private void start(ServerLevel l){
        long now=l.getGameTime();
        if(now<nextPulse){result=RESULT_COOLING;return;}
        if(availableWatts()<setting.watts){result=RESULT_NO_POWER;return;}
        pulsing=true;elapsed=0;started=now;result=RESULT_NONE;printNote=PRINT_NONE;lastSurvey=null;
        scan=new PlumbLineScan(l,worldPosition,setting);
        thump(l);
        updateState();
        FrozenDawn.LOGGER.info("[PlumbLine] Pulse started at {} setting={}",worldPosition.toShortString(),setting);
    }

    /**
     * A Survey Sheet of the last pulse costs one paper and one coal or charcoal (the ink), each copy; creative is free.
     * The sheet is stamped with the current time, so its ink fades a day after printing, not after the pulse.
     */
    private void print(ServerPlayer player){
        if(lastSurvey==null){printNote=PRINT_NO_DATA;return;}
        var inv=player.getInventory();
        int paper=slot(inv,s->s.is(net.minecraft.world.item.Items.PAPER)),ink=slot(inv,s->s.is(net.minecraft.world.item.Items.COAL)||s.is(net.minecraft.world.item.Items.CHARCOAL));
        if(!player.getAbilities().instabuild){
            if(paper<0||ink<0){printNote=PRINT_NO_MATERIALS;return;}
            inv.getItem(paper).shrink(1);inv.getItem(ink).shrink(1);
        }
        var s=lastSurvey;var sheet=new net.minecraft.world.item.ItemStack(ModItems.PLUMB_LINE_SURVEY.get());
        sheet.set(ModDataComponents.PLUMB_LINE_SURVEY.get(),new com.frozendawn.gpr.PlumbLineSurvey(s.dimension(),s.scanner(),s.radius(),level.getGameTime(),s.returns()));
        if(!inv.add(sheet))player.drop(sheet,false);
        printNote=PRINT_DONE;
        level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,SoundSource.BLOCKS,1f,1f);
    }
    private static int slot(Inventory inv,java.util.function.Predicate<net.minecraft.world.item.ItemStack> match){
        for(int i=0;i<inv.getContainerSize();i++)if(!inv.getItem(i).isEmpty()&&match.test(inv.getItem(i)))return i;
        return -1;
    }

    /** The loud low-frequency thump: heard by players, by Blind Architects nearby, and through the ground. */
    private void thump(ServerLevel l){
        // Transmitter discharge and ground thump, then the receiver listening for the ten-second pulse.
        l.playSound(null,worldPosition,ModSounds.PLUMB_LINE_PULSE.get(),SoundSource.BLOCKS,setting==Setting.DEEP?3f:2f,1f);
        l.playSound(null,worldPosition,ModSounds.PLUMB_LINE_SCAN.get(),SoundSource.BLOCKS,0.8f,1f);
        var center=worldPosition.getCenter();
        BlindArchitectEvents.emit(l,center,null,setting==Setting.DEEP?96:64,"GPR_PULSE");
        BlindArchitectEvents.vibrate(l,BlindVibrationPropagation.block(BlindVibrationPropagation.Kind.MACHINE,worldPosition,getBlockState(),center,setting==Setting.DEEP?60:40,l.getGameTime()));
    }

    public void serverTick(){
        if(!(level instanceof ServerLevel l))return;
        long now=l.getGameTime();
        if(now%20==1)powered=PowerNetworks.powered(l,worldPosition)&&demandWatts()>0;
        if(!pulsing||scan==null)return;
        elapsed++;
        // Read the allocation made on the previous tick's accounting pass, as the Uplink does.
        if(now%20==1&&now>=started+2&&!powered){finish(l,false);return;}
        long left=scan.workLeft();int ticksLeft=Math.max(1,PlumbLinePolicy.PULSE_TICKS-elapsed);
        scan.advance((int)Math.min(Integer.MAX_VALUE,Math.max(512,left/ticksLeft+1)));
        if(elapsed>=PlumbLinePolicy.PULSE_TICKS)finish(l,true);
    }

    private void finish(ServerLevel l,boolean complete){
        pulsing=false;nextPulse=l.getGameTime()+PlumbLinePolicy.COOLDOWN_TICKS;
        if(!complete){result=RESULT_ABORTED;scan=null;updateState();setChanged();
            // Cut the listening hum for everyone who could hear it, then the power-loss sound.
            var stop=new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(ModSounds.PLUMB_LINE_SCAN.getId(),SoundSource.BLOCKS);
            for(var p:l.players())if(p.distanceToSqr(worldPosition.getCenter())<=48*48)p.connection.send(stop);
            l.playSound(null,worldPosition,ModSounds.PLUMB_LINE_ABORT.get(),SoundSource.BLOCKS,1.2f,1f);
            FrozenDawn.LOGGER.info("[PlumbLine] Pulse aborted at {}: power below required draw",worldPosition.toShortString());return;}
        // Anything left after ten seconds (rays into slow chunk loads) is finished now; it is bounded by the ray count.
        while(!scan.finished())scan.advance(65_536);
        long now=l.getGameTime();
        var returns=scan.results(target,v->PlumbLineMemory.triangulate(l,v,worldPosition,now));
        scan=null;
        ores=(int)returns.stream().filter(r->r.kind()==Kind.ORE).count();voids=(int)returns.stream().filter(r->r.kind()==Kind.VOID).count();
        structures=(int)returns.stream().filter(r->r.kind()==Kind.STRUCTURE).count();metals=(int)returns.stream().filter(r->r.kind()==Kind.METAL).count();
        result=RESULT_DONE;
        lastSurvey=new com.frozendawn.gpr.PlumbLineSurvey(l.dimension().location().toString(),worldPosition,setting.radius,now,returns);
        l.playSound(null,worldPosition,ModSounds.PLUMB_LINE_COMPLETE.get(),SoundSource.BLOCKS,1f,1f);
        var payload=new PlumbLineReturnsPayload(l.dimension().location().toString(),worldPosition,returns);
        for(var p:l.players())if(p.distanceToSqr(worldPosition.getCenter())<=PlumbLinePolicy.VIEW_RANGE*PlumbLinePolicy.VIEW_RANGE)PacketDistributor.sendToPlayer(p,payload);
        updateState();setChanged();
        FrozenDawn.LOGGER.info("[PlumbLine] Pulse complete at {}: ores={} voids={} structures={} metal={}",worldPosition.toShortString(),ores,voids,structures,metals);
    }

    private void updateState(){
        if(level!=null&&getBlockState().getValue(PlumbLineBlock.PULSING)!=pulsing)level.setBlock(worldPosition,getBlockState().setValue(PlumbLineBlock.PULSING,pulsing),3);
    }

    public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.plumb_line");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new PlumbLineMenu(id,inv,this);}
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){
        super.saveAdditional(t,r);t.putInt("Setting",setting.ordinal());t.putInt("Target",target.ordinal());t.putLong("NextPulse",nextPulse);t.putInt("Result",result);
        t.putIntArray("Counts",new int[]{ores,voids,structures,metals});
    }
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){
        super.loadAdditional(t,r);setting=Setting.byId(t.getInt("Setting"));target=PlumbLineOres.Target.byId(t.getInt("Target"));nextPulse=Math.max(0,t.getLong("NextPulse"));
        result=Math.clamp(t.getInt("Result"),0,4);var c=t.getIntArray("Counts");if(c.length==4){ores=c[0];voids=c[1];structures=c[2];metals=c[3];}
        // A pulse in flight is not saved: its scan is transient, so a reload simply ends it.
        pulsing=false;scan=null;
    }
}
