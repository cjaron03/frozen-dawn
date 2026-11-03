package com.frozendawn.network;
import com.frozendawn.FrozenDawn;
import com.frozendawn.cyclone.CycloneStorm;
import com.frozendawn.cyclone.CycloneVisuals;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CycloneSyncPayload(Optional<CycloneStorm> storm,boolean suppressed,long serverTime,float pushScale,CycloneVisuals.Peaks visibility) implements CustomPacketPayload {
    public CycloneSyncPayload(Optional<CycloneStorm> storm,boolean suppressed,long serverTime){this(storm,suppressed,serverTime,1);}
    public CycloneSyncPayload(Optional<CycloneStorm> storm,boolean suppressed,long serverTime,float pushScale){this(storm,suppressed,serverTime,pushScale,CycloneVisuals.Peaks.DEFAULT);}
    public static final Type<CycloneSyncPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,"cyclone"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CycloneSyncPayload> STREAM_CODEC=new StreamCodec<>() {
        public CycloneSyncPayload decode(RegistryFriendlyByteBuf b) {
            boolean present=b.readBoolean();var storm=present?Optional.of(CycloneStorm.load(b.readNbt())):Optional.<CycloneStorm>empty();
            return new CycloneSyncPayload(storm,b.readBoolean(),b.readLong(),b.readFloat(),new CycloneVisuals.Peaks(b.readFloat(),b.readFloat(),b.readFloat()));
        }
        public void encode(RegistryFriendlyByteBuf b,CycloneSyncPayload p) {
            b.writeBoolean(p.storm.isPresent());p.storm.ifPresent(s->b.writeNbt(s.save()));b.writeBoolean(p.suppressed);b.writeLong(p.serverTime);b.writeFloat(p.pushScale);b.writeFloat(p.visibility.moderate());b.writeFloat(p.visibility.severe());b.writeFloat(p.visibility.catastrophic());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
