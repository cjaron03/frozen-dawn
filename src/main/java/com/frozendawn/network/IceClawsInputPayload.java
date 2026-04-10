package com.frozendawn.network;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.IceClawsHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IceClawsInputPayload(boolean jumpHeld, BlockPos anchorPos, byte wallSide2d) implements CustomPacketPayload {

    public static final Type<IceClawsInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "ice_claws_input"));

    public static final StreamCodec<ByteBuf, IceClawsInputPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IceClawsInputPayload decode(ByteBuf buffer) {
            boolean jumpHeld = ByteBufCodecs.BOOL.decode(buffer);
            boolean hasAnchor = ByteBufCodecs.BOOL.decode(buffer);
            if (!hasAnchor) {
                return new IceClawsInputPayload(jumpHeld, BlockPos.ZERO, IceClawsHandler.FACE_NONE);
            }

            BlockPos anchorPos = BlockPos.STREAM_CODEC.decode(buffer);
            byte wallSide2d = ByteBufCodecs.BYTE.decode(buffer);
            return new IceClawsInputPayload(jumpHeld, anchorPos, wallSide2d);
        }

        @Override
        public void encode(ByteBuf buffer, IceClawsInputPayload value) {
            ByteBufCodecs.BOOL.encode(buffer, value.jumpHeld());
            boolean hasAnchor = value.hasAnchor();
            ByteBufCodecs.BOOL.encode(buffer, hasAnchor);
            if (!hasAnchor) {
                return;
            }

            BlockPos.STREAM_CODEC.encode(buffer, value.anchorPos());
            ByteBufCodecs.BYTE.encode(buffer, value.wallSide2d());
        }
    };

    public boolean hasAnchor() {
        return wallSide2d != IceClawsHandler.FACE_NONE;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
