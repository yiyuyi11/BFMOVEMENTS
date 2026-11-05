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

    private static final int STEP_AIR_TICKS = 5;

    /** Ticks spent lowering the camera at the start of a slide; shared with the server's bullet origin. */
    private static final int ENTER_TICKS = cn.bfm.StanceTransition.SLIDE_ENTER_TICKS;

    /**
     * Ticks spent raising the camera again once the slide ends.
     *
     * <p>Shorter than the entry: standing up has to feel responsive, whereas dropping into the slide is
     * the deliberate half. Without this the exit was a hard cut upward, which was the more jarring of the
     * two ends even after the entrance was smoothed.
     */
    private static final int EXIT_TICKS = 4;
    private static boolean active;
    private static boolean suppressSneak;
    /** Tick the slide ended on, or -1 while it is running; drives the exit curve. */
    private static long ended = -1L;
    /** Eye height the exit curve starts from. */
    private static float exitFrom = 1.62F;
    private static int ticks;
    private static int airTicks;
    private static int cooldown;
    private static double speed, travelled, lastX, lastZ;
    private static double dirX;
    private static double dirZ;
    /**
     * Eye height when the slide began, and the tick it began on.
     *
     * <p>The slide used to snap the camera between standing and sliding height in a single tick. The
     * dive already blends its height over a few ticks and reads far smoother, so a slide now carries the
     * same timeline: record where the eyes started and let {@link #slideEye} walk a smooth curve to the
     * slide height. Recording {@code fromEye} rather than always starting at 1.62 also makes a slide
     * started from a crouch continue from the crouched height instead of jumping up first.
     */
    private static float fromEye = 1.62F;
    private static long started;

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
        var tuning = ServerEyeSettings.tuning();
        if (!tuning.slide() || horizontal < tuning.slideMinSpeed() || active || cooldown > 0
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
        if (tuning.slideRequireSprint() && !player.isSprinting() && horizontal < 0.12D) {
            return false;
        }
        return true;
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
        var tuning = ServerEyeSettings.tuning();
        speed = tuning.slideSpeed() * (0.88D + Math.min(.40D,horizontal) * .55D);
        travelled = 0;
        lastX = player.getX();
        lastZ = player.getZ();
        dirX = direction[0];
        dirZ = direction[1];
        active = true;
        ticks = 0;
        airTicks = 0;
        fromEye = player.getEyeHeight();
        started = player.level().getGameTime();
        ended = -1L;
        suppressSneak = viaSneak;
        player.setSprinting(false);
        PacketDistributor.sendToServer(
                new SlidePayload(true, ServerEyeSettings.tuning().slideTicks(), (float) dirX, (float) dirZ));
        AnimController.onSlide(player.getId(), true);
        if (ServerEyeSettings.tuning().slideParticles()) {
            player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.45F, 0.55F);
            burst(player, 6);
        }
    }

    public static void tick(Minecraft client) {
        if (cooldown > 0) {
            cooldown--;
        }

        LocalPlayer player = client.player;
        if (active && player != null) {
            ticks++;
            // Losing support briefly at a step edge should not end a grounded slide.
            airTicks = player.onGround() ? 0 : airTicks + 1;
            boolean invalid = !ServerEyeSettings.tuning().slide() || airTicks > STEP_AIR_TICKS
                    || player.isInWater() || player.isPassenger()
                    || player.horizontalCollision || ticks >= ServerEyeSettings.tuning().slideTicks();
            if (invalid) {
                stop(player, true);
            } else if (player.onGround() && ServerEyeSettings.tuning().slideParticles() && ticks % 2 == 0) {
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
        travelled += Math.hypot(player.getX()-lastX,player.getZ()-lastZ);
        lastX = player.getX();
        lastZ = player.getZ();
        var tuning = ServerEyeSettings.tuning();
        double step = Math.min(speed*ramp,Math.max(0,tuning.slideDistance()-travelled));
        player.setDeltaMovement(dirX * step, player.getDeltaMovement().y, dirZ * step);
        player.hasImpulse = true;
        if (step <= .001) { stop(player,true); return; }
        speed *= tuning.slideFriction();
        if (speed < MIN_SPEED) {
            stop(player, true);
        }
    }

    public static void stop(LocalPlayer player, boolean send) {
        if (!active) {
            return;
        }
        boolean crouch=player!=null && Minecraft.getInstance().options.keyShift.isDown()
                && !ProneClient.isLocalProne() && !DiveClient.isActive();
        if(player!=null && !ProneClient.isLocalProne() && !DiveClient.isActive()){
            player.input.shiftKeyDown=crouch;player.setShiftKeyDown(crouch);
            if(crouch)player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
        }
        // Remember where the eyes were so the camera can ease back up instead of cutting.
        exitFrom = slideEye(0.0F);
        ended = player == null || player.level() == null ? 0L : player.level().getGameTime();
        active = false;
        ticks = 0;
        airTicks = 0;
        // The suppression only exists for the duration of the slide. If sneak
        // is still physically held now, letting it through means the player
        // settles into crouch; if it was released, the player stands up.
        suppressSneak = false;
        cooldown = ServerEyeSettings.tuning().slideCooldown();
        AnimController.onSlide(player == null ? -1 : player.getId(), false);
        if (send && player != null) {
            PacketDistributor.sendToServer(new SlidePayload(false, 0, 0.0F, 0.0F,crouch));
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

    /**
     * Eye height part-way through the slide's entry.
     *
     * <p>Shared smooth-step curve with the dive and the prone stance, so all three lower the camera the
     * same way. Sampled at render time with the frame's partial tick, which is what removes the tick-rate
     * stutter: a height that only changes 20 times a second reads as a series of small jerks, while the
     * same curve sampled per frame glides.
     *
     * @param partial the frame's interpolation fraction, or 0 when called from a tick context
     */
    public static float slideEye(float partial) {
        if (!active) {
            return ServerEyeSettings.slide();
        }
        var level = Minecraft.getInstance().level;
        float elapsed = level == null ? ENTER_TICKS : level.getGameTime() - started + partial;
        return cn.bfm.StanceTransition.blend(fromEye, ServerEyeSettings.slide(), elapsed, ENTER_TICKS);
    }

    /**
     * True while the camera is still easing back up after the slide ended.
     *
     * <p>Reported separately from {@link #isActive()} because the slide itself is over — movement, pose
     * and the sneak suppression are all finished — while the camera has a few ticks left to travel.
     */
    public static boolean settling() {
        if (ended < 0L) {
            return false;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        return level.getGameTime() - ended < EXIT_TICKS;
    }

    /**
     * Eye height during the exit curve.
     *
     * <p>Blends from wherever the camera actually was up to the player's current eye height, so a slide
     * that ended early (still part-way down) does not first snap to the full slide height.
     */
    public static float exitEye(float partial) {
        var level = Minecraft.getInstance().level;
        float elapsed = level == null ? EXIT_TICKS : level.getGameTime() - ended + partial;
        float target = cn.bfm.client.CameraSmoother.poseEyeHeight();
        return cn.bfm.StanceTransition.blend(exitFrom, target, elapsed, EXIT_TICKS);
    }

    public static float cameraDrop(float partial) {
        // Blend instead of snapping. The eye height, the aim ray and TaCZ's bullet origin all read the
        // same curve through ActionEyeHeight, so smoothing the camera does not desync the shot.
        return active ? 1.62F - slideEye(partial) : 0.0F;
    }

    public static void reset() {
        active = false;
        suppressSneak = false;
        ticks = 0;
        airTicks = 0;
        cooldown = 0;
        speed = 0.0D;
        fromEye = 1.62F;
        started = 0L;
        ended = -1L;
        exitFrom = 1.62F;
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
