package cn.bfm.client;

import cn.bfm.BfmConfig;
import cn.bfm.BfmKeys;
import cn.bfm.client.prone.PronePoseRenderer;
import cn.bfm.network.ProneRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client tick, input and lifecycle wiring. */
@EventBusSubscriber(modid = cn.bfm.BattlefieldMovement.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean proneHeld;
    private static boolean sneakHeld;
    private static boolean desiredDown;
    private static long lastRequest=Long.MIN_VALUE;
    private static int slideBuffer;

    public static void init() {
        // Static subscription only; kept for the mod constructor's client branch.
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            SlideClient.reset();
            DiveClient.reset();
            ProneClient.reset();
            PronePoseRenderer.reset();
            CameraSmoother.reset();
            ServerEyeSettings.reset();
            AnimController.reset();
            AnimBridge.clearAll();
            proneHeld = false;
            sneakHeld = false;
            lastRequest=Long.MIN_VALUE;desiredDown=false;slideBuffer=0;
            return;
        }
        handleKeys(client);
        SlideClient.tick(client);
        DiveClient.tick(client);
        ProneClient.tick(client);
        PronePoseRenderer.tick(client);
        CameraSmoother.tick();
        AnimController.tick();
    }

    /** Applied after the entity tick so ground friction cannot eat the burst. */
    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof LocalPlayer player && player.level().isClientSide()) {
            SlideClient.applyVelocity(player);
            DiveClient.applyVelocity(player);
        }
    }

    @SubscribeEvent
    public static void movementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        Input input = event.getInput();
        boolean locked = SlideClient.isActive() || DiveClient.isActive();
        if (locked) {
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.jumping = false;
        }
        if (SlideClient.suppressSneak()) {
            input.shiftKeyDown = false;
        }
        if (DiveClient.isActive() || ProneClient.isLocalProne()) {
            input.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        SlideClient.reset();
        DiveClient.reset();
        ProneClient.reset();
        PronePoseRenderer.reset();
        CameraSmoother.reset();
        ServerEyeSettings.reset();
        AnimController.reset();
        AnimBridge.clearAll();
        proneHeld = false;
        sneakHeld = false;
        lastRequest=Long.MIN_VALUE;desiredDown=false;slideBuffer=0;
    }

    private static long rollReportedAt;

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (event.getCamera().getEntity() instanceof LocalPlayer) {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            // Do not tilt the aim ray independently of the rotation sent to the server.
            float extraRoll = DiveClient.cameraRoll(partial);
            event.setRoll(event.getRoll() + extraRoll);
            // Diagnostic only: reports the camera roll and the stance state while either is non-neutral, so
            // a "head stays tilted after standing up" report can be traced to a source instead of guessed at.
            long rollNow = System.currentTimeMillis();
            int rollPhase = ProneClient.phase(Minecraft.getInstance().player);
            if ((Math.abs(extraRoll) > 0.4F || rollPhase != ProneClient.STANDING) && rollNow - rollReportedAt > 500L) {
                rollReportedAt = rollNow;
                cn.bfm.BattlefieldMovement.LOG.info("[BFM] camera roll={} extra={} pronePhase={} proneAmount={} progress={}",
                        String.format("%.2f", event.getRoll()), String.format("%.2f", extraRoll), rollPhase,
                        String.format("%.3f", ProneClient.amount(Minecraft.getInstance().player, partial)),
                        String.format("%.3f", ProneClient.progress(Minecraft.getInstance().player, partial)));
            }
        }
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (event.getCamera().getEntity() instanceof LocalPlayer) {
            float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
            event.setFOV(event.getFOV() + DiveClient.fovBoost(partial));
        }
    }

    private static void handleKeys(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.screen != null) {
            // Remember the physical state so closing a screen does not fire a
            // stale key edge.
            if(player!=null&&BfmConfig.PRONE_HOLD.get()&&desiredDown)requestProne(false);
            if(BfmConfig.PRONE_HOLD.get())DiveClient.releaseStanceHold();
            proneHeld = BfmKeys.PRONE.isDown();
            sneakHeld = client.options.keyShift.isDown();
            while(BfmKeys.SLIDE.consumeClick()){}
            slideBuffer=0;
            return;
        }

        boolean proneDown = BfmKeys.PRONE.isDown();
        if (proneDown && !proneHeld) {
            proneHeld = true;
            pressProne(player);
        } else if (!proneDown && proneHeld) {
            proneHeld = false;
            releaseProne(player);
        }

        while (BfmKeys.SLIDE.consumeClick()) {
            if(ProneClient.phase(player)==ProneClient.EXITING)slideBuffer=8;
            else if (!SlideClient.isActive() && !DiveClient.isActive()) {
                SlideClient.start(player);
            }
        }
        if(slideBuffer>0){
            slideBuffer--;
            if(!ProneClient.isLocalProne()&&SlideClient.canStart(player)){SlideClient.start(player);slideBuffer=0;}
        }

        // Sprint + sneak is the primary slide trigger. Sneak is edge-detected so
        // holding it does not re-slide, and crouch is suppressed while sliding.
        boolean sneakDown = client.options.keyShift.isDown();
        if (sneakDown && !sneakHeld) {
            sneakHeld = true;
            if (!SlideClient.suppressSneak() && BfmConfig.SLIDE_ON_SNEAK.get()) {
                SlideClient.start(player, true);
            }
        } else if (!sneakDown && sneakHeld) {
            sneakHeld = false;
            SlideClient.clearSneakSuppression();
        }
    }

    private static void pressProne(LocalPlayer player) {
        if(!BfmConfig.PRONE_ENABLED.get())return;
        if(SlideClient.isActive()){
            float from=ServerEyeSettings.slide();
            // Send the stance request first: the server still owns the slide height at this point.
            requestProne(true);
            ProneClient.predictFromSlide(from);
            SlideClient.stop(player,true);
            AnimController.onProne(player.getId(),ProneClient.ENTERING);
            return;
        }
        if (DiveClient.isActive()) {
            if(BfmConfig.PRONE_HOLD.get())DiveClient.keepProneOnLanding();
            return;
        }
        long now=player.level().getGameTime();
        boolean pending=lastRequest!=Long.MIN_VALUE&&now-lastRequest>=0&&now-lastRequest<=8;
        boolean current=pending?desiredDown:ProneClient.isLocalProne()&&ProneClient.phase(player)!=ProneClient.EXITING;
        if (current) {
            requestProne(BfmConfig.PRONE_HOLD.get());
            return;
        }
        if (BfmConfig.DIVE_ENABLED.get() && DiveClient.canStart(player)) {
            DiveClient.start(player);
            return;
        }
        requestProne(true);
    }

    private static void releaseProne(LocalPlayer player) {
        if(BfmConfig.PRONE_HOLD.get()&&(DiveClient.isActive()||DiveClient.awaitingLandingProne())){
            DiveClient.releaseStanceHold();return;
        }
        if (BfmConfig.PRONE_HOLD.get() && (desiredDown||ProneClient.isLocalProne())) {
            requestProne(false);
        }
    }

    private static void requestProne(boolean down) {
        var player=Minecraft.getInstance().player;
        if(player!=null){desiredDown=down;lastRequest=player.level().getGameTime();}
        PacketDistributor.sendToServer(new ProneRequestPayload(down, false));
    }

    private ClientEvents() {
    }
}
