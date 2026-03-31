package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.FrozenTownState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.core.component.DataComponents.CUSTOM_NAME;
import static net.minecraft.core.component.DataComponents.LORE;
import static net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT;

@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class FrozenTownRuntime {

    private static final ResourceKey<Structure> FROZEN_TOWN = ResourceKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "frozen_town")
    );

    private static final Set<Long> pendingTownChunks = ConcurrentHashMap.newKeySet();

    private FrozenTownRuntime() {
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ServerLevel.OVERWORLD) {
            return;
        }

        ChunkPos chunkPos = event.getChunk().getPos();
        if (chunkHasFrozenTown(level, chunkPos)) {
            pendingTownChunks.add(packChunkPos(chunkPos.x, chunkPos.z));
        }
    }

    public static void tickProcessing(ServerLevel level) {
        if (level.players().isEmpty() || pendingTownChunks.isEmpty()) {
            return;
        }

        FrozenTownState state = FrozenTownState.get(level.getServer());
        for (Long packed : Set.copyOf(pendingTownChunks)) {
            int chunkX = unpackChunkX(packed);
            int chunkZ = unpackChunkZ(packed);
            if (state.isChunkProcessed(chunkX, chunkZ)) {
                pendingTownChunks.remove(packed);
                continue;
            }

            if (!level.isLoaded(new BlockPos(chunkX << 4, level.getMinBuildHeight(), chunkZ << 4))) {
                continue;
            }

            if (!chunkHasFrozenTown(level, new ChunkPos(chunkX, chunkZ))) {
                state.markChunkProcessed(chunkX, chunkZ);
                pendingTownChunks.remove(packed);
                continue;
            }

            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            processTownChunk(level, chunk);
            state.markChunkProcessed(chunkX, chunkZ);
            pendingTownChunks.remove(packed);
        }
    }

    public static boolean isInsideFrozenTown(ServerLevel level, BlockPos pos) {
        Structure structure = getFrozenTownStructure(level);
        if (structure == null) {
            return false;
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
        return start != null && start.isValid();
    }

    public static boolean shouldSuppressHostileSpawn(ServerLevel level, BlockPos pos, boolean naturalSpawn, Object entity) {
        return naturalSpawn && entity instanceof Enemy && isInsideFrozenTown(level, pos);
    }

    public static void reset() {
        pendingTownChunks.clear();
    }

    private static void processTownChunk(ServerLevel level, LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BarrelBlockEntity barrel) {
                fillTownContainer(level, barrel);
            } else if (blockEntity instanceof SignBlockEntity sign) {
                updateTownSign(level, sign);
            }
        }
    }

    private static void fillTownContainer(ServerLevel level, BarrelBlockEntity barrel) {
        Component customName = barrel.getCustomName();
        if (customName == null || !barrel.isEmpty()) {
            return;
        }

        String role = customName.getString();
        RandomSource random = RandomSource.create(level.getSeed() ^ barrel.getBlockPos().asLong() ^ role.hashCode());
        List<ItemStack> items = switch (role) {
            case "Kitchen Pantry", "Hall Closet", "Basement Storage", "Apartment Cupboard" -> createResidentialLoot(random);
            case "Grocery Shelf", "Cold Case" -> createGroceryLoot(random);
            case "Hardware Shelf", "Tool Cage" -> createHardwareLoot(random);
            case "Fuel Locker", "Garage Stock" -> createGasStationLoot(random);
            case "Medicine Cabinet", "Back Room Stock" -> createPharmacyLoot(random);
            case "Church Office", "Offering Plate" -> createChurchLoot(random);
            case "School Supplies", "Library Cart" -> createSchoolLoot(random);
            case "Town Records", "Filing Cabinet" -> createTownHallLoot(level, barrel.getBlockPos(), random);
            case "Fire Locker" -> createFireStationLoot(random);
            default -> List.of();
        };

        for (int slot = 0; slot < items.size() && slot < barrel.getContainerSize(); slot++) {
            barrel.setItem(slot, items.get(slot));
        }
        barrel.setChanged();
    }

    private static void updateTownSign(ServerLevel level, SignBlockEntity sign) {
        String marker = sign.getFrontText().getMessage(0, false).getString();
        if (!"EVAC NOTICE".equals(marker)) {
            return;
        }

        CampDirectiveHelper.CampDirective directive = CampDirectiveHelper.findNearestCamp(level, sign.getBlockPos());
        if (directive == null) {
            return;
        }

        sign.updateText(text -> text
                .setMessage(0, Component.literal("REPORT TO"))
                .setMessage(1, Component.literal("CAMP " + directive.designation()))
                .setMessage(2, Component.literal("X:" + directive.pos().getX()))
                .setMessage(3, Component.literal("Z:" + directive.pos().getZ())), true);
        sign.setChanged();
    }

    @Nullable
    private static Structure getFrozenTownStructure(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(FROZEN_TOWN.location());
    }

    private static boolean chunkHasFrozenTown(ServerLevel level, ChunkPos chunkPos) {
        Structure structure = getFrozenTownStructure(level);
        if (structure == null) {
            return false;
        }
        return !level.structureManager().startsForStructure(chunkPos, candidate -> candidate == structure).isEmpty();
    }

    private static List<ItemStack> createResidentialLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(switch (random.nextInt(4)) {
            case 0 -> createWrittenBook("Grocery List", "M. Hale",
                    "milk, eggs, bread, batteries, extra blankets\n\ncold snap coming",
                    "Don't forget lamp oil if the power cuts again.");
            case 1 -> createWrittenBook("Diary Entry", "Lena",
                    "Nov 3\n\nPower went out again. Third time this week.",
                    "ORSA says the grid is being prioritized for essential services. We're not essential apparently.");
            case 2 -> createWrittenBook("Love Letter", "M",
                    "I know you're worried. I am too.",
                    "But we've survived worse than a cold winter. I'll be home Thursday. Keep the fireplace going.");
            default -> createWrittenBook("Homework", "Elliot",
                    "Write 3 sentences about what you want to be when you grow up.",
                    "I want to be an astronaut so I can live on Mars.");
        });
        items.add(new ItemStack(Items.BREAD, 1 + random.nextInt(3)));
        items.add(new ItemStack(Items.APPLE, 1 + random.nextInt(3)));
        items.add(new ItemStack(Items.POTATO, 2 + random.nextInt(4)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.COAL, 1 + random.nextInt(4)));
        }
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.STRING, 1 + random.nextInt(3)));
        }
        if (random.nextInt(4) == 0) {
            items.add(random.nextBoolean() ? new ItemStack(Items.IRON_AXE) : new ItemStack(Items.IRON_SHOVEL));
        }
        return items;
    }

    private static List<ItemStack> createGroceryLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.BREAD, 2 + random.nextInt(3)));
        items.add(new ItemStack(Items.POTATO, 4 + random.nextInt(4)));
        items.add(new ItemStack(Items.CARROT, 2 + random.nextInt(4)));
        items.add(new ItemStack(Items.GLASS_BOTTLE, 2 + random.nextInt(3)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.APPLE, 2 + random.nextInt(2)));
        }
        return items;
    }

    private static List<ItemStack> createHardwareLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4)));
        items.add(random.nextBoolean() ? new ItemStack(Items.IRON_PICKAXE) : new ItemStack(Items.IRON_AXE));
        items.add(new ItemStack(Items.REDSTONE, 2 + random.nextInt(5)));
        items.add(new ItemStack(Items.COBBLESTONE, 8 + random.nextInt(8)));
        items.add(new ItemStack(Items.OAK_PLANKS, 8 + random.nextInt(8)));
        return items;
    }

    private static List<ItemStack> createGasStationLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.COAL, 4 + random.nextInt(5)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.COAL_BLOCK, 1 + random.nextInt(2)));
        }
        if (random.nextInt(3) == 0) {
            items.add(new ItemStack(Items.BLAZE_POWDER, 1 + random.nextInt(2)));
        }
        items.add(createPaperNote("Pump Service Slip",
                "Road access suspended pending ice clearance.",
                "Customer fuel limits remain in effect."));
        return items;
    }

    private static List<ItemStack> createPharmacyLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.GLASS_BOTTLE, 2 + random.nextInt(3)));
        items.add(new ItemStack(Items.SPIDER_EYE, 1 + random.nextInt(2)));
        items.add(new ItemStack(Items.SUGAR, 1 + random.nextInt(3)));
        if (random.nextInt(5) == 0) {
            items.add(new ItemStack(Items.GOLDEN_APPLE));
        }
        return items;
    }

    private static List<ItemStack> createChurchLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.SOUL_TORCH, 2 + random.nextInt(3)));
        items.add(new ItemStack(Items.GOLD_NUGGET, 2 + random.nextInt(4)));
        items.add(createWrittenBook("Sermon Notes", "Pastor Elian",
                "The cold is not mercy, but it is also not the end.",
                "Keep the candles lit for those who left after dusk."));
        return items;
    }

    private static List<ItemStack> createSchoolLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.PAPER, 3 + random.nextInt(5)));
        items.add(new ItemStack(Items.BOOK, 2 + random.nextInt(3)));
        items.add(new ItemStack(switch (random.nextInt(4)) {
            case 0 -> Items.BLUE_DYE;
            case 1 -> Items.RED_DYE;
            case 2 -> Items.YELLOW_DYE;
            default -> Items.GREEN_DYE;
        }, 1 + random.nextInt(2)));
        items.add(createWrittenBook("Homework", "Mira",
                "My town has a church and a grocery store.",
                "I want to be an astronaut so I can live on Mars."));
        return items;
    }

    private static List<ItemStack> createTownHallLoot(ServerLevel level, BlockPos pos, RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        CampDirectiveHelper.CampDirective directive = CampDirectiveHelper.findNearestCamp(level, pos);
        if (directive != null) {
            items.add(createPaperNote("Evacuation Notice",
                    "ALL RESIDENTS: Report to ORSA Field Camp " + directive.designation() + ".",
                    String.format(Locale.US, "Bring one bag per person. X:%d / Z:%d", directive.pos().getX(), directive.pos().getZ())));
        }
        items.add(new ItemStack(Items.PAPER, 2 + random.nextInt(4)));
        items.add(new ItemStack(Items.MAP));
        items.add(createPaperNote("Utility Ledger",
                "Heating oil allotment reduced again.",
                "Mayor signed the final revision without comment."));
        return items;
    }

    private static List<ItemStack> createFireStationLoot(RandomSource random) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.LEATHER, 2 + random.nextInt(2)));
        items.add(new ItemStack(Items.COAL, 2 + random.nextInt(4)));
        if (random.nextBoolean()) {
            items.add(new ItemStack(Items.IRON_AXE));
        }
        return items;
    }

    private static ItemStack createPaperNote(String title, String... lines) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(CUSTOM_NAME, Component.literal(title));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.literal(line));
        }
        stack.set(LORE, new ItemLore(lore));
        return stack;
    }

    private static ItemStack createWrittenBook(String title, String author, String... pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(CUSTOM_NAME, Component.literal(title));
        List<Filterable<Component>> content = new ArrayList<>();
        for (String page : pages) {
            content.add(Filterable.passThrough(Component.literal(page)));
        }
        book.set(WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title),
                author,
                0,
                content,
                true
        ));
        return book;
    }

    private static long packChunkPos(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackChunkZ(long packed) {
        return (int) packed;
    }
}
