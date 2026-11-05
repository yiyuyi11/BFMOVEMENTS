import cn.bfm.StanceTransition;

/**
 * Headless checks for the slide camera curve.
 *
 * <p>The slide used to snap its camera height in a single tick, which is what read as a jolt. It now
 * blends over {@link StanceTransition#SLIDE_ENTER_TICKS} ticks. The properties that make that a fix
 * rather than a new bug are all checkable without a game:
 *
 * <ul>
 *   <li>the curve starts exactly where the eyes already were, so there is no first-frame jump;</li>
 *   <li>it ends exactly at the slide height, so the camera settles where the geometry expects;</li>
 *   <li>it never moves backwards, so the descent does not bob;</li>
 *   <li>consecutive frames differ by a small fraction of the total, so the motion reads as a glide;</li>
 *   <li>a slide started from a crouch continues downward instead of jumping up first.</li>
 * </ul>
 *
 * <p>Compile against {@code StanceTransition} only; it imports nothing from Minecraft.
 *
 *   java -cp build\slide-sim SlideCurveSim
 */
public final class SlideCurveSim {
    private static int checks;

    public static void main(String[] args) {
        startsWhereTheEyesWere();
        endsAtSlideHeight();
        isMonotonic();
        hasNoVisibleSteps();
        crouchStartDoesNotJumpUp();
        matchesDiveEntryPacing();
        exitEasesUpWithoutCutting();
        exitLandsOnItsTarget();
        interruptedSlideExitsFromWhereItWas();
        System.out.println("SlideCurveSim: " + checks + " checks passed");
    }

    /** The exit must rise gradually: a hard cut upward is the other half of the jolt. */
    private static void exitEasesUpWithoutCutting() {
        float start = 1.07F;
        float target = 1.62F;
        float total = target - start;
        float previous = start;
        float largest = 0F;
        int frames = EXIT_TICKS * 3;
        for (int i = 1; i <= frames; i++) {
            float value = StanceTransition.blend(start, target, i / 3F, EXIT_TICKS);
            assertTrue(value >= previous - 1e-6F, "the exit never dips at frame " + i);
            largest = Math.max(largest, value - previous);
            previous = value;
        }
        assertTrue(largest <= total * 0.35F,
                "largest single-frame rise " + largest + " stays under a third of " + total);
    }

    /** The exit curve has to actually arrive, or the camera would hover below eye level forever. */
    private static void exitLandsOnItsTarget() {
        float target = 1.62F;
        float last = StanceTransition.blend(1.07F, target, EXIT_TICKS, EXIT_TICKS);
        assertClose(last, target, 1e-6F, "the exit curve lands on the pose height");
        float beyond = StanceTransition.blend(1.07F, target, EXIT_TICKS + 20F, EXIT_TICKS);
        assertClose(beyond, target, 1e-6F, "the exit curve stays clamped afterwards");
    }

    /**
     * A slide cancelled while still descending must exit from where it actually was.
     *
     * <p>Otherwise interrupting a slide would first jump down to the full slide height and then ease
     * back up, which reads far worse than the original snap.
     */
    private static void interruptedSlideExitsFromWhereItWas() {
        float midHeight = StanceTransition.blend(1.62F, 1.07F, 2F, StanceTransition.SLIDE_ENTER_TICKS);
        assertTrue(midHeight < 1.62F && midHeight > 1.07F, "the mid-slide height is between the two");
        float first = StanceTransition.blend(midHeight, 1.62F, 0F, EXIT_TICKS);
        assertClose(first, midHeight, 1e-6F, "an interrupted slide exits from its current height");
        assertTrue(first > 1.07F, "an interrupted slide does not first drop to the full slide height");
    }

    private static final int EXIT_TICKS = 4;

    /** The first frame must equal the starting eye height, otherwise the camera still snaps. */
    private static void startsWhereTheEyesWere() {
        float[] starts = {1.62F, 1.48F, 1.32F};
        for (float start : starts) {
            float first = StanceTransition.blend(start, 1.07F, 0F, StanceTransition.SLIDE_ENTER_TICKS);
            assertClose(first, start, 1e-6F, "t=0 equals the starting height (" + start + ")");
        }
    }

    /** The curve must land exactly on the target, or the camera would settle slightly off. */
    private static void endsAtSlideHeight() {
        float target = 1.07F;
        float last = StanceTransition.blend(1.62F, target,
                StanceTransition.SLIDE_ENTER_TICKS, StanceTransition.SLIDE_ENTER_TICKS);
        assertClose(last, target, 1e-6F, "t=duration equals the slide height");

        float beyond = StanceTransition.blend(1.62F, target,
                StanceTransition.SLIDE_ENTER_TICKS + 40F, StanceTransition.SLIDE_ENTER_TICKS);
        assertClose(beyond, target, 1e-6F, "past the duration stays clamped at the slide height");
    }

    /** A descent that ever rises would read as a bob during the drop. */
    private static void isMonotonic() {
        float start = 1.62F;
        float target = 1.07F;
        float previous = start;
        int steps = StanceTransition.SLIDE_ENTER_TICKS * 40;
        for (int i = 1; i <= steps; i++) {
            float elapsed = i / 40F;
            float value = StanceTransition.blend(start, target, elapsed, StanceTransition.SLIDE_ENTER_TICKS);
            assertTrue(value <= previous + 1e-6F, "never rises at t=" + elapsed);
            previous = value;
        }
    }

    /**
     * No single frame may carry a large share of the drop.
     *
     * <p>This is the actual complaint: the old behaviour put the whole 0.55 blocks into one frame. The
     * smoothstep's steepest step is about 1.875x the average, so a bound of a quarter of the total per
     * frame is comfortably loose and still catches a reversion to a snap.
     */
    private static void hasNoVisibleSteps() {
        float start = 1.62F;
        float target = 1.07F;
        float total = start - target;
        float previous = start;
        float largest = 0F;
        // 60 fps against a 20 Hz tick: three frames per tick is the realistic granularity.
        int frames = StanceTransition.SLIDE_ENTER_TICKS * 3;
        for (int i = 1; i <= frames; i++) {
            float elapsed = i / 3F;
            float value = StanceTransition.blend(start, target, elapsed, StanceTransition.SLIDE_ENTER_TICKS);
            largest = Math.max(largest, previous - value);
            previous = value;
        }
        assertTrue(largest > 0F, "the camera does move");
        assertTrue(largest <= total * 0.25F,
                "largest single-frame drop " + largest + " stays under a quarter of " + total);
    }

    /** Starting from a crouch must keep going down, not rise to standing height first. */
    private static void crouchStartDoesNotJumpUp() {
        float crouched = 1.32F;
        float target = 1.07F;
        float first = StanceTransition.blend(crouched, target, 0F, StanceTransition.SLIDE_ENTER_TICKS);
        assertTrue(first <= crouched + 1e-6F, "a slide from a crouch does not rise on frame one");
        float last = StanceTransition.blend(crouched, target,
                StanceTransition.SLIDE_ENTER_TICKS, StanceTransition.SLIDE_ENTER_TICKS);
        assertClose(last, target, 1e-6F, "a slide from a crouch still reaches the slide height");
    }

    /** Six ticks is short enough to stay responsive and long enough to read as motion. */
    private static void matchesDiveEntryPacing() {
        assertTrue(StanceTransition.SLIDE_ENTER_TICKS >= 4, "long enough to be visible");
        assertTrue(StanceTransition.SLIDE_ENTER_TICKS <= 10, "short enough to stay responsive");
        assertTrue(StanceTransition.SLIDE_ENTER_TICKS == 6, "matches the dive entry pacing");
    }

    private static void assertClose(float actual, float expected, float tolerance, String what) {
        checks++;
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError("FAILED: " + what + " (got " + actual + ", expected " + expected + ")");
        }
    }

    private static void assertTrue(boolean condition, String what) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + what);
        }
    }
}
