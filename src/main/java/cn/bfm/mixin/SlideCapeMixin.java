package cn.bfm.mixin;

import cn.bfm.client.AnimController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla cape layer starts at the player root, so attach it to the posed torso. */
@Mixin(CapeLayer.class)
public abstract class SlideCapeMixin extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    protected SlideCapeMixin(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void battlefieldMovement$attachCape(PoseStack pose, MultiBufferSource buffer, int light,
                                                AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                                                float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        if (AnimController.slideWeight(player, partial) > 0f) {
            // CapeLayer has already pushed its pose. Its offset and swing are
            // now applied in torso space, and its own pop restores other layers.
            getParentModel().body.translateAndRotate(pose);
        }
    }
}
