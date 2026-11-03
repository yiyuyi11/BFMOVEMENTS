package cn.bfm.client.prone;

import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Quaternion to ZYX Euler, copied from the Tacz Animation Additions pose math. */
public final class PronePoseMath {
    public static Vector3f eulerZYX(Quaternionfc q) {
        double x = q.x();
        double y = q.y();
        double z = q.z();
        double w = q.w();
        double scale = 2.0 / (x * x + y * y + z * z + w * w);
        double r20 = (x * z - w * y) * scale;
        double ry = Math.asin(Math.max(-1.0, Math.min(1.0, -r20)));
        double rx;
        double rz;
        if (Math.abs(r20) > 0.9999999) {
            rx = Math.atan2(-(y * z - w * x) * scale, 1.0 - (x * x + z * z) * scale);
            rz = 0.0;
        } else {
            rx = Math.atan2((y * z + w * x) * scale, 1.0 - (x * x + y * y) * scale);
            rz = Math.atan2((x * y + w * z) * scale, 1.0 - (y * y + z * z) * scale);
        }
        return new Vector3f((float) rx, (float) ry, (float) rz);
    }

    private PronePoseMath() {
    }
}
