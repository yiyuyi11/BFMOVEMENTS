package cn.bfm;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared prone footprint and head offset. The collider stays centered on the entity position. */
public final class ProneGeometry {
    public static final double MODEL_CENTER_SHIFT = .10;
    public static final double HEAD_FORWARD = .50 + MODEL_CENTER_SHIFT;
    private static final float FREE_LOOK_DEGREES = 35.0F;
    private static final double HEAD_CLEARANCE = .08;
    private static final double HEAD_RECOVERY_PER_TICK = .06;
    private static final double HALF_LENGTH = .72;
    private static final double HALF_WIDTH = .29;

    private record Heading(float previous, float current, double previousReach, double currentReach) {}
    private static final java.util.Map<Player,Heading> HEADINGS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static float bodyYaw(Player player,float partial) {
        Heading heading = HEADINGS.get(player);
        return heading == null ? player.getViewYRot(partial)
                : Mth.rotLerp(partial, heading.previous(), heading.current());
    }

    private static void updateHeading(Player player) {
        if (!active(player)) {
            HEADINGS.remove(player);
            return;
        }
        HEADINGS.compute(player,(key,old) -> {
            float previous = old == null ? player.getYRot() : old.current();
            // Vanilla body yaw follows movement, even when the player holds
            // the view still. A prone eye is offset from the center, so that
            // yaw would also move the camera and turn the collision envelope.
            float difference = Mth.wrapDegrees(player.getYRot() - previous);
            float excess = Math.max(0.0F, Math.abs(difference) - FREE_LOOK_DEGREES);
            float turn = Math.copySign(Math.min(9.0F, excess * .35F), difference);
            float current = previous + turn;
            double limit = headClearance(player, player.position().add(0, player.getEyeHeight(), 0),
                    forward(current), HEAD_FORWARD);
            double previousReach = old == null ? limit : old.currentReach();
            double currentReach = Math.min(limit, previousReach + HEAD_RECOVERY_PER_TICK);
            return new Heading(previous, current, previousReach, currentReach);
        });
    }

    // Prevent render interpolation from looking past a nearby obstruction
    // that still blocks the shot from the current simulation eye.
    public static Vec3 coverSafeEye(Player player,Vec3 interpolated,Vec3 current,float partial) {
        if (interpolated.distanceToSqr(current) < .000025) return interpolated;
        Vec3 ray = player.getViewVector(partial).scale(4);
        double currentReach = reach(player,current,ray);
        if (currentReach >= 4) return interpolated;
        // The interpolated eye sits between the previous and the current tick. Moving forward it is BEHIND
        // the current eye, so it is farther from anything ahead and its reach is LARGER. Treating that as
        // cover to protect against was backwards: it fires on ordinary forward motion, and returning
        // `current` there repeats one tick's position for the rest of the tick, quantising the eye to 20 Hz
        // (a visible jump every tick while the frame rate stays smooth).
        //
        // Only clamp when the interpolated eye actually sees LESS far, which is the direction where it
        // could be poking past an obstruction the simulation eye cannot see past.
        double interpolatedReach = reach(player,interpolated,ray);
        return interpolatedReach < currentReach - .16 ? current : interpolated;
    }

    private static double reach(Player player,Vec3 start,Vec3 ray) {
        var hit = player.level().clip(new net.minecraft.world.level.ClipContext(start,start.add(ray),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? 4 : start.distanceTo(hit.getLocation());
    }

    public static Vec3 forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
    }

    public static AABB envelope(AABB base, float yaw, float amount) {
        Vec3 forward = forward(yaw);
        double halfX = Math.min(.72, HALF_LENGTH * Math.abs(forward.x) + HALF_WIDTH * Math.abs(forward.z));
        double halfZ = Math.min(.72, HALF_LENGTH * Math.abs(forward.z) + HALF_WIDTH * Math.abs(forward.x));
        double growX = Math.max(0, halfX - base.getXsize() * .5) * amount;
        double growZ = Math.max(0, halfZ - base.getZsize() * .5) * amount;
        return base.inflate(growX, 0, growZ);
    }

    public static Vec3 headPosition(Player player, Vec3 origin, float yaw, float amount, float partial) {
        double distance = HEAD_FORWARD * Mth.clamp(amount, 0f, 1f);
        Heading heading = HEADINGS.get(player);
        if (heading != null) {
            distance = Math.min(distance, Mth.lerp(partial, heading.previousReach(), heading.currentReach()));
        }
        if (distance < .001) return origin;
        Vec3 direction = forward(yaw);
        return origin.add(direction.scale(headClearance(player, origin, direction, distance)));
    }

    private static double headClearance(Player player, Vec3 origin, Vec3 direction, double distance) {
        Vec3 target = origin.add(direction.scale(distance));
        AABB sweep = new AABB(origin, target).inflate(HEAD_CLEARANCE);
        double limit = distance;
        // Sweep a small volume around the eye. A center ray can miss an edge
        // for one frame and let the view jump all the way past the cover.
        for (var shape : player.level().getBlockCollisions(player, sweep)) {
            for (AABB box : shape.toAabbs()) {
                AABB expanded = box.inflate(HEAD_CLEARANCE);
                if (expanded.contains(origin)) return 0;
                var hit = expanded.clip(origin, target);
                if (hit.isPresent()) limit = Math.min(limit, origin.distanceTo(hit.get()));
            }
        }
        return limit;
    }

    private static boolean active(Player player) {
        return ProneState.isProne(player);
    }

    public static AABB selectBox(Player player, AABB base) {
        if (!active(player)) return base;
        base = player.getDimensions(Pose.SWIMMING).makeBoundingBox(
                new Vec3((base.minX+base.maxX)*.5,base.minY,(base.minZ+base.maxZ)*.5));
        float eye = player.level().isClientSide()
                ? cn.bfm.client.ProneClient.eyeHeight(player)
                : player instanceof net.minecraft.server.level.ServerPlayer server ? ProneManager.eyeHeight(server) : StanceTransition.PRONE_EYE;
        float amount = Mth.clamp(StanceTransition.amount(eye), 0f, 1f);
        if (amount < .001f) return base;
        AABB target = envelope(base, bodyYaw(player,1f), amount);
        AABB current = player.getBoundingBox();
        if (current == null) return fits(player, target) ? target : base;
        AABB previous = base.inflate(
                Math.max(0, (current.getXsize() - base.getXsize()) * .5), 0,
                Math.max(0, (current.getZsize() - base.getZsize()) * .5));
        boolean settled = amount >= .999f;
        boolean keepPrevious = fits(player, previous);
        double dx = target.getXsize() - previous.getXsize();
        double dz = target.getZsize() - previous.getZsize();
        if (settled && keepPrevious) {
            // Ignore tiny body-yaw changes, and require spare clearance before
            // expanding again near a wall. Contraction remains immediate.
            if (Math.abs(dx) < .025 && Math.abs(dz) < .025) return previous;
            AABB clearance = target.inflate(dx > 0 ? .025 : 0, 0, dz > 0 ? .025 : 0);
            if (!fits(player, clearance)) return previous;
            return target;
        }
        if (fits(player, target)) return target;
        if (keepPrevious && dx + dz >= 0) return previous;
        return base;
    }

    static boolean fits(Player player, AABB box) {
        return !player.level().getBlockCollisions(player, box.deflate(1e-7)).iterator().hasNext();
    }

    public static void refresh(Player player) {
        updateHeading(player);
        if (!player.hasPose(Pose.SWIMMING) && !active(player)) return;
        AABB base = player.getDimensions(Pose.SWIMMING).makeBoundingBox(player.position());
        AABB current = player.getBoundingBox();
        if (!active(player) && current.getXsize() <= base.getXsize() + .001
                && current.getZsize() <= base.getZsize() + .001) return;
        AABB target = selectBox(player, base);
        if (!target.equals(current)) player.setBoundingBox(target);
    }

    private ProneGeometry() {}
}
