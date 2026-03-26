package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.block.MonitoringStationTerminalBlockEntity;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MonitoringStationStructureBuilder {

    private static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "monitoring_station");

    // The bundled template is authored around a logical station center at local (9, 0, 9).
    private static final BlockPos TEMPLATE_CENTER_OFFSET = new BlockPos(9, 0, 9);
    private static final BlockPos WEATHER_CHART_LOCAL_POS = new BlockPos(7, 2, 6);
    private static final Direction WEATHER_CHART_FACING = Direction.SOUTH;
    private static final List<BlockPos> LEGACY_WEATHER_CHART_LOCAL_POSITIONS = List.of(
            new BlockPos(3, 2, 8),
            new BlockPos(3, 2, 9)
    );
    private static final BlockPos CALENDAR_LOCAL_POS = new BlockPos(13, 2, 12);
    private static final Direction CALENDAR_FACING = Direction.WEST;

    private static final int MIN_X = -9;
    private static final int MAX_X = 8;
    private static final int MIN_Z = -9;
    private static final int MAX_Z = 8;
    private static final int ROOF_Y = 5;
    private static final int PARTITION_Z = -3;
    private static final int UNLOCK_MIN_X = 3;
    private static final int UNLOCK_MAX_X = 4;
    private static final int LOCKED_BARS_MIN_Y = 1;
    private static final int LOCKED_BARS_MAX_Y = 2;

    private static final String[] JOURNAL_VARIANTS = {
            "Day 34. Everyone else left. Someone has to keep the instruments calibrated.",
            "Day 67. ORSA stopped responding to data uploads three weeks ago. Recording anyway.",
            "Day 91. Thermometer broke at -80. Writing readings by hand now.",
            "Day 112. Power failed last night. Backup generator has maybe a week of fuel. The anemometer is still spinning.",
            "Day 45. The university recalled all field staff. I told them the data matters more than my contract. They stopped arguing.",
            "Day 78. Something walked past the station last night. Too tall. Didn't stop. Pretending I didn't see it.",
            "Day 53. The snow drift reached the lower windows this morning. Cleared them again. No point stopping now.",
            "Day 86. Still filing hourly reports to an empty uplink. Habit is stronger than common sense, apparently.",
            "Day 104. Coffee ran out four days ago. I keep boiling water anyway. The routine matters.",
            "Day 119. If this station goes dark, the last clean record goes with it. So I stay."
    };

    private MonitoringStationStructureBuilder() {
    }

    public static void place(ServerLevel level, BlockPos center) {
        int phase = getCurrentPhase(level);
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        gradeTerrain(level, cx, cy, cz);

        StructureTemplate template = level.getStructureManager().get(TEMPLATE_ID)
                .orElseThrow(() -> new IllegalStateException("Missing monitoring station template: " + TEMPLATE_ID));

        BlockPos origin = center.subtract(TEMPLATE_CENTER_OFFSET);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setKnownShape(true);
        RandomSource random = RandomSource.create(level.getSeed() ^ center.asLong());
        template.placeInWorld(level, origin, origin, settings, random, 2);

        postProcessPlacedTemplate(level, center, origin, template, settings);
        applyPhaseBurial(level, cx, cy, cz, phase);
    }

    public static void unlockBackRoom(ServerLevel level, BlockPos center) {
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        for (int dx = UNLOCK_MIN_X; dx <= UNLOCK_MAX_X; dx++) {
            for (int dy = LOCKED_BARS_MIN_Y; dy <= LOCKED_BARS_MAX_Y; dy++) {
                level.setBlock(new BlockPos(cx + dx, cy + dy, cz + PARTITION_Z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    public static boolean isProtectedBackRoomBlock(BlockPos center, BlockPos pos) {
        return pos.getX() >= center.getX() + MIN_X
                && pos.getX() <= center.getX() + MAX_X
                && pos.getZ() >= center.getZ() + MIN_Z
                && pos.getZ() <= center.getZ() + PARTITION_Z
                && pos.getY() >= center.getY() - 1
                && pos.getY() <= center.getY() + ROOF_Y + 1;
    }

    private static void postProcessPlacedTemplate(ServerLevel level, BlockPos center, BlockPos origin,
                                                  StructureTemplate template, StructurePlaceSettings settings) {
        for (StructureTemplate.StructureBlockInfo terminalInfo : template.filterBlocks(origin, settings, ModBlocks.MONITORING_STATION_TERMINAL.get())) {
            if (level.getBlockEntity(terminalInfo.pos()) instanceof MonitoringStationTerminalBlockEntity terminal) {
                terminal.setStationCenter(center);
                terminal.setChanged();
            }
        }

        for (StructureTemplate.StructureBlockInfo crateInfo : template.filterBlocks(origin, settings, ModBlocks.ORSA_SUPPLY_CRATE.get())) {
            if (!(level.getBlockEntity(crateInfo.pos()) instanceof BarrelBlockEntity barrel)) {
                continue;
            }

            List<ItemStack> loot = crateInfo.pos().getZ() < center.getZ()
                    ? createBackRoomLoot(center)
                    : createMainRoomLoot();
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                barrel.setItem(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < loot.size(); i++) {
                barrel.setItem(i, loot.get(i));
            }
            barrel.setChanged();
        }

        ensureWeatherChart(level, center);
        ensureCalendar(level, center);
    }

    private static void gradeTerrain(ServerLevel level, int cx, int cy, int cz) {
        for (int dx = -11; dx <= 10; dx++) {
            for (int dz = -11; dz <= 10; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                for (int y = cy - 1; y >= cy - 4; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isSolid()) {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
                    }
                }
                for (int y = cy; y <= cy + 13; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void applyPhaseBurial(ServerLevel level, int cx, int cy, int cz, int phase) {
        if (phase < 4) {
            return;
        }

        int roofSnowHeight = phase >= 5 ? 3 : 1;
        for (int dx = MIN_X + 1; dx <= MAX_X - 1; dx++) {
            for (int dz = MIN_Z + 1; dz <= MAX_Z - 1; dz++) {
                if (phase >= 5 && dx >= 4 && dz <= -5) {
                    continue;
                }
                for (int dy = 0; dy < roofSnowHeight; dy++) {
                    level.setBlock(new BlockPos(cx + dx, cy + ROOF_Y + 1 + dy, cz + dz), Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                }
            }
        }

        if (phase >= 5) {
            for (int dx = -10; dx <= 9; dx++) {
                for (int dz = -10; dz <= 9; dz++) {
                    int edgeDistance = Math.max(Math.max(Math.abs(dx) - 6, 0), Math.max(Math.abs(dz) - 6, 0));
                    int snowHeight = 4 - edgeDistance;
                    if (snowHeight <= 0) {
                        continue;
                    }
                    for (int dy = 0; dy < snowHeight; dy++) {
                        BlockPos pos = new BlockPos(cx + dx, cy + dy, cz + dz);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    public static void ensureWeatherChart(ServerLevel level, BlockPos center) {
        BlockPos origin = center.subtract(TEMPLATE_CENTER_OFFSET);
        BlockPos chartPos = origin.offset(WEATHER_CHART_LOCAL_POS);

        if (hasExpectedWeatherChart(level, chartPos, WEATHER_CHART_FACING)) {
            return;
        }

        int removedFrames = removeWeatherChartFrames(level, chartPos);
        for (BlockPos legacyPos : LEGACY_WEATHER_CHART_LOCAL_POSITIONS) {
            removedFrames += removeWeatherChartFrames(level, origin.offset(legacyPos));
        }

        if (placeWeatherChart(level, center, chartPos, WEATHER_CHART_FACING)) {
            FrozenDawn.LOGGER.info("Monitoring Station weather chart repaired at ({}, {}, {}) after removing {} stale frame(s)",
                    center.getX(), center.getY(), center.getZ(), removedFrames);
        } else {
            BlockPos supportPos = chartPos.relative(WEATHER_CHART_FACING.getOpposite());
            FrozenDawn.LOGGER.warn("Monitoring Station weather chart repair failed at ({}, {}, {}); target frame pos {} facing {} support {} state {}",
                    center.getX(), center.getY(), center.getZ(), chartPos, WEATHER_CHART_FACING,
                    supportPos, level.getBlockState(supportPos));
        }
    }

    public static boolean hasWeatherChart(ServerLevel level, BlockPos center) {
        BlockPos origin = center.subtract(TEMPLATE_CENTER_OFFSET);
        return hasExpectedWeatherChart(level, origin.offset(WEATHER_CHART_LOCAL_POS), WEATHER_CHART_FACING);
    }

    public static boolean hasStationMarker(ServerLevel level, BlockPos centerGuess) {
        for (int dx = MIN_X; dx <= MAX_X; dx++) {
            for (int dz = MIN_Z; dz <= MAX_Z; dz++) {
                for (int dy = -2; dy <= ROOF_Y + 2; dy++) {
                    BlockPos pos = centerGuess.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(ModBlocks.MONITORING_STATION_TERMINAL.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasExpectedWeatherChart(ServerLevel level, BlockPos pos, Direction facing) {
        AABB frameBox = new AABB(pos).inflate(0.25);
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, frameBox)) {
            ItemStack item = frame.getItem();
            if (frame.getPos().equals(pos)
                    && frame.getDirection() == facing
                    && item.is(Items.FILLED_MAP)
                    && Objects.equals(item.get(DataComponents.CUSTOM_NAME), Component.literal("Surface Synoptic Chart"))) {
                return true;
            }
        }
        return false;
    }

    private static int removeWeatherChartFrames(ServerLevel level, BlockPos pos) {
        AABB frameBox = new AABB(pos).inflate(1.5);
        int removed = 0;
        for (ItemFrame existing : level.getEntitiesOfClass(ItemFrame.class, frameBox)) {
            existing.discard();
            removed++;
        }
        return removed;
    }

    private static boolean placeWeatherChart(ServerLevel level, BlockPos center, BlockPos pos, Direction facing) {
        ItemFrame frame = new ItemFrame(level, pos, facing);
        frame.setItem(MonitoringStationWeatherChart.create(level, center), false);
        frame.setInvulnerable(true);
        if (frame.survives()) {
            level.addFreshEntity(frame);
            return true;
        }
        return false;
    }

    public static void ensureCalendar(ServerLevel level, BlockPos center) {
        BlockPos origin = center.subtract(TEMPLATE_CENTER_OFFSET);
        BlockPos calPos = origin.offset(CALENDAR_LOCAL_POS);

        if (hasExpectedCalendar(level, calPos, CALENDAR_FACING)) {
            return;
        }

        // Remove any sign block at the calendar position
        if (!level.getBlockState(calPos).isAir()) {
            level.setBlock(calPos, Blocks.AIR.defaultBlockState(), 2);
        }

        // Remove any existing item frames
        AABB frameBox = new AABB(calPos).inflate(0.5);
        for (ItemFrame existing : level.getEntitiesOfClass(ItemFrame.class, frameBox)) {
            existing.discard();
        }

        if (placeCalendar(level, center, calPos, CALENDAR_FACING)) {
            FrozenDawn.LOGGER.info("Monitoring Station calendar placed at ({}, {}, {})",
                    calPos.getX(), calPos.getY(), calPos.getZ());
        } else {
            BlockPos supportPos = calPos.relative(CALENDAR_FACING.getOpposite());
            FrozenDawn.LOGGER.warn("Monitoring Station calendar placement failed at ({}, {}, {}); target frame pos {} facing {} support {} state {}",
                    center.getX(), center.getY(), center.getZ(), calPos, CALENDAR_FACING,
                    supportPos, level.getBlockState(supportPos));
        }
    }

    private static boolean hasExpectedCalendar(ServerLevel level, BlockPos pos, Direction facing) {
        AABB frameBox = new AABB(pos).inflate(0.25);
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, frameBox)) {
            ItemStack item = frame.getItem();
            if (frame.getPos().equals(pos)
                    && frame.getDirection() == facing
                    && item.is(Items.FILLED_MAP)
                    && Objects.equals(item.get(DataComponents.CUSTOM_NAME), Component.literal("Station Calendar"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean placeCalendar(ServerLevel level, BlockPos center, BlockPos pos, Direction facing) {
        ItemFrame frame = new ItemFrame(level, pos, facing);
        frame.setItem(MonitoringStationCalendar.create(level, center), false);
        frame.setInvulnerable(true);
        if (frame.survives()) {
            level.addFreshEntity(frame);
            return true;
        }
        return false;
    }

    private static int getCurrentPhase(ServerLevel level) {
        ApocalypseState state = ApocalypseState.get(level.getServer());
        return state.getPhase();
    }

    private static List<ItemStack> createMainRoomLoot() {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(createOrsaDocument("Weather Upload Log", "station_upload_log"));
        loot.add(createOrsaDocument("Requisition Form - Replacement Thermocouple", "station_requisition"));
        loot.add(createWeatherReportItem("Station Summary - June 29, 2042",
                "Surface Temp: -18C",
                "Wind: 31 kts NE",
                "Uploads accepted by ORSA relay"));
        loot.add(createWeatherReportItem("Station Summary - August 04, 2042",
                "Surface Temp: -43C",
                "Wind: 52 kts",
                "Solar dimming now obvious at noon"));
        loot.add(new ItemStack(Items.COAL, 6));
        loot.add(new ItemStack(Items.BREAD, 3));
        return loot;
    }

    private static List<ItemStack> createBackRoomLoot(BlockPos center) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModItems.THERMAL_CORE.get()));
        loot.add(new ItemStack(Items.REDSTONE, 10));
        loot.add(new ItemStack(Items.REPEATER, 2));
        loot.add(new ItemStack(Items.COMPARATOR, 1));
        loot.add(createOrsaDocument("ORSA Ingest Node Maintenance Bulletin", "station_maintenance_bulletin"));
        loot.add(createOrsaDocument("Relay Diagnostics Printout", "station_relay_diagnostics"));
        loot.add(createJournal(center));
        return loot;
    }

    private static ItemStack createWeatherReportItem(String title, String... lines) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.literal(line));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack createOrsaDocument(String title, String docType) {
        ItemStack doc = new ItemStack(ModItems.ORSA_DOCUMENT.get());
        doc.set(DataComponents.CUSTOM_NAME, Component.literal(title));
        CompoundTag tag = new CompoundTag();
        tag.putString("doc_type", docType);
        doc.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return doc;
    }

    private static ItemStack createJournal(BlockPos center) {
        ItemStack journal = new ItemStack(ModItems.METEOROLOGIST_JOURNAL.get());
        journal.set(DataComponents.CUSTOM_NAME, Component.literal("Meteorologist's Journal"));
        journal.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Recovered from the sealed station office."),
                Component.literal("The cover is stamped with a fading NOAA seal.")
        )));

        int variant = Math.floorMod(center.getX() * 31 + center.getZ() * 17, JOURNAL_VARIANTS.length);
        String stationId = "Station " + Math.abs(center.getX() / 16) + "-" + Math.abs(center.getZ() / 16);
        List<Filterable<Component>> pages = List.of(
                Filterable.passThrough(Component.literal(
                        "GROUND TRUTH CLIMATE LOG\n\n"
                                + stationId + "\n"
                                + "September 2042\n\n"
                                + "Contractor: Civilian meteorology office\n"
                                + "ORSA terminal installed after initial build.")),
                Filterable.passThrough(Component.literal(JOURNAL_VARIANTS[variant]))
        );

        journal.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Ground Truth Climate Log"),
                "Station Meteorologist",
                0,
                pages,
                true
        ));
        return journal;
    }
}
