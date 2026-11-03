package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> clients: authoritative prone phase for one entity. */
public record ProneSyncPayload(int entityId, int phase, int duration, float fromEye, long since) implements CustomPacketPayload {
    public static final Type<ProneSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "prone_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProneSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ProneSyncPayload::entityId,
            ByteBufCodecs.VAR_INT, ProneSyncPayload::phase,
            ByteBufCodecs.VAR_INT, ProneSyncPayload::duration,
            ByteBufCodecs.FLOAT, ProneSyncPayload::fromEye,
            ByteBufCodecs.VAR_LONG, ProneSyncPayload::since,
            ProneSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
