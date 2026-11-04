package cn.bfm.mixin;

import cn.bfm.client.prone.DivePoseRenderer;
import cn.bfm.client.prone.PronePoseRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the ported prone pose, or the short dive pose, after other model setup. */
@Mixin(value = PlayerModel.class, priority = 500)
public abstract class PlayerModelMixin {
    @Unique
    private boolean battlefieldMovement$customPose;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void battlefieldMovement$restorePivots(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                 float age, float yaw, float pitch, CallbackInfo ci) {
        if (!battlefieldMovement$customPose) return;
        battlefieldMovement$customPose = false;
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        model.head.resetPose();
        model.body.resetPose();
        model.leftArm.resetPose();
        model.rightArm.resetPose();
        model.leftLeg.resetPose();
        model.rightLeg.resetPose();
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void battlefieldMovement$pronePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                               float ageInTicks, float netHeadYaw, float headPitch,
                                               CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        if (PronePoseRenderer.active(player)) {
            battlefieldMovement$customPose = true;
            PronePoseRenderer.applyPose(player, model);
        } else if (DivePoseRenderer.active(player)) {
            battlefieldMovement$customPose = true;
            DivePoseRenderer.applyPose(player, model);
        } else {
            // No pose owns this model any more: drop the roll a previous pose left behind, or the head would
            // stay tilted for the rest of the session.
            PronePoseRenderer.clearRoll(model);
            if (cn.bfm.client.AnimController.ownsSlide(player)) {
                battlefieldMovement$customPose = true;
                cn.bfm.client.prone.SlidePoseRenderer.apply(player, model, ageInTicks - player.tickCount);
            }
        }
    }
}
