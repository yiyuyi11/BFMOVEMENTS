package cn.bfm.mixin;

import cn.bfm.ActionEyeHeight;

import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ constructs kinetic bullets at the shooter's half-tick position:
 * {@code xOld + (x - xOld) / 2}. During a fast slide that is ~0.2 blocks behind
 * the camera, which reads as the shot leaving from the wrong place.
 *
 * <p>Use the shooter's current eye position instead; the eye height itself is
 * already kept in sync with the lowered slide/dive/prone camera by
 * EyeHeightMixin.</p>
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public abstract class TaczBulletOriginMixin {
    @Shadow
    private Vec3 startPos;

    // setPos is inherited from Entity, so it cannot be @Shadowed on
    // EntityKineticBullet; call it through an Entity cast instead.

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("TAIL"),
            require = 1
    )
    private void battlefieldMovement$currentEyeOrigin(EntityType<?> entityType, Level level, LivingEntity thrower, ItemStack gun,
                                                      ResourceLocation ammoId, ResourceLocation gunId,
                                                      ResourceLocation gunDisplayId, boolean tracer,
                                                      GunData gunData, BulletData bulletData,
                                                      CallbackInfo ci) {
        if (thrower instanceof Player player && !player.isPassenger() && ActionEyeHeight.overrides(player)) {
            Vec3 eye = thrower.getEyePosition();
            ((net.minecraft.world.entity.Entity) (Object) this).setPos(eye.x, eye.y, eye.z);
            this.startPos = eye;
        }
    }
}
