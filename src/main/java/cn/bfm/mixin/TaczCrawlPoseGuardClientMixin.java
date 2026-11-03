package cn.bfm.mixin;

import cn.bfm.client.ProneClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ's LocalPlayerCrawl.tickCrawl runs every client tick and unconditionally
 * calls setForcedPose(null) when its own isCrawling flag is false. That flag is
 * never set by this mod (we own prone directly), so without this guard TaCZ
 * cleared the SWIMMING pose every tick: the camera/eye height flickered, the
 * body played stand-up repeatedly, and shots used a standing eye height.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.gameplay.LocalPlayerCrawl", remap = false)
public abstract class TaczCrawlPoseGuardClientMixin {
    @Inject(method = "tickCrawl", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlefieldMovement$keepPronePose(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ProneClient.isProne(player)) {
            ci.cancel();
        }
    }
}
