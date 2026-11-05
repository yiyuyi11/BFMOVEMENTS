package cn.bfm;

import net.minecraft.world.entity.player.Player;

/** Shared "this player is currently in our prone stance" test. */
public final class ProneState {
    public static boolean isProne(Player player) {
        if (player == null || player.level() == null || player.getUUID() == null) {
            return false;
        }
        if (!player.level().isClientSide()) {
            return ProneManager.isActive(player) && player.isAlive();
        }
        return cn.bfm.client.ProneClient.isProne(player) && player.isAlive();
    }

    private ProneState() {
    }
}
