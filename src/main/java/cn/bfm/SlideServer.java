package cn.bfm;

import cn.bfm.network.SlidePayload;
import cn.bfm.network.SlideSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slide replication and a short-lived server-side state flag.
 *
 * <p>The flag is what lets the common eye-height mixin spawn bullets from the
 * lowered first-person camera position, instead of from standing eye height.</p>
 */
public final class SlideServer {
    private static final Map<UUID, Long> LAST_BROADCAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ACTIVE_UNTIL = new ConcurrentHashMap<>();
    private static final long MIN_INTERVAL_MS = 250L;

    public static void accept(ServerPlayer player, SlidePayload payload) {
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
        PacketDistributor.sendToPlayersTrackingEntity(player,
                new SlideSyncPayload(player.getId(), payload.active(), duration, payload.dirX(), payload.dirZ()));
    }

    public static boolean isSliding(net.minecraft.world.entity.player.Player player) {
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

    private SlideServer() {
    }
}
