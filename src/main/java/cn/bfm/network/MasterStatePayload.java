package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> clients: the movement mod's authoritative on/off state. */
public record MasterStatePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<MasterStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "master_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MasterStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MasterStatePayload::enabled,
            MasterStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
