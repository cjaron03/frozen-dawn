package com.frozendawn.item;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PlayerEndStats;
import com.frozendawn.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.List;

public class BoardPacketItem extends Item {

    private static final String REVEALED_TAG = "blackglass_marginalia_revealed";
    private static final String CLUE_COPY_TAG = "blackglass_reflective_copy_given";
    private static final String BOOK_TITLE = "Continuity Session 14-B";
    private static final String CLUE_SYMBOLS = "?\u03A9]\u00A9 ,.\u00B6\u03A9. |,>\u00E6}\u00AE? #]:\u03A9 ]:\u03A9\u00AE\u00B6] ;,.\u00B6";

    private static final String[] VISIBLE_PAGES = {
            """
            ORSA EXECUTIVE BOARD
            STRATEGIC CONTINUITY SESSION 14-B

            PRE-PHASE BOARD PACKET
            DISTRIBUTION COPY 04 OF 06

            CLASSIFICATION:
            INTERNAL // BOARD EYES
            """,
            """
            SESSION ATTENDANCE AND SEATING

            SEAT 1  CHAIRMAN
                    VALE, M.
            SEAT 2  CFO
                    REN, A.
            SEAT 3  LEGAL DIRECTOR
                    SATO, J.
            SEAT 4  MISSION DIRECTOR
                    HOLLIS, D.
            SEAT 5  COLONIAL ASSETS
                    KLINE, R.
            SEAT 6  SECURITY OFFICER
                    [REDACTED]
            """,
            """
            PAGE 1 - SESSION OPENING

            Agenda 1. Call to order. Chair confirms quorum and opens the session.

            Agenda 2. Approval of prior minutes pending legal review of redactions.

            Agenda 3. Budget overview presented by finance covering Mars deployment costs.

            Agenda 4. Operations report submitted on schedule by infrastructure division.
            """,
            """
            PAGE 2 - INFRASTRUCTURE AND HOLDINGS

            Agenda 1. Atmospheric stabilization update. Polar arrays remain in standby configuration.

            Agenda 2. Colonial holdings revenue lagging projection across three fiscal quarters.

            Agenda 3. Legal exposure summary noting external counsel recommendations.

            Agenda 4. Kinetic launch corridor secured for next manifest cycle.
            """,
            """
            PAGE 3 - OPERATIONS AND PERSONNEL

            Agenda 1. Security audit of facility blackglass inventory with chain of custody appendix.

            Agenda 2. Geothermal damper diagnostics returned within nominal operating bands.

            Agenda 3. Labor cohort selection criteria revised for technical and breeding classes.

            Agenda 4. Atmospheric injection station readiness verified by mission engineering.
            """,
            """
            PAGE 4 - STRATEGIC CONTINUITY MOTIONS

            Agenda 1. Settler manifest expansion approved subject to financial guarantee adjustments.

            Agenda 2. Strategic continuity provisions reviewed and accepted without objection.

            Agenda 3. Closing remarks delivered by chair noting deniability requirements.

            Agenda 4. Adjournment recorded at standard timestamp with attendance confirmed.
            """,
            """
            END OF PACKET

            Distributed copies to be returned to Records following adjournment.

            Annotations in non-archival ink are prohibited under directive 7-C.
            """
    };

    private static final String[] ANNOTATED_EXTRA_PAGES = {
            """
            REFLECTIVE LAYER RECOVERED

            Margin marks visible under nonstandard reflected light:

            %s

            SEAT _RDER COLUMNS PAGE AGENDA WORD
            CHAIR ORDER IS KEY
            """.formatted(CLUE_SYMBOLS)
    };

    public BoardPacketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hasMirrorInOtherHand(player, hand) && !isRevealed(stack)) {
            reveal(stack);
            refreshBookContent(stack);
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                giveReflectiveCopy(serverPlayer, stack);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 0.85f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.65f, 1.4f);
                serverPlayer.displayClientMessage(Component.literal("Non-archival marks surface in the reflection.")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }

        openPacket(level, player, hand, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Returned clean. Margins failed inspection.")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        if (isRevealed(stack)) {
            tooltip.add(Component.literal("Reflective annotation layer recovered.")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    public static boolean tryRevealHeldPacket(Level level, Player player, InteractionHand fragmentHand) {
        InteractionHand packetHand = fragmentHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        ItemStack packet = player.getItemInHand(packetHand);
        if (!isBoardPacket(packet)) {
            return false;
        }

        if (!isRevealed(packet)) {
            reveal(packet);
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                giveReflectiveCopy(serverPlayer, packet);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.8f, 0.85f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.65f, 1.4f);
                serverPlayer.displayClientMessage(Component.literal("Non-archival marks surface in the reflection.")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }

        openPacket(level, player, packetHand, packet);
        return true;
    }

    public static boolean isBoardPacket(ItemStack stack) {
        return stack.is(ModItems.BOARD_PACKET.get());
    }

    private static boolean hasMirrorInOtherHand(Player player, InteractionHand hand) {
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return player.getItemInHand(otherHand).is(ModItems.MIRRORED_FRAGMENT.get());
    }

    private static boolean isRevealed(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().getBoolean(REVEALED_TAG);
    }

    private static boolean hasGivenClueCopy(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().getBoolean(CLUE_COPY_TAG);
    }

    private static void reveal(ItemStack stack) {
        CompoundTag tag;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            tag = existing.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putBoolean(REVEALED_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void markClueCopyGiven(ItemStack stack) {
        CompoundTag tag;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            tag = existing.copyTag();
        } else {
            tag = new CompoundTag();
        }
        tag.putBoolean(CLUE_COPY_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void giveReflectiveCopy(ServerPlayer player, ItemStack packet) {
        if (hasGivenClueCopy(packet)) {
            return;
        }

        ItemStack clueSlip = new ItemStack(Items.PAPER);
        clueSlip.set(DataComponents.CUSTOM_NAME,
                Component.literal("Reflective Margin Copy").withStyle(ChatFormatting.AQUA));
        clueSlip.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(CLUE_SYMBOLS).withStyle(ChatFormatting.GRAY),
                Component.literal("SEAT _RDER COLUMNS PAGE AGENDA WORD").withStyle(ChatFormatting.WHITE),
                Component.literal("CHAIR ORDER IS KEY").withStyle(ChatFormatting.WHITE),
                Component.literal("Recovered under nonstandard reflected light.")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)
        )));

        boolean inserted = player.addItem(clueSlip);
        if (!inserted) {
            player.drop(clueSlip, false);
        }

        markClueCopyGiven(packet);
        player.displayClientMessage(Component.literal("A reflective copy slips into your inventory.")
                .withStyle(ChatFormatting.GRAY), true);
    }

    private static void openPacket(Level level, Player player, InteractionHand hand, ItemStack stack) {
        refreshBookContent(stack);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            grantFirstRead(serverPlayer);
            serverPlayer.openItemGui(stack, hand);
        }
    }

    private static void refreshBookContent(ItemStack stack) {
        String[] basePages = VISIBLE_PAGES;
        String[] extraPages = isRevealed(stack) ? ANNOTATED_EXTRA_PAGES : new String[0];
        List<Filterable<Component>> pages = new java.util.ArrayList<>(basePages.length + extraPages.length);
        for (String page : basePages) {
            pages.add(Filterable.passThrough(Component.literal(page.strip())));
        }
        for (String page : extraPages) {
            pages.add(Filterable.passThrough(Component.literal(page.strip())));
        }

        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(BOOK_TITLE),
                "ORSA Records",
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

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "found_board_packet");
        AdvancementHolder holder = server.getAdvancements().get(loc);
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

        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, name);
        AdvancementHolder holder = server.getAdvancements().get(loc);
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
