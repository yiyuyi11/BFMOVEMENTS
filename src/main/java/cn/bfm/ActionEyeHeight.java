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
        return Math.clamp(1.62F - (float) (double) BfmConfig.SLIDE_CAMERA_DROP.get(), 0.08F, (BfmConfig.SLIDE_CROUCH_STYLE.get() ? 1.32F : (float) SlideGeometry.HEIGHT - .06F));
    }

    public static float diveHeight() {
        return net.minecraft.util.Mth.clamp(1.62F - (float) (double) BfmConfig.DIVE_CAMERA_DROP.get(), 0.08F, (float) DiveGeometry.HEIGHT - .12F);
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
                // Same entry curve as the client, so the bullet origin follows the camera down instead
                // of jumping straight to the finished slide height on the first tick.
                return SlideServer.eyeHeight(player);
            }
            if (DiveServer.isDiving(player)) {
                return DiveServer.eyeHeight(player);
            }
            return -1.0F;
        }
        if (cn.bfm.client.ProneClient.isProne(player)) {
            return cn.bfm.client.ProneClient.eyeHeight(player);
        }
        if (player == net.minecraft.client.Minecraft.getInstance().player
                && cn.bfm.client.SlideClient.isActive()) {
            // The same entry curve the camera draws, so the aim ray and TaCZ's bullet origin sit exactly
            // where the operator is looking instead of snapping to the finished height on tick one.
            return cn.bfm.client.SlideClient.slideEye(0.0F);
        }
        if (player == net.minecraft.client.Minecraft.getInstance().player
                && cn.bfm.client.SlideClient.settling()) {
            // The exit curve as well: the slide is over, but the camera has a few ticks left to travel.
            // Handing this to vanilla here would cut straight to the pose height.
            return cn.bfm.client.SlideClient.exitEye(0.0F);
        }
        if (player == net.minecraft.client.Minecraft.getInstance().player
                && cn.bfm.client.DiveClient.isActive()) {
            return cn.bfm.client.DiveClient.eyeHeight(0);
        }
        return -1.0F;
    }

    private ActionEyeHeight() {
    }
}
