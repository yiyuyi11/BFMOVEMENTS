package cn.bfm.mixin;

import cn.bfm.ProneManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A prone player cannot jump out of the stance; Z is the only way up. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityJumpMixin {
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void battlefieldMovement$blockProneJump(CallbackInfo ci) {
        if (((Object) this) instanceof Player player && player.level() != null
                && !player.level().isClientSide() && ProneManager.isActive(player)) {
            ci.cancel();
        }
    }
}
