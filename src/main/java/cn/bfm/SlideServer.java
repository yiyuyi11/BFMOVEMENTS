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
    /**
     * When the slide began and where the eyes were, mirroring {@link DiveServer}.
     *
     * <p>Without these the server would keep spawning bullets from the finished slide height on the very
     * first tick, while the client's camera is still easing down — the shot would appear to leave from
     * somewhere the operator is not looking. Both sides now read the same curve.
     */
    private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> FROM_EYE = new ConcurrentHashMap<>();
    private static final long MIN_INTERVAL_MS = 250L;

    public static void accept(ServerPlayer player, SlidePayload payload) {
        int duration = Math.max(1, Math.min(60, payload.duration()));
        long now = System.currentTimeMillis();
        long last = LAST_BROADCAST.getOrDefault(player.getUUID(), 0L);
        if (payload.active()) {
            if (!BattlefieldMovement.enabled() || !BfmConfig.SLIDE_ENABLED.get()
                    || !player.isAlive() || player.isSpectator() || player.isPassenger()
                    || player.isInWater() || player.isFallFlying() || ProneState.isProne(player)
                    || DiveServer.isDiving(player)) return;
            duration = Math.min(duration, BfmConfig.SLIDE_MAX_TICKS.get());
            if (!isSliding(player)) {
                // Only record on a fresh slide: a repeat payload mid-slide must not restart the curve,
                // or the camera would keep snapping back up and easing down again.
                STARTED.put(player.getUUID(), player.level().getGameTime());
                FROM_EYE.put(player.getUUID(), player.getEyeHeight());
            }
            ACTIVE_UNTIL.put(player.getUUID(), player.level().getGameTime() + duration);
            if (now - last < MIN_INTERVAL_MS) {
                return;
            }
            LAST_BROADCAST.put(player.getUUID(), now);
        } else {
            LAST_BROADCAST.remove(player.getUUID());
            ACTIVE_UNTIL.remove(player.getUUID());
            STARTED.remove(player.getUUID());
            FROM_EYE.remove(player.getUUID());
            if(!ProneState.isProne(player)&&!DiveServer.isDiving(player)){
                player.setShiftKeyDown(payload.crouchAfter());
                if(payload.crouchAfter())player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            }
        }
        SlideGeometry.refresh(player);
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

    /**
     * The player's eye height part-way through the slide's entry.
     *
     * <p>Uses the same six-tick smooth curve as the client, so the server's bullet origin and the
     * camera agree on every frame of the transition. When the slide starts from a crouch, blending from
     * the recorded {@code FROM_EYE} means the shot follows the crouched height rather than assuming
     * standing height.
     */
    public static float eyeHeight(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return ActionEyeHeight.slideHeight();
        }
        float start = FROM_EYE.getOrDefault(player.getUUID(), StanceTransition.STAND_EYE);
        long began = STARTED.getOrDefault(player.getUUID(), player.level().getGameTime());
        float elapsed = player.level().getGameTime() - began;
        return StanceTransition.blend(start, ActionEyeHeight.slideHeight(), elapsed,
                StanceTransition.SLIDE_ENTER_TICKS);
    }

    public static void clear(UUID uuid) {
        LAST_BROADCAST.remove(uuid);
        ACTIVE_UNTIL.remove(uuid);
        STARTED.remove(uuid);
        FROM_EYE.remove(uuid);
    }

    private SlideServer() {
    }
}
