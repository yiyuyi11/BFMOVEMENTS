package cn.bfm.network;
import net.minecraft.network.FriendlyByteBuf;
public record ProneSyncPayload(int entityId, int phase, int duration, float fromEye, long since) {
 public static void encode(ProneSyncPayload value,FriendlyByteBuf buf){buf.writeVarInt(value.entityId());buf.writeVarInt(value.phase());buf.writeVarInt(value.duration());buf.writeFloat(value.fromEye());buf.writeVarLong(value.since());}
 public static ProneSyncPayload decode(FriendlyByteBuf buf){return new ProneSyncPayload(buf.readVarInt(),buf.readVarInt(),buf.readVarInt(),buf.readFloat(),buf.readVarLong());}
}
