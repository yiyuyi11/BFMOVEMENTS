package cn.bfm.client;

import cn.bfm.BfmConfig;
import cn.bfm.BfmKeys;
import cn.bfm.network.SlidePayload;
import cn.bfm.network.SlideSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side short combat slide.
 *
 * <p>Unlike parkour slide mods this is a single committed burst: a fixed
 * ~4-block glide that decays in under a second and then goes on cooldown.  It
 * uses the short collision box shared with the server.</p>
 */
public final class SlideClient {
    private static final double MIN_SPEED = 0.12D;

    private static boolean active;
    private static boolean suppressSneak;
    private static int ticks;
    private static int cooldown;
    private static double speed;
    private static double dirX;
    private static double dirZ;
    private static float camera;
    private static float previousCamera;

    private static final Map<Integer, Remote> REMOTE = new HashMap<>();

    private record Remote(int duration, long startTick) {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isSliding(net.minecraft.world.entity.player.Player player) {
        if (player == Minecraft.getInstance().player) return active;
        Remote remote = REMOTE.get(player.getId());
        return remote != null && player.level().getGameTime() - remote.startTick() < remote.duration();
    }

    /** True while the sneak key must not crouch, because it started this slide. */
    public static boolean suppressSneak() {
        return suppressSneak;
    }

    public static void clearSneakSuppression() {
        suppressSneak = false;
    }

    public static boolean canStart(LocalPlayer player) {
        double horizontal = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        if (!BfmConfig.SLIDE_ENABLED.get() || active || cooldown > 0
                || ProneClient.isLocalProne() || DiveClient.isActive()) {
            return false;
        }
        if (!player.onGround() || player.isPassenger() || player.isSpectator()
                || player.isInWater() || player.isFallFlying() || player.isSleeping()) {
            return false;
        }
        if (!player.isCreative() && player.getFoodData().getFoodLevel() <= 6) {
            return false;
        }
        // Sprinting is the intended trigger, but sneaking cancels the sprint flag one
        // tick before this check runs. High horizontal speed is the reliable signal.
        if (BfmConfig.SLIDE_REQUIRE_SPRINT.get() && !player.isSprinting() && horizontal < 0.12D) {
            return false;
        }
        return player.isSprinting() || horizontal > 0.06D;
    }

    public static void start(LocalPlayer player) {
        start(player, false);
    }

    public static void start(LocalPlayer player, boolean viaSneak) {
        if (!canStart(player)) {
            return;
        }
        double[] direction = inputDirection(player);
        double horizontal = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        double base = BfmConfig.SLIDE_SPEED.get();
        speed = Mth.clamp(base * (0.88D + horizontal * 0.55D), 0.30D, 0.64D);
        dirX = direction[0];
        dirZ = direction[1];
        active = true;
        ticks = 0;
        suppressSneak = viaSneak;
        player.setSprinting(false);
        PacketDistributor.sendToServer(
                new SlidePayload(true, BfmConfig.SLIDE_MAX_TICKS.get(), (float) dirX, (float) dirZ));
        AnimController.onSlide(player.getId(), true);
        if (BfmConfig.SLIDE_PARTICLES.get()) {
            player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45F, 0.55F);
            burst(player, 6);
        }
    }

    public static void tick(Minecraft client) {
        if (cooldown > 0) {
            cooldown--;
        }
        previousCamera = camera;
        double configuredDrop = BfmConfig.SLIDE_CAMERA_DROP.get();
        float target = active ? (float) configuredDrop : 0.0F;
        camera += (target - camera) * 0.38F;
        if (Math.abs(target - camera) < 0.001F) {
            camera = target;
        }

        LocalPlayer player = client.player;
        if (active && player != null) {
            ticks++;
            boolean invalid = !player.onGround() || player.isInWater() || player.isPassenger()
                    || player.horizontalCollision || ticks >= BfmConfig.SLIDE_MAX_TICKS.get();
            if (invalid) {
                stop(player, true);
            } else if (BfmConfig.SLIDE_PARTICLES.get() && ticks % 2 == 0) {
                burst(player, 2);
            }
        } else if (active) {
            active = false;
        }

        long now = client.level == null ? 0L : client.level.getGameTime();
        Iterator<Map.Entry<Integer, Remote>> it = REMOTE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Remote> entry = it.next();
            if (now - entry.getValue().startTick() > entry.getValue().duration() + 20L) {
                it.remove();
                AnimController.onSlide(entry.getKey(), false);
            }
        }
    }

    /** Applied after the player tick so vanilla friction cannot eat the burst. */
    public static void applyVelocity(LocalPlayer player) {
        if (!active) {
            return;
        }
        // Short acceleration ramp: a hard velocity snap is the main source of the
        // "stutter" you feel on the first frame.
        double ramp = Math.min(1.0D, 0.55D + ticks * 0.18D);
        player.setDeltaMovement(dirX * speed * ramp, player.getDeltaMovement().y, dirZ * speed * ramp);
        player.hasImpulse = true;
        speed *= BfmConfig.SLIDE_FRICTION.get();
        if (speed < MIN_SPEED) {
            stop(player, true);
        }
    }

    public static void stop(LocalPlayer player, boolean send) {
        if (!active) {
            return;
        }
        active = false;
        ticks = 0;
        // The suppression only exists for the duration of the slide. If sneak
        // is still physically held now, letting it through means the player
        // settles into crouch; if it was released, the player stands up.
        suppressSneak = false;
        cooldown = BfmConfig.SLIDE_COOLDOWN_TICKS.get();
        AnimController.onSlide(player == null ? -1 : player.getId(), false);
        if (send && player != null) {
            PacketDistributor.sendToServer(new SlidePayload(false, 0, 0.0F, 0.0F));
        }
    }

    public static void acceptRemote(SlideSyncPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && payload.entityId() == client.player.getId()) {
            return;
        }
        if (payload.active()) {
            long now = client.level == null ? 0L : client.level.getGameTime();
            REMOTE.put(payload.entityId(), new Remote(payload.duration(), now));
        } else {
            REMOTE.remove(payload.entityId());
        }
    }

    public static float cameraDrop(float partial) {
        // Match the server, which uses the configured drop for the whole slide
        // window. A client-side ramp made the camera/eye height lag behind the
        // server bullet origin at the start and end of a slide.
        return active ? 1.62F - ServerEyeSettings.slide() : 0.0F;
    }

    public static void reset() {
        active = false;
        suppressSneak = false;
        ticks = 0;
        cooldown = 0;
        speed = 0.0D;
        camera = 0.0F;
        previousCamera = 0.0F;
        REMOTE.clear();
    }

    private static double[] inputDirection(LocalPlayer player) {
        float forward = player.input.forwardImpulse;
        float strafe = player.input.leftImpulse;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double x = fx * forward + lx * strafe;
        double z = fz * forward + lz * strafe;
        double length = Math.sqrt(x * x + z * z);
        if (length < 0.01D) {
            Vec3 look = player.getLookAngle();
            x = look.x;
            z = look.z;
            length = Math.sqrt(x * x + z * z);
        }
        if (length < 0.0001D) {
            return new double[]{0.0D, 1.0D};
        }
        return new double[]{x / length, z / length};
    }

    private static void burst(LocalPlayer player, int count) {
        for (int i = 0; i < count; i++) {
            double spread = (player.getRandom().nextDouble() - 0.5D) * 0.5D;
            player.level().addParticle(ParticleTypes.CLOUD,
                    player.getX() - dirX * 0.45D + spread,
                    player.getY() + 0.08D,
                    player.getZ() - dirZ * 0.45D + spread,
                    dirX * -0.08D, 0.015D, dirZ * -0.08D);
        }
    }

    private SlideClient() {
    }
}
