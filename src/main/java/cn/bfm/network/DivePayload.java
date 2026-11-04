package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record DivePayload(boolean active, int duration) {
 public static void encode(DivePayload value,FriendlyByteBuf buf){buf.writeBoolean(value.active());buf.writeVarInt(value.duration());}
 public static DivePayload decode(FriendlyByteBuf buf){return new DivePayload(buf.readBoolean(),buf.readVarInt());}
}
