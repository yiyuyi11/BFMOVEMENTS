package cn.bfm.mixin;

import cn.bfm.ProneManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server counterpart: TaCZ's LivingEntityCrawl.tickCrawling otherwise clears the
 * forced SWIMMING pose every tick, which made the server eye height (and thus
 * kinetic bullet origin and prone hitbox) alternate back to standing.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityCrawl", remap = false)
public abstract class TaczCrawlPoseGuardServerMixin {
    @Shadow
    @Final
    private LivingEntity shooter;

    @Inject(method = "tickCrawling", at = @At("HEAD"), cancellable = true, require = 0)
    private void battlefieldMovement$keepPronePose(CallbackInfo ci) {
        if (this.shooter instanceof Player player && ProneManager.isActive(player)) {
            ci.cancel();
        }
    }
}
