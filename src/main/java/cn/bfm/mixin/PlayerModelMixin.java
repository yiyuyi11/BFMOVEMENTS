package cn.bfm.mixin;

import cn.bfm.client.prone.DivePoseRenderer;
import cn.bfm.client.prone.PronePoseRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the ported prone pose, or the short dive pose, after other model setup. */
@Mixin(value = PlayerModel.class, priority = 500)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void battlefieldMovement$pronePose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                               float ageInTicks, float netHeadYaw, float headPitch,
                                               CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        if (PronePoseRenderer.active(player)) {
            PronePoseRenderer.applyPose(player, model);
        } else {
            DivePoseRenderer.applyPose(player, model);
        }
    }
}
