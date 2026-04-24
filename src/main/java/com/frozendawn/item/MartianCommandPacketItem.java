package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PlayerEndStats;
import com.frozendawn.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.List;

public class MartianCommandPacketItem extends Item {

    private static final String BOOK_TITLE = "Martian Command Packet";
    private static final String AUTHOR = "ORSA Relay Buffer";
    private static final String[] PAGES = {
            """
            ORSA MARTIAN COMMAND

            RELAY PACKET 01
            SOURCE: MARS COMMAND
            ROUTE: LIGHTHOUSE RELAY

            STATUS:
            RECOVERED FROM
            TRANSPONDER BUFFER
            """,
            """
            "Whoa, wait. I'm getting something here. Hello?"

            "Um... crap. Stick to the script."

            "Martian Command to unidentified surface respondent."

            "Never mind. That sounds too stupid."
            """,
            """
            "Whoever is still out there, we never expected anyone to even still be alive."

            "If you are, I am sending over blueprints for a rocket."

            "You'll need to use the ORSA blast pit."
            """,
            """
            "Good luck, and godspeed."

            PACKET NOTE:
            Blueprint handoff confirmed.
            Blast-pit launch package attached.
            """
    };

    public MartianCommandPacketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        refreshBookContent(stack);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            grantFirstRead(serverPlayer);
            serverPlayer.openItemGui(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Recovered relay packet from Martian Command.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("Right-click to review & archive.")
                .withStyle(ChatFormatting.AQUA));
    }

    public static void grantIfMissing(ServerPlayer player, boolean showNotice) {
        if (hasDiscovered(player) || inventoryHasPacket(player.getInventory())) {
            return;
        }

        ItemStack stack = new ItemStack(ModItems.MARTIAN_COMMAND_PACKET.get());
        refreshBookContent(stack);
        boolean inserted = player.addItem(stack);
        if (!inserted) {
            player.drop(stack, false);
        }

        if (showNotice) {
            player.displayClientMessage(Component.translatable("message.frozendawn.mars_command.packet_received")
                    .withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static boolean inventoryHasPacket(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.MARTIAN_COMMAND_PACKET.get())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDiscovered(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        AdvancementHolder holder = server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_martian_command_packet"));
        if (holder == null) {
            return false;
        }
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static void refreshBookContent(ItemStack stack) {
        List<Filterable<Component>> pages = new java.util.ArrayList<>(PAGES.length);
        for (String page : PAGES) {
            pages.add(Filterable.passThrough(Component.literal(page.strip())));
        }

        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(BOOK_TITLE),
                AUTHOR,
                0,
                pages,
                true
        ));
    }

    private static void grantFirstRead(ServerPlayer player) {
        grantAdvancement(player, "classified_information");

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        AdvancementHolder holder = server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_martian_command_packet"));
        if (holder == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
        PlayerEndStats.incrementOrsaDocumentsRead(player);
        player.displayClientMessage(
                Component.literal("\u00A77[\u00A76Frozen Dawn\u00A77] \u00A7fDocument archived in your \u00A76ORSA Field Survival Manual\u00A7f."),
                false);
    }

    private static void grantAdvancement(ServerPlayer player, String name) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        AdvancementHolder holder = server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name));
        if (holder == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
