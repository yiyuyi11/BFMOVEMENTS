package cn.bfm.client;

import cn.bfm.BfmConfig;
import cn.bfm.network.DivePayload;
import cn.bfm.network.DiveSyncPayload;
import cn.bfm.network.ProneRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Combat dive: sprint + prone key. A short forward leap with a horizontal body,
 * landing into the prone stance. It is deliberately separate from the slide so
 * the two can be bound to different keys.
 */
public final class DiveClient {
    private static boolean active;
    private static int ticks;
    private static int cooldown;
    private static int proneRequestTicks;
    private static boolean standAfterLanding;
    private static double speed;
    private static double dirX;
    private static double dirZ;
    private static float camera;
    private static float previousCamera;
    private static float amount;
    private static float previousAmount;

    private static final Map<Integer, Long> REMOTE = new HashMap<>();

    public static boolean isActive() {
        return active;
    }
    public static boolean awaitingLandingProne(){return proneRequestTicks>0;}
    public static void releaseStanceHold(){if(active||proneRequestTicks>0)standAfterLanding=true;}
    public static void keepProneOnLanding(){standAfterLanding=false;}

    public static boolean isDiving(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return false;
        }
        if (player == Minecraft.getInstance().player) {
            return active;
        }
        return REMOTE.containsKey(player.getId());
    }

    public static boolean canStart(LocalPlayer player) {
        double horizontal = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        if (!BfmConfig.DIVE_ENABLED.get() || active || cooldown > 0
                || ProneClient.isLocalProne() || SlideClient.isActive()) {
            return false;
        }
        if (!player.onGround() || player.isPassenger() || player.isSpectator()
                || player.isInWater() || player.isFallFlying() || player.isSleeping()) {
            return false;
        }
        // Walking should still go prone, not dive. Only a real sprint (or a very
        // fast fall/momentum frame) qualifies.
        return (player.isSprinting() && horizontal > 0.08D) || horizontal > 0.22D;
    }

    public static void start(LocalPlayer player) {
        if (!canStart(player)) {
            return;
        }
        double[] direction = inputDirection(player);
        dirX = direction[0];
        dirZ = direction[1];
        speed = BfmConfig.DIVE_SPEED.get();
        active = true;
        standAfterLanding=false;
        ticks = 0;
        player.setSprinting(false);
        player.setDeltaMovement(dirX * speed, BfmConfig.DIVE_UP.get(), dirZ * speed);
        player.hasImpulse = true;        cn.bfm.BfmNetwork.sendToServer(new DivePayload(true, 18));
        AnimController.onDive(player.getId(), true);
        if (BfmConfig.SLIDE_PARTICLES.get()) {
            player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.45F);
            player.level().addParticle(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.1D, player.getZ(), 0.0D, 0.02D, 0.0D);
        }
    }

    public static void tick(Minecraft client) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (proneRequestTicks > 0) {
            if (ProneClient.confirmedProne()) {
                proneRequestTicks = 0;
                if(standAfterLanding){
                    cn.bfm.BfmNetwork.sendToServer(new ProneRequestPayload(false,false));
                    standAfterLanding=false;
                }
            } else {
                if (proneRequestTicks % 2 == 0) {
                    cn.bfm.BfmNetwork.sendToServer(new ProneRequestPayload(true, true));
                }
                proneRequestTicks--;
            }
        }
        previousCamera = camera;
        previousAmount = amount;
        double configuredDrop = BfmConfig.DIVE_CAMERA_DROP.get();
        float target = active ? (float) configuredDrop : 0.0F;
        camera += (target - camera) * 0.4F;
        if (Math.abs(target - camera) < 0.001F) {
            camera = target;
        }
        float amountTarget = active ? 1.0F : 0.0F;
        amount += (amountTarget - amount) * 0.3F;
        if (Math.abs(amountTarget - amount) < 0.001F) {
            amount = amountTarget;
        }

        LocalPlayer player = client.player;
        if (active && player != null) {
            ticks++;
            boolean landed = ticks > 4 && player.onGround();
            boolean invalid = player.isInWater() || player.isPassenger() || player.isFallFlying();
            if (landed) {
                land(player);
            } else if (invalid || ticks > 30) {
                cancel(player);
            }
        } else if (active) {
            active = false;
        }

        long now = client.level == null ? 0L : client.level.getGameTime();
        Iterator<Map.Entry<Integer, Long>> it = REMOTE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Long> entry = it.next();
            if (now - entry.getValue() > 40L) {
                it.remove();
                AnimController.onDive(entry.getKey(), false);
            }
        }
    }

    public static void applyVelocity(LocalPlayer player) {
        // Deliberately empty: the dive is a real leap. Re-setting horizontal
        // velocity every tick turned it into a grounded slide.
    }

    public static float cameraTilt(float partial) {
        return 16.0F * Mth.lerp(partial, previousAmount, amount);
    }

    public static float cameraRoll(float partial) {
        return 3.5F * Mth.lerp(partial, previousAmount, amount);
    }

    public static float fovBoost(float partial) {
        return 12.0F * Mth.lerp(partial, previousAmount, amount);
    }

    public static void acceptRemote(DiveSyncPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && payload.entityId() == client.player.getId()) {
            return;
        }
        if (payload.active()) {
            long now = client.level == null ? 0L : client.level.getGameTime();
            REMOTE.put(payload.entityId(), now);
        } else {
            REMOTE.remove(payload.entityId());
        }
    }

    public static float cameraDrop(float partial) {
        // Same reasoning as SlideClient.cameraDrop: the server applies the full
        // configured drop while the dive is active, so the client must too.
        return active ? 1.62F - ServerEyeSettings.dive() : 0.0F;
    }

    private static void land(LocalPlayer player) {
        active = false;
        ticks = 0;
        cooldown = BfmConfig.DIVE_COOLDOWN_TICKS.get();
        cn.bfm.BfmNetwork.sendToServer(new DivePayload(false, 0));
        AnimController.onDive(player.getId(), false);
        // Predict prone locally so the landing never shows a one-frame stand-up.
        ProneClient.predictProne();
        cn.bfm.BfmNetwork.sendToServer(new ProneRequestPayload(true, true));
        // The server may still see us as airborne for a tick; retry briefly.
        proneRequestTicks = 8;
    }

    private static void cancel(LocalPlayer player) {
        active = false;
        ticks = 0;
        cooldown = BfmConfig.DIVE_COOLDOWN_TICKS.get();
        proneRequestTicks = 0;
        standAfterLanding=false;
        cn.bfm.BfmNetwork.sendToServer(new DivePayload(false, 0));
        AnimController.onDive(player.getId(), false);
    }

    public static void reset() {
        active = false;
        ticks = 0;
        cooldown = 0;
        proneRequestTicks = 0;
        standAfterLanding=false;
        speed = 0.0D;
        camera = 0.0F;
        previousCamera = 0.0F;
        amount = 0.0F;
        previousAmount = 0.0F;
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

    private DiveClient() {
    }
}
