package cn.bfm;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Collision footprint for the airborne dive, shared by the client and server. */
public final class DiveGeometry {
    public static final double HEIGHT = .88;
    private static final Map<Player, Float> HEADINGS = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean active(Player player) {
        if (!BattlefieldMovement.enabled() || player.level() == null || player.getUUID() == null) return false;
        boolean diving = player.level().isClientSide()
                ? cn.bfm.client.DiveClient.isDiving(player) : DiveServer.isDiving(player);
        return diving && player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.isInWater() && !player.isFallFlying() && !ProneState.isProne(player);
    }

    public static float yaw(Player player) {
        return HEADINGS.computeIfAbsent(player, key -> key.getYRot());
    }

    public static AABB selectBox(Player player, AABB base) {
        if (active(player)) {
            AABB low = shorten(base);
            AABB extended = ProneGeometry.envelope(low, yaw(player), 1.0F);
            return ProneGeometry.fits(player, extended) ? extended : low;
        }
        if (player.level() == null || ProneState.isProne(player)) return base;
        AABB current = player.getBoundingBox();
        // Keep clearance on a cancelled dive until the next pose fits.
        if (isDiveBox(current) && base.getYsize() > HEIGHT && !ProneGeometry.fits(player, base)) {
            return shorten(base);
        }
        return base;
    }

    public static void refresh(Player player) {
        boolean diving = active(player);
        if (!diving) HEADINGS.remove(player);
        AABB current = player.getBoundingBox();
        if (ProneState.isProne(player) || (!diving && !isDiveBox(current))) return;
        AABB base = player.getDimensions(player.getPose()).makeBoundingBox(player.position());
        AABB target = selectBox(player, base);
        if (!target.equals(current)) player.setBoundingBox(target);
    }

    private static boolean isDiveBox(AABB box) {
        return box != null && Math.abs(box.getYsize() - HEIGHT) < .001;
    }

    private static AABB shorten(AABB box) {
        return new AABB(box.minX, box.minY, box.minZ, box.maxX, Math.min(box.maxY, box.minY + HEIGHT), box.maxZ);
    }

    private DiveGeometry() {}
}
