package cn.bfm.network;

import cn.bfm.BattlefieldMovement;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "turn the movement mod off/on for this world". Ignored without permission level 2. */
public record MasterTogglePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<MasterTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, "master_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MasterTogglePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MasterTogglePayload::enabled,
            MasterTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
