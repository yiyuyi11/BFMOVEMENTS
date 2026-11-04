package cn.bfm.network;

import cn.bfm.MovementTuning;
import net.minecraft.network.FriendlyByteBuf;

public record MovementSettingsPayload(float slideEyeHeight, float diveEyeHeight, MovementTuning tuning) {
    public static void encode(MovementSettingsPayload value,FriendlyByteBuf buf) {
        buf.writeFloat(value.slideEyeHeight);
        buf.writeFloat(value.diveEyeHeight);
        value.tuning.write(buf);
    }
    public static MovementSettingsPayload decode(FriendlyByteBuf buf) {
        return new MovementSettingsPayload(buf.readFloat(),buf.readFloat(),MovementTuning.read(buf));
    }
}
