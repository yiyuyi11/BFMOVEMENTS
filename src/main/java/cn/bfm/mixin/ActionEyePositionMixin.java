package cn.bfm.mixin;

import cn.bfm.ProneGeometry;
import cn.bfm.ProneState;
import cn.bfm.StanceTransition;
import cn.bfm.ActionEyeHeight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Match the pick ray and projectile origin to the prone camera's wall-clipped head position. */
@Mixin(Entity.class)
public abstract class ActionEyePositionMixin {
    @Inject(method="getEyePosition()Lnet/minecraft/world/phys/Vec3;",at=@At("RETURN"),cancellable=true)
    private void battlefieldMovement$eye(CallbackInfoReturnable<Vec3> result) {
        adjust(1f,false,result);
    }

    @Inject(method="getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",at=@At("RETURN"),cancellable=true)
    private void battlefieldMovement$eye(float partial,CallbackInfoReturnable<Vec3> result) {
        adjust(partial,true,result);
    }

    @org.spongepowered.asm.mixin.Unique
    private void adjust(float partial,boolean render,CallbackInfoReturnable<Vec3> result) {
        if (!((Object)this instanceof Player player)) return;
        boolean prone = ProneState.isProne(player);
        if (!prone && (!render || !player.level().isClientSide() || !ActionEyeHeight.overrides(player))) return;
        Vec3 origin = result.getReturnValue();
        if (prone) {
            float height = player.getEyeHeight();
            if (player.level().isClientSide() && render) {
                height = cn.bfm.client.ProneClient.eyeHeight(player,partial);
                origin = origin.add(0,height-player.getEyeHeight(),0);
            }
            origin = ProneGeometry.headPosition(player,origin,ProneGeometry.bodyYaw(player,partial),StanceTransition.amount(height));
        }
        if (render && player.level().isClientSide() && partial < 1f)
            origin = ProneGeometry.coverSafeEye(player,origin,player.getEyePosition(),partial);
        result.setReturnValue(origin);
    }
}
