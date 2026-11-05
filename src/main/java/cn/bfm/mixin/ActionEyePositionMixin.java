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
        boolean diving = !prone && render && player.level().isClientSide()
                && cn.bfm.client.DiveClient.isActive();
        if (diving)
            origin=origin.add(0,cn.bfm.client.DiveClient.eyeHeight(partial)-player.getEyeHeight(),0);
        boolean sliding = !prone && render && player.level().isClientSide()
                && cn.bfm.client.SlideClient.isActive();
        if (sliding) {
            // Same correction the dive applies above. Without it this path adds getEyeHeight(), which has
            // no partial tick, so every render-side consumer of the eye position - first-person hand,
            // particles, the pick ray - would step at 20 Hz while the camera glides.
            float smooth = cn.bfm.client.SlideClient.slideEye(partial);
            origin = origin.add(0, smooth - player.getEyeHeight(), 0);
        }
        if (prone) {
            float height = player.getEyeHeight();
            if (player.level().isClientSide() && render) {
                height = cn.bfm.client.ProneClient.eyeHeight(player,partial);
                origin = origin.add(0,height-player.getEyeHeight(),0);
            }
            origin = ProneGeometry.headPosition(player,origin,ProneGeometry.bodyYaw(player,partial),StanceTransition.amount(height),partial);
        }
        // A prone head is already clipped against blocks. Snapping it to the
        // next tick's eye near cover makes the camera jump at the tick boundary.
        //
        // No per-stance exception is needed here: coverSafeEye now only clamps when the interpolated eye
        // sees LESS far than the simulation eye, so an ordinary slide or dive - where the interpolated eye
        // lags behind and therefore sees farther - passes straight through and stays smooth.
        if (!prone && render && player.level().isClientSide() && partial < 1f)
            origin = ProneGeometry.coverSafeEye(player,origin,player.getEyePosition(),partial);
        result.setReturnValue(origin);
    }
}
