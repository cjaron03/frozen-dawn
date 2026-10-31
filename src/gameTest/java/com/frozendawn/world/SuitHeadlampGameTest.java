package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.gametest.*;
import com.frozendawn.init.*;
import com.frozendawn.item.PowerCellItem;
import com.frozendawn.network.HeadlampTogglePayload;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

@GameTestHolder(FrozenDawn.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuitHeadlampGameTest {
    @BeforeBatch(batch="headlamp_equipment") public static void report(ServerLevel level) {GameTestReporting.installReporter(level);}

    @GameTest(template=GameTestTemplates.EMPTY,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampEquipmentExactPowerAndCellOrder(GameTestHelper h) {
        var p=player(h.getLevel());var module=equip(p);
        p.getInventory().setItem(0,PowerCellItem.charged(1));
        p.getInventory().setItem(8,PowerCellItem.charged(2));
        p.getInventory().setItem(9,PowerCellItem.charged(500000));
        p.setItemInHand(InteractionHand.OFF_HAND,PowerCellItem.charged(100));
        h.assertTrue(SuitHeadlamp.payTick(p),"Split loose-cell funds can pay one complete tick");
        h.assertTrue(energy(p,0)==0&&energy(p,8)==0&&energy(p,9)==500000,"Hotbar cells drain sequentially before main inventory");
        h.assertTrue(module.get(ModDataComponents.HEADLAMP_CREDIT)==1,"Three joules leaves one prepaid half-joule");
        for(int i=1;i<20;i++)h.assertTrue(SuitHeadlamp.payTick(p),"Every paid tick stays powered");
        h.assertTrue(energy(p,9)==499953&&PowerCellItem.energy(p.getOffhandItem())==100,"Twenty ticks spend exactly 50 J and preserve later cells");
        h.assertTrue(module.get(ModDataComponents.HEADLAMP_CREDIT)==0,"One second ends with no rounding drift");
        // Survival and Creative both use the same physical accounting; no free Creative mode.
        p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);SuitHeadlamp.payTick(p);
        h.assertTrue(energy(p,9)==499950,"Creative also pays the next three-joule debit");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampEquipmentDepletionResumeAndContainerIsolation(GameTestHelper h) {
        var p=player(h.getLevel());var module=equip(p);
        var lantern=new ItemStack(ModItems.BATTERY_LANTERN.get());
        lantern.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(PowerCellItem.charged(500000))));
        p.getInventory().setItem(0,lantern);p.getInventory().setItem(1,PowerCellItem.charged(2));
        h.assertTrue(!SuitHeadlamp.payTick(p)&&energy(p,1)==2,"Insufficient complete-tick funds do not consume charge or reach into a Lantern");
        p.getInventory().setItem(2,PowerCellItem.charged(1));
        h.assertTrue(SuitHeadlamp.payTick(p)&&energy(p,1)==0&&energy(p,2)==0,"Adding a cell resumes across the final joules of older cells");
        h.assertTrue(!SuitHeadlamp.payTick(p)&&SuitHeadlamp.enabled(module),"Empty cells pause without changing the saved ON switch");
        p.setItemInHand(InteractionHand.OFF_HAND,PowerCellItem.charged(2));
        h.assertTrue(SuitHeadlamp.payTick(p)&&PowerCellItem.energy(p.getOffhandItem())==0,"Offhand loose cell is the final inventory source");
        h.assertTrue(com.frozendawn.item.BatteryLanternItem.cell(lantern).get(ModDataComponents.CELL_JOULES)==500000,"Installed Lantern charge remains untouched");
        h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampEquipmentHelmetToggleAndDuplicateModules(GameTestHelper h) {
        var p=player(h.getLevel());var primary=equip(p);p.getInventory().setItem(0,PowerCellItem.charged(100));
        var extra=new ItemStack(ModItems.SUIT_HEADLAMP.get());extra.set(ModDataComponents.HEADLAMP_ENABLED.get(),true);p.getInventory().setItem(20,extra);
        for(var helmet:List.of(Items.IRON_HELMET,ModItems.EMERGENCY_EVA_HELMET.get(),Items.AIR)) {
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(helmet));
            h.assertTrue(!SuitHeadlamp.payTick(p)&&energy(p,0)==100&&SuitHeadlamp.enabled(primary),"Unsupported or missing helmet pauses charge while preserving ON");
        }
        p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.ORSA_THERMAL_VISOR.get()));
        h.assertTrue(SuitHeadlamp.payTick(p)&&energy(p,0)==97,"Visor alone works without the rest of the suit and duplicate modules do not multiply drain");
        HeadlampTogglePayload.handle(p);
        h.assertTrue(!SuitHeadlamp.enabled(primary)&&SuitHeadlamp.enabled(extra)&&!SuitHeadlamp.payTick(p),"H toggles only the primary module; spare ON module cannot bypass its OFF switch");
        HeadlampTogglePayload.handle(p);h.assertTrue(SuitHeadlamp.payTick(p)&&energy(p,0)==95,"Authenticated player toggle needs no operator command and resumes fractional credit");
        mount(p,ItemStack.EMPTY);
        p.getInventory().setItem(10,primary);
        h.assertTrue(!SuitHeadlamp.payTick(p)&&energy(p,0)==95,"Inventory modules, including ON spares, cannot light or spend");
        p.getInventory().setItem(10,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,primary);
        h.assertTrue(!SuitHeadlamp.payTick(p)&&energy(p,0)==95,"Offhand module is inactive too");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);mount(p,primary);
        h.assertTrue(SuitHeadlamp.payTick(p)&&energy(p,0)==92,"Re-equipping the same ON mount resumes its prepaid accounting");
        p.setHealth(0);h.assertTrue(!SuitHeadlamp.payTick(p),"Dead players never pay or light");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampEquipmentPersistedSwitchCreditAndNoOfflineDrain(GameTestHelper h) {
        var l=h.getLevel();var p=player(l);var module=equip(p);p.getInventory().setItem(0,PowerCellItem.charged(100));
        SuitHeadlamp.payTick(p);
        var savedMount=CuriosApi.getCuriosInventory(p).orElseThrow().saveInventory(false);var savedCell=(CompoundTag)p.getInventory().getItem(0).save(l.registryAccess());
        var restored=player(l);CuriosApi.getCuriosInventory(restored).orElseThrow().loadInventory(savedMount);
        restored.getInventory().setItem(0,ItemStack.parseOptional(l.registryAccess(),savedCell));
        restored.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));
        h.assertTrue(SuitHeadlamp.enabled(SuitHeadlamp.module(restored))&&energy(restored,0)==97,"NBT roundtrip preserves ON and exact physical charge without elapsed-time debit");
        h.assertTrue(SuitHeadlamp.payTick(restored)&&energy(restored,0)==95,"Restored prepaid half-joule funds the two-joule tick");
        SuitHeadlamp.toggle(restored);
        var savedOffMount=CuriosApi.getCuriosInventory(restored).orElseThrow().saveInventory(false);
        mount(restored,ItemStack.EMPTY);CuriosApi.getCuriosInventory(restored).orElseThrow().loadInventory(savedOffMount);
        var off=SuitHeadlamp.module(restored);restored.getInventory().setItem(0,PowerCellItem.charged(500000));
        h.assertTrue(!SuitHeadlamp.payTick(restored)&&energy(restored,0)==500000&&!SuitHeadlamp.enabled(off),"OFF survives serialization and replacing a cell cannot enable it");
        SuitHeadlamp.toggle(restored);restored.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        h.assertTrue(!SuitHeadlamp.payTick(restored)&&energy(restored,0)==500000,"Spectators cannot spend or project");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampEquipmentProductionLightAndIndependentLantern(GameTestHelper h) {
        var l=h.getLevel();var p=player(l);var module=equip(p);p.getInventory().setItem(0,PowerCellItem.charged(100));
        var start=h.absolutePos(new BlockPos(4,5,10));p.setPos(start.getCenter());p.setYRot(-90);p.setXRot(0);
        var beam=HeadlampBeam.project(l,p.getEyePosition(),p.getLookAngle());
        var lantern=new ItemStack(ModItems.BATTERY_LANTERN.get());lantern.set(DataComponents.MAX_STACK_SIZE,1);
        lantern.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(PowerCellItem.charged(100))));p.setItemInHand(InteractionHand.OFF_HAND,lantern);
        HeldLanternLights.tickLevel(l,List.of(p));
        h.assertTrue(energy(p,0)==97&&com.frozendawn.item.BatteryLanternItem.cell(lantern).get(ModDataComponents.CELL_JOULES)==97,"Headlamp and offhand Lantern each pay their own 50 W");
        h.assertTrue(beam.keySet().stream().anyMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),"Real module projects the accepted beam");
        p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);HeldLanternLights.tickLevel(l,List.of(p));
        h.assertTrue(energy(p,0)==97&&SuitHeadlamp.enabled(module),"Removing helmet stops headlamp drain while preserving switch");
        h.assertTrue(beam.keySet().stream().noneMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),"Removing helmet clears beam without clearing independent nearby Lantern");
        h.assertTrue(l.getBlockState(BlockPos.containing(p.getEyePosition())).is(ModBlocks.HELD_LANTERN_LIGHT),"Lantern still emits after helmet removal");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);HeldLanternLights.tickLevel(l,List.of(p));
        h.assertTrue(l.getBlockState(BlockPos.containing(p.getEyePosition())).is(Blocks.AIR),"Last source removal cleans all markers");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampEquipmentFunctionsAndPacketCodec(GameTestHelper h) {
        for(var name:List.of("load","bootstrap","tick","setup","controls","read","short_cell","full_cell","tunnel","wall","behind","corner","vertical","water"))
            h.assertTrue(h.getLevel().getServer().getFunctions().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("headlamp_equipment",name)).isPresent(),"Native parser loads equipment function "+name);
        var buffer=io.netty.buffer.Unpooled.buffer();
        try {HeadlampTogglePayload.STREAM_CODEC.encode(buffer,new HeadlampTogglePayload());h.assertTrue(HeadlampTogglePayload.STREAM_CODEC.decode(buffer).type().equals(HeadlampTogglePayload.TYPE),"Toggle payload carries no client-controlled target or charge");}
        finally {buffer.release();}
        var p=player(h.getLevel());HeadlampTogglePayload.handle(p);
        h.assertTrue(SuitHeadlamp.module(p).isEmpty()&&!HeadlampPrototype.enabled(p),"Missing module packet cannot grant free prototype light outside exact lab worlds");h.succeed();
    }
    @GameTest(template=GameTestTemplates.EMPTY,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampHelmetMountSlotValidationAndGoggles(GameTestHelper h) {
        var p=player(h.getLevel());var inventory=CuriosApi.getCuriosInventory(p).orElseThrow();
        var handler=inventory.getStacksHandler("helmet_mount").orElseThrow();
        h.assertTrue(handler.getSlots()==1&&!handler.hasCosmetic(),"Player receives one functional Helmet Mount without a cosmetic slot");
        var context=new SlotContext("helmet_mount",p,0,false,true);
        var module=new ItemStack(ModItems.SUIT_HEADLAMP.get());
        h.assertTrue(CuriosApi.isStackValid(context,module),"Registered headlamp tag permits insertion into Helmet Mount");
        h.assertTrue(!CuriosApi.isStackValid(context,new ItemStack(ModItems.POWER_CELL.get()))
                &&!CuriosApi.isStackValid(context,new ItemStack(ModItems.BLIZZARD_GOGGLES.get())),"Cells and goggles cannot occupy the mount");
        var curio=CuriosApi.getCurio(module).orElseThrow();
        h.assertTrue(curio.canEquip(context)&&curio.canEquipFromUse(context)
                &&!curio.canEquip(new SlotContext("head",p,0,false,true))
                &&!curio.canEquip(new SlotContext("helmet_mount",p,1,false,true))
                &&!curio.canEquip(new SlotContext("helmet_mount",p,0,true,true)),"Curio capability only permits functional mount index zero");
        var active=equip(p);p.getInventory().setItem(0,PowerCellItem.charged(100));
        inventory.getStacksHandler("head").orElseThrow().getStacks().setStackInSlot(0,new ItemStack(ModItems.BLIZZARD_GOGGLES.get()));
        h.assertTrue(com.frozendawn.compat.curios.CuriosCompat.hasBlizzardGogglesEquipped(p)&&SuitHeadlamp.payTick(p),"Goggles and powered headlamp can be worn together");
        handler.getActiveStates().set(0,false);
        h.assertTrue(!SuitHeadlamp.payTick(p)&&energy(p,0)==97&&SuitHeadlamp.enabled(active),"Disabled functional mount does not light or spend, preserving ON");
        handler.getActiveStates().set(0,true);
        h.assertTrue(SuitHeadlamp.payTick(p)&&energy(p,0)==95,"Re-enabled functional mount resumes fractional payment");h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampHelmetMountRemovalCleanupAndStorage(GameTestHelper h) {
        var l=h.getLevel();var p=player(l);var module=equip(p);p.getInventory().setItem(0,PowerCellItem.charged(100));
        p.setPos(h.absolutePos(new BlockPos(4,5,10)).getCenter());p.setYRot(-90);p.setXRot(0);
        var beam=HeadlampBeam.project(l,p.getEyePosition(),p.getLookAngle());HeldLanternLights.tickLevel(l,List.of(p));
        h.assertTrue(energy(p,0)==97&&beam.keySet().stream().anyMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),"Mounted module lights the accepted beam");
        mount(p,ItemStack.EMPTY);p.getInventory().setItem(10,module);
        HeldLanternLights.tickLevel(l,List.of(p));
        h.assertTrue(energy(p,0)==97&&SuitHeadlamp.enabled(module)&&module.get(ModDataComponents.HEADLAMP_CREDIT)==1
                &&beam.keySet().stream().noneMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),"Unequipping clears the beam and preserves charge, ON and half-joule in inventory");
        HeadlampTogglePayload.handle(p);
        h.assertTrue(SuitHeadlamp.enabled(module),"H while stored cannot change an inactive module's saved switch");
        p.getInventory().setItem(10,ItemStack.EMPTY);mount(p,module);HeldLanternLights.tickLevel(l,List.of(p));
        h.assertTrue(energy(p,0)==95&&beam.keySet().stream().anyMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),"Re-equipping resumes without another H and consumes the retained half-joule");
        HeldLanternLights.tickLevel(l,List.of());h.succeed();
    }

    @GameTest(template=GameTestTemplates.EMPTY_LARGE,batch="headlamp_equipment",timeoutTicks=100)
    public static void headlampFloorPlacementKeepsAPoweredSource(GameTestHelper h) {
        var l=h.getLevel();var p=player(l);var module=equip(p);
        p.getInventory().setItem(9,PowerCellItem.charged(100));
        for(int x=8;x<=12;x++)for(int z=3;z<=13;z++)h.setBlock(new BlockPos(x,4,z),Blocks.STONE);
        var feet=h.absolutePos(new BlockPos(10,5,5));p.setPos(feet.getX()+.5,feet.getY(),feet.getZ()+.5);
        for(int distance:List.of(1,2,3,4,5)) {
            p.setYRot(0);p.setXRot((float)Math.toDegrees(Math.atan2(p.getEyeHeight(),distance)));
            var target=feet.offset(0,0,distance);
            HeldLanternLights.tickLevel(l,List.of(p));
            var before=HeadlampBeam.project(l,p.getEyePosition(),p.getLookAngle());
            h.assertTrue(distance>=5||l.getBlockState(target).is(ModBlocks.HELD_LANTERN_LIGHT),
                    "Nearby floor placement targets a real projected light voxel");
            var item=new ItemStack(Items.CRAFTING_TABLE);p.setItemInHand(InteractionHand.MAIN_HAND,item);
            var hit=new net.minecraft.world.phys.BlockHitResult(target.below().getCenter().add(0,.5,0),
                    net.minecraft.core.Direction.UP,target.below(),false);
            var result=((BlockItem)item.getItem()).place(new net.minecraft.world.item.context.BlockPlaceContext(l,p,InteractionHand.MAIN_HAND,item,hit));
            h.assertTrue(result.consumesAction()&&l.getBlockState(target).is(Blocks.CRAFTING_TABLE),"Real floor placement succeeds at distance "+distance);
            h.assertTrue(before.keySet().stream().anyMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),
                    "A paid source survives immediately after placement, before the next lighting-manager tick: "+distance);
            int charge=energy(p,9);HeldLanternLights.tickLevel(l,List.of(p));
            var after=HeadlampBeam.project(l,p.getEyePosition(),p.getLookAngle());
            h.assertTrue(!after.isEmpty()&&after.size()<=HeadlampBeam.MAX_SOURCES
                    &&after.keySet().stream().allMatch(pos->l.getBlockState(pos).is(ModBlocks.HELD_LANTERN_LIGHT)),
                    "Next tick retargets the same bounded beam in front of the placed block");
            h.assertTrue(energy(p,9)<charge&&SuitHeadlamp.enabled(module),"Placement neither toggles the module nor interrupts normal paid ticks");
            HeldLanternLights.tickLevel(l,List.of());l.removeBlock(target,false);
        }
        h.succeed();
    }

    private static FakePlayer player(ServerLevel l) {
        var player=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"headlamp_equipment"));
        CuriosApi.getCuriosInventory(player).orElseThrow().reset();return player;
    }
    private static void mount(ServerPlayer p,ItemStack module) {
        CuriosApi.getCuriosInventory(p).orElseThrow().getStacksHandler("helmet_mount").orElseThrow().getStacks().setStackInSlot(0,module);
    }
    private static ItemStack equip(ServerPlayer p) {
        var module=new ItemStack(ModItems.SUIT_HEADLAMP.get());module.set(ModDataComponents.HEADLAMP_ENABLED.get(),true);
        mount(p,module);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(ModItems.EVA_HELMET.get()));return module;
    }
    private static int energy(ServerPlayer p,int slot) {return PowerCellItem.energy(p.getInventory().getItem(slot));}
}
