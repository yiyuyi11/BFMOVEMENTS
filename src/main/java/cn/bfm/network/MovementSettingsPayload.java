package cn.bfm.network;

import cn.bfm.MovementTuning;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MovementSettingsPayload(float slideEyeHeight, float diveEyeHeight, MovementTuning tuning) implements CustomPacketPayload {
    public static final Type<MovementSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("battlefield_movement", "eye_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementSettingsPayload> CODEC =
            StreamCodec.of((buf,value) -> encode(value,buf), MovementSettingsPayload::decode);
    public static void encode(MovementSettingsPayload value,FriendlyByteBuf buf) {
        buf.writeFloat(value.slideEyeHeight);
        buf.writeFloat(value.diveEyeHeight);
        value.tuning.write(buf);
    }
    public static MovementSettingsPayload decode(FriendlyByteBuf buf) {
        return new MovementSettingsPayload(buf.readFloat(),buf.readFloat(),MovementTuning.read(buf));
    }
    public Type<MovementSettingsPayload> type() { return TYPE; }
}
