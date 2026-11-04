package cn.bfm.network;

import net.minecraft.network.FriendlyByteBuf;

/** Client -> server: request to turn the movement mod off/on for this world. Ignored without permission level 2. */
public record MasterTogglePayload(boolean enabled) {
    public static void encode(MasterTogglePayload payload, FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.enabled());
    }

    public static MasterTogglePayload decode(FriendlyByteBuf buffer) {
        return new MasterTogglePayload(buffer.readBoolean());
    }
}
