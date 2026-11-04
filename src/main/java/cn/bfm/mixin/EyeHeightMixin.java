package cn.bfm.mixin;

import cn.bfm.ActionEyeHeight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces eye height for slide/dive/prone with an explicit target instead of
 * subtracting an offset from the current pose eye height. This keeps the camera
 * and TaCZ's bullet origin identical even when the underlying pose is still
 * transitioning.
 */
@Mixin(Entity.class)
public abstract class EyeHeightMixin {
    @Inject(method = "getEyeHeight()F", at = @At("RETURN"), cancellable = true)
    private void battlefieldMovement$eyeHeight(CallbackInfoReturnable<Float> ci) {
        if ((Object) this instanceof Player player && ActionEyeHeight.overrides(player)) {
            ci.setReturnValue(Math.max(0.08F, ActionEyeHeight.target(player)));
        }
    }

    @Inject(method = "getEyeY()D", at = @At("RETURN"), cancellable = true)
    private void battlefieldMovement$eyeY(CallbackInfoReturnable<Double> ci) {
        if ((Object) this instanceof Player player && ActionEyeHeight.overrides(player)) {
            ci.setReturnValue(player.getY() + Math.max(0.08F, ActionEyeHeight.target(player)));
        }
    }
}
