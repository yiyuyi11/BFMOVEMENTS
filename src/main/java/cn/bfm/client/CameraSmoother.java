package cn.bfm.client;

import cn.bfm.ActionEyeHeight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * First-person eye height. Stance states return an explicit target through
 * {@link ActionEyeHeight}; normal standing/crouching keeps the pose dimensions.
 *
 * <p>There is deliberately no previous/current lerp here: the entity eye height
 * and TaCZ's bullet origin use the same explicit target, so the camera must not
 * lag one tick behind it.</p>
 */
public final class CameraSmoother {
    private static float current = 1.62F;

    public static void tick() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        float action = ActionEyeHeight.target(player);
        float target = action >= 0.0F
                ? action
                : player.getEyeHeight(player.getPose());
        current = Math.max(0.08F, target);
    }

    public static float eyeHeight(float partial) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && ProneClient.isProne(client.player)) {
            return ProneClient.eyeHeight(client.player, partial);
        }
        return current;
    }

    public static void reset() {
        current = 1.62F;
    }

    private CameraSmoother() {
    }
}
