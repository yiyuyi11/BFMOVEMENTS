package cn.bfm.client;

import cn.bfm.network.ProneSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** Client mirror of the server prone phase plus the camera and back-roll bridge. */
public final class ProneClient {
    public static final int STANDING = 0;
    public static final int ENTERING = 1;
    public static final int PRONE = 2;
    public static final int EXITING = 3;

    private static final Map<Integer, Sync> STATES = new HashMap<>();
    private static final java.util.Set<Integer> FINISHING = new java.util.HashSet<>();
    private static final Map<Integer, Long> SWIM_RELEASE = new HashMap<>();
    private static final java.util.Set<Integer> SETTLING=new java.util.HashSet<>();
    private static final java.util.Set<Integer> DIVE_LANDING=new java.util.HashSet<>();
    private static boolean localForced;
    private static boolean predicted;

    private record Sync(int entityId, int phase, int duration, long since, float fromEye) {
    }

    /** Local prediction used when a dive lands; removes the server round-trip "stand then fall". */
    public static void predictProne() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        long now = client.level.getGameTime();
        STATES.put(player.getId(), new Sync(player.getId(), PRONE, 1, now,cn.bfm.StanceTransition.PRONE_EYE));
        if (player.getForcedPose() != Pose.SWIMMING) {
            player.setForcedPose(Pose.SWIMMING);
        }
        player.setPose(Pose.SWIMMING);
        player.setSprinting(false);
        localForced = true;
        predicted = true;
        TaczAnimationCompat.stopAll(player);
    }

    /** A slide already lowered the camera: carry its height directly into the short prone transition. */
    public static void predictFromSlide(float fromEye){
        predictProne();
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null)STATES.put(mc.player.getId(),new Sync(mc.player.getId(),ENTERING,
                cn.bfm.StanceTransition.duration(fromEye,true),mc.level.getGameTime(),fromEye));
    }
    public static void predictTransition(boolean down){
        var mc=Minecraft.getInstance();var player=mc.player;
        if(player==null||mc.level==null)return;
        Sync current=STATES.get(player.getId());
        if(!down&&current==null)return;
        int phase=down?ENTERING:EXITING;
        if(current!=null&&(current.phase()==phase||down&&current.phase()==PRONE))return;
        float from=current!=null?eyeHeight(player):SlideClient.isActive()?ServerEyeSettings.slide():player.getEyeHeight();
        STATES.put(player.getId(),new Sync(player.getId(),phase,cn.bfm.StanceTransition.duration(from,down),mc.level.getGameTime(),from));
        SETTLING.remove(player.getId());DIVE_LANDING.remove(player.getId());predicted=true;localForced=true;
        player.setForcedPose(Pose.SWIMMING);player.setPose(Pose.SWIMMING);player.setSprinting(false);
        cn.bfm.StanceFireLock.begin(player,down);
        TaczAnimationCompat.stopAll(player);
        AnimController.onProne(player.getId(),phase);
    }
    public static void predictFromDive(float fromEye){
        predictFromSlide(fromEye);
        var mc=Minecraft.getInstance();if(mc.player!=null){DIVE_LANDING.add(mc.player.getId());cn.bfm.StanceFireLock.begin(mc.player,true);}
    }
    public static boolean confirmedProne(){return isLocalProne()&&!predicted;}

    public static void accept(ProneSyncPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && payload.entityId() == client.player.getId()) {
            cn.bfm.BattlefieldMovement.LOG.debug("[BFM] prone sync phase={} dur={} now={}",
                    payload.phase(), payload.duration(),
                    client.level == null ? -1 : client.level.getGameTime());
        }
        if (payload.phase() == STANDING) {
            if(predicted&&client.player!=null&&payload.entityId()==client.player.getId()&&DiveClient.awaitingLandingProne())return;
            Sync previous = STATES.get(payload.entityId());
            // Finish presenting our exit before handing the model to vanilla.
            if (previous != null && previous.phase() == EXITING && client.level != null
                    && client.level.getGameTime() - previous.since() < previous.duration()
                    && client.level.getEntity(payload.entityId()) instanceof Player subject
                    && subject.isAlive() && !subject.isPassenger() && !subject.isSpectator()) {
                FINISHING.add(payload.entityId());
                return;
            }
            finish(payload.entityId(), client);
            return;
        }
        long now = client.level == null ? 0L : client.level.getGameTime();
        Sync previous = STATES.get(payload.entityId());
        boolean own=client.player!=null && payload.entityId()==client.player.getId();
        if(own && previous!=null && previous.phase()==ENTERING && payload.phase()==PRONE
                && now-previous.since()<previous.duration()){
            SETTLING.add(payload.entityId());predicted=false;return;
        }
        if(own && predicted && previous!=null && previous.phase()==payload.phase()){
            predicted=false;return;
        }
        if(own && (payload.phase()==ENTERING||payload.phase()==EXITING)
                && (previous==null||previous.phase()!=payload.phase()))cn.bfm.StanceFireLock.begin(client.player,payload.phase()==ENTERING);
        // Duplicate synchronizations acknowledge a phase; they never restart it.
        if (previous != null && previous.phase() == payload.phase()
                && !(predicted && client.player != null && payload.entityId() == client.player.getId())) {
            return;
        }
        FINISHING.remove(payload.entityId());
        STATES.put(payload.entityId(), new Sync(payload.entityId(), payload.phase(),
                Math.max(1, payload.duration()), payload.since(),cn.bfm.StanceTransition.clampEye(payload.fromEye())));
        if (client.level != null && client.level.getEntity(payload.entityId()) instanceof
                net.minecraft.client.player.AbstractClientPlayer subject) {
            TaczAnimationCompat.stopAll(subject);
        }
        if (client.player != null && payload.entityId() == client.player.getId()) {
            predicted = false;
            SlideClient.stop(client.player, true);
            client.player.setForcedPose(Pose.SWIMMING);
            client.player.setPose(Pose.SWIMMING);
            localForced = true;
        }
    }

    private static void finish(int entityId, Minecraft client) {
            FINISHING.remove(entityId);DIVE_LANDING.remove(entityId);SETTLING.remove(entityId);
            Sync removed = STATES.remove(entityId);
            if (removed != null && client.level != null) {
                // Allow time for the entity pose metadata to catch up, without
                // accumulating a second vanilla swim -> stand transition.
                SWIM_RELEASE.put(entityId, client.level.getGameTime() + 14L);
            }
            if (removed != null && client.level != null
                    && client.level.getEntity(entityId) instanceof Player subject
                    && subject != client.player && !subject.hasPose(Pose.SWIMMING)) subject.refreshDimensions();
            if (client.player != null && entityId == client.player.getId()) {
                if (localForced) {
                    client.player.setForcedPose(null);
                    localForced = false;
                }
                // TaCZ's PlayerAnimator layer can still be fading from "lie" to
                // its standing animation here, which produced a second body
                // stand-up after our custom exit. Stop it at the hand-off.
                TaczAnimationCompat.stopAll(client.player);
                boolean crouch=client.options.keyShift.isDown();
                client.player.input.shiftKeyDown=crouch;client.player.setShiftKeyDown(crouch);
                client.player.setPose(crouch?Pose.CROUCHING:Pose.STANDING);
                SlideClient.stop(client.player, false);
            }
    }

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        long now = client.level.getGameTime();
        SWIM_RELEASE.values().removeIf(until -> until < now);
        for(int id:java.util.List.copyOf(SETTLING)){
            Sync state=STATES.get(id);
            if(state==null||state.phase()!=ENTERING){SETTLING.remove(id);continue;}
            if(now-state.since()>=state.duration()){
                STATES.put(id,new Sync(id,PRONE,0,now,cn.bfm.StanceTransition.PRONE_EYE));SETTLING.remove(id);
            }
        }
        for (int entityId : java.util.List.copyOf(FINISHING)) {
            Sync state = STATES.get(entityId);
            if (state == null || client.level.getGameTime() - state.since() >= state.duration()) {
                finish(entityId, client);
            }
        }
        Sync own = STATES.get(player.getId());
        if (own == null) {
            if (localForced) {
                player.setForcedPose(null);
                localForced = false;
            }
            return;
        }
        if (predicted && client.level.getGameTime() - own.since() > 25L) {
            // The server rejected the dive-landing request (e.g. it still saw us
            // airborne). Drop the visual prediction instead of lying forever.
            STATES.remove(player.getId());
            if (localForced) {
                player.setForcedPose(null);
                localForced = false;
            }
            predicted = false;
            return;
        }
        if (own.phase() != EXITING) {
            player.setSprinting(false);
        }
        if (player.getForcedPose() != Pose.SWIMMING) {
            player.setForcedPose(Pose.SWIMMING);
        }
        player.setPose(Pose.SWIMMING);
        localForced = true;

        // The addon's 37 stationary angle poses already roll the body onto its
        // back when the view turns behind you; no extra supine switch needed.
    }

    public static boolean isLocalProne() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && isProne(client.player);
    }

    public static boolean isProne(Player player) {
        Sync sync = STATES.get(player.getId());
        return sync != null && sync.phase() != STANDING;
    }

    public static boolean suppressVanillaSwimming(Player player) {
        // Actual swimming and flight still belong to vanilla.
        if (player.isInWater() || player.isSwimming() || player.isFallFlying()) return false;
        return isProne(player) || player.level().getGameTime()
                <= SWIM_RELEASE.getOrDefault(player.getId(), Long.MIN_VALUE);
    }

    public static boolean isEntering(Player player) {
        Sync sync = STATES.get(player.getId());
        return sync != null && sync.phase() == ENTERING;
    }

    public static int phase(Player player) {
        Sync sync = STATES.get(player.getId());
        return sync == null ? STANDING : sync.phase();
    }

    public static float progress(Player player, float partial) {
        Minecraft client = Minecraft.getInstance();
        Sync sync = STATES.get(player.getId());
        if (sync == null || client.level == null) {
            return 0.0F;
        }
        // Prediction continues on the same timeline when the server acknowledges the phase.
        return Mth.clamp((client.level.getGameTime() - sync.since() + partial)
                / (float) Math.max(1, sync.duration()), 0.0F, 1.0F);
    }

    /** Explicit camera/bullet eye height during the whole prone lifecycle. */
    public static float eyeHeight(Player player) {
        return eyeHeight(player, 0.0F);
    }

    public static float eyeHeight(Player player, float partial) {
        Sync sync=STATES.get(player.getId());
        if(sync==null)return cn.bfm.StanceTransition.STAND_EYE;
        if(sync.phase()==PRONE)return cn.bfm.StanceTransition.PRONE_EYE;
        return cn.bfm.StanceTransition.height(sync.fromEye(),sync.phase()==ENTERING,progress(player,partial),1);
    }

    public static float amount(Player player,float partial){
        if(DIVE_LANDING.contains(player.getId())&&phase(player)==ENTERING)return 1;
        return cn.bfm.StanceTransition.amount(eyeHeight(player,partial));
    }
    public static float enterClipProgress(Player player,float partial){
        Sync sync=STATES.get(player.getId());
        if(sync==null)return 0;
        float from=cn.bfm.StanceTransition.amount(sync.fromEye());
        return from+(1-from)*progress(player,partial);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    public static float cameraLift(float partial) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return 0.0F;
        }
        Sync sync = STATES.get(player.getId());
        if (sync == null) {
            return 0.0F;
        }
        float actual = player.getEyeHeight();
        if(sync.phase()==ENTERING||sync.phase()==EXITING)return eyeHeight(player,partial)-actual;
        return 0.0F;
    }

    public static void reset() {
        STATES.clear();
        FINISHING.clear();SETTLING.clear();DIVE_LANDING.clear();cn.bfm.StanceFireLock.clearClient();
        SWIM_RELEASE.clear();
        localForced = false;
        predicted = false;
    }

    private ProneClient() {
    }
}
