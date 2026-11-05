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
        model.head.xRot = Mth.lerp(amount,model.head.xRot,headPitch*Mth.DEG_TO_RAD);
        model.head.yRot = Mth.lerp(amount,model.head.yRot,headYaw*Mth.DEG_TO_RAD);
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
        if (TaczProneCompat.isFirearm(main) || TaczProneCompat.isFirearm(off)) return;

        HumanoidArm primary = player.getMainArm();
        HumanoidArm support = off.isEmpty() ? primary.getOpposite() : main.isEmpty() ? primary : null;
        if (support == null || !braceGround) return;

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
