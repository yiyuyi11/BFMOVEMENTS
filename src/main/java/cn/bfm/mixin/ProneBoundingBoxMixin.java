package cn.bfm.mixin;

import cn.bfm.ProneGeometry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ProneBoundingBoxMixin {
    @Inject(method = "makeBoundingBox", at = @At("RETURN"), cancellable = true)
    private void battlefieldMovement$proneBox(CallbackInfoReturnable<AABB> cir) {
        if ((Object) this instanceof Player player) {
            var box = ProneGeometry.selectBox(player, cir.getReturnValue());
            cir.setReturnValue(cn.bfm.SlideGeometry.selectBox(player, box));
        }
    }
}
