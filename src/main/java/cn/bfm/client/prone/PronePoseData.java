package cn.bfm.client.prone;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Pose data ported from Tacz Animation Additions 1.2.12
 * (assets/locknar_doorkick/doorkick_data/prone_tactical_animations.json, MIT).
 *
 * <p>The file contains two 7-frame enter/exit clips and 37 stationary poses
 * every 10 degrees of view angle. The stationary poses are what make turning
 * while prone roll the body onto its back, exactly like the original mod.</p>
 */
public final class PronePoseData {
    private static final String RESOURCE =
            "/assets/battlefield_movement/prone_tactical_animations.json";
    private static final Data DATA = load();

    public static boolean available() {
        return DATA != null && DATA.anglePoses != null && !DATA.anglePoses.isEmpty();
    }

    public static float enterDuration() {
        return DATA == null || DATA.proneEnter == null ? 0.85F : DATA.proneEnter.duration;
    }

    public static float exitDuration() {
        return DATA == null || DATA.proneExit == null ? 0.7F : DATA.proneExit.duration;
    }

    public static Sample sampleEnter(double progress) {
        return DATA == null ? null : sample(DATA.proneEnter, progress);
    }

    public static Sample sampleExit(double progress) {
        return DATA == null ? null : sample(DATA.proneExit, progress);
    }

    /**
     * Monotonic stand-up used instead of the addon exit clip. The original exit
     * keyframes pitch the torso forward (0→55→32→0), which reads as sitting up
     * twice; this blends from the front stationary pose straight to neutral.
     */
    public static Sample sampleStand(double progress) {
        Sample prone = sampleAngle(0.0D);
        if (prone == null) {
            return null;
        }
        double t = smooth(progress);
        Sample out = Sample.lerp(prone, Sample.neutral(), t);
        Transform root = Transform.copy(out.root());
        root.rotX = (float) (90.0D * (1.0D - t));
        root.rotY = 0.0F;
        root.rotZ = 0.0F;
        root.x = 0.0F;
        root.y = 0.0F;
        root.z = 0.0F;
        return new Sample(out.leftLeg(), out.rightLeg(), out.body(), out.head(),
                out.leftArm(), out.rightArm(), root);
    }

    public static Sample sampleAngle(double angle) {
        if (DATA == null || DATA.anglePoses == null || DATA.anglePoses.isEmpty()) {
            return null;
        }
        List<AnglePose> poses = DATA.anglePoses;
        double clamped = Math.max(-180.0, Math.min(180.0, angle));
        double scaled = (clamped + 180.0) / 10.0;
        int i1 = Math.max(0, Math.min(poses.size() - 2, (int) Math.floor(scaled)));
        int i2 = i1 + 1;
        double local = Math.max(0.0, Math.min(1.0, scaled - i1));
        Frame p1 = poses.get(i1).pose;
        Frame p2 = poses.get(i2).pose;
        Frame p0 = poses.get(Math.max(0, i1 - 1)).pose;
        Frame p3 = poses.get(Math.min(poses.size() - 1, i2 + 1)).pose;
        return Sample.hermite(p0, p1, p2, p3, local);
    }

    private static Sample sample(Animation animation, double progress) {
        if (animation == null || animation.frames == null || animation.frames.isEmpty()) {
            return null;
        }
        List<Frame> frames = animation.frames;
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        double scaled = clamped * (frames.size() - 1);
        int i1 = Math.max(0, Math.min(frames.size() - 1, (int) Math.floor(scaled)));
        int i2 = Math.min(frames.size() - 1, i1 + 1);
        int i0 = Math.max(0, i1 - 1);
        int i3 = Math.min(frames.size() - 1, i2 + 1);
        double local = clamped >= 1.0 ? 1.0 : scaled - i1;
        return Sample.hermite(frames.get(i0), frames.get(i1), frames.get(i2), frames.get(i3), local);
    }

    private static double smooth(double x) {
        double clamped = Math.max(0.0D, Math.min(1.0D, x));
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static Data load() {
        try (InputStream stream = PronePoseData.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return null;
            }
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class Data {
        public int schemaVersion;
        public Animation proneEnter;
        public Animation proneExit;
        public List<AnglePose> anglePoses;
    }

    public static final class Animation {
        public float duration;
        public List<Frame> frames;
    }

    public static final class AnglePose {
        public float angle;
        public float bodyYaw;
        public Frame pose;
    }

    public static final class Frame {
        public Transform leftLeg;
        public Transform rightLeg;
        public Transform body;
        public Transform head;
        public Transform leftArm;
        public Transform rightArm;
        public Transform root;
    }

    public static final class Transform {
        public float x;
        public float y;
        public float z;
        public float rotX;
        public float rotY;
        public float rotZ;

        static Transform hermite(Transform a, Transform b, Transform c, Transform d, double t) {
            Transform out = new Transform();
            out.x = h(a.x, b.x, c.x, d.x, t);
            out.y = h(a.y, b.y, c.y, d.y, t);
            out.z = h(a.z, b.z, c.z, d.z, t);
            out.rotX = h(a.rotX, b.rotX, c.rotX, d.rotX, t);
            out.rotY = h(a.rotY, b.rotY, c.rotY, d.rotY, t);
            out.rotZ = h(a.rotZ, b.rotZ, c.rotZ, d.rotZ, t);
            return out;
        }

        private static float h(float p0, float p1, float p2, float p3, double t) {
            double t2 = t * t;
            double t3 = t2 * t;
            return (float) (0.5 * (2.0 * p1
                    + (-p0 + p2) * t
                    + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                    + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3));
        }

        static Transform lerp(Transform a, Transform b, double t) {
            Transform out = new Transform();
            out.x = (float) (a.x + (b.x - a.x) * t);
            out.y = (float) (a.y + (b.y - a.y) * t);
            out.z = (float) (a.z + (b.z - a.z) * t);
            out.rotX = (float) (a.rotX + (b.rotX - a.rotX) * t);
            out.rotY = (float) (a.rotY + (b.rotY - a.rotY) * t);
            out.rotZ = (float) (a.rotZ + (b.rotZ - a.rotZ) * t);
            return out;
        }

        static Transform copy(Transform value) {
            Transform out = new Transform();
            out.x = value.x;
            out.y = value.y;
            out.z = value.z;
            out.rotX = value.rotX;
            out.rotY = value.rotY;
            out.rotZ = value.rotZ;
            return out;
        }
    }

    public record Sample(Transform leftLeg, Transform rightLeg, Transform body,
                         Transform head, Transform leftArm, Transform rightArm, Transform root) {
        static Sample hermite(Frame a, Frame b, Frame c, Frame d, double t) {
            return new Sample(
                    Transform.hermite(nz(a.leftLeg), nz(b.leftLeg), nz(c.leftLeg), nz(d.leftLeg), t),
                    Transform.hermite(nz(a.rightLeg), nz(b.rightLeg), nz(c.rightLeg), nz(d.rightLeg), t),
                    Transform.hermite(nz(a.body), nz(b.body), nz(c.body), nz(d.body), t),
                    Transform.hermite(nz(a.head), nz(b.head), nz(c.head), nz(d.head), t),
                    Transform.hermite(nz(a.leftArm), nz(b.leftArm), nz(c.leftArm), nz(d.leftArm), t),
                    Transform.hermite(nz(a.rightArm), nz(b.rightArm), nz(c.rightArm), nz(d.rightArm), t),
                    Transform.hermite(nz(a.root), nz(b.root), nz(c.root), nz(d.root), t));
        }

        static Sample neutral() {
            return new Sample(new Transform(), new Transform(), new Transform(),
                    new Transform(), new Transform(), new Transform(), new Transform());
        }

        static Sample lerp(Sample a, Sample b, double t) {
            return new Sample(
                    Transform.lerp(a.leftLeg, b.leftLeg, t),
                    Transform.lerp(a.rightLeg, b.rightLeg, t),
                    Transform.lerp(a.body, b.body, t),
                    Transform.lerp(a.head, b.head, t),
                    Transform.lerp(a.leftArm, b.leftArm, t),
                    Transform.lerp(a.rightArm, b.rightArm, t),
                    Transform.lerp(a.root, b.root, t));
        }

        private static Transform nz(Transform transform) {
            return transform == null ? new Transform() : transform;
        }
    }

    private PronePoseData() {
    }
}
