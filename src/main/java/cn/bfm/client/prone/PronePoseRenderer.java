package cn.bfm.client.prone;

import cn.bfm.client.ProneClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Third-person prone renderer ported from Tacz Animation Additions 1.2.12.
 *
 * <p>Unlike the old PlayerAnimator clip approach this drives the vanilla
 * PlayerModel directly: the root is laid down in setupRotations, then the
 * <em>37 authored angle poses</em> from the addon's data file are blended by the
 * view angle. Looking backwards rolls the body onto its back, which is the
 * Battlefield-5 behaviour.</p>
 */
public final class PronePoseRenderer {
    private static final Map<Integer, Visual> VISUAL = new HashMap<>();
    private static final Map<Integer, Motion> MOTION = new HashMap<>();
    private static final Map<Integer, Float> ROLL = new HashMap<>();
    private static final Map<Integer, Integer> LAST_PHASE = new HashMap<>();
    private static final Map<Integer, float[]> LAST_POSE = new HashMap<>();
    private static final Map<Integer, float[]> EXIT_POSE = new HashMap<>();
    private static final Map<Integer, float[]> ENTER_POSE = new HashMap<>();
    private static final Map<Integer,Float> ENTER_ROLL=new HashMap<>();
    private static final PronePoseData.Sample EXIT_BASE = PronePoseData.sampleStand(0.0);
    private static final Map<Integer, Float> EXIT_ROLL = new HashMap<>();

    public static void reset() {
        VISUAL.clear(); MOTION.clear(); ROLL.clear(); LAST_PHASE.clear();
        LAST_POSE.clear(); EXIT_POSE.clear(); ENTER_POSE.clear(); ENTER_ROLL.clear(); EXIT_ROLL.clear();
    }

    private record Visual(PronePoseData.Sample sample, float anchor, float prone, float roll, float aim,
                          double movement, double forward, double side, double stride,
                          boolean transition, float progress) {
    }

    private static final class Motion {
        double x;
        double z;
        double phase;
        double previousPhase;
        double weight;
        double previousWeight;
        double forward = 1.0;
        double side;
        boolean moving;
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            reset();
            return;
        }
        for (AbstractClientPlayer player : client.level.players()) {
            if (!ProneClient.isProne(player)) {
                MOTION.remove(player.getId());
                ROLL.remove(player.getId());
                LAST_POSE.remove(player.getId());
                EXIT_POSE.remove(player.getId());
                ENTER_POSE.remove(player.getId());
                ENTER_ROLL.remove(player.getId());
                EXIT_ROLL.remove(player.getId());
                continue;
            }
            Motion motion = MOTION.computeIfAbsent(player.getId(), id -> {
                Motion created = new Motion();
                created.x = player.getX();
                created.z = player.getZ();
                return created;
            });
            double dx = player.getX() - motion.x;
            double dz = player.getZ() - motion.z;
            motion.x = player.getX();
            motion.z = player.getZ();
            double distance = Math.hypot(dx, dz);
            if (distance > 1.0) {
                distance = 0.0;
            }
            boolean moving = distance > 0.002;
            if (moving != motion.moving) {
                motion.moving = moving;
            }
            motion.previousWeight = motion.weight;
            motion.previousPhase = motion.phase;
            motion.weight = Mth.clamp(motion.weight + (moving ? 1.0 : -1.0) / 6.0, 0.0, 1.0);
            if (moving) {
                double yaw = Math.toRadians(player.yBodyRot);
                motion.forward = (-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance;
                motion.side = (dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance;
                motion.phase += distance * 7.5;
            }
        }
    }

    public static void begin(AbstractClientPlayer player, PoseStack stack, float partial) {
        int phase = ProneClient.phase(player);
        int previousPhase = LAST_PHASE.getOrDefault(player.getId(), -1);
        if (previousPhase != phase) {
            LAST_PHASE.put(player.getId(), phase);
            cn.bfm.BattlefieldMovement.LOG.debug("[BFM] visual phase {} -> {} progress={}",
                    previousPhase, phase, ProneClient.progress(player, partial));
        }
        if (phase == ProneClient.STANDING) {
            VISUAL.remove(player.getId());
            ROLL.remove(player.getId());
            return;
        }
        if (phase == ProneClient.EXITING && previousPhase != phase) {
            float[] last = LAST_POSE.get(player.getId());
            if (last != null) EXIT_POSE.put(player.getId(), last.clone());
            EXIT_ROLL.put(player.getId(), ROLL.getOrDefault(player.getId(), 0.0F));
        } else if (phase != ProneClient.EXITING) {
            EXIT_POSE.remove(player.getId());
            EXIT_ROLL.remove(player.getId());
        }
        if(phase==ProneClient.ENTERING && previousPhase!=phase){
            ENTER_ROLL.put(player.getId(),ROLL.getOrDefault(player.getId(),0f));
            float[] last=LAST_POSE.get(player.getId());
            if(last!=null)ENTER_POSE.put(player.getId(),last.clone());
        }else if(phase!=ProneClient.ENTERING){ENTER_POSE.remove(player.getId());ENTER_ROLL.remove(player.getId());}
        float progress = ProneClient.progress(player, partial);
        float anchor = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.lerp(partial, player.yHeadRotO, player.yHeadRot);
        float aim = Mth.wrapDegrees(headYaw - anchor);
        boolean transition = phase == ProneClient.ENTERING || phase == ProneClient.EXITING;
        PronePoseData.Sample sample;
        float prone;
        if (phase == ProneClient.ENTERING) {
            sample = PronePoseData.sampleEnter(ProneClient.enterClipProgress(player,partial));
            prone = ProneClient.amount(player,partial);
        } else if (phase == ProneClient.EXITING) {
            sample = EXIT_BASE;
            prone = ProneClient.amount(player,partial);
        } else {
            sample = PronePoseData.sampleAngle(aim);
            prone = 1.0F;
        }
        if (sample == null) {
            VISUAL.remove(player.getId());
            return;
        }
        float roll = transition ? 0.0F : sample.root().rotY;
        if(phase==ProneClient.ENTERING){
            var target=PronePoseData.sampleAngle(aim);
            float targetRoll=target==null?0:target.root().rotY;
            roll=cn.bfm.StanceTransition.angle(ENTER_ROLL.getOrDefault(player.getId(),0f)*Mth.DEG_TO_RAD,targetRoll*Mth.DEG_TO_RAD,smooth(progress))*Mth.RAD_TO_DEG;
        }
        if (phase == ProneClient.EXITING) {
            // Scale by the lying amount as well as by the exit progress: the two curves differ, and a roll
            // that outlives the tilt is exactly the "head stays crooked after standing up" report.
            roll = EXIT_ROLL.getOrDefault(player.getId(), 0.0F) * (1.0F - smooth(progress)) * prone;
        }
        if (!transition) {
            float previousRoll = ROLL.getOrDefault(player.getId(), roll);
            roll = previousRoll + Mth.wrapDegrees(roll - previousRoll) * 0.28F;
        }
        ROLL.put(player.getId(),roll);
        Motion motion = MOTION.get(player.getId());
        double movement = motion == null ? 0.0D : Mth.lerp(partial, motion.previousWeight, motion.weight);
        double forward = motion == null ? 1.0D : motion.forward;
        double side = motion == null ? 0.0D : motion.side;
        double stride = motion == null ? 0.0D : Mth.lerp(partial, motion.previousPhase, motion.phase);
        VISUAL.put(player.getId(), new Visual(sample, anchor, prone, roll, aim,
                movement, forward, side, stride, transition, progress));
    }

    public static void end(AbstractClientPlayer player) {
        VISUAL.remove(player.getId());
    }

    public static boolean active(AbstractClientPlayer player) {
        return VISUAL.containsKey(player.getId());
    }

    /** Applied from PlayerRenderer.setupRotations while prone. */
    public static boolean applyRoot(AbstractClientPlayer player, PoseStack stack) {
        Visual visual = VISUAL.get(player.getId());
        if (visual == null) {
            return false;
        }
        stack.mulPose(Axis.YP.rotationDegrees(180.0F - visual.anchor));
        PronePoseData.Transform root = visual.sample.root();
        if (visual.transition) {
            stack.translate(0.0D, root.y / 16.0D, root.z / 16.0D);
        }
        stack.mulPose(Axis.XP.rotationDegrees(-90.0F * visual.prone));
        stack.translate(0.0D, -visual.prone, 0.15D * visual.prone);
        stack.mulPose(Axis.YP.rotationDegrees(visual.roll));
        if (!visual.transition) {
            stack.translate(root.x / 16.0D, root.y / 16.0D, root.z / 16.0D);
        }
        // The addon root lowers the model by one block; without its per-cube
        // ground scan the body/limbs end up under the floor. Cancel most of that
        // translation and keep a small clearance.
        stack.translate(0.0D, cn.bfm.BfmConfig.PRONE_GROUND_LIFT.get() * visual.prone, 0.0D);
        return true;
    }

    /** Applied from PlayerModel.setupAnim(TAIL). */
    public static void applyPose(AbstractClientPlayer player, PlayerModel<?> model) {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player && client.options.getCameraType().isFirstPerson()) {
            // Do not pose the first-person hand model; the camera bridge handles
            // the first-person feel and TaCZ owns the gun rendering there.
            return;
        }
        Visual visual = VISUAL.get(player.getId());
        if (visual == null) {
            return;
        }
        boolean keepReloadArms = !visual.transition && TaczProneCompat.isGunAction(player);
        PartPose reloadRight = keepReloadArms ? model.rightArm.storePose() : null;
        PartPose reloadLeft = keepReloadArms ? model.leftArm.storePose() : null;
        if (visual.transition && ProneClient.phase(player) == ProneClient.EXITING) {
            applyExitPose(model, player, visual);
            capturePose(player,model);
            return;
        }
        resetParts(model, keepReloadArms);
        if (visual.transition) {
            apply(model.leftLeg, visual.sample.leftLeg());
            apply(model.rightLeg, visual.sample.rightLeg());
            apply(model.head, visual.sample.head());
            apply(model.leftArm, visual.sample.leftArm());
            apply(model.rightArm, visual.sample.rightArm());
            bendAtWaist(model, visual.sample.body());
            float[] captured=ENTER_POSE.get(player.getId());
            if(captured!=null)blendCaptured(model,captured,smooth(visual.progress));
            copyWear(model);
            capturePose(player,model);
            return;
        }
        stationary(model, player, visual, keepReloadArms);
        if (keepReloadArms) {
            retargetReload(model, player, visual, reloadRight, reloadLeft);
        }
        copyWear(model);
        capturePose(player,model);
    }

    private static void capturePose(AbstractClientPlayer player,PlayerModel<?> model){
        float[] snapshot = LAST_POSE.computeIfAbsent(player.getId(), id -> new float[36]);
        for (int i = 0; i < 6; i++) {
            ModelPart part = bodyPart(model, i);
            int at = i * 6;
            snapshot[at] = part.x; snapshot[at+1] = part.y; snapshot[at+2] = part.z;
            snapshot[at+3] = part.xRot; snapshot[at+4] = part.yRot; snapshot[at+5] = part.zRot;
        }
    }

    private static void blendCaptured(PlayerModel<?> model,float[] captured,float t){
        for(int i=0;i<6;i++){
            ModelPart part=bodyPart(model,i);int at=i*6;
            part.x=Mth.lerp(t,captured[at],part.x);part.y=Mth.lerp(t,captured[at+1],part.y);part.z=Mth.lerp(t,captured[at+2],part.z);
            part.xRot=captured[at+3]+Mth.wrapDegrees((part.xRot-captured[at+3])*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD*t;
            part.yRot=captured[at+4]+Mth.wrapDegrees((part.yRot-captured[at+4])*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD*t;
            part.zRot=captured[at+5]+Mth.wrapDegrees((part.zRot-captured[at+5])*Mth.RAD_TO_DEG)*Mth.DEG_TO_RAD*t;
        }
    }

    /** Port of the addon's reload arm retarget: keeps TaCZ's reload pose attached to the prone body. */
    private static void retargetReload(PlayerModel<?> model, AbstractClientPlayer player, Visual visual,
                                       PartPose right, PartPose left) {
        if (right == null || left == null) {
            return;
        }
        float pi = (float) Math.PI;
        Quaternionf body = new Quaternionf().rotateX(-pi / 2.0F)
                .rotateY((float) Math.toRadians(visual.roll)).rotateZ(pi);
        Quaternionf aim = body.invert()
                .rotateY((float) Math.toRadians(-visual.aim))
                .rotateZ(pi)
                .rotateX((float) Math.toRadians(Mth.clamp(player.getXRot(), -70.0F, 70.0F)))
                .rotateX(-pi / 2.0F);
        Quaternionf basis = aim.mul(new Quaternionf().rotationZYX(right.zRot, right.yRot, right.xRot).invert());
        ModelPart[] arms = new ModelPart[]{model.rightArm, model.leftArm};
        PartPose[] poses = new PartPose[]{right, left};
        for (int i = 0; i < 2; i++) {
            ModelPart part = arms[i];
            PartPose pose = poses[i];
            PartPose initial = part.getInitialPose();
            Vector3f offset = basis.transform(new Vector3f(
                    pose.x - initial.x, pose.y - initial.y, pose.z - initial.z));
            part.x += offset.x;
            part.y += offset.y;
            part.z += offset.z;
            rotate(part, new Quaternionf(basis)
                    .mul(new Quaternionf().rotationZYX(pose.zRot, pose.yRot, pose.xRot)));
        }
    }

    /**
     * Continuous stand-up: begin from the exact stationary prone pose and blend
     * every part to its initial (standing) pose. The root rotation comes from
     * the monotonic exit sample, so there is no snap to neutral at t=0 and no
     * second "sit up" motion.
     */
    private static void applyExitPose(PlayerModel<?> model, AbstractClientPlayer player, Visual visual) {
        float[] captured = EXIT_POSE.get(player.getId());
        if (captured != null) {
            float t = smooth(visual.progress);
            for (int i = 0; i < 6; i++) {
                ModelPart part = bodyPart(model, i);
                int at = i * 6;
                PartPose base = part.getInitialPose();
                // The normal model has already set up this frame's held-item
                // and walking pose. Blend into it, not a neutral T-pose that
                // would snap to the real pose again at the end of the exit.
                part.x = Mth.lerp(t, captured[at], base.x);
                part.y = Mth.lerp(t, captured[at+1], base.y);
                part.z = Mth.lerp(t, captured[at+2], base.z);
                  part.xRot = cn.bfm.StanceTransition.angle(captured[at+3], part.xRot,t);
                  part.yRot = cn.bfm.StanceTransition.angle(captured[at+4], part.yRot,t);
                  part.zRot = cn.bfm.StanceTransition.angle(captured[at+5], part.zRot,t);
            }
            copyWear(model);
            return;
        }
        PronePoseData.Sample start = PronePoseData.sampleAngle(visual.aim);
        if (start == null) {
            return;
        }
        resetParts(model, false);
        Visual stationary = new Visual(start, visual.anchor, 1.0F, visual.roll, visual.aim,
                0.0D, 1.0D, 0.0D, 0.0D, false, 1.0F);
        stationary(model, player, stationary, false);
        float t = smooth(visual.progress);
        for (ModelPart part : List.of(model.head, model.body, model.leftArm, model.rightArm,
                model.leftLeg, model.rightLeg)) {
            blendPose(part, part.getInitialPose(), t);
        }
        copyWear(model);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static ModelPart bodyPart(PlayerModel<?> model, int index) {
        return switch (index) {
            case 0 -> model.head; case 1 -> model.body; case 2 -> model.leftArm;
            case 3 -> model.rightArm; case 4 -> model.leftLeg; default -> model.rightLeg;
        };
    }

    private static void stationary(PlayerModel<?> model, AbstractClientPlayer player, Visual visual,
                                   boolean keepReloadArms) {
        PronePoseData.Sample sample = visual.sample;
        model.leftLeg.zRot = -0.09F;
        model.rightLeg.zRot = 0.09F;
        bendAtWaist(model, sample.body());
        apply(model.leftLeg, sample.leftLeg());
        apply(model.rightLeg, sample.rightLeg());

        float yaw = (float) Math.toRadians(visual.aim);
        float pitch = (float) Math.toRadians(Mth.clamp(player.getXRot(), -70.0F, 70.0F));
        float roll = (float) Math.toRadians(visual.roll);
        float pi = (float) Math.PI;
        Quaternionf body = new Quaternionf().rotateX(-pi / 2.0F).rotateY(roll).rotateZ(pi);
        Quaternionf head = new Quaternionf(body).invert().rotateY(-yaw).rotateZ(pi).rotateX(pitch);
        rotate(model.head, head);
        if (!keepReloadArms) {
            Quaternionf arm = new Quaternionf(head).rotateX(-pi / 2.0F);
            rotate(model.leftArm, new Quaternionf(arm).rotateX((float) Math.toRadians(4.0F)).rotateZ(-0.12F));
            rotate(model.rightArm, new Quaternionf(arm).rotateX((float) Math.toRadians(4.0F)).rotateZ(0.12F));
        }

        apply(model.head, sample.head());
        if (!keepReloadArms) {
            apply(model.leftArm, sample.leftArm());
            apply(model.rightArm, sample.rightArm());
        }

        gait(model, visual, keepReloadArms);

        for (ModelPart leg : List.of(model.leftLeg, model.rightLeg)) {
            Quaternionf local = new Quaternionf().rotationZYX(leg.zRot, leg.yRot, leg.xRot);
            double brace = 65.0D * Math.sin(Math.toRadians(visual.roll));
            rotate(leg, new Quaternionf().rotateY((float) Math.toRadians(brace)).mul(local));
        }
    }

    private static void gait(PlayerModel<?> model, Visual visual, boolean keepReloadArms) {
        if (visual.movement <= 0.0D) {
            return;
        }
        double forward = visual.forward;
        double side = visual.side;
        double weight = visual.movement;
        double cycle = visual.stride * (forward < -0.15D ? -1 : 1);
        double a = Math.sin(cycle);
        double b = Math.cos(cycle);
        double left = (1.0D + b) * 0.5D;
        double right = (1.0D - b) * 0.5D;

        model.leftLeg.xRot += (float) ((0.04D + left * 0.22D) * weight);
        model.rightLeg.xRot += (float) ((0.04D + right * 0.22D) * weight);
        model.leftLeg.yRot += (float) (side * 0.2D * weight);
        model.rightLeg.yRot += (float) (side * 0.2D * weight);
        model.leftLeg.zRot -= (float) ((0.1D + left * 0.2D) * weight);
        model.rightLeg.zRot += (float) ((0.1D + right * 0.2D) * weight);
        model.leftLeg.y -= (float) (left * 2.8D * weight);
        model.rightLeg.y -= (float) (right * 2.8D * weight);
        model.leftLeg.x += (float) ((a * 0.65D + side * left * 1.2D) * weight);
        model.rightLeg.x += (float) ((-a * 0.65D + side * right * 1.2D) * weight);
        model.leftLeg.z += (float) (Math.max(0.0D, a) * 1.25D * weight);
        model.rightLeg.z += (float) (Math.max(0.0D, -a) * 1.25D * weight);

        if (!keepReloadArms) {
            blendPose(model.leftArm, PartPose.offsetAndRotation(5.0F, 2.0F + (float) (a * 0.9D), 0.0F,
                    (float) Math.toRadians(-174.0D), 0.0F, -0.12F), weight);
            blendPose(model.rightArm, PartPose.offsetAndRotation(-5.0F, 2.0F - (float) (a * 0.9D), 0.0F,
                    (float) Math.toRadians(-174.0D), 0.0F, 0.12F), weight);
        }
    }

    private static void bendAtWaist(PlayerModel<?> model, PronePoseData.Transform transform) {
        double radians = Math.toRadians(transform.rotX);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        model.body.x = transform.x;
        model.body.y = (float) (transform.y + 12.0D - 12.0D * cos);
        model.body.z = (float) (transform.z - 12.0D * sin);
        model.body.xRot = (float) radians;
        for (ModelPart part : List.of(model.head, model.leftArm, model.rightArm)) {
            double y = part.y - 12.0D;
            double z = part.z;
            part.y = (float) (12.0D + y * cos - z * sin + transform.y);
            part.z = (float) (y * sin + z * cos + transform.z);
            part.xRot += (float) radians;
        }
    }

    private static void apply(ModelPart part, PronePoseData.Transform transform) {
        if (transform == null) {
            return;
        }
        part.x += transform.x;
        part.y += transform.y;
        part.z += transform.z;
        part.xRot += (float) Math.toRadians(transform.rotX);
        part.yRot += (float) Math.toRadians(transform.rotY);
        part.zRot += (float) Math.toRadians(transform.rotZ);
    }

    private static void rotate(ModelPart part, Quaternionf rotation) {
        Vector3f euler = PronePoseMath.eulerZYX(rotation);
        part.xRot = euler.x;
        part.yRot = euler.y;
        part.zRot = euler.z;
    }

    private static void blendPose(ModelPart part, PartPose target, double amount) {
        float t = (float) amount;
        part.x = Mth.lerp(t, part.x, target.x);
        part.y = Mth.lerp(t, part.y, target.y);
        part.z = Mth.lerp(t, part.z, target.z);
        Quaternionf old = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
        rotate(part, old.slerp(new Quaternionf().rotationZYX(target.zRot, target.yRot, target.xRot), t));
    }


    /**
     * Zeroes only the roll channel of the parts a prone or dive pose can rotate.
     *
     * <p>Vanilla's model setup rewrites xRot and yRot every frame but leaves zRot alone, so a pose that has
     * ended would otherwise keep its last roll forever — visible as a head that stays tilted after standing up.
     */
    public static void clearRoll(PlayerModel<?> model) {
        model.head.zRot = 0.0F;
        model.body.zRot = 0.0F;
        model.leftArm.zRot = 0.0F;
        model.rightArm.zRot = 0.0F;
        model.leftLeg.zRot = 0.0F;
        model.rightLeg.zRot = 0.0F;
        model.hat.zRot = 0.0F;
        model.jacket.zRot = 0.0F;
        model.leftSleeve.zRot = 0.0F;
        model.rightSleeve.zRot = 0.0F;
        model.leftPants.zRot = 0.0F;
        model.rightPants.zRot = 0.0F;
    }
    private static void resetParts(PlayerModel<?> model, boolean keepReloadArms) {
        for (ModelPart part : List.of(model.head, model.body, model.leftLeg, model.rightLeg)) {
            part.resetPose();
        }
        if (!keepReloadArms) {
            model.leftArm.resetPose();
            model.rightArm.resetPose();
        }
    }

    private static void copyWear(PlayerModel<?> model) {
        model.jacket.copyFrom(model.body);
        model.hat.copyFrom(model.head);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }

    private PronePoseRenderer() {
    }
}
