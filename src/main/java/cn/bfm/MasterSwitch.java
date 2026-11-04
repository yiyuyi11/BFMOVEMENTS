package cn.bfm;

import cn.bfm.network.MasterStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Broadcasting and permission checks for the master switch. */
public final class MasterSwitch {
    private MasterSwitch() {}

    public static void sendTo(ServerPlayer player) {
        if (player == null) return;
        BfmNetwork.sendToPlayer(player, new MasterStatePayload(BattlefieldMovement.enabled()));
    }

    public static void broadcast() {
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        boolean state = BattlefieldMovement.enabled();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BfmNetwork.sendToPlayer(player, new MasterStatePayload(state));
        }
    }
}
