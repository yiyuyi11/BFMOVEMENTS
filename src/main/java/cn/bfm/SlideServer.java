package cn.bfm;

import cn.bfm.network.SlidePayload;
import cn.bfm.network.SlideSyncPayload;
import net.minecraft.server.level.ServerPlayer;

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
    /** Tick the slide began on, and the eye height it began from; mirror of the client's entry curve. */
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
            // Only record the start once: a repeat payload mid-slide must not restart the curve, or the
            // camera would keep dropping back to the top of the animation.
            if (!isSliding(player)) {
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
        cn.bfm.BfmNetwork.sendToPlayersTrackingEntity(player,
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
     * Eye height during the entry curve, for the server-side bullet origin and aim ray.
     *
     * <p>Blends over the same {@link StanceTransition#SLIDE_ENTER_TICKS} the client camera uses, so the
     * two stay in agreement throughout the transition instead of only at its ends.
     */
    public static float eyeHeight(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return ActionEyeHeight.slideHeight();
        }
        Long began = STARTED.get(player.getUUID());
        Float from = FROM_EYE.get(player.getUUID());
        if (began == null || from == null) {
            return ActionEyeHeight.slideHeight();
        }
        float elapsed = player.level().getGameTime() - began;
        return StanceTransition.blend(from, ActionEyeHeight.slideHeight(), elapsed,
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
