package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record ProneRequestPayload(boolean down, boolean instant) {
 public static void encode(ProneRequestPayload value,FriendlyByteBuf buf){buf.writeBoolean(value.down());buf.writeBoolean(value.instant());}
 public static ProneRequestPayload decode(FriendlyByteBuf buf){return new ProneRequestPayload(buf.readBoolean(),buf.readBoolean());}
}
