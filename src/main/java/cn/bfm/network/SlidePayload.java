package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: a slide started or ended, so other clients can animate it. */
public record SlidePayload(boolean active, int duration, float dirX, float dirZ) implements CustomPacketPayload {
    public static final Type<SlidePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "slide"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SlidePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SlidePayload::active,
            ByteBufCodecs.VAR_INT, SlidePayload::duration,
            ByteBufCodecs.FLOAT, SlidePayload::dirX,
            ByteBufCodecs.FLOAT, SlidePayload::dirZ,
            SlidePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
