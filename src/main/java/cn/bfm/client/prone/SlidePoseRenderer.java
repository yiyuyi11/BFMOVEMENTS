package cn.bfm.client.prone;

import cn.bfm.client.AnimController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** The original reclined slide, applied directly to the third-person model. */
public final class SlidePoseRenderer {
    private static final class HeadAim {
        double time;float yaw,pitch;
        HeadAim(double time,float yaw,float pitch){this.time=time;this.yaw=yaw;this.pitch=pitch;}
    }
    private static final java.util.Map<Integer,HeadAim> HEADS=new java.util.LinkedHashMap<>(64,.75f,true){
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer,HeadAim> entry){return size()>256;}
    };
    private static HeadAim aim(AbstractClientPlayer player,float partial,float yaw,float pitch){
        double now=player.level().getGameTime()+partial;
        float targetYaw=Mth.wrapDegrees(Float.isFinite(yaw)?yaw:0),targetPitch=Mth.clamp(Float.isFinite(pitch)?pitch:0,-80,80);
        HeadAim state=HEADS.get(player.getId());
        if(state==null||now<state.time||now-state.time>8){
            state=new HeadAim(now,Mth.clamp(targetYaw,-85,85),targetPitch);HEADS.put(player.getId(),state);return state;
        }
        // Looking behind can wrap +179 to -179; keep the nearest shoulder until the body turns.
        if(Math.abs(targetYaw)>120&&Math.abs(state.yaw)>40)targetYaw=Math.copySign(85,state.yaw);
        targetYaw=Mth.clamp(targetYaw,-85,85);
        float elapsed=(float)Math.max(0,Math.min(1,now-state.time));state.time=now;
        state.yaw+=Mth.clamp(targetYaw-state.yaw,-12*elapsed,12*elapsed);
        state.pitch+=Mth.clamp(targetPitch-state.pitch,-15*elapsed,15*elapsed);
        return state;
    }
    public static void apply(AbstractClientPlayer player, PlayerModel<?> model, float partial, float headYaw, float headPitch) {
        var mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        float amount = AnimController.slideWeight(player, partial);
        if (amount <= 0) return;

        // ModelPart rotates the torso at its top, but the slide rotates it
        // about the waist. Move the independent model pivots as a single rig.
        boolean crouch = cn.bfm.client.ServerEyeSettings.tuning().crouchSlide();
        boolean knees = crouch && KneeBend.available();
        ModelPart torso = model.body;
        Quaternionf standing = new Quaternionf().rotationZYX(torso.zRot, torso.yRot, torso.xRot);
        Vector3f waistOffset = standing.transform(new Vector3f(0, 12, 0));
        Vector3f waist = new Vector3f(torso.x, torso.y, torso.z).add(waistOffset);
        Vector3f hips = new Vector3f(waist).add(0, (crouch ? 7.5f : 9.5f) * amount, 0);

        torso.xRot = Mth.lerp(amount, torso.xRot, (crouch ? 22 : -63) * Mth.DEG_TO_RAD);
        torso.yRot = Mth.lerp(amount, torso.yRot, -6 * Mth.DEG_TO_RAD);
        torso.zRot = Mth.lerp(amount, torso.zRot, (crouch ? 4 : 14) * Mth.DEG_TO_RAD);
        Quaternionf reclined = new Quaternionf().rotationZYX(torso.zRot, torso.yRot, torso.xRot);
        Quaternionf change = new Quaternionf(reclined).mul(standing.conjugate());
        moveJoint(torso, waist, hips, change);
        moveJoint(model.head, waist, hips, change);
        moveJoint(model.leftArm, waist, hips, change);
        moveJoint(model.rightArm, waist, hips, change);
        moveJoint(model.leftLeg, waist, hips, change);
        moveJoint(model.rightLeg, waist, hips, change);
        // Shoulder movement carries the head pivot. Its rotation stays on the
        // aim rather than inheriting the torso pitch or a fixed animation frame.
        HeadAim aim=aim(player,partial,headYaw,headPitch);
        model.head.xRot = Mth.lerp(amount,model.head.xRot,aim.pitch*Mth.DEG_TO_RAD);
        model.head.yRot = Mth.lerp(amount,model.head.yRot,aim.yaw*Mth.DEG_TO_RAD);
        model.head.zRot *= 1-amount;
        steadyArms(player, model, amount, !crouch);
        // Right thigh drops toward the floor; left knee leads the slide.
        // Rigid-leg models use a shallower rear-leg angle to clear the ground.
        model.rightLeg.xRot = Mth.lerp(amount,model.rightLeg.xRot,(crouch ? (knees ? 43 : 69) : -89)*Mth.DEG_TO_RAD);
        model.leftLeg.xRot = Mth.lerp(amount,model.leftLeg.xRot,(crouch ? (knees ? -92 : -66) : -92)*Mth.DEG_TO_RAD);
        model.rightLeg.yRot = Mth.lerp(amount, model.rightLeg.yRot, 1 * Mth.DEG_TO_RAD);
        model.leftLeg.yRot = Mth.lerp(amount, model.leftLeg.yRot, (crouch ? 5 : 15) * Mth.DEG_TO_RAD);
        model.rightLeg.zRot = 2 * Mth.DEG_TO_RAD * amount;
        model.leftLeg.zRot = -6 * Mth.DEG_TO_RAD * amount;

        model.jacket.copyFrom(model.body);
        model.hat.copyFrom(model.head);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        if (knees) KneeBend.apply(model,amount);
    }

    private static void steadyArms(AbstractClientPlayer player, PlayerModel<?> model, float amount, boolean braceGround) {
        var main = player.getMainHandItem();
        var off = player.getOffhandItem();
        // A firearm can use both hands, even when the offhand slot is empty.
        //
        // This must still pose the arms rather than return. TaczAnimationOwnerMixin cancels TaCZ's
        // playAnimation for the whole slide and TaczProneAnimatorMixin cancels its lower and rotation
        // layers, so with a gun in hand nothing else drives the arms. Returning here left them on vanilla's
        // setupAnim walk cycle, which is the arm swinging seen while sliding with a TaCZ gun.
        if (TaczProneCompat.isFirearm(main) || TaczProneCompat.isFirearm(off)) {
            holdArms(model, amount);
            return;
        }

        HumanoidArm primary = player.getMainArm();
        HumanoidArm support = off.isEmpty() ? primary.getOpposite() : main.isEmpty() ? primary : null;

        // Both arms still have to be taken over even when nothing is braced against the ground. In the
        // crouched slide this method used to return early, leaving the arms on vanilla's setupAnim walk
        // cycle, so they kept swinging with limbSwing while the body was supposed to be locked in a slide.
        if (!braceGround || support == null) {
            tuckArms(model, amount);
            return;
        }

        ModelPart brace = support == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        float roll = (support == HumanoidArm.LEFT ? -.38f : .38f);
        // The arm is a rigid twelve-pixel part. Angle it out and back so the
        // end reaches the sole plane without continuing through the ground.
        float reach = Mth.clamp((24f - brace.y) / (12f * Mth.cos(roll)), 0f, 1f);
        float pitch = (float) Math.acos(reach);
        brace.xRot = Mth.lerp(amount, brace.xRot, pitch);
        brace.yRot = Mth.lerp(amount, brace.yRot, 0f);
        brace.zRot = Mth.lerp(amount, brace.zRot, roll);

        if (main.isEmpty() && off.isEmpty()) {
            ModelPart free = primary == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
            free.xRot = Mth.lerp(amount, free.xRot, -.32f);
            free.yRot = Mth.lerp(amount, free.yRot, 0f);
            free.zRot = Mth.lerp(amount, free.zRot, 0f);
        }
    }

    /**
     * Fold both arms in and cancel the walk swing for the crouched slide.
     *
     * <p>The arms are pulled slightly back and inward so they read as trailing behind the slide rather
     * than paddling. Interpolating from whatever the model already had keeps the entry smooth.
     */
    private static void tuckArms(PlayerModel<?> model, float amount) {
        applyTuck(model.leftArm, amount, -0.55f, 0.30f, -0.20f);
        applyTuck(model.rightArm, amount, -0.55f, -0.30f, 0.20f);
    }

    private static void applyTuck(ModelPart arm, float amount, float pitch, float yaw, float roll) {
        arm.xRot = Mth.lerp(amount, arm.xRot, pitch);
        arm.yRot = Mth.lerp(amount, arm.yRot, yaw);
        arm.zRot = Mth.lerp(amount, arm.zRot, roll);
    }

    /**
     * Hold pose for the crouched slide with a gun in hand.
     *
     * <p>TaCZ's own layers are cancelled for the duration of the slide, so this is the only thing posing
     * the arms. They are brought forward and inward into a two-handed carry held low against the slide,
     * rather than folded at the sides like the empty-handed tuck: the weapon has to stay readable.
     */
    private static void holdArms(PlayerModel<?> model, float amount) {
        applyTuck(model.rightArm, amount, -1.15f, -0.34f, 0.16f);
        applyTuck(model.leftArm, amount, -1.28f, 0.44f, -0.20f);
    }

    private static void moveJoint(ModelPart part, Vector3f waist, Vector3f hips, Quaternionf change) {
        Vector3f pivot = change.transform(new Vector3f(part.x, part.y, part.z).sub(waist)).add(hips);
        part.setPos(pivot.x, pivot.y, pivot.z);
    }

    private static final boolean PLAYER_ANIMATOR = net.minecraftforge.fml.ModList.get().isLoaded("playeranimator");

    private static final class KneeBend {
        static boolean available() {
            return PLAYER_ANIMATOR && dev.kosmx.playerAnim.impl.Helper.isBendEnabled();
        }

        static void apply(PlayerModel<?> model,float amount) {
            var bend = dev.kosmx.playerAnim.impl.animation.IBendHelper.INSTANCE;
            float right = 46*Mth.DEG_TO_RAD*amount, left = 88*Mth.DEG_TO_RAD*amount;
            bend.bend(model.rightLeg,0,right);
            bend.bend(model.rightPants,0,right);
            bend.bend(model.leftLeg,0,left);
            bend.bend(model.leftPants,0,left);
        }
    }

    public static void clearKnees(PlayerModel<?> model) {
        if (KneeBend.available()) KneeBend.apply(model,0);
    }

    private SlidePoseRenderer() {}
}
