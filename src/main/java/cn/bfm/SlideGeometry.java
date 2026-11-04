package cn.bfm;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Sliding shortens the actual collision box on both sides of the connection. */
public final class SlideGeometry {
    public static final double HEIGHT = 1.18;
    private static final double CROUCH_HEIGHT = 1.38;

    private static double height(Player player) {
        boolean crouch = player.level().isClientSide()
                ? cn.bfm.client.ServerEyeSettings.tuning().crouchSlide() : BfmConfig.SLIDE_CROUCH_STYLE.get();
        return crouch ? CROUCH_HEIGHT : HEIGHT;
    }

    private static boolean low(AABB box) {
        return Math.abs(box.getYsize()-HEIGHT) < .001 || Math.abs(box.getYsize()-CROUCH_HEIGHT) < .001;
    }

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
            if (current == null || !low(current)
                    || base.getYsize() <= current.getYsize() || ProneGeometry.fits(player, base)) return base;
            // Vanilla chooses crouch or crawl on its next pose update. Until
            // then retain the low clearance instead of standing into blocks.
            return shorten(base,current.getYsize());
        }

        AABB low = shorten(base,height(player));
        var direction = ProneGeometry.forward(player.yBodyRot);
        AABB extended = low.inflate(.22 * Math.abs(direction.x), 0, .22 * Math.abs(direction.z));
        return ProneGeometry.fits(player, extended) ? extended : low;
    }

    public static void refresh(Player player) {
        if (!active(player) && !low(player.getBoundingBox())) return;
        AABB base = player.getDimensions(player.getPose()).makeBoundingBox(player.position());
        AABB target = selectBox(player, base);
        if (!target.equals(player.getBoundingBox())) player.setBoundingBox(target);
    }

    private static AABB shorten(AABB base,double height) {
        return new AABB(base.minX, base.minY, base.minZ, base.maxX,
                Math.min(base.maxY, base.minY + height), base.maxZ);
    }

    private SlideGeometry() {}
}
