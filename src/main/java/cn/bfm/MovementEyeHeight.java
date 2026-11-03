package cn.bfm;

import net.minecraft.world.entity.player.Player;

/** Shared eye-height offset for the lowered slide/dive first-person camera. */
public final class MovementEyeHeight {
    public static float offset(Player player) {
        if (player == null || !player.isAlive()) {
            return 0.0F;
        }
        if (!player.level().isClientSide()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer server
                    && ProneManager.isActive(server)) {
                return ProneManager.eyeOffset(server);
            }
            if (SlideServer.isSliding(player)) {
                return (float) (double) BfmConfig.SLIDE_CAMERA_DROP.get();
            }
            if (DiveServer.isDiving(player)) {
                return (float) (double) BfmConfig.DIVE_CAMERA_DROP.get();
            }
            return 0.0F;
        }
        return cn.bfm.client.ClientEyeOffset.offset(player);
    }

    private MovementEyeHeight() {
    }
}
