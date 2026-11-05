package cn.bfm.mixin;

import cn.bfm.ActionEyeHeight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.tacz.guns.client.gameplay.LocalPlayerShoot",remap=false)
public abstract class TaczShotPoseMixin {
    @Shadow @Final private LocalPlayer player;

    @Inject(method="lambda$doShoot$2",at=@At(value="NEW",target="com/tacz/guns/network/message/ClientMessagePlayerShoot"),require=0)
    private void battlefieldMovement$sendPoseBeforeShot(CallbackInfo ci) {
        if (!player.isPassenger() && ActionEyeHeight.overrides(player)) {
            // TaCZ's shot packet carries a timestamp, but uses the last
            // position and angles received by vanilla on the server.
            player.connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(),player.getY(),
                    player.getZ(),player.getYRot(),player.getXRot(),player.onGround()));
        }
    }
}
