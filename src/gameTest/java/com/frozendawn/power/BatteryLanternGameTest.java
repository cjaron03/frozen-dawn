package com.frozendawn.power;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.*;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.*;
import com.frozendawn.world.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BatteryLanternGameTest {
    @BeforeBatch(batch="battery_lantern") public static void report(ServerLevel level) {GameTestReporting.installReporter(level);}

    @GameTest(template=GameTestTemplates.EMPTY,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternAndWorkLampRecipesAreDistinct(GameTestHelper h) {
        var level=h.getLevel();
        for(boolean lantern:List.of(false,true)) {
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
                    ItemStack.EMPTY,new ItemStack(Items.IRON_INGOT),ItemStack.EMPTY,
                    new ItemStack(Items.GLASS),new ItemStack(lantern?Items.COPPER_INGOT:Items.REDSTONE),new ItemStack(Items.GLASS),
                    ItemStack.EMPTY,new ItemStack(lantern?Items.REDSTONE:Items.COPPER_INGOT),ItemStack.EMPTY));
            var output=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level)
                    .orElseThrow().value().assemble(input,level.registryAccess());
            h.assertTrue(output.is(lantern?ModItems.BATTERY_LANTERN.get():ModItems.WORK_LAMP.get())
                    &&output.getCount()==(lantern?1:2),"Survival recipe resolves to the intended light and quantity: "+lantern);
            if(lantern)h.assertTrue(BatteryLanternItem.cell(output).isEmpty(),"Crafting supplies no free power cell");
        }
        // Resolve against the complete recipe manager to catch collisions with the other lights.
        for(int row:List.of(0,1)) {
            var stacks=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));
            var ingredients=List.of(Items.IRON_INGOT,Items.GLASS,Items.IRON_INGOT,
                    Items.COPPER_INGOT,Items.REDSTONE,Items.COPPER_INGOT);
            for(int i=0;i<ingredients.size();i++)stacks.set(row*3+i,new ItemStack(ingredients.get(i)));
            var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,stacks);
            var output=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level)
                    .orElseThrow().value().assemble(input,level.registryAccess());
            h.assertTrue(output.is(ModItems.SUIT_HEADLAMP)&&output.getCount()==1
                    &&!SuitHeadlamp.enabled(output)&&output.getOrDefault(ModDataComponents.HEADLAMP_CREDIT.get(),0)==0,
                    "Either crafting-grid row resolves to one fresh OFF headlamp without prepaid power");
        }
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternSixMountsWaterAndSupportDrops(GameTestHelper h) {
        var level=h.getLevel();var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"lantern_mount"));
        var support=h.absolutePos(new BlockPos(5,5,5));player.setPos(support.south(3).getCenter());
        for(var face:Direction.values()) {
            level.setBlock(support,Blocks.STONE.defaultBlockState(),3);var pos=support.relative(face);
            level.setBlock(pos,Blocks.WATER.defaultBlockState(),3);
            var item=new ItemStack(ModItems.BATTERY_LANTERN.get());
            var hit=new BlockHitResult(support.getCenter().relative(face,.5),face,support,false);
            h.assertTrue(((BlockItem)item.getItem()).place(new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,item,hit)).consumesAction(),"Actual item places on "+face);
            var state=level.getBlockState(pos);
            h.assertTrue(state.getValue(BatteryLanternBlock.FACING)==face&&state.getValue(BatteryLanternBlock.WATERLOGGED),"Mount and water retained: "+face);
            var be=(BatteryLanternBlockEntity)level.getBlockEntity(pos);be.swapCell(PowerCellItem.charged(12345));be.toggle();
            for(var drop:level.getEntitiesOfClass(ItemEntity.class,new AABB(support).inflate(4)))drop.discard();
            level.removeBlock(support,false);
            var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
            h.assertTrue(level.getFluidState(pos).getType()==Fluids.WATER&&!level.getBlockState(pos).is(ModBlocks.BATTERY_LANTERN),"Support loss restores water "+face);
            h.assertTrue(drops.size()==1&&drops.getFirst().getItem().is(ModItems.BATTERY_LANTERN)
                    &&PowerCellItem.energy(BatteryLanternItem.cell(drops.getFirst().getItem()))==12345,"Exactly one complete charged lantern drops "+face);
            drops.forEach(ItemEntity::discard);level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
        }
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternExactDrainPauseAndPartialReload(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(5,3,5));
        var be=new BatteryLanternBlockEntity(pos,ModBlocks.BATTERY_LANTERN.get().defaultBlockState());
        be.swapCell(PowerCellItem.charged(500));be.serverTick();
        h.assertTrue(be.storedJoules()==497&&be.prepaidHalfJoules()==1,"First tick prepays 3 J, retaining exactly half a joule");
        var saved=be.saveWithFullMetadata(level.registryAccess());
        var restored=new BatteryLanternBlockEntity(pos,be.getBlockState());restored.loadWithComponents(saved,level.registryAccess());
        restored.toggle();for(int i=0;i<200;i++)restored.serverTick();
        h.assertTrue(restored.storedJoules()==497&&restored.prepaidHalfJoules()==1&&!restored.enabled(),"Off simulation neither spends nor resets saved credit");
        restored.toggle();for(int i=0;i<19;i++)restored.serverTick();
        h.assertTrue(restored.storedJoules()==450&&restored.prepaidHalfJoules()==0,"Exactly 50 J per 20 enabled ticks across reload and pause");
        for(int i=0;i<180;i++)restored.serverTick();
        h.assertTrue(restored.storedJoules()==0&&restored.prepaidHalfJoules()==0,"500 J funds exactly 200 ticks");
        for(int i=0;i<200;i++)restored.serverTick();h.assertTrue(restored.storedJoules()==0,"Empty cell cannot recharge itself");
        restored.swapCell(PowerCellItem.charged(2));restored.serverTick();
        h.assertTrue(restored.storedJoules()==2,"Insufficient energy does not vanish or fund a free full tick");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternPickupCodecPlacementAndStackLimits(GameTestHelper h) {
        var level=h.getLevel();var p=place(h,5,3,5);var be=(BatteryLanternBlockEntity)level.getBlockEntity(p);
        var cell=PowerCellItem.charged(123456);cell.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("physical cell"));
        be.swapCell(cell);be.serverTick();be.toggle();
        var drops=Block.getDrops(be.getBlockState(),level,p,be);
        h.assertTrue(drops.size()==1&&drops.getFirst().getMaxStackSize()==1,"Loaded loot is exactly one unstackable lantern");
        var carried=ItemStack.parseOptional(level.registryAccess(),(net.minecraft.nbt.CompoundTag)drops.getFirst().save(level.registryAccess()));
        h.assertTrue(PowerCellItem.energy(BatteryLanternItem.cell(carried))==123453
                &&BatteryLanternItem.cell(carried).getHoverName().getString().equals("physical cell"),"Physical cell metadata and exact charge survive item codec");
        h.assertTrue(!carried.getOrDefault(ModDataComponents.LANTERN_ENABLED.get(),true)&&carried.getOrDefault(ModDataComponents.LANTERN_CREDIT.get(),0)==1,"Switch and fractional credit travel with item");
        level.removeBlock(p,false);
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"lantern_pickup"));player.setPos(p.south(2).getCenter());
        var hit=new BlockHitResult(p.below().getCenter().add(0,.5,0),Direction.UP,p.below(),false);
        h.assertTrue(((BlockItem)carried.getItem()).place(new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,carried,hit)).consumesAction(),"Real BlockItem replaces carried lamp");
        var restored=(BatteryLanternBlockEntity)level.getBlockEntity(p);
        h.assertTrue(restored.storedJoules()==123453&&restored.prepaidHalfJoules()==1&&!restored.enabled()
                &&!level.getBlockState(p).getValue(BatteryLanternBlock.LIT),"Replacement restores charge, credit and OFF state before ticking");
        restored.toggle();restored.serverTick();h.assertTrue(restored.storedJoules()==123451&&restored.prepaidHalfJoules()==0,"Pickup cannot reset fractional drain");
        restored.swapCell(ItemStack.EMPTY);
        var empty=Block.getDrops(restored.getBlockState(),level,p,restored).getFirst();
        h.assertTrue(empty.getMaxStackSize()==16&&new ItemStack(ModItems.BATTERY_LANTERN.get()).getMaxStackSize()==16,"Empty crafted and emptied lamps stack to 16");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternRealTicksVacuumWaterAndNoGrid(GameTestHelper h) {
        var level=h.getLevel();var p=place(h,5,3,5);var be=(BatteryLanternBlockEntity)level.getBlockEntity(p);
        level.setBlock(p,be.getBlockState().setValue(BatteryLanternBlock.WATERLOGGED,true),3);be.swapCell(PowerCellItem.charged(1000));
        var source=h.absolutePos(new BlockPos(9,3,5));level.setBlock(source,ModBlocks.CELL_DOCK.get().defaultBlockState(),3);
        h.assertTrue(PowerNetworks.link(level,source,p)!=PowerLinkRules.Result.OK&&PowerNetworkSavedData.get(level).degree(p)==0,"Lantern cannot become a grid device");
        var apocalypse=ApocalypseState.get(level.getServer());long original=apocalypse.getApocalypseTicks();
        try {
            apocalypse.setApocalypseTicks((long)(apocalypse.getTotalDays()*24000L*.90),level.getServer());be.serverTick();
            h.assertTrue(CombustionAtmosphere.isVacuum(level)&&!RoomAtmosphere.hasAir(level,p)
                    &&level.getBlockState(p).getLightEmission(level,p)==14&&level.getFluidState(p).getType()==Fluids.WATER,"Real vacuum and water do not prevent light 14");
            h.assertTrue(RoomAtmosphere.isPassage(level,p,be.getBlockState().setValue(BatteryLanternBlock.WATERLOGGED,false)),"Dry lantern cannot seal an oxygen breach; water retains its normal barrier");
        } finally {apocalypse.setApocalypseTicks(original,level.getServer());}
        int before=be.storedJoules()*2+be.prepaidHalfJoules();
        h.runAfterDelay(20,()->{
            int spent=before-(be.storedJoules()*2+be.prepaidHalfJoules());
            h.assertTrue(spent>=95&&spent<=105,"Real server ticker spends 50 W, within one tick of callback boundary: "+spent);
            be.toggle();int retained=be.storedJoules();
            h.runAfterDelay(20,()->{h.assertTrue(be.storedJoules()==retained&&!level.getBlockState(p).getValue(BatteryLanternBlock.LIT),"Placed OFF stops real tick consumption and light");h.succeed();});
        });
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternBothHandSwapsAndFullInventoryRemoval(GameTestHelper h) {
        var level=h.getLevel();var p=place(h,5,3,5);var be=(BatteryLanternBlockEntity)level.getBlockEntity(p);
        var profile=new GameProfile(UUID.randomUUID(),"lantern_click");var player=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
        var channel=new io.netty.channel.embedded.EmbeddedChannel();
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public io.netty.channel.Channel channel(){return channel;}
        };
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),connection,player,net.minecraft.server.network.CommonListenerCookie.createInitial(profile,false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}
            @Override public void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload){}
        };
        player.setPos(p.south().getCenter());var hit=new BlockHitResult(p.getCenter(),Direction.SOUTH,p,false);
        try {
            for(var hand:InteractionHand.values()) {
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                be.swapCell(PowerCellItem.charged(12345));player.setItemInHand(hand,PowerCellItem.charged(54321));
                if(hand==InteractionHand.OFF_HAND)h.assertTrue(!player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit).consumesAction(),"Empty main hand leaves offhand cell its click");
                player.gameMode.useItemOn(player,level,player.getItemInHand(hand),hand,hit);
                h.assertTrue(be.storedJoules()==54321&&PowerCellItem.energy(player.getItemInHand(hand))==12345&&be.enabled(),"Exact cell swap with no free duplicate or switch toggle: "+hand);
            }
            for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            player.setShiftKeyDown(true);player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);player.setShiftKeyDown(false);
            h.assertTrue(PowerCellItem.energy(player.getMainHandItem())==54321&&be.cell().isEmpty()&&be.enabled(),"Shift-empty returns sole physical cell to available hand even when inventory full");
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            player.gameMode.useItemOn(player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);
            h.assertTrue(!be.enabled(),"Empty hand toggles without a GUI");h.succeed();
        } finally {channel.finishAndReleaseAll();}
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternPlaytestFunctionsParse(GameTestHelper h) {
        for(var name:List.of("load","bootstrap","tick","setup","controls","cave","mounts","water","survival","creative","read"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("battery_lantern_check",name)).isPresent(),"Native parser loads Battery Lantern function "+name);
        h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternOffhandExactEnergyToggleAndPlacement(GameTestHelper h) {
        var level=h.getLevel();var p=place(h,4,3,4);var be=(BatteryLanternBlockEntity)level.getBlockEntity(p);
        var named=PowerCellItem.charged(500);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("offhand cell"));
        be.swapCell(named);be.serverTick();
        var carried=Block.getDrops(be.getBlockState(),level,p,be).getFirst();level.removeBlock(p,false);
        h.assertTrue(BatteryLanternItem.payHeldTick(carried)&&PowerCellItem.energy(BatteryLanternItem.cell(carried))==495
                &&carried.getOrDefault(ModDataComponents.LANTERN_CREDIT.get(),0)==0,"Placed half-joule credit continues exactly into held use");
        for(int i=0;i<18;i++)h.assertTrue(BatteryLanternItem.payHeldTick(carried),"Held source pays each simulated tick");
        h.assertTrue(PowerCellItem.energy(BatteryLanternItem.cell(carried))==450,"Placed + held 20 ticks cost exactly 50 J");
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"lantern_air_toggle"));player.setItemInHand(InteractionHand.OFF_HAND,carried);player.setShiftKeyDown(true);
        carried.getItem().use(level,player,InteractionHand.OFF_HAND);
        for(int i=0;i<20;i++)h.assertTrue(!BatteryLanternItem.payHeldTick(carried),"Held OFF never drains");
        h.assertTrue(PowerCellItem.energy(BatteryLanternItem.cell(carried))==450,"Held toggle preserves exact charge");
        carried.getItem().use(level,player,InteractionHand.OFF_HAND);player.setShiftKeyDown(false);
        var restored=ItemStack.parseOptional(level.registryAccess(),(net.minecraft.nbt.CompoundTag)carried.save(level.registryAccess()));
        h.assertTrue(BatteryLanternItem.cell(restored).getHoverName().getString().equals("offhand cell"),"Held energy writes retain physical cell metadata");
        var hit=new BlockHitResult(p.below().getCenter().add(0,.5,0),Direction.UP,p.below(),false);
        h.assertTrue(((BlockItem)restored.getItem()).place(new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,restored,hit)).consumesAction(),"Held item re-places normally");
        var placed=(BatteryLanternBlockEntity)level.getBlockEntity(p);h.assertTrue(placed.enabled()&&placed.storedJoules()==450,"Held charge and switch survive placement");
        placed.swapCell(PowerCellItem.charged(5));var shortCell=Block.getDrops(placed.getBlockState(),level,p,placed).getFirst();
        h.assertTrue(BatteryLanternItem.payHeldTick(shortCell)&&BatteryLanternItem.payHeldTick(shortCell)&&!BatteryLanternItem.payHeldTick(shortCell)
                &&PowerCellItem.energy(BatteryLanternItem.cell(shortCell))==0,"5 J funds exactly two held ticks, then depletes");
        h.assertTrue(!BatteryLanternItem.payHeldTick(new ItemStack(ModItems.BATTERY_LANTERN.get())),"Empty lantern cannot emit paid held light");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="battery_lantern",timeoutTicks=100)
    public static void batteryLanternMovingLightOverlapTerrainWaterAndCleanup(GameTestHelper h) {
        var level=h.getLevel();var a=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"lantern_walker_a"));
        var b=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"lantern_walker_b"));
        var p=h.absolutePos(new BlockPos(4,4,4));a.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);b.setPos(a.position());
        var fixture=place(h,2,3,2);var be=(BatteryLanternBlockEntity)level.getBlockEntity(fixture);be.swapCell(PowerCellItem.charged(1000));
        var item=Block.getDrops(be.getBlockState(),level,fixture,be).getFirst();a.setItemInHand(InteractionHand.OFF_HAND,item);
        var head=BlockPos.containing(a.getEyePosition());var own=ModBlocks.HELD_LANTERN_LIGHT.get();
        HeldLanternLights.tickLevel(level,List.of(a));
        h.assertTrue(level.getBlockState(head).is(own)&&level.getBlockState(head).getLightEmission(level,head)==14,"Offhand places actual light 14 at player head");
        h.assertTrue(PowerCellItem.energy(BatteryLanternItem.cell(item))==997,"Manager debits the actual physical offhand cell");
        h.assertTrue(!level.getBlockState(head).isAir()&&!level.getBlockState(head).is(Blocks.LIGHT),"Alarm-beacon air/vanilla-light writes cannot claim this marker");
        b.setItemInHand(InteractionHand.OFF_HAND,item.copy());HeldLanternLights.tickLevel(level,List.of(a,b));
        a.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);HeldLanternLights.tickLevel(level,List.of(a,b));
        h.assertTrue(level.getBlockState(head).is(own),"Overlapping second player keeps the shared light alive");
        b.setPos(p.east(4).getCenter());var moved=BlockPos.containing(b.getEyePosition());HeldLanternLights.tickLevel(level,List.of(a,b));
        h.assertTrue(level.isEmptyBlock(head)&&level.getBlockState(moved).is(own),"Movement removes old light and creates the new source");
        b.setItemInHand(InteractionHand.MAIN_HAND,b.getOffhandItem());b.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        int retained=PowerCellItem.energy(BatteryLanternItem.cell(b.getMainHandItem()));HeldLanternLights.tickLevel(level,List.of(a,b));
        h.assertTrue(level.isEmptyBlock(moved)&&PowerCellItem.energy(BatteryLanternItem.cell(b.getMainHandItem()))==retained,"Stowing into main hand removes light without inventory drain");
        b.setItemInHand(InteractionHand.OFF_HAND,b.getMainHandItem());b.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        level.setBlock(moved,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,7),3);HeldLanternLights.tickLevel(level,List.of(b));HeldLanternLights.tickLevel(level,List.of());
        h.assertTrue(level.getBlockState(moved).is(Blocks.LIGHT)&&level.getBlockState(moved).getValue(LightBlock.LEVEL)==7,"Pre-existing vanilla/alarm light survives source and cleanup");
        level.setBlock(moved,Blocks.STONE.defaultBlockState(),3);level.setBlock(b.blockPosition(),Blocks.STONE.defaultBlockState(),3);HeldLanternLights.tickLevel(level,List.of(b));
        h.assertTrue(level.getBlockState(moved).is(Blocks.STONE)&&level.getBlockState(b.blockPosition()).is(Blocks.STONE),"Occupied terrain is never replaced");
        level.setBlock(b.blockPosition(),Blocks.AIR.defaultBlockState(),3);level.setBlock(moved,Blocks.WATER.defaultBlockState(),3);HeldLanternLights.tickLevel(level,List.of(b));
        h.assertTrue(level.getBlockState(moved).is(own)&&level.getBlockState(moved).getValue(LightBlock.WATERLOGGED),"Held light operates in source water");
        HeldLanternLights.tickLevel(level,List.of());h.assertTrue(level.getBlockState(moved).is(Blocks.WATER),"Logout/dimension departure restores water");
        level.setBlock(moved,own.defaultBlockState().setValue(LightBlock.WATERLOGGED,true),3);
        level.getBlockState(moved).tick(level,moved,level.random);h.assertTrue(level.getBlockState(moved).is(Blocks.WATER),"Persisted orphan marker cleans itself without an in-memory owner after restart/unload");
        level.setBlock(moved,own.defaultBlockState(),3);var flow=Fluids.FLOWING_WATER.getFlowing(3,false);
        h.assertTrue(own.canPlaceLiquid(null,level,moved,level.getBlockState(moved),flow.getType())
                &&own.placeLiquid(level,moved,level.getBlockState(moved),flow),"Flowing water can displace a transient dry light");
        HeldLanternLights.tickLevel(level,List.of());h.assertTrue(level.getBlockState(moved).equals(flow.createLegacyBlock()),"Cleanup preserves exact incoming flow, not an invented source");
        level.setBlock(moved,Blocks.AIR.defaultBlockState(),3);HeldLanternLights.tickLevel(level,List.of(b));b.setHealth(0);HeldLanternLights.tickLevel(level,List.of(b));
        h.assertTrue(level.isEmptyBlock(moved),"Dead player cannot retain a light");
        a.setPos(p.getCenter());be.swapCell(PowerCellItem.charged(5));a.setItemInHand(InteractionHand.OFF_HAND,Block.getDrops(be.getBlockState(),level,fixture,be).getFirst());
        var shortHead=BlockPos.containing(a.getEyePosition());HeldLanternLights.tickLevel(level,List.of(a));HeldLanternLights.tickLevel(level,List.of(a));
        h.assertTrue(level.getBlockState(shortHead).is(own),"Last paid held tick remains lit");HeldLanternLights.tickLevel(level,List.of(a));
        h.assertTrue(level.isEmptyBlock(shortHead),"Depletion removes the moving light");
        a.setItemInHand(InteractionHand.OFF_HAND,item);var absent=new BlockPos(1234567,200,1234567);a.setPos(absent.getCenter());
        h.assertTrue(!level.isLoaded(absent),"Probe begins outside loaded chunks");HeldLanternLights.tickLevel(level,List.of(a));
        h.assertTrue(!level.isLoaded(absent),"Held lighting never forces chunks");HeldLanternLights.tickLevel(level,List.of());h.succeed();
    }

    private static BlockPos place(GameTestHelper h,int x,int y,int z) {
        var p=h.absolutePos(new BlockPos(x,y,z));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);
        h.getLevel().setBlock(p,ModBlocks.BATTERY_LANTERN.get().defaultBlockState(),3);return p;
    }
}
