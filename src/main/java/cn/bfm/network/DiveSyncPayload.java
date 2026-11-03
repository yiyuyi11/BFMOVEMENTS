package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> clients: replicate a dive for third-person animation. */
public record DiveSyncPayload(int entityId, boolean active, int duration) implements CustomPacketPayload {
    public static final Type<DiveSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "dive_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiveSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DiveSyncPayload::entityId,
            ByteBufCodecs.BOOL, DiveSyncPayload::active,
            ByteBufCodecs.VAR_INT, DiveSyncPayload::duration,
            DiveSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
