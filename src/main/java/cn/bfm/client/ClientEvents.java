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
    private static boolean jumpHeld;
    private static boolean suppressJump;
    private static boolean desiredDown;
    private static long lastRequest=Long.MIN_VALUE;
    private static int slideBuffer;
    private static boolean slideHeld;

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
            slideHeld = false;
            sneakHeld = false;
            jumpHeld = suppressJump = false;
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
            for (var subject : client.level.players()) {
                cn.bfm.SlideGeometry.refresh(subject);
                cn.bfm.DiveGeometry.refresh(subject);
            }
            proneHeld = false;
            slideHeld = false;
            jumpHeld = suppressJump = false;
            desiredDown = false;
            lastRequest = Long.MIN_VALUE;
            slideBuffer = 0;
            return;
        }
        handleKeys(client);
        SlideClient.tick(client);
        DiveClient.tick(client);
        ProneClient.tick(client);
        for (var subject : client.level.players()) {
            cn.bfm.ProneGeometry.refresh(subject);
            cn.bfm.SlideGeometry.refresh(subject);
            cn.bfm.DiveGeometry.refresh(subject);
        }
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
        if (ProneClient.isLocalProne() || suppressJump && Minecraft.getInstance().options.keyJump.isDown()) {
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
        jumpHeld = suppressJump = false;
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
            jumpHeld = client.options.keyJump.isDown();
            if (!jumpHeld) suppressJump = false;
            slideHeld = slideKeyboardDown(client);
            while(BfmKeys.SLIDE.consumeClick()){}
            slideBuffer=0;
            return;
        }

        boolean proneDown = BfmKeys.PRONE.isDown();
        if (proneDown && !proneHeld) {
            proneHeld = true;
            slideBuffer = 0;
            pressProne(player);
        } else if (!proneDown && proneHeld) {
            proneHeld = false;
            releaseProne(player);
        }

        boolean keyboardDown = slideKeyboardDown(client);
        boolean pressed = keyboardDown && !slideHeld;
        slideHeld = keyboardDown;
        while (BfmKeys.SLIDE.consumeClick()) pressed = true;
        boolean jumpDown = client.options.keyJump.isDown();
        boolean sneakDown = client.options.keyShift.isDown();
        boolean jumpPressed = jumpDown && !jumpHeld;
        boolean sneakPressed = sneakDown && !sneakHeld;
        jumpHeld = jumpDown;
        sneakHeld = sneakDown;
        if (!jumpDown) suppressJump = false;
        if (!sneakDown) SlideClient.clearSneakSuppression();

        if ((jumpPressed || sneakPressed) && (ProneClient.isLocalProne() || desiredDownIsPending(player)
                || DiveClient.isActive() || DiveClient.awaitingLandingProne() || SlideClient.isActive())) {
            slideBuffer = 0;
            suppressJump = jumpPressed;
            leaveLowStance(player);
        } else if (pressed && ServerEyeSettings.tuning().slide()) {
            if (ProneClient.isLocalProne() || desiredDownIsPending(player)
                    || DiveClient.isActive() || DiveClient.awaitingLandingProne()) {
                leaveLowStance(player);
                slideBuffer = 20;
            } else {
                SlideClient.start(player);
            }
        } else if (sneakPressed && ServerEyeSettings.tuning().slideOnSneak()) {
            SlideClient.start(player, true);
        }

        if (slideBuffer > 0) {
            slideBuffer--;
            if (!desiredDownIsPending(player) && !DiveClient.awaitingLandingProne() && SlideClient.canStart(player)) {
                SlideClient.start(player);
                slideBuffer = 0;
            }
        }
    }

    private static boolean desiredDownIsPending(LocalPlayer player) {
        long elapsed = player.level().getGameTime() - lastRequest;
        return desiredDown && elapsed >= 0 && elapsed <= 8;
    }

    private static void leaveLowStance(LocalPlayer player) {
        if (DiveClient.isActive() || DiveClient.awaitingLandingProne()) DiveClient.releaseStanceHold();
        if (ProneClient.isLocalProne() || desiredDownIsPending(player)) requestProne(false);
        if (SlideClient.isActive()) SlideClient.stop(player, true);
    }

    private static boolean slideKeyboardDown(Minecraft client) {
        var key = BfmKeys.SLIDE.getKey();
        return BfmKeys.SLIDE.getKeyModifier() == net.neoforged.neoforge.client.settings.KeyModifier.NONE
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
        if (DiveClient.isActive() || DiveClient.awaitingLandingProne()) {
            DiveClient.keepProneOnLanding();
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
        PacketDistributor.sendToServer(new ProneRequestPayload(down, false));
    }

    private ClientEvents() {
    }
}
