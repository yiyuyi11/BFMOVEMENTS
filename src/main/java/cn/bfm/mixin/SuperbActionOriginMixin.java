package cn.bfm.mixin;

import cn.bfm.ActionEyeHeight;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets="com.atsuishio.superbwarfare.item.gun.GunItem",remap=false)
public abstract class SuperbActionOriginMixin {
    @ModifyArgs(method={
        "shoot(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;DZLjava/util/UUID;)V",
        "shoot(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;DZLjava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V"
    },at=@At(value="INVOKE",target="Lcom/atsuishio/superbwarfare/data/gun/ShootParameters;<init>(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lcom/atsuishio/superbwarfare/data/gun/GunData;DZLjava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V"),require=0)
    private void battlefieldMovement$alignShot(Args args) {
        if (args.get(0) instanceof Player player && !player.isPassenger() && ActionEyeHeight.overrides(player))
            args.set(3,player.getEyePosition());
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="shoot(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;DZLjava/util/UUID;)V",at=@At("HEAD"),cancellable=true,require=0)
    private void battlefieldMovement$blockStance(@org.spongepowered.asm.mixin.injection.Coerce Object data,net.minecraft.world.entity.Entity shooter,double damage,boolean spread,java.util.UUID uuid,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(shooter instanceof Player player && cn.bfm.StanceFireLock.blocked(player))ci.cancel();
    }
    @org.spongepowered.asm.mixin.injection.Inject(method="shoot(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;DZLjava/util/UUID;Lnet/minecraft/world/phys/Vec3;)V",at=@At("HEAD"),cancellable=true,require=0)
    private void battlefieldMovement$blockStance(@org.spongepowered.asm.mixin.injection.Coerce Object data,net.minecraft.world.entity.Entity shooter,double damage,boolean spread,java.util.UUID uuid,net.minecraft.world.phys.Vec3 direction,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        if(shooter instanceof Player player && cn.bfm.StanceFireLock.blocked(player))ci.cancel();
    }

}
