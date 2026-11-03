package cn.bfm.mixin;

import cn.bfm.client.CameraSmoother;
import cn.bfm.ActionEyeHeight;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Set the camera pivot before vanilla positions it and raycasts F5 zoom. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Unique
    private Entity battlefieldMovement$heightOwner;

    @Inject(method = "setup", at = @At("HEAD"))
    private void battlefieldMovement$camera(BlockGetter level, Entity entity, boolean detached,
                                           boolean thirdPerson, float partial, CallbackInfo ci) {
        if (!(entity instanceof LocalPlayer player)) {
            battlefieldMovement$heightOwner = null;
            return;
        }
        boolean active = ActionEyeHeight.overrides(player);
        boolean releasing = !active && battlefieldMovement$heightOwner == entity;
        if (!active && !releasing) {
            battlefieldMovement$heightOwner = null;
            return;
        }
        // Finish from the actual new pose, including a held crouch key. Do not
        // let Camera.tick's half-step towards the previous prone eye height
        // survive as a one-frame downward kick after our exit has completed.
        float target = active ? CameraSmoother.eyeHeight(partial) : player.getEyeHeight();
        eyeHeight = target;
        eyeHeightOld = target;
        battlefieldMovement$heightOwner = active ? entity : null;
        // Leave positioning to vanilla. Both front/back F5 views now use this
        // same interpolated pivot BEFORE getMaxZoom checks wall collisions.
    }
}
