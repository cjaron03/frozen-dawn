package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record OpenMeteorologistJournalPayload(ItemStack stack) implements CustomPacketPayload {

    public static final Type<OpenMeteorologistJournalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "open_meteorologist_journal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMeteorologistJournalPayload> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(OpenMeteorologistJournalPayload::new, OpenMeteorologistJournalPayload::stack);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
