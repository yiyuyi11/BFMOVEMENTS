package cn.bfm;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Independent stance keys.
 *
 * <ul>
 *   <li>{@code PRONE} (Z): while sprinting a dive, otherwise prone / stand.</li>
 *   <li>{@code SLIDE} (left alt): direct slide, in addition to sprint + sneak.</li>
 * </ul>
 */
public final class BfmKeys {
    public static final String CATEGORY = "key.categories.battlefield_movement";

    public static final KeyMapping PRONE = new KeyMapping(
            "key.battlefield_movement.prone", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping SLIDE = new KeyMapping(
            "key.battlefield_movement.slide", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    /** Unbound by default: a personal on/off switch for the whole mod. */
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.battlefield_movement.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PRONE);
        event.register(SLIDE);
        event.register(TOGGLE);
    }

    private BfmKeys() {
    }
}
