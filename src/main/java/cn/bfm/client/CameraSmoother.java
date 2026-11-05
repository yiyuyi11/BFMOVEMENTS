package cn.bfm.client;

import cn.bfm.ActionEyeHeight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * First-person eye height. Stance states return an explicit target through
 * {@link ActionEyeHeight}; normal standing/crouching keeps the pose dimensions.
 *
 * <p>Interpolation lives in the shared stance timelines rather than an extra camera-only
 * low-pass filter, so the aim ray and projectile origin use the same height curve.</p>
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
                : player.getDimensions(player.getPose()).eyeHeight();
        current = Math.max(0.08F, target);
    }

    public static float eyeHeight(float partial) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && ProneClient.isProne(client.player)) {
            return ProneClient.eyeHeight(client.player, partial);
        }
        if(client.player!=null && DiveClient.isActive())return DiveClient.eyeHeight(partial);
        // Sampled with the frame's partial tick rather than the once-per-tick value, which is what turns
        // the slide's camera drop into a glide instead of twenty small steps a second.
        if(client.player!=null && SlideClient.isActive())return SlideClient.slideEye(partial);
        // After the slide ends the camera still has a few ticks of travel back up to the pose height.
        if(client.player!=null && SlideClient.settling())return SlideClient.exitEye(partial);
        return current;
    }

    /**
     * Where the camera would rest with no stance active: the current pose's eye height.
     *
     * <p>Used as the exit curve's destination so a slide that ends into a crouch settles at the crouched
     * height rather than overshooting to standing.
     */
    public static float poseEyeHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return 1.62F;
        }
        return Math.max(0.08F, client.player.getDimensions(client.player.getPose()).eyeHeight());
    }

    public static void reset() {
        current = 1.62F;
    }

    private CameraSmoother() {
    }
}
