package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record MovementSettingsPayload(float slideEyeHeight, float diveEyeHeight) {
 public static void encode(MovementSettingsPayload value,FriendlyByteBuf buf){buf.writeFloat(value.slideEyeHeight());buf.writeFloat(value.diveEyeHeight());}
 public static MovementSettingsPayload decode(FriendlyByteBuf buf){return new MovementSettingsPayload(buf.readFloat(),buf.readFloat());}
}
