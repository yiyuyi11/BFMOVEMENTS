package cn.bfm.mixin;

import cn.bfm.client.ProneClient;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TaCZ's PlayerAnimator layers also write a body transform in
 * PlayerRenderer.setupRotations(RETURN). While our custom prone root is active
 * that produces a second body motion (the camera/root stays prone, the body
 * still rises), so stop feeding TaCZ's layers while prone. The custom pose owns
 * the body, head, arms and legs.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.compat.playeranimator.animation.AnimationManager", remap = false)
public abstract class TaczProneAnimatorMixin {
    @Inject(method = "isPlayerLie", at = @At("HEAD"), cancellable = true, require = 0)
    private static void battlefieldMovement$proneAnimations(AbstractClientPlayer player,
                                                            CallbackInfoReturnable<Boolean> ci) {
        if (ProneClient.isProne(player)) {
            ci.setReturnValue(true);
        }
    }

    @Inject(method = "playLowerAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void battlefieldMovement$noLower(AbstractClientPlayer player, GunDisplayInstance display,
                                                     float limbSwingAmount, CallbackInfo ci) {
        if (ProneClient.isProne(player) || cn.bfm.client.AnimController.ownsSlide(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "playLoopUpperAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void battlefieldMovement$noUpper(AbstractClientPlayer player, GunDisplayInstance display,
                                                     float limbSwingAmount, CallbackInfo ci) {
        if (ProneClient.isProne(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "playRotationAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void battlefieldMovement$noRotation(AbstractClientPlayer player, GunDisplayInstance display,
                                                        CallbackInfo ci) {
        if (ProneClient.isProne(player) || cn.bfm.client.AnimController.ownsSlide(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "playOnceAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private static void battlefieldMovement$noOnce(AbstractClientPlayer player, GunDisplayInstance display,
                                                    ResourceLocation dataId, String animationName,
                                                    CallbackInfo ci) {
        if (ProneClient.isProne(player)) {
            ci.cancel();
        }
    }
}
