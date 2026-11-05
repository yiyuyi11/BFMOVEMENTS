package cn.bfm.mixin;

import cn.bfm.client.ProneClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ProneViewBobMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void battlefieldMovement$skipWalkingBob(PoseStack pose, float partial, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.getCameraEntity() == client.player && ProneClient.isLocalProne()) {
            ci.cancel();
        }
    }
}
