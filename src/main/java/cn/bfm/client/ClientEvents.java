package cn.bfm.client;

import cn.bfm.BfmConfig;
import cn.bfm.BfmKeys;
import cn.bfm.client.prone.PronePoseRenderer;
import cn.bfm.network.ProneRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;

/** Client tick, input and lifecycle wiring. */
@EventBusSubscriber(modid = cn.bfm.BattlefieldMovement.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean proneHeld;
    private static boolean sneakHeld;
    private static boolean desiredDown;
    private static long lastRequest=Long.MIN_VALUE;
    private static int slideBuffer;
    private static boolean slideHeld;

    public static void init() {
        // Static subscription only; kept for the mod constructor's client branch.
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
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
            slideHeld = false;
            sneakHeld = false;
            lastRequest=Long.MIN_VALUE;desiredDown=false;slideBuffer=0;
            return;
        }
        if (BfmKeys.TOGGLE.consumeClick()) {
            // Ask the server rather than flipping a private flag: on a server the authoritative state is the only
            // one that changes behaviour, and a local-only switch is what made the mod look impossible to turn on.
            cn.bfm.BfmNetwork.sendToServer(new cn.bfm.network.MasterTogglePayload(!cn.bfm.BattlefieldMovement.enabled()));
            if (client.player != null) {
                client.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "Battlefield Movement: requested " + (!cn.bfm.BattlefieldMovement.enabled() ? "on" : "off")), true);
            }
        }
        if (!cn.bfm.BattlefieldMovement.enabled()) {
            // Stop anything mid-stance, then stay out of the way entirely.
            SlideClient.reset();
            DiveClient.reset();
            ProneClient.reset();
            PronePoseRenderer.reset();
            CameraSmoother.reset();
            AnimController.reset();
            for (var subject : client.level.players()) cn.bfm.SlideGeometry.refresh(subject);
            proneHeld = false;
            slideHeld = false;
            return;
        }
        handleKeys(client);
        SlideClient.tick(client);
        DiveClient.tick(client);
        ProneClient.tick(client);
        for (var subject : client.level.players()) {
            cn.bfm.ProneGeometry.refresh(subject);
            cn.bfm.SlideGeometry.refresh(subject);
        }
        PronePoseRenderer.tick(client);
        CameraSmoother.tick();
        AnimController.tick();
    }

    /** Applied after the entity tick so ground friction cannot eat the burst. */
    @SubscribeEvent
    public static void playerTick(PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (event.player instanceof LocalPlayer player && player.level().isClientSide()) {
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
        slideHeld = false;
        sneakHeld = false;
        lastRequest=Long.MIN_VALUE;desiredDown=false;slideBuffer=0;
    }

    private static long rollReportedAt;

    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (event.getCamera().getEntity() instanceof LocalPlayer) {
            float partial = (float) event.getPartialTick();
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
            float partial = (float) event.getPartialTick();
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
            slideHeld = slideKeyboardDown(client);
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

        boolean keyboardDown = slideKeyboardDown(client);
        boolean pressed = keyboardDown && !slideHeld;
        slideHeld = keyboardDown;
        while (BfmKeys.SLIDE.consumeClick()) pressed = true;
        if (pressed) {
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
            if (!SlideClient.suppressSneak() && ServerEyeSettings.tuning().slideOnSneak()) {
                SlideClient.start(player, true);
            }
        } else if (!sneakDown && sneakHeld) {
            sneakHeld = false;
            SlideClient.clearSneakSuppression();
        }
    }

    private static boolean slideKeyboardDown(Minecraft client) {
        var key = BfmKeys.SLIDE.getKey();
        return BfmKeys.SLIDE.getKeyModifier() == net.minecraftforge.client.settings.KeyModifier.NONE
                && key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && key.getValue() >= 0
                && com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow().getWindow(), key.getValue());
    }

    private static void pressProne(LocalPlayer player) {
        boolean proneEnabled = ServerEyeSettings.tuning().prone();
        if(SlideClient.isActive()){
            if (!proneEnabled) return;
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
        if (DiveClient.canStart(player)) {
            DiveClient.start(player);
            return;
        }
        if (proneEnabled) requestProne(true);
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
        cn.bfm.BfmNetwork.sendToServer(new ProneRequestPayload(down, false));
    }

    private ClientEvents() {
    }
}
