package cn.bfm.mixin;

import cn.bfm.client.ProneClient;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SWIMMING is our hitbox, not a second source of body animation. Client only. */
@Mixin(LivingEntity.class)
public abstract class ProneSwimAnimationMixin {
    @Shadow private float swimAmount;
    @Shadow private float swimAmountO;

    private boolean battlefieldMovement$ownsSwimming() {
        return (Object) this instanceof Player player && ProneClient.suppressVanillaSwimming(player);
    }

    @Inject(method = "updateSwimAmount", at = @At("HEAD"), cancellable = true)
    private void battlefieldMovement$noSecondStandUp(CallbackInfo ci) {
        if (battlefieldMovement$ownsSwimming()) {
            swimAmount = 0.0F;
            swimAmountO = 0.0F;
            ci.cancel();
        }
    }

    @Inject(method = "getSwimAmount", at = @At("HEAD"), cancellable = true)
    private void battlefieldMovement$noResidualRotation(float partial,
                                                        CallbackInfoReturnable<Float> ci) {
        if (battlefieldMovement$ownsSwimming()) ci.setReturnValue(0.0F);
    }
}
