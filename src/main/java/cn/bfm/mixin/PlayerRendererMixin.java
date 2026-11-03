package cn.bfm.mixin;

import cn.bfm.client.prone.DivePoseRenderer;
import cn.bfm.client.prone.PronePoseRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the prone/dive pose before rendering and replaces vanilla root rotation. */
@Mixin(value = PlayerRenderer.class, priority = 500)
public abstract class PlayerRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void battlefieldMovement$beginProne(AbstractClientPlayer player, float yaw, float partial,
                                                PoseStack stack, MultiBufferSource buffer, int light,
                                                CallbackInfo ci) {
        PronePoseRenderer.begin(player, stack, partial);
        DivePoseRenderer.begin(player, stack, partial);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void battlefieldMovement$endProne(AbstractClientPlayer player, float yaw, float partial,
                                              PoseStack stack, MultiBufferSource buffer, int light,
                                              CallbackInfo ci) {
        PronePoseRenderer.end(player);
        DivePoseRenderer.end(player);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void battlefieldMovement$rootProne(AbstractClientPlayer player, PoseStack stack, float age,
                                               float bodyYaw, float partial, float scale, CallbackInfo ci) {
        if (PronePoseRenderer.applyRoot(player, stack) || DivePoseRenderer.applyRoot(player, stack)) {
            ci.cancel();
        }
    }
}
