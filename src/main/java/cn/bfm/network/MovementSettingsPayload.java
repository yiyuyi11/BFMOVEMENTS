package cn.bfm.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MovementSettingsPayload(float slideEyeHeight, float diveEyeHeight) implements CustomPacketPayload {
    public static final Type<MovementSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("battlefield_movement", "eye_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementSettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, MovementSettingsPayload::slideEyeHeight,
            ByteBufCodecs.FLOAT, MovementSettingsPayload::diveEyeHeight, MovementSettingsPayload::new);
    public Type<MovementSettingsPayload> type() { return TYPE; }
}
