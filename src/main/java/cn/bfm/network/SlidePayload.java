package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record SlidePayload(boolean active, int duration, float dirX, float dirZ) {
 public static void encode(SlidePayload value,FriendlyByteBuf buf){buf.writeBoolean(value.active());buf.writeVarInt(value.duration());buf.writeFloat(value.dirX());buf.writeFloat(value.dirZ());}
 public static SlidePayload decode(FriendlyByteBuf buf){return new SlidePayload(buf.readBoolean(),buf.readVarInt(),buf.readFloat(),buf.readFloat());}
}
