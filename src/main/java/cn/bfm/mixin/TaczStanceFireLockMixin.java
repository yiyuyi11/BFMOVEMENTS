package cn.bfm.mixin;
import cn.bfm.StanceFireLock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.LogicalSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets={"com.tacz.guns.api.event.common.GunShootEvent","com.tacz.guns.api.event.common.GunFireEvent"},remap=false)
public abstract class TaczStanceFireLockMixin {
    @Inject(method="<init>",at=@At("TAIL"),require=1)
    private void battlefieldMovement$blockDuringTransition(LivingEntity shooter,ItemStack gun,LogicalSide side,CallbackInfo ci){
        if(shooter instanceof Player player && StanceFireLock.blocked(player))((net.neoforged.bus.api.ICancellableEvent)(Object)this).setCanceled(true);
    }
}
