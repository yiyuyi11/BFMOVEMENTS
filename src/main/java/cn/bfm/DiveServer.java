package cn.bfm;

import cn.bfm.network.DivePayload;
import cn.bfm.network.DiveSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Dive replication and the matching short-lived server-side eye-height flag. */
public final class DiveServer {
    private static final Map<UUID, Long> LAST_BROADCAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ACTIVE_UNTIL = new ConcurrentHashMap<>();
    private static final long MIN_INTERVAL_MS = 250L;

    public static void accept(ServerPlayer player, DivePayload payload) {
        int duration = Math.max(1, Math.min(60, payload.duration()));
        long now = System.currentTimeMillis();
        long last = LAST_BROADCAST.getOrDefault(player.getUUID(), 0L);
        if (payload.active()) {
            ACTIVE_UNTIL.put(player.getUUID(), player.level().getGameTime() + duration);
            if (now - last < MIN_INTERVAL_MS) {
                return;
            }
            LAST_BROADCAST.put(player.getUUID(), now);
        } else {
            LAST_BROADCAST.remove(player.getUUID());
            ACTIVE_UNTIL.remove(player.getUUID());
        }
        cn.bfm.BfmNetwork.sendToPlayersTrackingEntity(player,
                new DiveSyncPayload(player.getId(), payload.active(), duration));
    }

    public static boolean isDiving(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return false;
        }
        Long until = ACTIVE_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    public static void clear(UUID uuid) {
        LAST_BROADCAST.remove(uuid);
        ACTIVE_UNTIL.remove(uuid);
    }

    private DiveServer() {
    }
}
