package cn.bfm.mixin;

import cn.bfm.DiveGeometry;
import cn.bfm.ProneGeometry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ProneBoundingBoxMixin {
    // Vanilla dimension refresh and some mods write boxes directly, without
    // going through makeBoundingBox. Guard tall writes at the final setter.
    @ModifyVariable(method = "setBoundingBox", at = @At("HEAD"), argsOnly = true)
    private AABB battlefieldMovement$rejectStandingBox(AABB box) {
        if (box.getYsize() > 1 && (Object)this instanceof Player player) {
            if (cn.bfm.ProneState.isProne(player)) return ProneGeometry.selectBox(player, box);
            return DiveGeometry.selectBox(player, box);
        }
        return box;
    }

    @Inject(method = "makeBoundingBox", at = @At("RETURN"), cancellable = true)
    private void battlefieldMovement$proneBox(CallbackInfoReturnable<AABB> cir) {
        if ((Object) this instanceof Player player) {
            var box = ProneGeometry.selectBox(player, cir.getReturnValue());
            cir.setReturnValue(DiveGeometry.selectBox(player, cn.bfm.SlideGeometry.selectBox(player, box)));
        }
    }
}
