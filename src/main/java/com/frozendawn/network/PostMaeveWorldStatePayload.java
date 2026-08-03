package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** World-scoped post-Maeve authority mirrored to each client. */
public record PostMaeveWorldStatePayload(
        boolean maeveErased,
        boolean undoneSpawningReleased) implements CustomPacketPayload {

    public static final Type<PostMaeveWorldStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    FrozenDawn.MOD_ID, "post_maeve_world_state"));

    public static final StreamCodec<ByteBuf, PostMaeveWorldStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    PostMaeveWorldStatePayload::maeveErased,
                    ByteBufCodecs.BOOL,
                    PostMaeveWorldStatePayload::undoneSpawningReleased,
                    PostMaeveWorldStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
