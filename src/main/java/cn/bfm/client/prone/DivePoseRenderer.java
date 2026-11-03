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
    private static final Map<Integer, Float> YAW = new HashMap<>();

    public static void begin(AbstractClientPlayer player, PoseStack stack, float partial) {
        if (!DiveClient.isDiving(player)) {
            YAW.remove(player.getId());
            return;
        }
        YAW.put(player.getId(), Mth.lerp(partial, player.yBodyRotO, player.yBodyRot));
    }

    public static void end(AbstractClientPlayer player) {
        YAW.remove(player.getId());
    }

    public static boolean active(AbstractClientPlayer player) {
        return YAW.containsKey(player.getId());
    }

    public static boolean applyRoot(AbstractClientPlayer player, PoseStack stack) {
        Float yaw = YAW.get(player.getId());
        if (yaw == null) {
            return false;
        }
        stack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        stack.mulPose(Axis.XP.rotationDegrees(-72.0F));
        stack.translate(0.0D, -0.32D, 0.18D);
        return true;
    }

    public static void applyPose(AbstractClientPlayer player, PlayerModel<?> model) {
        if (!YAW.containsKey(player.getId())) {
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
        model.head.xRot = (float) Math.toRadians(42.0D);
        model.leftArm.xRot = (float) Math.toRadians(166.0D);
        model.rightArm.xRot = (float) Math.toRadians(166.0D);
        model.leftArm.zRot = (float) Math.toRadians(-8.0D);
        model.rightArm.zRot = (float) Math.toRadians(8.0D);
        model.leftLeg.xRot = (float) Math.toRadians(5.0D);
        model.rightLeg.xRot = (float) Math.toRadians(5.0D);
        model.leftLeg.zRot = (float) Math.toRadians(-4.0D);
        model.rightLeg.zRot = (float) Math.toRadians(4.0D);
        model.jacket.copyFrom(model.body);
        model.hat.copyFrom(model.head);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    private DivePoseRenderer() {
    }
}
