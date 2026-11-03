package cn.bfm.mixin;

import cn.bfm.ProneState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Last line of defence against other mods clearing our forced SWIMMING pose.
 *
 * <p>TaCZ's crawl tick, BattlefieldOps' crawl mixins and similar systems call
 * {@code setForcedPose(null)} every tick when their own crawl flag is false.
 * While our prone state is active that clear is always wrong, so drop it at the
 * player level. Standing up removes our state first, so its own clear passes.</p>
 */
@Mixin(Player.class)
public abstract class ForcedPoseMixin {
    @Inject(method = "setForcedPose", at = @At("HEAD"), cancellable = true, require = 1)
    private void battlefieldMovement$protectPronePose(Pose pose, CallbackInfo ci) {
        if (pose == null && ProneState.isProne((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
