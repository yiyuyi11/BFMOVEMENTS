package cn.bfm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Client half of {@link cn.bfm.MovementEyeHeight}. */
public final class ClientEyeOffset {
    public static float offset(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (player == null || player != client.player) {
            return 0.0F;
        }
        if (ProneClient.isLocalProne()) {
            // During enter/exit the camera is between 1.62 and 0.4 while the
            // SWIMMING pose keeps the base eye at 0.4. Return the negative lift
            // so bullets spawn where the camera actually is.
            int phase = ProneClient.phase(player);
            float progress = smooth(ProneClient.progress(player, 0.0F));
            float desired;
            if (phase == ProneClient.ENTERING) {
                desired = Mth.lerp(progress, 1.62F, 0.4F);
            } else if (phase == ProneClient.EXITING) {
                desired = Mth.lerp(progress, 0.4F, 1.62F);
            } else {
                desired = 0.4F;
            }
            return 0.4F - desired;
        }
        // Tick context, so the partial tick is 0 by definition. The per-frame interpolation the camera
        // needs happens in CameraSmoother.eyeHeight(partial), which has the render frame's fraction.
        return SlideClient.cameraDrop(0.0F) + DiveClient.cameraDrop(0.0F);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private ClientEyeOffset() {
    }
}
