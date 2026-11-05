package cn.bfm.client.prone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Gun packs that ship a PlayerAnimator third person animation bake their hold pose at a level aim. TaCZ never
 * calls {@code ThirdPersonManager.animateGunHold} for those packs, and that method is the only place the vanilla
 * branch adds {@code head.xRot} to the arms, so a third person viewer sees the weapon frozen on one horizontal
 * line while the aim moves up and down.
 *
 * <p>This restores the missing pitch. A {@code ModelPart} turns about its own pivot, so composing the view pitch
 * onto the arm is enough for the weapon to swing with the aim, exactly like the vanilla branch's
 * {@code rightArm.xRot = -1.4 + head.xRot}. The torso leans by a fraction of the same angle, and the head and
 * both shoulders ride along, so the upper body follows too instead of staying bolt upright behind raised arms.
 *
 * <p>Only the authored branch is touched. Packs without {@code player_animator_3rd} keep using
 * {@code animateGunHold}, which already follows pitch, and must not be pitched twice.
 */
public final class StandingGunPitch {
    /** Waist, where the torso cube ends and the legs begin. Model +y points down. */
    private static final float WAIST_Y = 12.0F;
    /** Share of the aim the torso leans by. The arms take the whole angle, so the weapon stays exact. */
    private static final float SPINE_SHARE = 0.3F;
    private static final float MAX_PITCH = 85.0F;

    public static boolean apply(AbstractClientPlayer player, PlayerModel<?> model, float pitchDegrees) {
        var mc = Minecraft.getInstance();
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) return false;
        if (player.isFallFlying() || player.isVisuallySwimming() || !TaczProneCompat.hasAuthoredHold(player)) {
            return false;
        }
        // The authored layer can also keyframe the head at level aim.
        // Use the render-time aim angle as the absolute head pitch.
        model.head.xRot = pitchDegrees * Mth.DEG_TO_RAD;
        model.hat.copyFrom(model.head);
        float pitch = Mth.clamp(pitchDegrees, -MAX_PITCH, MAX_PITCH) * Mth.DEG_TO_RAD;
        if (Math.abs(pitch) < 0.001F) return false;

        // View pitch turns about model +x, the same axis vanilla gives `head.xRot`.
        Quaternionf aim = new Quaternionf().rotationX(pitch);
        Quaternionf spine = new Quaternionf().rotationX(pitch * SPINE_SHARE);

        // The torso pivot sits at the neck, so move it along the lean to hinge the lean at the waist instead.
        carry(model.body, spine);
        addRotation(model.body, spine);

        // The head is a sibling of the torso and vanilla already pitched it to the aim, so only carry it; turning
        // it here as well would double the view pitch.
        carry(model.head, spine);
        model.hat.copyFrom(model.head);

        carry(model.leftArm, spine);
        carry(model.rightArm, spine);
        addRotation(model.leftArm, aim);
        addRotation(model.rightArm, aim);

        // Vanilla laid the wear layers over the arms and torso before this hook ran.
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        return true;
    }

    /** Moves a pivot as if it were rigidly attached to the torso leaning about the waist. */
    private static void carry(ModelPart part, Quaternionf spine) {
        Vector3f offset = spine.transform(new Vector3f(0.0F, part.y - WAIST_Y, part.z));
        part.y = WAIST_Y + offset.y;
        part.z = offset.z;
    }

    private static void addRotation(ModelPart part, Quaternionf q) {
        Vector3f euler = PronePoseMath.eulerZYX(new Quaternionf(q).rotateZYX(part.zRot, part.yRot, part.xRot));
        part.xRot = euler.x;
        part.yRot = euler.y;
        part.zRot = euler.z;
    }

    private StandingGunPitch() {
    }
}
