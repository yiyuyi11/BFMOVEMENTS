package cn.bfm.network;

import net.minecraft.network.FriendlyByteBuf;

/** Server -> clients: the movement mod's authoritative on/off state. */
public record MasterStatePayload(boolean enabled) {
    public static void encode(MasterStatePayload payload, FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.enabled());
    }

    public static MasterStatePayload decode(FriendlyByteBuf buffer) {
        return new MasterStatePayload(buffer.readBoolean());
    }
}
