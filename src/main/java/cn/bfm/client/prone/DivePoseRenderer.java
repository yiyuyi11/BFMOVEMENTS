package cn.bfm.client.prone;

import cn.bfm.client.DiveClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Third-person dive pose.
 *
 * <p>The dive used to be a PlayerAnimator clip; when that layer was not applied
 * the player just kept the standing model while moving, which read as "slide
 * then fall". This renderer owns the root and limbs for the short airborne
 * window, so the body is visibly horizontal in third person.</p>
 */
public final class DivePoseRenderer {
    private record Frame(float yaw, float extension, float pitch) {}
    private static final Map<Integer, Frame> FRAMES = new HashMap<>();

    public static void begin(AbstractClientPlayer player, PoseStack stack, float partial) {
        if (!cn.bfm.BattlefieldMovement.enabled()) return;
        if (!DiveClient.isDiving(player)) {
            FRAMES.remove(player.getId());
            return;
        }
        FRAMES.put(player.getId(), new Frame(cn.bfm.DiveGeometry.yaw(player), DiveClient.extension(player, partial),Mth.lerp(partial,player.xRotO,player.getXRot())));
    }

    public static void end(AbstractClientPlayer player) {
        FRAMES.remove(player.getId());
    }

    public static boolean active(AbstractClientPlayer player) {
        return FRAMES.containsKey(player.getId());
    }

    public static boolean applyRoot(AbstractClientPlayer player, PoseStack stack) {
        Frame frame = FRAMES.get(player.getId());
        if (frame == null) {
            return false;
        }
        stack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(frame.extension(),player.yBodyRot,frame.yaw())));
        stack.translate(0.0D, 0.0D, -cn.bfm.ProneGeometry.MODEL_CENTER_SHIFT*frame.extension());
        stack.mulPose(Axis.XP.rotationDegrees(-90.0F*frame.extension()));
        stack.translate(0.0D, -frame.extension(), 0.15D*frame.extension());
        return true;
    }

    public static void applyPose(AbstractClientPlayer player, PlayerModel<?> model) {
        Frame frame = FRAMES.get(player.getId());
        if (frame == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (player == client.player && client.options.getCameraType().isFirstPerson()) {
            return;
        }
        for (ModelPart part : List.of(model.head, model.body, model.leftArm, model.rightArm,
                model.leftLeg, model.rightLeg)) {
            part.resetPose();
        }
        float headPitch = Mth.lerp(frame.extension(), 42.0F, 58.0F) + frame.pitch() * .55F;
        model.head.xRot = Mth.lerp(frame.extension(),frame.pitch()*Mth.DEG_TO_RAD,Mth.clamp(headPitch,16.0F,82.0F)*Mth.DEG_TO_RAD);
        model.leftArm.xRot = Mth.lerp(frame.extension(), -118.0F, -155.0F) * Mth.DEG_TO_RAD * frame.extension();
        model.rightArm.xRot = Mth.lerp(frame.extension(), -85.0F, -147.0F) * Mth.DEG_TO_RAD * frame.extension();
        model.leftArm.zRot = (float) Math.toRadians(-8.0D) * frame.extension();
        model.rightArm.zRot = (float) Math.toRadians(8.0D) * frame.extension();
        model.leftLeg.xRot = Mth.lerp(frame.extension(), -19.0F, 4.0F) * Mth.DEG_TO_RAD * frame.extension();
        model.rightLeg.xRot = Mth.lerp(frame.extension(), 24.0F, 2.0F) * Mth.DEG_TO_RAD * frame.extension();
        model.leftLeg.zRot = (float) Math.toRadians(-4.0D) * frame.extension();
        model.rightLeg.zRot = (float) Math.toRadians(4.0D) * frame.extension();
        model.jacket.copyFrom(model.body);
        model.hat.copyFrom(model.head);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        PronePoseRenderer.rememberExternalPose(player,model);
    }

    private DivePoseRenderer() {
    }
}
