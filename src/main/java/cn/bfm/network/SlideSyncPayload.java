package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record SlideSyncPayload(int entityId, boolean active, int duration, float dirX, float dirZ) {
 public static void encode(SlideSyncPayload value,FriendlyByteBuf buf){buf.writeVarInt(value.entityId());buf.writeBoolean(value.active());buf.writeVarInt(value.duration());buf.writeFloat(value.dirX());buf.writeFloat(value.dirZ());}
 public static SlideSyncPayload decode(FriendlyByteBuf buf){return new SlideSyncPayload(buf.readVarInt(),buf.readBoolean(),buf.readVarInt(),buf.readFloat(),buf.readFloat());}
}
