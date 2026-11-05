/**
 * Reproduces the dive's intermittent in-air position jump without launching the game.
 *
 * <p>Reported symptom: with blocks nearby, during a dive, the picture stays continuous, neither FPS nor
 * tick time drops, and the position jumps. That rules out frame pacing and points at the eye position
 * being switched between two values inside one frame budget.
 *
 * <p>{@code ProneGeometry.coverSafeEye} is called as
 * {@code coverSafeEye(player, interpolated, player.getEyePosition(), partial)}. Note the arguments:
 * {@code interpolated} is {@code getEyePosition(partial)}, which lies BETWEEN the previous and the
 * current tick, while {@code current} is {@code getEyePosition()} - the newest tick position. In time
 * order it is {@code interpolated ... current}, so while moving forward the interpolated point is
 * BEHIND the current one, i.e. farther from an obstruction ahead.
 *
 * <p>The switch then reads:
 *
 * <pre>
 *   if (reach(current) &gt;= 4) return interpolated;       // nothing within 4 blocks: smooth
 *   return reach(interpolated) &gt; reach(current) + 0.16
 *          ? current                                     // snap back to the tick value
 *          : interpolated;
 * </pre>
 *
 * <p>Because the interpolated point is behind, its reach is LARGER by about
 * {@code (1 - partial) * perTick}. That clears the +0.16 threshold for most of every tick, so the eye is
 * returned as the tick position for most frames and only interpolates near the tick boundary. The result
 * is motion quantised to 20 Hz for as long as cover stays within four blocks - exactly "near blocks it
 * jumps, while the picture stays smooth".
 *
 * <p>Models only that arithmetic; it does not link against Minecraft.
 */
public final class CoverSmoothSim {
    private static final double REACH_CAP = 4.0D;
    private static final double PER_TICK = 0.35D;
    private static final int FRAMES_PER_TICK = 3;
    private static int checks;

    /** Distance from the eye to the obstruction along the view ray, capped at 4 like {@code reach}. */
    private static double reach(double eyeX, double wallX) {
        if (eyeX >= wallX) return REACH_CAP;
        return Math.min(REACH_CAP, wallX - eyeX);
    }

    /** True when the original code snaps to the newest tick position instead of the interpolated one. */
    private static boolean picksTick(double interpolated, double current, double wallX) {
        if (Math.abs(interpolated - current) < 0.005D) return false;
        double currentReach = reach(current, wallX);
        if (currentReach >= REACH_CAP) return false;
        return reach(interpolated, wallX) > currentReach + 0.16D;
    }

    /** The smooth path the renderer would like to draw: one tick of travel spread over the frames. */
    private static double interpolatedAt(int tick, int frame) {
        double partial = frame / (double) FRAMES_PER_TICK;
        return (tick - 1 + partial) * PER_TICK;
    }

    private static double currentAt(int tick) {
        return tick * PER_TICK;
    }

    public static void main(String[] args) {
        nearCoverThePathIsQuantisedToTicks();
        farFromCoverThePathIsSmooth();
        theSmoothPathItselfNeverJumps();
        theFixKeepsForwardMotionSmooth();
        theFixStillClampsWhenTheEyeIsObstructed();
        System.out.println("CoverSmoothSim: " + checks + " checks passed");
    }

    /**
     * The corrected predicate: snap only when the interpolated eye sees LESS far, i.e. it is the one
     * poking into geometry. Being farther from cover than the simulation eye is ordinary forward motion.
     */
    private static boolean fixedPicksTick(double interpolated, double current, double wallX) {
        if (Math.abs(interpolated - current) < 0.005D) return false;
        double currentReach = reach(current, wallX);
        if (currentReach >= REACH_CAP) return false;
        return reach(interpolated, wallX) < currentReach - 0.16D;
    }

    /** Forward motion near cover must no longer be quantised by the switch. */
    private static void theFixKeepsForwardMotionSmooth() {
        double wallX = 2.4D;
        double previous = Double.NaN;
        double maxJump = 0.0D;
        for (int tick = 1; tick <= 8; tick++) {
            for (int frame = 0; frame < FRAMES_PER_TICK; frame++) {
                double interpolated = interpolatedAt(tick, frame);
                double chosen = fixedPicksTick(interpolated, currentAt(tick), wallX)
                        ? currentAt(tick) : interpolated;
                if (!Double.isNaN(previous)) maxJump = Math.max(maxJump, Math.abs(chosen - previous));
                previous = chosen;
            }
        }
        double smoothStep = PER_TICK / FRAMES_PER_TICK;
        assertTrue(maxJump <= smoothStep + 1e-9D,
                "after the fix the eye advances evenly near cover (largest jump " + fmt(maxJump) + ")");
    }

    /** The protection that matters still fires: an eye closer to cover than the simulation eye. */
    private static void theFixStillClampsWhenTheEyeIsObstructed() {
        double wallX = 6.0D;
        double current = 3.0D;              // simulation eye: 3.0 to the wall, inside the four-block cap
        double worse = current + 0.30D;     // interpolated eye advanced past it, closer to cover
        assertTrue(fixedPicksTick(worse, current, wallX),
                "the fix still clamps when the interpolated eye sees less far than the simulation eye");
        double better = current - 0.30D;    // behind the simulation eye, farther from cover
        assertTrue(!fixedPicksTick(better, current, wallX),
                "the fix no longer clamps ordinary lagging motion");
    }

    /** With cover in range the chosen eye becomes the tick position for most frames. */
    private static void nearCoverThePathIsQuantisedToTicks() {
        double wallX = 2.4D;                 // cover within four blocks of the eye while diving
        int snapped = 0;
        int frames = 0;
        double maxJump = 0.0D;
        double previous = Double.NaN;
        for (int tick = 1; tick <= 8; tick++) {
            for (int frame = 0; frame < FRAMES_PER_TICK; frame++) {
                double interpolated = interpolatedAt(tick, frame);
                double current = currentAt(tick);
                boolean tickSnap = picksTick(interpolated, current, wallX);
                double chosen = tickSnap ? current : interpolated;
                if (tickSnap) snapped++;
                if (!Double.isNaN(previous)) maxJump = Math.max(maxJump, Math.abs(chosen - previous));
                previous = chosen;
                frames++;
            }
        }
        assertTrue(snapped >= frames / 3,
                "cover in range snaps " + snapped + " of " + frames + " frames to the tick position");
        double smoothStep = PER_TICK / FRAMES_PER_TICK;
        // Those frames repeat one tick's position, so the drawn eye advances in whole-tick steps while the
        // frames in between are interpolated: the motion reads as a jump every tick.
        assertTrue(maxJump > 2.5D * smoothStep,
                "the chosen eye jumps " + fmt(maxJump) + " per frame versus a smooth " + fmt(smoothStep));
        System.out.println("  near cover: snapped " + snapped + "/" + frames
                + " frames, largest jump " + fmt(maxJump) + " vs smooth " + fmt(smoothStep));
    }

    /** Out of range, the first test short-circuits and nothing is ever snapped. */
    private static void farFromCoverThePathIsSmooth() {
        double wallX = 40.0D;
        for (int tick = 1; tick <= 8; tick++) {
            for (int frame = 0; frame < FRAMES_PER_TICK; frame++) {
                assertTrue(!picksTick(interpolatedAt(tick, frame), currentAt(tick), wallX),
                        "no snapping when the nearest cover is beyond four blocks");
            }
        }
    }

    /** Control: the underlying eye path is already smooth, so any jump comes from the switch alone. */
    private static void theSmoothPathItselfNeverJumps() {
        double previous = Double.NaN;
        for (int tick = 1; tick <= 8; tick++) {
            for (int frame = 0; frame < FRAMES_PER_TICK; frame++) {
                double interpolated = interpolatedAt(tick, frame);
                if (!Double.isNaN(previous)) {
                    assertTrue(Math.abs(interpolated - previous) <= PER_TICK / FRAMES_PER_TICK + 1e-9D,
                            "the interpolated path itself advances evenly");
                }
                previous = interpolated;
            }
        }
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static void assertTrue(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError("FAILED: " + message);
    }
}
