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
    private static final double HALF_LENGTH = .94;
    private static final double HALF_WIDTH = .33;

    public static Vec3 forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
    }

    public static AABB envelope(AABB base, float yaw, float amount) {
        Vec3 forward = forward(yaw);
        double halfX = HALF_LENGTH * Math.abs(forward.x) + HALF_WIDTH * Math.abs(forward.z);
        double halfZ = HALF_LENGTH * Math.abs(forward.z) + HALF_WIDTH * Math.abs(forward.x);
        double growX = Math.max(0, halfX - base.getXsize() * .5) * amount;
        double growZ = Math.max(0, halfZ - base.getZsize() * .5) * amount;
        return base.inflate(growX, 0, growZ);
    }

    private static boolean active(Player player) {
        return ProneState.isProne(player) && player.hasPose(Pose.SWIMMING);
    }

    public static AABB selectBox(Player player, AABB base) {
        if (!active(player)) return base;
        float eye = player.level().isClientSide()
                ? cn.bfm.client.ProneClient.eyeHeight(player)
                : player instanceof net.minecraft.server.level.ServerPlayer server ? ProneManager.eyeHeight(server) : StanceTransition.PRONE_EYE;
        float amount = Mth.clamp(StanceTransition.amount(eye), 0f, 1f);
        if (amount < .001f) return base;
        AABB target = envelope(base, player.yBodyRot, amount);
        // Don't grow the box into a wall when a player turns or transitions.
        if (fits(player, target)) return target;
        AABB current = player.getBoundingBox();
        AABB previous = new AABB(player.getX() - current.getXsize() * .5, base.minY,
                player.getZ() - current.getZsize() * .5, player.getX() + current.getXsize() * .5,
                base.maxY, player.getZ() + current.getZsize() * .5);
        if (previous.getXsize() + previous.getZsize() <= target.getXsize() + target.getZsize()
                && fits(player, previous)) return previous;
        return base;
    }

    static boolean fits(Player player, AABB box) {
        return !player.level().getBlockCollisions(player, box.deflate(1e-7)).iterator().hasNext();
    }

    public static void refresh(Player player) {
        if (!player.hasPose(Pose.SWIMMING)) return;
        AABB base = player.getDimensions(Pose.SWIMMING).makeBoundingBox(player.position());
        AABB current = player.getBoundingBox();
        if (!active(player) && current.getXsize() <= base.getXsize() + .001
                && current.getZsize() <= base.getZsize() + .001) return;
        AABB target = selectBox(player, base);
        if (!target.equals(current)) player.setBoundingBox(target);
    }

    private ProneGeometry() {}
}
