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
import net.neoforged.neoforge.network.PacketDistributor;

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
    private static double speed, travelled, lastX, lastZ;
    private static double dirX;
    private static double dirZ;
    private static float fromEye=1.62F;
    private static long started;
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

    public static float extension(net.minecraft.world.entity.player.Player player, float partial) {
        Long remoteStart = REMOTE.get(player.getId());
        float age = player == Minecraft.getInstance().player ? player.level().getGameTime()-started
                : remoteStart == null ? 0 : player.level().getGameTime() - remoteStart;
        return cn.bfm.StanceTransition.smooth((age + partial) / 6.0F);
    }

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
        if (!ServerEyeSettings.tuning().dive() || active || cooldown > 0
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
        var tuning = ServerEyeSettings.tuning();
        speed = Math.min(tuning.diveSpeed(),tuning.diveDistance());
        travelled = 0;
        lastX = player.getX();
        lastZ = player.getZ();
        fromEye=player.getEyeHeight();started=player.level().getGameTime();
        cn.bfm.StanceFireLock.begin(player,true);
        active = true;
        standAfterLanding=false;
        ticks = 0;
        cn.bfm.DiveGeometry.refresh(player);
        player.setSprinting(false);
        player.setDeltaMovement(dirX * speed, tuning.diveUp(), dirZ * speed);
        player.hasImpulse = true;        PacketDistributor.sendToServer(new DivePayload(true, 31));
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
                    PacketDistributor.sendToServer(new ProneRequestPayload(false,false));
                    standAfterLanding=false;
                }
            } else {
                if (proneRequestTicks % 2 == 0) {
                    PacketDistributor.sendToServer(new ProneRequestPayload(true, false));
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
            boolean invalid = !ServerEyeSettings.tuning().dive() || player.isInWater() || player.isPassenger() || player.isFallFlying();
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
        if (!active) return;
        travelled += Math.hypot(player.getX()-lastX,player.getZ()-lastZ);
        lastX = player.getX();
        lastZ = player.getZ();
        Vec3 velocity = player.getDeltaMovement();
        double horizontal = velocity.horizontalDistance();
        double remaining = Math.max(0,ServerEyeSettings.tuning().diveDistance()-travelled);
        if (horizontal > remaining)
            player.setDeltaMovement(velocity.x*remaining/horizontal,velocity.y,velocity.z*remaining/horizontal);
    }

    public static float cameraTilt(float partial) {
        return 16.0F * Mth.lerp(partial, previousAmount, amount);
    }

    public static float cameraRoll(float partial) {
        return 1.5F * Mth.lerp(partial, previousAmount, amount);
    }

    public static float fovBoost(float partial) {
        return 5.0F * Mth.lerp(partial, previousAmount, amount);
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

    public static float eyeHeight(float partial){
        var level=Minecraft.getInstance().level;
        return cn.bfm.StanceTransition.blend(fromEye,ServerEyeSettings.dive(),
                level==null?6:level.getGameTime()-started+partial,6);
    }

    public static float cameraDrop(float partial) {
        // Shared six-tick entry curve, sampled at render time instead of a height snap.
        return active ? 1.62F - eyeHeight(partial) : 0.0F;
    }

    private static void land(LocalPlayer player) {
        float landingEye=eyeHeight(0);
        active = false;
        ticks = 0;
        cooldown = BfmConfig.DIVE_COOLDOWN_TICKS.get();
        if (!standAfterLanding && ServerEyeSettings.tuning().prone()) {
            // Hand collision ownership to prone before ending the dive.
            ProneClient.predictFromDive(landingEye);
            PacketDistributor.sendToServer(new ProneRequestPayload(true, false));
            proneRequestTicks = 8;
        }
        standAfterLanding = false;
        PacketDistributor.sendToServer(new DivePayload(false, 0));
        AnimController.onDive(player.getId(), false);
        cn.bfm.DiveGeometry.refresh(player);
    }

    private static void cancel(LocalPlayer player) {
        active = false;
        ticks = 0;
        cooldown = BfmConfig.DIVE_COOLDOWN_TICKS.get();
        proneRequestTicks = 0;
        standAfterLanding=false;
        PacketDistributor.sendToServer(new DivePayload(false, 0));
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
