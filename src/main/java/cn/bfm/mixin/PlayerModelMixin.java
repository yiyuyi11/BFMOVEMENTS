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
    @Unique private boolean battlefieldMovement$slidePose;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void battlefieldMovement$restorePivots(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                                 float age, float yaw, float pitch, CallbackInfo ci) {
        if (!battlefieldMovement$customPose) return;
        battlefieldMovement$customPose = false;
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        if (battlefieldMovement$slidePose) {
            cn.bfm.client.prone.SlidePoseRenderer.clearKnees(model);
            battlefieldMovement$slidePose = false;
        }
        model.head.resetPose();
        model.body.resetPose();
        model.leftArm.resetPose();
        model.rightArm.resetPose();
        model.leftLeg.resetPose();
        model.rightLeg.resetPose();
        // A pose moved these off their pivots last frame. Vanilla only copies the arms and the torso onto
        // them, so a leftover offset would leave a sleeve or the jacket floating behind.
        model.hat.resetPose();
        model.jacket.resetPose();
        model.leftSleeve.resetPose();
        model.rightSleeve.resetPose();
        model.leftPants.resetPose();
        model.rightPants.resetPose();
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
                battlefieldMovement$slidePose = true;
                cn.bfm.client.prone.SlidePoseRenderer.apply(player, model, ageInTicks - player.tickCount, netHeadYaw, headPitch);
            } else if (cn.bfm.client.prone.StandingGunPitch.apply(player, model, headPitch)) {
                battlefieldMovement$customPose = true;
            }
        }
    }
}
