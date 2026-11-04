package cn.bfm;

import net.minecraft.world.entity.player.Player;

/**
 * Explicit first-person eye height for our stances. Do not derive slide/dive
 * from the current pose dimensions: when a slide starts in the same tick that a
 * prone forced pose is being released, the pose can still be SWIMMING and the
 * old calculation put the camera at 0.08.
 */
public final class ActionEyeHeight {
    public static float slideHeight() {
        return Math.clamp(1.62F - (float) (double) BfmConfig.SLIDE_CAMERA_DROP.get(), 0.08F, (float) SlideGeometry.HEIGHT - .06F);
    }

    public static float diveHeight() {
        return Math.max(0.08F, 1.62F - (float) (double) BfmConfig.DIVE_CAMERA_DROP.get());
    }
    public static boolean overrides(Player player) {
        return target(player) >= 0.0F;
    }

    /** @return the eye height, or a negative value when no stance is active. */
    public static float target(Player player) {
        if (player == null || !player.isAlive()) {
            return -1.0F;
        }
        if (!player.level().isClientSide()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer server
                    && ProneManager.isActive(server)) {
                return ProneManager.eyeHeight(server);
            }
            if (SlideServer.isSliding(player)) {
                return slideHeight();
            }
            if (DiveServer.isDiving(player)) {
                return diveHeight();
            }
            return -1.0F;
        }
        if (cn.bfm.client.ProneClient.isProne(player)) {
            return cn.bfm.client.ProneClient.eyeHeight(player);
        }
        if (player == net.minecraft.client.Minecraft.getInstance().player
                && cn.bfm.client.SlideClient.isActive()) {
            return cn.bfm.client.ServerEyeSettings.slide();
        }
        if (player == net.minecraft.client.Minecraft.getInstance().player
                && cn.bfm.client.DiveClient.isActive()) {
            return cn.bfm.client.ServerEyeSettings.dive();
        }
        return -1.0F;
    }

    private ActionEyeHeight() {
    }
}
