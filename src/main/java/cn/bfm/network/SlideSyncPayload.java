package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> clients: replicate a slide so third-person animation lands everywhere. */
public record SlideSyncPayload(int entityId, boolean active, int duration, float dirX, float dirZ)
        implements CustomPacketPayload {
    public static final Type<SlideSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "slide_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlideSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SlideSyncPayload::entityId,
            ByteBufCodecs.BOOL, SlideSyncPayload::active,
            ByteBufCodecs.VAR_INT, SlideSyncPayload::duration,
            ByteBufCodecs.FLOAT, SlideSyncPayload::dirX,
            ByteBufCodecs.FLOAT, SlideSyncPayload::dirZ,
            SlideSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
