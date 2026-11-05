package cn.bfm;

import cn.bfm.network.ProneSyncPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative prone state.
 *
 * <p>The stance uses the vanilla SWIMMING pose on purpose: it is the one pose
 * that shrinks the player hitbox and drops the camera without touching entity
 * dimensions, and it is what BattlefieldOps' prone backend already speaks.
 * Compact transitions use the same sampled pose library, with six ticks down
 * and seven ticks up; reversals carry the current eye height.</p>
 */
public final class ProneManager {
    public static final int STANDING = 0;
    public static final int ENTERING = 1;
    public static final int PRONE = 2;
    public static final int EXITING = 3;

    private static final UUID SPEED_ID = UUID.nameUUIDFromBytes(
            (BattlefieldMovement.MODID + ":prone_speed").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final Map<UUID, Entry> ACTIVE = new ConcurrentHashMap<>();

    private record Entry(int phase, long since, int duration, float fromEye) {
    }

    public static void request(ServerPlayer player, boolean down, boolean instant) {
        if (player.level().isClientSide()) {
            return;
        }
        if(down&&!BfmConfig.PRONE_ENABLED.get()){broadcast(player);return;}
        Entry current = ACTIVE.get(player.getUUID());
        if (down) {
            if (current != null && current.phase()!=EXITING) {
                broadcast(player);
                return;
            }
            if (current==null && !canEnter(player)) {
                broadcast(player);
                return;
            }
            float from=current!=null?eyeHeight(player):SlideServer.isSliding(player)?ActionEyeHeight.slideHeight():player.getEyeHeight();
            int phase = instant ? PRONE : ENTERING;
            ACTIVE.put(player.getUUID(), new Entry(phase, now(player), instant ? 0 : StanceTransition.duration(from,true),StanceTransition.clampEye(from)));
            player.setForcedPose(Pose.SWIMMING);
            player.setPose(Pose.SWIMMING);
            player.setSprinting(false);
            applySpeed(player, true);
            broadcast(player);
        } else {
            if (current == null || current.phase() == EXITING) {
                broadcast(player);
                return;
            }
            if (!canStand(player)) {
                player.displayClientMessage(Component.translatable("message.battlefield_movement.no_room"), true);
                broadcast(player);
                return;
            }
            float from=eyeHeight(player);
            ACTIVE.put(player.getUUID(), new Entry(EXITING, now(player),StanceTransition.duration(from,false),from));
            broadcast(player);
        }
    }

    public static void tick(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || player.isInWater() || player.isSwimming() || player.isFallFlying()) {
            finish(player);
            return;
        }
        long elapsed = now(player) - entry.since();
        if (entry.phase() == ENTERING) {
            // Do not auto-cancel the entry on a one-tick onGround flicker:
            // changing to the 0.6-high SWIMMING hitbox can make onGround false
            // for a tick, which used to bounce enter → exit → enter.
            player.setForcedPose(Pose.SWIMMING);
            if (elapsed >= entry.duration()) {
                ACTIVE.put(player.getUUID(), new Entry(PRONE, now(player), 0,StanceTransition.PRONE_EYE));
                player.setPose(Pose.SWIMMING);
                broadcast(player);
            }
        } else if (entry.phase() == PRONE) {
            player.setForcedPose(Pose.SWIMMING);
        } else if (entry.phase() == EXITING) {
            if (!canStand(player)) {
                // Abort toward the current prone pose instead of finishing the camera inside a ceiling.
                float from=eyeHeight(player);
                ACTIVE.put(player.getUUID(),new Entry(ENTERING,now(player),StanceTransition.duration(from,true),from));
                broadcast(player);
                return;
            }
            if (elapsed >= entry.duration()) {
                finish(player);
            }
        }
    }

    public static boolean isActive(net.minecraft.world.entity.player.Player player) {
        return player != null && ACTIVE.containsKey(player.getUUID());
    }

    /** Negative lift so {@code getEyeHeight()} follows the camera during enter/exit. */
    public static float eyeOffset(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null) {
            return 0.0F;
        }
        return 0.4F - eyeHeight(player);
    }

    /** Explicit server eye height; mirrors the client's camera target. */
    public static float eyeHeight(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null) {
            return 1.62F;
        }
        long elapsed = now(player) - entry.since();
        if (entry.phase() == ENTERING) {
            return StanceTransition.height(entry.fromEye(),true,elapsed,entry.duration());
        }
        if (entry.phase() == EXITING) {
            return StanceTransition.height(entry.fromEye(),false,elapsed,entry.duration());
        }
        return 0.4F;
    }

    public static void clear(ServerPlayer player, boolean sync) {
        if (player == null) {
            return;
        }
        Entry removed = ACTIVE.remove(player.getUUID());
        if (removed != null) {
            releasePose(player);
            if (sync) {
                send(player, new ProneSyncPayload(player.getId(), STANDING, 0,StanceTransition.STAND_EYE, now(player)));
            }
        }
        applySpeed(player, false);
    }

    public static void sendCurrent(ServerPlayer viewer, ServerPlayer subject) {
        Entry entry = ACTIVE.get(subject.getUUID());
        if (entry != null) {
            send(viewer, payload(subject, entry));
        }
    }

    private static void finish(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) == null) {
            return;
        }
        releasePose(player);
        applySpeed(player, false);
        broadcast(player);
    }

    private static void releasePose(ServerPlayer player) {
        player.setForcedPose(null);
        if (canStand(player)) {
            player.setPose(Pose.STANDING);
        }
    }

    private static void applySpeed(ServerPlayer player, boolean prone) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double penalty = prone ? Math.max(0.0, Math.min(0.8, BfmConfig.PRONE_SPEED_PENALTY.get())) : 0.0;
        var modifier = speed.getModifier(SPEED_ID);
        if (modifier != null && penalty > 0.0 && modifier.getAmount() == -penalty) return;
        if (modifier != null) speed.removeModifier(SPEED_ID);
        if (penalty > 0.0) {
            speed.addTransientModifier(new AttributeModifier(
                    SPEED_ID, "prone_speed", -penalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static boolean canEnter(ServerPlayer player) {
        if (player.isSpectator() || player.isPassenger() || player.isSleeping()
                || !player.onGround() || player.isInWater() || player.isFallFlying()) {
            return false;
        }
        return player.level().noCollision(player,
                player.getDimensions(Pose.SWIMMING).makeBoundingBox(player.position()));
    }

    private static boolean canStand(ServerPlayer player) {
        return player.level().noCollision(player,
                player.getDimensions(Pose.STANDING).makeBoundingBox(player.position()));
    }

    private static void broadcast(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        BattlefieldMovement.LOG.debug("[BFM] server phase={} dur={}",
                entry == null ? STANDING : entry.phase(), entry == null ? 0 : entry.duration());
        ProneSyncPayload payload = entry == null
                ? new ProneSyncPayload(player.getId(), STANDING, 0,StanceTransition.STAND_EYE, now(player))
                : payload(player, entry);
        cn.bfm.BfmNetwork.sendToPlayersTrackingEntityAndSelf(player, payload);
    }

    private static ProneSyncPayload payload(ServerPlayer player, Entry entry) {
        return new ProneSyncPayload(player.getId(), entry.phase(), entry.duration(),entry.fromEye(), entry.since());
    }

    private static void send(ServerPlayer viewer, ProneSyncPayload payload) {
        cn.bfm.BfmNetwork.sendToPlayer(viewer, payload);
    }

    private static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }

    private ProneManager() {
    }
}
