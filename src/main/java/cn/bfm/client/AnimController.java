package cn.bfm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Drives the PlayerAnimator layers for slide, dive, prone and the back roll.
 *
 * <p>Every transition is a cross-fade, never a hard {@code setAnimation}. That
 * is what removes the visible pop between "enter" and "idle" clips.</p>
 */
public final class AnimController {
    private static final Map<Integer, State> STATES = new HashMap<>();

    private enum Kind {
        SLIDE_START, SLIDE_LOOP, DIVE, PRONE_ENTER, PRONE_IDLE, PRONE_BACK, PRONE_EXIT
    }

    private static final class State {
        Kind kind;
        int ticks;

        State(Kind kind) {
            this.kind = kind;
        }
    }

    public static void onSlide(int entityId, boolean active) {
        if (entityId < 0) {
            return;
        }
        if (!active) {
            STATES.remove(entityId);
            AnimBridge.stop(entityId, 2);
            return;
        }
        play(entityId, "slide_start", 2);
        STATES.put(entityId, new State(Kind.SLIDE_START));
    }

    public static void onDive(int entityId, boolean active) {
        // Dive is rendered by DivePoseRenderer so it does not depend on the
        // PlayerAnimator layer being available.
        STATES.remove(entityId);
        AnimBridge.stop(entityId, 2);
    }

    public static void onProne(int entityId, int phase) {
        // Prone is rendered exclusively by PronePoseRenderer. Never fall back to
        // a PlayerAnimator clip here: running both was what produced a second
        // "animated" stand-up on top of the custom pose.
        STATES.remove(entityId);
        // Drop the old layer immediately when the custom pose takes ownership.
        AnimBridge.stop(entityId, 0);
    }

    public static void tick() {
        Iterator<Map.Entry<Integer, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, State> entry = it.next();
            State state = entry.getValue();
            state.ticks++;
            switch (state.kind) {
                case SLIDE_START -> {
                    if (state.ticks >= 8) {
                        play(entry.getKey(), "slide_loop", 2);
                        state.kind = Kind.SLIDE_LOOP;
                        state.ticks = 0;
                    }
                }
                case PRONE_ENTER -> {
                    if (state.ticks >= 10) {
                        play(entry.getKey(), "prone_idle", 5);
                        state.kind = Kind.PRONE_IDLE;
                        state.ticks = 0;
                    }
                }
                case PRONE_EXIT -> {
                    if (state.ticks >= 9) {
                        it.remove();
                        AnimBridge.stop(entry.getKey(), 4);
                    }
                }
                case DIVE -> {
                    if (state.ticks >= 14) {
                        it.remove();
                        AnimBridge.stop(entry.getKey(), 4);
                    }
                }
                default -> {
                }
            }
        }
    }

    public static void reset() {
        for (Integer id : STATES.keySet()) {
            AnimBridge.stop(id, 0);
        }
        STATES.clear();
    }

    private static void play(int entityId, String animation, int fadeTicks) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity entity = client.level.getEntity(entityId);
        if (entity instanceof AbstractClientPlayer player) {
            AnimBridge.play(player, animation, fadeTicks);
        }
    }

    private AnimController() {
    }
}
