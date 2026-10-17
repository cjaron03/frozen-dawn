package com.frozendawn.block;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.*;
import com.frozendawn.power.PowerNetworks;
import com.frozendawn.power.ReactorPhysics;
import com.frozendawn.world.RoomAtmosphere;
import com.frozendawn.world.RoomThermalManager;
import com.frozendawn.world.TemperatureManager;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** CFU-7 Controller: validates the shell, steps the core once per second, burns rods, drives radiators,
 * leaks casing heat into its room and supplies the power network. Readings are transient. */
public final class ReactorControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 5, OUTPUT = 4, CHARGE_MARGIN_WATTS = 5000;
    // Menu data, synced as shorts: temperatures and kW x10, fractions x1000.
    public static final int CORE = 0, INSERTION = 1, HEAT_IN = 2, HEAT_OUT = 3, POWER = 4, EFFICIENCY = 5, RADIATORS = 6,
            COOLANT = 7, STATE = 8, FLAGS = 9, CLIMB = 10, FAILURE = 11, DAMAGE = 12, SHELL = 13, SHELL_AT = 14, TARGET = 15,
            RATED = 16, RADIATOR_KW = 17, DATA = 18;
    public static final int F_SCRAM = 1, F_AUTO = 2, F_FOLLOW = 4, F_SHELL = 8, F_LOCKED = 16, F_FROZEN = 32, F_RODS = 64, F_RESETTABLE = 128;
    private static final ReactorPhysics.Readout IDLE = new ReactorPhysics.Readout(ReactorPhysics.State.NOMINAL,
            ReactorPhysics.Coolant.IDLE, 0, 0, 0, 0, 0, 0, Double.POSITIVE_INFINITY, 0, 0);
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ReactorPhysics core = new ReactorPhysics();
    private ReactorPhysics.Readout readout = IDLE;
    private ReactorMultiblock.Result shell = new ReactorMultiblock.Result(ReactorMultiblock.Problem.UNLOADED, -1, BlockPos.ZERO, List.of());
    private final Set<BlockPos> hot = new HashSet<>();
    private int radiators;
    private double target = Double.NaN, rated, burnDebt;
    private String scramLog = "";
    /** Synced 0..1 share of full fission heat; drives the client hum. */
    private float hum;
    /** Synced alarm loop: none, overheat klaxon (350C) or failure siren (700C). */
    public static final int ALARM_NONE = 0, ALARM_OVERHEAT = 1, ALARM_FAILURE = 2;
    private int alarm;
    /** Last SCRAM state heard, so each trip sounds once (button, redstone or automatic). */
    private boolean scramHeard;

    public ReactorControllerBlockEntity(BlockPos p, BlockState s) { super(ModBlockEntities.REACTOR_CONTROLLER.get(), p, s); }
    public NonNullList<ItemStack> items() { return items; }
    public ReactorPhysics physics() { return core; }
    public ReactorPhysics.Readout readout() { return readout; }
    public ReactorMultiblock.Result shell() { return shell; }
    public int radiatorCount() { return radiators; }
    public int powerWatts() { return shell.valid() ? (int) Math.round(readout.powerKw() * 1000) : 0; }
    public boolean rodsLocked() { return core.rodsLocked(); }
    public float hum() { return hum; }
    public int alarm() { return alarm; }
    public int comparator() { return (int) Math.clamp(Math.round(core.coreC() / ReactorPhysics.MELTDOWN_C * 15), 0, 15); }
    public static boolean fuel(ItemStack s) { return s.is(ModItems.FUEL_ROD.get()) && s.getDamageValue() < s.getMaxDamage(); }
    public int loadedRods() { int n = 0; for (int i = 0; i < ReactorPhysics.ROD_SLOTS; i++) if (fuel(items.get(i))) n++; return n; }

    @Override public void setRemoved() {
        if (level instanceof ServerLevel l) for (var p : hot) if (l.isLoaded(p)) ReactorRadiatorBlock.publish(l, p, false);
        super.setRemoved();
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel l)) return;
        long t = l.getGameTime();
        if (t % 20 == 0) second(l);
        // Klaxon (350C) and failure siren (700C) loop on the client (ReactorAlarmSound) from the synced alarm level.
    }

    /** One second of reactor time. Also public for GameTests, which drive it directly. */
    public void second(ServerLevel l) {
        var facing = getBlockState().getValue(ReactorControllerBlock.FACING);
        shell = ReactorMultiblock.validate(l, worldPosition, facing);
        var linked = new TreeSet<BlockPos>();
        if (shell.valid()) for (var port : shell.ports()) linked.addAll(PowerNetworks.coolantRadiators(l, port));
        var working = new ArrayList<BlockPos>(); var sinks = new ArrayList<Double>();
        for (var pos : linked) ReactorRadiatorBlock.sink(l, pos).ifPresent(c -> { working.add(pos); sinks.add(c); });
        radiators = working.size();
        int rods = shell.valid() ? loadedRods() : 0;
        rated = rods * ReactorPhysics.ROD_KW;
        target = core.loadFollow() ? PowerNetworks.loadFollowTargetWatts(l, worldPosition, powerWatts()) / 1000.0 : Double.NaN;
        boolean wasMelted = core.meltdown(), wasFrozen = core.frozen();
        readout = core.step(new ReactorPhysics.Inputs(rods, sinks, roomC(l, facing), l.hasNeighborSignal(worldPosition), target));
        announceScram(l);
        burn(readout.burn());
        ejectSpent();
        // Coolant circulates through every linked radiator, buried or not: warm coolant heats them all.
        boolean warm = core.coreC() >= ReactorPhysics.RADIATOR_HOT_C && !core.frozen() && !core.meltdown()
                && readout.coolant() != ReactorPhysics.Coolant.LOST;
        var nowHot = warm ? new HashSet<>(linked) : new HashSet<BlockPos>();
        for (var p : hot) if (!nowHot.contains(p) && l.isLoaded(p)) ReactorRadiatorBlock.publish(l, p, false);
        for (var p : nowHot) ReactorRadiatorBlock.publish(l, p, true);
        hot.clear(); hot.addAll(nowHot);
        if (readout.roomHeatKw() > 0) RoomThermalManager.addMachineHeat(l, worldPosition, readout.roomHeatKw() * 1000 * ResistiveHeaterBlockEntity.HEAT_PER_JOULE);
        if (readout.coolant() == ReactorPhysics.Coolant.FLOWING) com.frozendawn.event.BlindArchitectEvents.machine(l, worldPosition);
        RoomAtmosphere.keepAlive(this);
        if (core.meltdown() && !wasMelted) { meltdown(l); if (l.getBlockEntity(worldPosition) != this) return; }
        if (wasFrozen && !core.frozen()) com.frozendawn.radiation.ReactorAdvancements.grantNear(l, worldPosition, 32, "even_the_reactor_gets_cold");
        if (readout.efficiency() > 0.85 && readout.powerKw() > 1) com.frozendawn.radiation.ReactorAdvancements.grantNear(l, worldPosition, 32, "carnot_would_be_proud");
        float nextHum = core.meltdown() ? 0 : Math.round((float) Math.clamp(readout.heatInKw() / (ReactorPhysics.ROD_SLOTS * ReactorPhysics.ROD_KW), 0, 1) * 40) / 40f;
        int nextAlarm = core.meltdown() ? ALARM_NONE : core.coreC() >= ReactorPhysics.LOSS_OF_COOLANT_C ? ALARM_FAILURE
                : core.coreC() >= ReactorPhysics.WARNING_C ? ALARM_OVERHEAT : ALARM_NONE;
        if (nextHum != hum || nextAlarm != alarm) { hum = nextHum; alarm = nextAlarm; l.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
        int alarm = core.meltdown() ? 3 : core.coreC() >= ReactorPhysics.DAMAGE_C ? 3 : core.coreC() >= ReactorPhysics.WARNING_C ? 2
                : readout.heatInKw() > 0.05 || readout.coolant() == ReactorPhysics.Coolant.FLOWING ? 1 : 0;
        var state = getBlockState();
        if (state.getValue(ReactorControllerBlock.ALARM) != alarm) l.setBlock(worldPosition, state.setValue(ReactorControllerBlock.ALARM, alarm), 3);
        l.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        setChanged();
    }

    private double roomC(ServerLevel l, net.minecraft.core.Direction facing) {
        var front = worldPosition.relative(facing);
        var room = RoomThermalManager.cachedTemperatureAt(l, front);
        if (room.isPresent()) return room.getAsDouble();
        var a = ApocalypseState.get(l.getServer());
        return TemperatureManager.getBackgroundTemperature(front.getY(), a.getCurrentDay(), a.getTotalDays());
    }
    /** Rod life is full-output seconds; lower insertion stretches it proportionally. */
    private void burn(double fraction) {
        burnDebt += fraction;
        int whole = (int) burnDebt; if (whole <= 0) return;
        burnDebt -= whole;
        for (int i = 0; i < ReactorPhysics.ROD_SLOTS; i++) {
            var rod = items.get(i);
            if (fuel(rod)) rod.setDamageValue(Math.min(rod.getMaxDamage(), rod.getDamageValue() + whole));
        }
    }
    private void ejectSpent() {
        for (int i = 0; i < ReactorPhysics.ROD_SLOTS; i++) {
            var rod = items.get(i);
            if (!rod.is(ModItems.FUEL_ROD.get()) || fuel(rod)) continue;
            var out = items.get(OUTPUT);
            if (out.is(ModItems.SHIELDED_CASK.get())) { if (!com.frozendawn.item.ShieldedCaskItem.add(out)) continue; }
            else if (out.isEmpty()) items.set(OUTPUT, new ItemStack(ModItems.SPENT_FUEL_ROD.get()));
            else if (out.is(ModItems.SPENT_FUEL_ROD.get()) && out.getCount() < out.getMaxStackSize()) out.grow(1);
            else continue;
            items.set(i, ItemStack.EMPTY);
        }
    }
    /** Core becomes molten Corium that melts down through the casing and floor; the rest of the casing scorches.
     * Contamination arrives with the radiation slice. */
    public static final float HYDROGEN_BLAST = 6f;
    public static final int DEBRIS_COUNT = 8, DEBRIS_MIN = 6, DEBRIS_MAX = 18, FLASH_RANGE = 64;

    /** Meltdown. A prompt excursion flashes nearby players. Where there is air, hydrogen from hot cladding and steam
     * ignites (Fukushima): the blast breaks open the casing and room and throws core debris (Chernobyl). In vacuum the
     * hydrogen just vents. Either way the core becomes molten Corium, the casing scorches and severe fallout settles
     * through open space: about 12 blocks with air, 3 in vacuum. */
    private void meltdown(ServerLevel l) {
        var facing = getBlockState().getValue(ReactorControllerBlock.FACING);
        var coreAt = shell.core().equals(BlockPos.ZERO) ? ReactorMultiblock.core(worldPosition, facing) : shell.core();
        boolean air = com.frozendawn.world.CombustionAtmosphere.canBurnAt(l, worldPosition.relative(facing));
        for (int i = 0; i < ReactorPhysics.ROD_SLOTS; i++) items.set(i, ItemStack.EMPTY);
        var center = coreAt.getCenter();
        for (var player : l.players()) if (player.position().distanceTo(center) <= FLASH_RANGE)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.frozendawn.network.ReactorFlashPayload(center.x, center.y, center.z));
        for (var p : BlockPos.betweenClosed(coreAt.offset(-1, -1, -1), coreAt.offset(1, 1, 1))) {
            var s = l.getBlockState(p);
            if (s.getBlock() instanceof ReactorCasingBlock) l.setBlock(p, s.setValue(ReactorCasingBlock.SCORCHED, true), 3);
        }
        if (air) {
            // Open the core first: rays starting inside a blast-resistant block would be absorbed.
            if (l.getBlockState(coreAt).is(ModBlocks.REACTOR_CORE.get())) l.setBlock(coreAt, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            l.explode(null, center.x, center.y, center.z, HYDROGEN_BLAST, true, net.minecraft.world.level.Level.ExplosionInteraction.BLOCK);
            ejectDebris(l, coreAt, DEBRIS_COUNT);
        } else l.playSound(null, coreAt, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 3f, 0.6f);
        var here = l.getBlockState(coreAt);
        if (here.isAir() || here.canBeReplaced() || here.is(ModBlocks.REACTOR_CORE.get())) l.setBlock(coreAt, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), 3);
        var vents = new java.util.ArrayList<BlockPos>();
        for (var p : BlockPos.betweenClosed(coreAt.offset(-2, -1, -2), coreAt.offset(2, 2, 2))) vents.add(p.immutable());
        com.frozendawn.radiation.Contamination.spread(l, vents, coreAt, air ? com.frozendawn.radiation.Contamination.AIR_RADIUS : com.frozendawn.radiation.Contamination.VACUUM_RADIUS, true);
        var line = Component.translatable("message.frozendawn.reactor.meltdown").withStyle(ChatFormatting.GOLD);
        for (var player : l.players()) if (player.blockPosition().closerThan(worldPosition, 96)) player.sendSystemMessage(line);
        com.frozendawn.radiation.ReactorAdvancements.grantNear(l, worldPosition, 96, "elephants_foot");
        FrozenDawn.LOGGER.warn("CFU-7 meltdown at {} (air {}, automatic SCRAM disabled: {})", worldPosition, air, scramLog);
    }

    /** Scatter core fragments onto open floor 6 to 18 blocks out. Loaded chunks only. Public for GameTests. */
    public static int ejectDebris(ServerLevel l, BlockPos coreAt, int count) {
        var random = l.getRandom(); int placed = 0;
        for (int tries = 0; tries < count * 6 && placed < count; tries++) {
            double angle = random.nextDouble() * Math.PI * 2, dist = DEBRIS_MIN + random.nextDouble() * (DEBRIS_MAX - DEBRIS_MIN);
            var top = BlockPos.containing(coreAt.getX() + Math.cos(angle) * dist, coreAt.getY() + 4, coreAt.getZ() + Math.sin(angle) * dist);
            for (int dy = 0; dy < 12; dy++) {
                var p = top.below(dy);
                if (!l.isLoaded(p)) break;
                var s = l.getBlockState(p);
                if ((s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty()
                        && l.getBlockState(p.below()).isFaceSturdy(l, p.below(), net.minecraft.core.Direction.UP)) {
                    l.setBlock(p, ModBlocks.REACTOR_DEBRIS.get().defaultBlockState(), 3); placed++; break;
                }
            }
        }
        return placed;
    }

    public void setInsertion(double value) { if (!core.meltdown()) { core.setLoadFollow(false); core.setInsertion(value); setChanged(); } }
    public void manualScram() { core.scram(); if (level instanceof ServerLevel l) announceScram(l); setChanged(); }
    private void announceScram(ServerLevel l) {
        if (core.scrammed() && !scramHeard && !core.meltdown()) l.playSound(null, worldPosition, ModSounds.REACTOR_SCRAM.get(), SoundSource.BLOCKS, 2f, 1f);
        scramHeard = core.scrammed();
    }
    public boolean reset() { boolean ok = level != null && core.reset(level.hasNeighborSignal(worldPosition)); setChanged(); return ok; }
    public void toggleLoadFollow() { if (!core.meltdown()) { core.setLoadFollow(!core.loadFollow()); setChanged(); } }
    public void enableAutoScram() { core.setAutoScram(true); scramLog = ""; setChanged(); }
    /** Reached only through the two confirmation screens. The decision is logged. */
    public void disableAutoScram(Player player) {
        if (!core.autoScram()) return;
        core.setAutoScram(false);
        scramLog = player.getGameProfile().getName() + " at game time " + (level == null ? 0 : level.getGameTime());
        FrozenDawn.LOGGER.warn("CFU-7 at {}: automatic SCRAM disabled by {}", worldPosition, scramLog);
        setChanged();
    }

    public ContainerData data() {
        return new ContainerData() {
            @Override public int get(int i) {
                var r = readout;
                return switch (i) {
                    case CORE -> clampShort(core.coreC() * 10);
                    case INSERTION -> (int) Math.round(core.insertion() * 1000);
                    case HEAT_IN -> clampShort(r.heatInKw() * 10);
                    case HEAT_OUT -> clampShort(r.heatRemovedKw() * 10);
                    case POWER -> shell.valid() ? clampShort(r.powerKw() * 10) : 0;
                    case EFFICIENCY -> (int) Math.round(r.efficiency() * 1000);
                    case RADIATORS -> radiators;
                    case COOLANT -> r.coolant().ordinal();
                    case STATE -> core.meltdown() ? ReactorPhysics.State.MELTDOWN.ordinal() : r.state().ordinal();
                    case FLAGS -> (core.scrammed() ? F_SCRAM : 0) | (core.autoScram() ? F_AUTO : 0) | (core.loadFollow() ? F_FOLLOW : 0)
                            | (shell.valid() ? F_SHELL : 0) | (core.rodsLocked() ? F_LOCKED : 0) | (core.frozen() ? F_FROZEN : 0) | (loadedRods() > 0 ? F_RODS : 0)
                            | (level != null && core.canReset(level.hasNeighborSignal(worldPosition)) ? F_RESETTABLE : 0);
                    case CLIMB -> clampShort(r.climbCPerSecond() * 100);
                    case FAILURE -> Double.isFinite(r.secondsToFailure()) ? clampShort(r.secondsToFailure()) : -1;
                    case DAMAGE -> (int) Math.round(core.damage() * 1000);
                    case SHELL -> shell.problem().ordinal();
                    case SHELL_AT -> shell.offset();
                    case TARGET -> Double.isFinite(target) ? clampShort(target * 10) : -1;
                    case RATED -> clampShort(rated * 10);
                    case RADIATOR_KW -> clampShort(r.radiatorKw() * 10);
                    default -> 0;
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return DATA; }
        };
    }
    private static int clampShort(double v) { return (int) Math.clamp(Math.round(v), Short.MIN_VALUE + 1, Short.MAX_VALUE); }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r); ContainerHelper.saveAllItems(tag, items, r);
        var physics = new CompoundTag(); core.save(physics); tag.put("Core", physics);
        tag.putDouble("BurnDebt", burnDebt); tag.putString("ScramLog", scramLog);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r); items.clear(); ContainerHelper.loadAllItems(tag, items, r);
        core.load(tag.getCompound("Core")); burnDebt = Math.clamp(tag.getDouble("BurnDebt"), 0, 1); scramLog = tag.getString("ScramLog");
        readout = IDLE; hum = tag.getFloat("Hum"); alarm = tag.getInt("Alarm"); scramHeard = core.scrammed();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider r) { var tag = new CompoundTag(); tag.putFloat("Hum", hum); tag.putInt("Alarm", alarm); return tag; }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider r) { hum = tag.getFloat("Hum"); alarm = tag.getInt("Alarm"); }
    @Override public void onDataPacket(net.minecraft.network.Connection c, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket p, HolderLookup.Provider r) { hum = p.getTag().getFloat("Hum"); alarm = p.getTag().getInt("Alarm"); }
    public boolean stillValid(Player p) { return !p.isSpectator() && level != null && level.getBlockEntity(worldPosition) == this && p.distanceToSqr(worldPosition.getCenter()) <= 64; }
    @Override public Component getDisplayName() { return Component.translatable("block.frozendawn.reactor_controller"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new ReactorControllerMenu(id, inv, this); }
}
