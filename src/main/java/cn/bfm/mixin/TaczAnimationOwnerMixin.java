package cn.bfm.mixin;

import cn.bfm.client.ProneClient;
import com.tacz.guns.client.resource.GunDisplayInstance;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Own the whole TaCZ animation dispatch while a custom prone pose is active. */
@Pseudo
@Mixin(targets = "com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat", remap = false)
public abstract class TaczAnimationOwnerMixin {
    @Inject(method = "playAnimation", at = @At("HEAD"), cancellable = true, require = 1)
    private static void battlefieldMovement$ownProne(LivingEntity entity, GunDisplayInstance display,
                                                     float limbSwingAmount, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player && (ProneClient.isProne(player)||cn.bfm.client.AnimController.ownsSlide(player)||cn.bfm.client.DiveClient.isDiving(player))) {
            // The phase hand-off already clears old layers once. Clearing here
            // repeats reflection and rebuilds fade layers on every render.
            ci.cancel();
        }
    }
}
