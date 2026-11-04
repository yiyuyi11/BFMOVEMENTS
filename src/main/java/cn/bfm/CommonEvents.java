package cn.bfm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;

/** Server-side lifecycle for prone and slide bookkeeping. */
public final class CommonEvents {
    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BfmNetwork.sendEyeSettings(player);
    }
    @SubscribeEvent
    public void tick(PlayerTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if (event.player instanceof ServerPlayer player) {
            ProneManager.tick(player);
        }
    }

    @SubscribeEvent
    public void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer subject) {
            ProneManager.sendCurrent(viewer, subject);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProneManager.clear(player, false);
            SlideServer.clear(player.getUUID());
            DiveServer.clear(player.getUUID());
        }
    }

    @SubscribeEvent
    public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProneManager.clear(player, true);
        }
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProneManager.clear(player, true);
        }
    }
}
