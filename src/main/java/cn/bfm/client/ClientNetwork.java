package cn.bfm.client;

import cn.bfm.network.DiveSyncPayload;
import cn.bfm.network.ProneSyncPayload;
import cn.bfm.network.SlideSyncPayload;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Entry point for packets that only exist on the client. */
@OnlyIn(Dist.CLIENT)
public final class ClientNetwork {
    public static void onProneSync(ProneSyncPayload payload) {
        ProneClient.accept(payload);
        AnimController.onProne(payload.entityId(), payload.phase());
    }

    public static void onSlideSync(SlideSyncPayload payload) {
        SlideClient.acceptRemote(payload);
        AnimController.onSlide(payload.entityId(), payload.active());
    }

    public static void onDiveSync(DiveSyncPayload payload) {
        DiveClient.acceptRemote(payload);
        AnimController.onDive(payload.entityId(), payload.active());
    }

    private ClientNetwork() {
    }
}
