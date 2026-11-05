package cn.bfm.client;

import cn.bfm.ActionEyeHeight;

/** Server-owned gameplay height; local config is only a pre-login fallback. */
public final class ServerEyeSettings {
    private static float slide = -1.0F, dive = -1.0F;
    private static cn.bfm.MovementTuning tuning;
    public static cn.bfm.MovementTuning tuning() {
        return tuning == null ? cn.bfm.MovementTuning.config() : tuning;
    }
    public static void accept(float s, float d, cn.bfm.MovementTuning settings) {
        tuning = settings;
        if (Float.isFinite(s) && Float.isFinite(d) && s >= 0.08F && d >= 0.08F && s <= 1.62F && d <= 1.62F) {
            slide = s; dive = d;
        }
    }
    public static float slide() { return slide < 0 ? ActionEyeHeight.slideHeight() : slide; }
    public static float dive() { return dive < 0 ? ActionEyeHeight.diveHeight() : Math.min(dive, (float) cn.bfm.DiveGeometry.HEIGHT - .12F); }
    public static void reset() { slide = dive = -1.0F; tuning = null; }
    private ServerEyeSettings() {}
}
