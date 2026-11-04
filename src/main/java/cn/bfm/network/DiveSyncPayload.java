package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record DiveSyncPayload(int entityId, boolean active, int duration) {
 public static void encode(DiveSyncPayload value,FriendlyByteBuf buf){buf.writeVarInt(value.entityId());buf.writeBoolean(value.active());buf.writeVarInt(value.duration());}
 public static DiveSyncPayload decode(FriendlyByteBuf buf){return new DiveSyncPayload(buf.readVarInt(),buf.readBoolean(),buf.readVarInt());}
}
