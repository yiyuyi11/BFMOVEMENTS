package cn.bfm;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Sliding shortens the actual collision box on both sides of the connection. */
public final class SlideGeometry {
    public static final double HEIGHT = 1.18;

    private static boolean active(Player player) {
        if (!BattlefieldMovement.enabled()) return false;
        boolean sliding = player.level().isClientSide()
                ? cn.bfm.client.SlideClient.isSliding(player) : SlideServer.isSliding(player);
        return sliding && player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.isInWater() && !player.isFallFlying() && !player.hasPose(Pose.SWIMMING)
                && !ProneState.isProne(player);
    }

    public static AABB selectBox(Player player, AABB base) {
        if (!active(player)) {
            AABB current = player.getBoundingBox();
            if (current == null || Math.abs(current.getYsize() - HEIGHT) > .001
                    || base.getYsize() <= HEIGHT || ProneGeometry.fits(player, base)) return base;
            // Vanilla chooses crouch or crawl on its next pose update. Until
            // then retain the low clearance instead of standing into blocks.
            return shorten(base);
        }

        AABB low = shorten(base);
        var direction = ProneGeometry.forward(player.yBodyRot);
        AABB extended = low.inflate(.39 * Math.abs(direction.x), 0, .39 * Math.abs(direction.z));
        return ProneGeometry.fits(player, extended) ? extended : low;
    }

    public static void refresh(Player player) {
        if (!active(player) && Math.abs(player.getBoundingBox().getYsize() - HEIGHT) > .001) return;
        AABB base = player.getDimensions(player.getPose()).makeBoundingBox(player.position());
        AABB target = selectBox(player, base);
        if (!target.equals(player.getBoundingBox())) player.setBoundingBox(target);
    }

    private static AABB shorten(AABB base) {
        return new AABB(base.minX, base.minY, base.minZ, base.maxX,
                Math.min(base.maxY, base.minY + HEIGHT), base.maxZ);
    }

    private SlideGeometry() {}
}
