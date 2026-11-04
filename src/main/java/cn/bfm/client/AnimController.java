package cn.bfm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** Render-time slide envelope shared by local prediction and remote slide packets. */
public final class AnimController {
    private static final Map<Integer, Slide> SLIDES = new HashMap<>();
    private static final int ENTER = 4;
    private static final int EXIT = 4;

    private record Slide(long start, long end, float endWeight) {
        float weight(double time) {
            if (end >= 0) return endWeight * (1 - smooth((time - end) / EXIT));
            return smooth((time - start) / ENTER);
        }
    }

    public static void onSlide(int entityId, boolean active) {
        var level = Minecraft.getInstance().level;
        if (entityId < 0 || level == null) return;
        long now = level.getGameTime();
        Slide current = SLIDES.get(entityId);
        if (active) {
            if (current != null && current.end < 0) return;
            SLIDES.put(entityId, new Slide(now, -1, 0));
            if (level.getEntity(entityId) instanceof AbstractClientPlayer player) {
                TaczAnimationCompat.stopAll(player);
            }
        } else if (current != null && current.end < 0) {
            SLIDES.put(entityId, new Slide(current.start, now, current.weight(now)));
        }
    }

    public static float slideWeight(AbstractClientPlayer player, float partial) {
        Slide slide = SLIDES.get(player.getId());
        return slide == null ? 0 : slide.weight(player.level().getGameTime() + partial);
    }

    public static boolean ownsSlide(AbstractClientPlayer player) {
        return SLIDES.containsKey(player.getId());
    }

    public static void onDive(int entityId, boolean active) { SLIDES.remove(entityId); }
    public static void onProne(int entityId, int phase) { SLIDES.remove(entityId); }

    public static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) { reset(); return; }
        long now = level.getGameTime();
        SLIDES.entrySet().removeIf(entry -> {
            Slide slide = entry.getValue();
            return slide.end >= 0 ? now - slide.end >= EXIT : now - slide.start > 80;
        });
    }

    public static void reset() { SLIDES.clear(); }

    private static float smooth(double value) {
        float t = (float) Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private AnimController() {}
}
