package cn.bfm;

import cn.bfm.network.DivePayload;
import cn.bfm.network.DiveSyncPayload;
import cn.bfm.network.ProneRequestPayload;
import cn.bfm.network.ProneSyncPayload;
import cn.bfm.network.SlidePayload;
import cn.bfm.network.SlideSyncPayload;
import cn.bfm.network.MovementSettingsPayload;
import cn.bfm.network.MasterStatePayload;
import cn.bfm.network.MasterTogglePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** One registrar for all movement payloads. */
public final class BfmNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToClient(MovementSettingsPayload.TYPE, MovementSettingsPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) cn.bfm.client.ServerEyeSettings.accept(
                            payload.slideEyeHeight(), payload.diveEyeHeight());
                }));
        registrar.playToServer(ProneRequestPayload.TYPE, ProneRequestPayload.CODEC, BfmNetwork::onProneRequest);
        registrar.playToServer(SlidePayload.TYPE, SlidePayload.CODEC, BfmNetwork::onSlide);
        registrar.playToServer(DivePayload.TYPE, DivePayload.CODEC, BfmNetwork::onDive);
        registrar.playToClient(ProneSyncPayload.TYPE, ProneSyncPayload.CODEC, BfmNetwork::onProneSync);
        registrar.playToClient(SlideSyncPayload.TYPE, SlideSyncPayload.CODEC, BfmNetwork::onSlideSync);
        registrar.playToClient(DiveSyncPayload.TYPE, DiveSyncPayload.CODEC, BfmNetwork::onDiveSync);
        registrar.playToClient(MasterStatePayload.TYPE, MasterStatePayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist == Dist.CLIENT) BattlefieldMovement.acceptRemote(payload.enabled());
                }));
        registrar.playToServer(MasterTogglePayload.TYPE, MasterTogglePayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;
                    if (!player.hasPermissions(2)) {
                        BattlefieldMovement.LOG.info("{} tried to toggle the movement mod without permission",
                                player.getScoreboardName());
                        MasterSwitch.sendTo(player);
                        return;
                    }
                    if (BattlefieldMovement.enabled() != payload.enabled()) {
                        BattlefieldMovement.toggleConfig();
                    }
                    MasterSwitch.broadcast();
                }));
    }

    /** Thin wrappers so the switch code reads the same in both loader versions. */
    public static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }

    private static void onProneRequest(ProneRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ProneManager.request(player, payload.down(), payload.instant());
            }
        });
    }

    private static void onSlide(SlidePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.active()) sendEyeSettings(player);
                SlideServer.accept(player, payload);
            }
        });
    }

    private static void onDive(DivePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.active()) sendEyeSettings(player);
                DiveServer.accept(player, payload);
            }
        });
    }

    private static void onProneSync(ProneSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                cn.bfm.client.ClientNetwork.onProneSync(payload);
            }
        });
    }

    private static void onSlideSync(SlideSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                cn.bfm.client.ClientNetwork.onSlideSync(payload);
            }
        });
    }

    private static void onDiveSync(DiveSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                cn.bfm.client.ClientNetwork.onDiveSync(payload);
            }
        });
    }

    public static void sendEyeSettings(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new MovementSettingsPayload(ActionEyeHeight.slideHeight(), ActionEyeHeight.diveHeight()));
    }

    private BfmNetwork() {
    }
}
