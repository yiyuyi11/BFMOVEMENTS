package cn.bfm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;

/** Server-side lifecycle for prone and slide bookkeeping. */
public final class CommonEvents {
    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!BattlefieldMovement.enabled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            BfmNetwork.sendEyeSettings(player);
            MasterSwitch.sendTo(player);
        }
    }
    @SubscribeEvent
    public void tick(PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer player) {
            if (BattlefieldMovement.enabled()) {
                ProneManager.tick(player);
                ProneGeometry.refresh(player);
            }
            SlideGeometry.refresh(player);
            DiveGeometry.refresh(player);
        }
    }

    @SubscribeEvent
    public void startTracking(PlayerEvent.StartTracking event) {
        if (!BattlefieldMovement.enabled()) return;
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject) {
            ProneManager.sendCurrent(viewer, subject);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!BattlefieldMovement.enabled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ProneManager.clear(player, false);
            SlideServer.clear(player.getUUID());
            DiveServer.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!BattlefieldMovement.enabled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ProneManager.clear(player, true);
        }
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!BattlefieldMovement.enabled()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ProneManager.clear(player, true);
        }
    }
}
