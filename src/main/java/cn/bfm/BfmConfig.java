package cn.bfm;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Tuning knobs. Defaults are deliberately short: a slide is a commitment, not a movement tech. */
public final class BfmConfig {
    public static final ModConfigSpec SPEC;
    /** Master switch: off means this mod does nothing at all. */
    public static final ModConfigSpec.BooleanValue MASTER_ENABLED;

    public static final ModConfigSpec.BooleanValue SLIDE_ENABLED;
    public static final ModConfigSpec.BooleanValue SLIDE_ON_SNEAK;
    public static final ModConfigSpec.DoubleValue SLIDE_SPEED;
    public static final ModConfigSpec.DoubleValue SLIDE_MIN_SPEED;
    public static final ModConfigSpec.DoubleValue SLIDE_DISTANCE;
    public static final ModConfigSpec.BooleanValue SLIDE_CROUCH_STYLE;
    public static final ModConfigSpec.DoubleValue SLIDE_FRICTION;
    public static final ModConfigSpec.IntValue SLIDE_MAX_TICKS;
    public static final ModConfigSpec.IntValue SLIDE_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue SLIDE_CAMERA_DROP;
    public static final ModConfigSpec.BooleanValue SLIDE_PARTICLES;
    public static final ModConfigSpec.BooleanValue SLIDE_REQUIRE_SPRINT;

    public static final ModConfigSpec.BooleanValue DIVE_ENABLED;
    public static final ModConfigSpec.DoubleValue DIVE_SPEED;
    public static final ModConfigSpec.DoubleValue DIVE_DISTANCE;
    public static final ModConfigSpec.DoubleValue DIVE_UP;
    public static final ModConfigSpec.IntValue DIVE_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue DIVE_CAMERA_DROP;

    public static final ModConfigSpec.BooleanValue PRONE_ENABLED;
    public static final ModConfigSpec.BooleanValue PRONE_HOLD;
    public static final ModConfigSpec.DoubleValue PRONE_SPEED_PENALTY;
    public static final ModConfigSpec.DoubleValue PRONE_GROUND_LIFT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        MASTER_ENABLED = b.comment("Master switch for the whole movement mod (slide, dive, prone, camera drops).")
                .define("enabled", true);
        b.push("slide");
        SLIDE_ENABLED = b.comment("Enable the short combat slide.").define("enabled", true);
        SLIDE_ON_SNEAK = b.comment("Sprint + sneak (shift) starts a slide instead of crouching.")
                .define("sprint_sneak_starts_slide", true);
        SLIDE_SPEED = b.comment("Initial slide speed in blocks per tick (0.46 ~ 9 m/s).")
                .defineInRange("speed", 0.46D, 0.20D, 0.80D);
        SLIDE_MIN_SPEED = b.comment("Minimum horizontal speed to start sliding, in blocks per tick.")
                .defineInRange("min_speed", 0.06D, 0D, 1D);
        SLIDE_DISTANCE = b.comment("Maximum horizontal travel in blocks.").defineInRange("distance", 4.0D, .5D, 16D);
        SLIDE_CROUCH_STYLE = b.comment("Use the Battlefield 1-style crouched slide instead of the reclined slide.")
                .define("crouch_style", true);
        SLIDE_FRICTION = b.comment("Per-tick decay. 0.92 keeps about 4 blocks, 0.85 is a long parkour slide.")
                .defineInRange("friction", 0.92D, 0.70D, 1.0D);
        SLIDE_MAX_TICKS = b.comment("Hard cap on slide length in ticks.").defineInRange("max_ticks", 16, 6, 60);
        SLIDE_COOLDOWN_TICKS = b.comment("Cooldown after a slide ends.").defineInRange("cooldown_ticks", 24, 0, 200);
        SLIDE_CAMERA_DROP = b.comment("First-person camera drop while sliding, in blocks.")
                .defineInRange("camera_drop", 0.55D, 0.0D, 1.4D);
        SLIDE_PARTICLES = b.comment("Ground dust and the start whoosh.").define("particles", true);
        SLIDE_REQUIRE_SPRINT = b.comment("Only sprinting players (or players already moving fast) may start a slide.")
                .define("require_sprint", true);
        b.pop();

        b.push("dive");
        DIVE_ENABLED = b.comment("Enable the combat dive (sprint + prone key).").define("enabled", true);
        DIVE_SPEED = b.comment("Forward launch speed of a dive in blocks per tick.")
                .defineInRange("speed", 0.38D, 0.20D, 0.70D);
        DIVE_DISTANCE = b.comment("Maximum horizontal travel in blocks.").defineInRange("distance", 4.5D, .5D, 16D);
        DIVE_UP = b.comment("Upward launch of a dive.").defineInRange("up", 0.42D, 0.20D, 0.60D);
        DIVE_COOLDOWN_TICKS = b.comment("Cooldown after a dive lands.").defineInRange("cooldown_ticks", 24, 0, 200);
        DIVE_CAMERA_DROP = b.comment("First-person camera drop while diving, in blocks.")
                .defineInRange("camera_drop", 0.45D, 0.0D, 1.0D);
        b.pop();

        b.push("prone");
        PRONE_ENABLED = b.comment("Enable the prone stance.").define("enabled", true);
        PRONE_HOLD = b.comment("Hold the prone key to stay prone instead of toggling.")
                .define("hold", false);
        PRONE_SPEED_PENALTY = b.comment("Movement speed reduction while prone (0.35 = -35%).")
                .defineInRange("speed_penalty", 0.35D, 0.0D, 0.80D);
        PRONE_GROUND_LIFT = b.comment("Root lift for the ported prone pose; raise if limbs clip into the floor.")
                .defineInRange("ground_lift", 0.98D, 0.0D, 1.20D);
        b.pop();

        SPEC = b.build();
    }

    private BfmConfig() {
    }
}
