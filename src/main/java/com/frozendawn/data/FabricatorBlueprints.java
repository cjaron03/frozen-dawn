package com.frozendawn.data;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModDataComponents;
import com.frozendawn.init.ModItems;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Fabricator blueprints this world has learned, shared by everyone in it. Data Slates teach the fusion parts; the
 * rocket blueprint stays story-driven and follows the Martian Command reply flag, so older saves keep it unchanged. */
public final class FabricatorBlueprints extends SavedData {
    public static final String ROCKET = "rocket";
    /** Blueprints a Data Slate can teach, in archive order. */
    public static final List<String> SLATES = List.of("magnet_coil", "first_wall", "lithium_blanket", "high_temperature_radiator", "isotope_column");
    private static final String DATA_NAME = FrozenDawn.MOD_ID + "_fabricator_blueprints";
    private final Set<String> learned = new LinkedHashSet<>();

    public static FabricatorBlueprints get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FabricatorBlueprints::new, FabricatorBlueprints::load, DataFixTypes.LEVEL), DATA_NAME);
    }
    /** Whether this world can fabricate parts of {@code id}. */
    public static boolean knows(MinecraftServer server, String id) {
        return ROCKET.equals(id) ? WinConditionState.get(server).isRocketBlueprintUnlocked() : get(server).knows(id);
    }
    private static FabricatorBlueprints load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new FabricatorBlueprints();
        var list = tag.getList("learned", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (SLATES.contains(list.getString(i))) state.learned.add(list.getString(i));
        return state;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag(); for (var id : learned) list.add(StringTag.valueOf(id));
        tag.put("learned", list);
        return tag;
    }

    public boolean knows(String id) { return learned.contains(id); }
    /** Learns a slate blueprint; false when it is already known or no slate teaches it. */
    public boolean learn(String id) {
        if (!SLATES.contains(id) || !learned.add(id)) return false;
        setDirty();
        return true;
    }
    /** Op test hook; also used by playtests to reset the archive. */
    public boolean forget(String id) {
        if (!learned.remove(id)) return false;
        setDirty();
        return true;
    }
    public boolean knowsAll() { return learned.containsAll(SLATES); }
    /** A random blueprint this world has not learned yet, or null when the archive is full. */
    public @Nullable String unlearned(RandomSource random) {
        var missing = SLATES.stream().filter(id -> !learned.contains(id)).toList();
        return missing.isEmpty() ? null : missing.get(random.nextInt(missing.size()));
    }
    public static ItemStack slate(String id) {
        var stack = new ItemStack(ModItems.DATA_SLATE.get());
        stack.set(ModDataComponents.BLUEPRINT.get(), id);
        return stack;
    }
}
