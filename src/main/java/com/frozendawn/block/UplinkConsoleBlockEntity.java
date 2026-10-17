package com.frozendawn.block;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.*;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.uplink.*;
import com.frozendawn.uplink.UplinkPolicy.Mode;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Console state machine. Draw is requested through the grid each second; a transmission that loses its full
 * draw, its dish or its world aborts with no reward. Cooldown and signal pressure are spent when the handshake
 * starts, so an aborted transmission is still heard.
 */
public final class UplinkConsoleBlockEntity extends BlockEntity implements MenuProvider {
    public enum State { IDLE, ARMED, TRANSMITTING }
    public static final int DISH_NONE=0, DISH_OK=1, DISH_NO_SKY=2;
    private Mode mode=Mode.OFF, resume=Mode.OFF;
    private State state=State.IDLE;
    private boolean boost;
    private int elapsed;
    private long started, nextBroadcast, armedAt;
    /** An unanswered transmit prompt lapses after a minute and releases its scheduled load. */
    public static final int ARM_TIMEOUT_TICKS=1200;
    private int broadcastIndex;
    private UUID transmitter;
    private BlockPos dish, driven;
    private int dishStatus;
    private boolean powered, reloaded;
    private ItemStack held=ItemStack.EMPTY;
    private final ArrayDeque<UplinkBroadcasts.Line> log=new ArrayDeque<>();

    public UplinkConsoleBlockEntity(BlockPos p,BlockState s){super(ModBlockEntities.UPLINK_CONSOLE.get(),p,s);}

    public Mode mode(){return mode;}
    public State state(){return state;}
    public boolean boost(){return boost;}
    public boolean powered(){return powered;}
    public int dishStatus(){return dishStatus;}
    public BlockPos dish(){return dish;}
    public int elapsedTicks(){return elapsed;}
    public int totalTicks(){return state==State.TRANSMITTING?transmitMode().ticks:0;}
    public List<UplinkBroadcasts.Line> log(){return List.copyOf(log);}
    private Mode transmitMode(){return boost?Mode.BOOST:Mode.TRANSMIT;}
    /** Grid request in watts: the active handshake, otherwise the receiver when listening. */
    public int demandWatts(){return state==State.TRANSMITTING?transmitMode().watts:mode==Mode.LISTEN?Mode.LISTEN.watts:0;}
    /** While the transmit prompt is up, the extra draw the handshake will need: load-follow ramps toward it. */
    public int scheduledWatts(){return state==State.ARMED?Math.max(0,transmitMode().watts-demandWatts()):0;}
    /** One-second funding the grid offers this console now: generation plus stored energy, after every other load. */
    public long availableWatts(){
        if(!(level instanceof ServerLevel l))return 0;
        var r=PowerNetworks.monitorReading(l,worldPosition);
        if(!r.connected())return 0;
        return Math.max(0,(long)r.generationWatts()+r.storedJoules()-Math.max(0,(long)r.demandWatts()-demandWatts()));
    }
    public long cooldownTicks(){return level instanceof ServerLevel l?UplinkPolicy.cooldownRemaining(l.getGameTime(),UplinkSavedData.get(l.getServer()).nextTransmit()):0;}

    public void serverTick(){
        if(!(level instanceof ServerLevel l))return;
        long now=l.getGameTime();
        // After a reload the grid rebuilds its links first; give an in-flight handshake one accounting pass.
        if(reloaded){reloaded=false;if(state==State.TRANSMITTING)started=now+20;}
        // Read the allocation made on the previous tick's accounting pass, as the power monitor does.
        if(now%20==1){refreshDish(l);powered=PowerNetworks.powered(l,worldPosition)&&demandWatts()>0;}
        if(state==State.ARMED&&now-armedAt>=ARM_TIMEOUT_TICKS){state=State.IDLE;add("uplink.frozendawn.log.disarmed");}
        if(state==State.TRANSMITTING){
            elapsed++;
            if(now%20==1&&now>=started+2){
                if(!powered){abort(l,"uplink.frozendawn.log.abort.power");return;}
                if(dishStatus!=DISH_OK){abort(l,"uplink.frozendawn.log.abort.dish");return;}
            }
            if(elapsed>=transmitMode().ticks)complete(l);
        } else if(mode==Mode.LISTEN&&powered&&dishStatus==DISH_OK&&now>=nextBroadcast){
            nextBroadcast=now+UplinkPolicy.BROADCAST_INTERVAL;broadcast(l);
        }
        if(now%UplinkPolicy.STORM_CHECK_INTERVAL==7&&dishStatus==DISH_OK)stormWear(l);
        if(now%20==1)updateActive();
        if(now%20==1)setChanged();
    }

    private void refreshDish(ServerLevel l){
        BlockPos best=null;int bestStatus=DISH_NONE;double bestSq=Double.MAX_VALUE;int r=UplinkPolicy.DISH_RANGE;
        for(var p:BlockPos.betweenClosed(worldPosition.offset(-r,-r,-r),worldPosition.offset(r,r,r))){
            double sq=p.distSqr(worldPosition);
            if(sq>r*r||!l.isLoaded(p)||!(l.getBlockState(p).getBlock() instanceof UplinkDishBlock))continue;
            // Open sky is full sky light above the dish: glass passes, any opaque roof blocks it.
            int status=l.canSeeSky(p.above())?DISH_OK:DISH_NO_SKY;
            if(status==DISH_OK&&bestStatus!=DISH_OK||status==bestStatus&&sq<bestSq||best==null){best=p.immutable();bestStatus=status;bestSq=sq;}
        }
        dish=best;dishStatus=bestStatus;
    }

    private void updateActive(){
        int active=state==State.TRANSMITTING?2:mode==Mode.LISTEN&&powered?1:0;
        if(getBlockState().getValue(UplinkConsoleBlock.ACTIVE)!=active)level.setBlock(worldPosition,getBlockState().setValue(UplinkConsoleBlock.ACTIVE,active),3);
        // The dish shows what the console is doing: sweep while listening, spin while transmitting.
        int dishMode=dishStatus!=DISH_OK?0:state==State.TRANSMITTING?(boost?3:2):active==1?1:0;
        if(driven!=null&&!driven.equals(dish))setDishMode(driven,0);
        driven=dishStatus==DISH_OK?dish:null;
        if(driven!=null)setDishMode(driven,dishMode);
    }
    private void setDishMode(BlockPos p,int value){
        if(level==null||!level.isLoaded(p))return;var s=level.getBlockState(p);
        if(s.getBlock() instanceof UplinkDishBlock&&s.getValue(UplinkDishBlock.MODE)!=value)level.setBlock(p,s.setValue(UplinkDishBlock.MODE,value),3);
    }
    /** Called when the console is removed: its dish comes to rest. */
    public void releaseDish(){if(driven!=null)setDishMode(driven,0);driven=null;}

    /** Menu buttons: 0 off, 1 listen, 2 arm transmit, 3 arm boost, 4 confirm, 5 cancel. */
    public boolean press(ServerPlayer player,int id){
        if(!(level instanceof ServerLevel l))return false;
        switch(id){
            case 0,1->{if(state==State.TRANSMITTING)return false;mode=id==0?Mode.OFF:Mode.LISTEN;state=State.IDLE;if(mode==Mode.LISTEN)nextBroadcast=l.getGameTime()+100;}
            case 2,3->{if(state==State.TRANSMITTING)return false;boost=id==3;state=State.ARMED;armedAt=l.getGameTime();}
            case 4->{if(state!=State.ARMED)return false;start(l,player);}
            case 5->{if(state!=State.ARMED)return false;state=State.IDLE;}
            default->{return false;}
        }
        setChanged();sync();return true;
    }

    private void start(ServerLevel l,ServerPlayer player){
        state=State.IDLE;
        var data=UplinkSavedData.get(l.getServer());long now=l.getGameTime();
        refreshDish(l);
        String refusal=l.dimension()!=ServerLevel.OVERWORLD?"uplink.frozendawn.log.refuse.orbit"
                :dishStatus==DISH_NONE?"uplink.frozendawn.log.refuse.no_dish"
                :dishStatus==DISH_NO_SKY?"uplink.frozendawn.log.refuse.sky"
                :!UplinkPolicy.cooledDown(now,data.nextTransmit())?"uplink.frozendawn.log.refuse.cooldown"
                :!budget(l)?"uplink.frozendawn.log.refuse.power":null;
        if(refusal!=null){
            if(refusal.endsWith("cooldown"))add(refusal,Long.toString((UplinkPolicy.cooldownRemaining(now,data.nextTransmit())+1199)/1200));
            else add(refusal);
            return;
        }
        data.startCooldown(now);
        data.signal(l.dimension().location().toString(),dish,now,boost);
        resume=mode;state=State.TRANSMITTING;elapsed=0;started=now;transmitter=player.getUUID();
        add(boost?"uplink.frozendawn.log.start.boost":"uplink.frozendawn.log.start");
        l.playSound(null,worldPosition,ModSounds.RADIO_STATIC_HEAVY.get(),SoundSource.BLOCKS,0.8f,0.8f);
        FrozenDawn.LOGGER.info("[Uplink] Transmission started at {} dish={} boost={} by {}",worldPosition.toShortString(),dish.toShortString(),boost,player.getName().getString());
    }

    /** The grid can fund the full draw for the next second: generation plus stored energy, after every other load. */
    private boolean budget(ServerLevel l){return availableWatts()>=transmitMode().watts;}

    private void abort(ServerLevel l,String reason){
        state=State.IDLE;mode=resume;elapsed=0;add(reason);
        l.playSound(null,worldPosition,ModSounds.RADIO_CUTOFF.get(),SoundSource.BLOCKS,0.8f,1.0f);
        FrozenDawn.LOGGER.info("[Uplink] Transmission aborted at {}: {}",worldPosition.toShortString(),reason);
        setChanged();sync();
    }

    private void complete(ServerLevel l){
        var result=UplinkRewards.grant(l,worldPosition,l.random);
        state=State.IDLE;mode=resume;elapsed=0;
        log(new UplinkBroadcasts.Line("uplink.frozendawn.log.complete",List.of()));
        logComponent(result.log());
        var player=transmitter==null?null:l.getServer().getPlayerList().getPlayer(transmitter);
        if(player!=null&&player.level()==l&&player.distanceToSqr(worldPosition.getCenter())<=32*32){
            if(!player.getInventory().add(result.item().copy()))player.drop(result.item().copy(),false);
            player.displayClientMessage(Component.translatable("uplink.frozendawn.printed").withStyle(ChatFormatting.AQUA),true);
        } else held=result.item().copy();
        l.playSound(null,worldPosition,ModSounds.RADIO_SIGNAL_LOCK.get(),SoundSource.BLOCKS,0.9f,1.0f);
        FrozenDawn.LOGGER.info("[Uplink] Transmission complete at {}: {}",worldPosition.toShortString(),result.reward());
        setChanged();sync();
    }

    private void logComponent(Component c){
        if(c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t){
            var args=new ArrayList<String>();for(var a:t.getArgs())args.add(String.valueOf(a instanceof Component ac?ac.getString():a));
            log(new UplinkBroadcasts.Line(t.getKey(),args));
        } else log(new UplinkBroadcasts.Line("uplink.frozendawn.log.raw",List.of(c.getString())));
    }

    private void broadcast(ServerLevel l){
        var line=UplinkBroadcasts.next(l,broadcastIndex++);log(line);
        var text=Component.translatable("uplink.frozendawn.listen.prefix").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.translatable(line.key(),line.args().toArray()).withStyle(ChatFormatting.GRAY));
        for(var p:l.players())if(p.distanceToSqr(worldPosition.getCenter())<=UplinkPolicy.LISTEN_RANGE*UplinkPolicy.LISTEN_RANGE)p.sendSystemMessage(text);
        l.playSound(null,worldPosition,ModSounds.RADIO_STATIC_BURST.get(),SoundSource.BLOCKS,0.5f,1.0f);
        setChanged();sync();
    }

    /** Storm wear (Uplink spec): an exposed dish can be torn off in a blizzard, losing its salvage parts. */
    private void stormWear(ServerLevel l){
        var a=ApocalypseState.get(l.getServer());
        if(dish==null||!PhaseManager.isBlizzardActive(a.getPhase(),a.getProgress())||l.random.nextDouble()>=UplinkPolicy.STORM_DISH_LOSS)return;
        l.destroyBlock(dish,false);add("uplink.frozendawn.log.storm");
        if(state==State.TRANSMITTING)abort(l,"uplink.frozendawn.log.abort.dish");
        dish=null;dishStatus=DISH_NONE;
    }

    private void add(String key,String... args){log(new UplinkBroadcasts.Line(key,List.of(args)));sync();}
    private void log(UplinkBroadcasts.Line line){log.addLast(line);while(log.size()>UplinkPolicy.LOG_LINES)log.removeFirst();}
    private void sync(){if(level instanceof ServerLevel l)l.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}

    public void deliverHeld(ServerPlayer player){
        if(held.isEmpty())return;
        if(!player.getInventory().add(held))player.drop(held,false);
        held=ItemStack.EMPTY;setChanged();
    }
    public void dropHeld(){
        if(level!=null&&!held.isEmpty())net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+1,worldPosition.getZ()+.5,held);
        held=ItemStack.EMPTY;
    }

    public boolean stillValid(Player p){return !p.isSpectator()&&level!=null&&level.getBlockEntity(worldPosition)==this&&p.distanceToSqr(worldPosition.getCenter())<=64;}
    @Override public Component getDisplayName(){return Component.translatable("block.frozendawn.uplink_console");}
    @Override public AbstractContainerMenu createMenu(int id,Inventory inv,Player p){return new UplinkConsoleMenu(id,inv,this);}

    private static ListTag saveLog(Collection<UplinkBroadcasts.Line> lines){
        var list=new ListTag();
        for(var line:lines){var t=new CompoundTag();t.putString("key",line.key());var args=new ListTag();for(var a:line.args())args.add(StringTag.valueOf(a));t.put("args",args);list.add(t);}
        return list;
    }
    private void loadLog(ListTag list){
        log.clear();
        for(Tag raw:list){
            var t=(CompoundTag)raw;var key=t.getString("key");if(!key.startsWith("uplink.frozendawn."))continue;
            var args=new ArrayList<String>();for(Tag a:t.getList("args",Tag.TAG_STRING))if(args.size()<4)args.add(a.getAsString());
            log(new UplinkBroadcasts.Line(key,args));
        }
    }
    @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){
        super.saveAdditional(t,r);
        t.putInt("Mode",mode.ordinal());t.putInt("Resume",resume.ordinal());t.putInt("State",state.ordinal());t.putBoolean("Boost",boost);
        t.putInt("Elapsed",elapsed);t.putLong("Started",started);t.putLong("NextBroadcast",nextBroadcast);t.putInt("BroadcastIndex",broadcastIndex);
        if(transmitter!=null)t.putUUID("Transmitter",transmitter);
        if(!held.isEmpty())t.put("Held",held.save(r));
        t.put("Log",saveLog(log));
    }
    @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){
        super.loadAdditional(t,r);
        mode=Mode.byId(t.getInt("Mode"));if(mode.transmitting())mode=Mode.OFF;
        resume=Mode.byId(t.getInt("Resume"));if(resume.transmitting())resume=Mode.OFF;
        int s=t.getInt("State");state=s>=0&&s<State.values().length?State.values()[s]:State.IDLE;
        boost=t.getBoolean("Boost");elapsed=Math.max(0,t.getInt("Elapsed"));started=t.getLong("Started");
        nextBroadcast=t.getLong("NextBroadcast");broadcastIndex=Math.max(0,t.getInt("BroadcastIndex"));
        transmitter=t.hasUUID("Transmitter")?t.getUUID("Transmitter"):null;
        held=t.contains("Held")?ItemStack.parseOptional(r,t.getCompound("Held")):ItemStack.EMPTY;
        loadLog(t.getList("Log",Tag.TAG_COMPOUND));
        // An armed prompt never survives a reload; an in-flight handshake resumes and must re-qualify for power.
        if(state==State.ARMED)state=State.IDLE;
        reloaded=true;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){var t=super.getUpdateTag(r);t.put("Log",saveLog(log));return t;}
    @Override public void handleUpdateTag(CompoundTag t,HolderLookup.Provider r){loadLog(t.getList("Log",Tag.TAG_COMPOUND));}
    @Override public void onDataPacket(net.minecraft.network.Connection c,ClientboundBlockEntityDataPacket p,HolderLookup.Provider r){loadLog(p.getTag().getList("Log",Tag.TAG_COMPOUND));}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
