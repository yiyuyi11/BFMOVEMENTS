package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: prone/stand request. {@code instant} skips the stand-up entry used after a dive. */
public record ProneRequestPayload(boolean down, boolean instant) implements CustomPacketPayload {
    public static final Type<ProneRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "prone_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProneRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ProneRequestPayload::down,
            ByteBufCodecs.BOOL, ProneRequestPayload::instant,
            ProneRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
