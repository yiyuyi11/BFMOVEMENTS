package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record SlidePayload(boolean active, int duration, float dirX, float dirZ, boolean crouchAfter) {
 public SlidePayload(boolean active,int duration,float dirX,float dirZ){this(active,duration,dirX,dirZ,false);}

 public static void encode(SlidePayload value,FriendlyByteBuf buf){buf.writeBoolean(value.active());buf.writeVarInt(value.duration());buf.writeFloat(value.dirX());buf.writeFloat(value.dirZ());buf.writeBoolean(value.crouchAfter());}
 public static SlidePayload decode(FriendlyByteBuf buf){return new SlidePayload(buf.readBoolean(),buf.readVarInt(),buf.readFloat(),buf.readFloat(),buf.readBoolean());}
}
