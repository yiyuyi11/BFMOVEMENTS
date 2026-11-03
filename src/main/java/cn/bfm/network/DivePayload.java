package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: a dive started or landed. */
public record DivePayload(boolean active, int duration) implements CustomPacketPayload {
    public static final Type<DivePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "dive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DivePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DivePayload::active,
            ByteBufCodecs.VAR_INT, DivePayload::duration,
            DivePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
