package cn.bfm.client;

import cn.bfm.BattlefieldMovement;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optional PlayerAnimator bridge for the 1.20.1 line (player-animation-lib 1.0.x).
 *
 * <p>Every reference to PlayerAnimator lives in {@link Impl}; the outer class only touches it when the mod is
 * present, so a pack without PlayerAnimator still loads this mod. 1.0.x differs from the 2.x API the 1.21 build
 * uses: the animation registry returns a {@code KeyframeAnimation} that has to be wrapped in a
 * {@code KeyframeAnimationPlayer}.
 */
public final class AnimBridge {
    private static final boolean AVAILABLE = ModList.get().isLoaded("playeranimator");

    public static boolean available() {
        return AVAILABLE;
    }

    public static void play(AbstractClientPlayer player, String animation, int fadeTicks) {
        if (AVAILABLE && player != null) {
            Impl.play(player, animation, fadeTicks);
        }
    }

    public static void stop(int entityId, int fadeTicks) {
        if (AVAILABLE) {
            Impl.stop(entityId, fadeTicks);
        }
    }

    public static void clearAll() {
        if (AVAILABLE) {
            Impl.clearAll();
        }
    }

    private static final class Impl {
        private static final Map<UUID, dev.kosmx.playerAnim.api.layered.ModifierLayer<
                dev.kosmx.playerAnim.api.layered.IAnimation>> LAYERS = new HashMap<>();
        private static final Map<Integer, UUID> BY_ENTITY = new HashMap<>();
        private static final java.util.Set<String> animationWarned = new java.util.HashSet<>();

        private static void play(AbstractClientPlayer player, String animation, int fadeTicks) {
            try {
                ResourceLocation id = new ResourceLocation(BattlefieldMovement.MODID, animation);
                dev.kosmx.playerAnim.core.data.KeyframeAnimation keyframes =
                        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(id);
                if (keyframes == null) {
                    // This Forge library searches assets/<ns>/player_animation.
                    if (animationWarned.add(id.toString())) {
                        cn.bfm.BattlefieldMovement.LOG.warn("[BFM] animation not found: {} (registry size {})",
                                id, dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimations().size());
                    }
                    return;
                }
                var stack = dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(player);
                var layer = LAYERS.get(player.getUUID());
                if (layer == null) {
                    layer = new dev.kosmx.playerAnim.api.layered.ModifierLayer<>();
                    stack.addAnimLayer(1000, layer);
                    LAYERS.put(player.getUUID(), layer);
                }
                BY_ENTITY.put(player.getId(), player.getUUID());
                var actual = new dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer(keyframes);
                actual.setFirstPersonMode(dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode.NONE);
                if (fadeTicks > 0 && layer.getAnimation() != null && layer.getAnimation().isActive()) {
                    layer.replaceAnimationWithFade(
                            dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier
                                    .standardFadeIn(fadeTicks, dev.kosmx.playerAnim.core.util.Ease.INOUTSINE),
                            actual);
                } else {
                    layer.setAnimation(actual);
                }
            } catch (IllegalArgumentException | LinkageError ignored) {
                // Player is not ready yet (login) or the animation failed to load.
            }
        }

        private static void stop(int entityId, int fadeTicks) {
            UUID uuid = BY_ENTITY.remove(entityId);
            if (uuid == null) {
                return;
            }
            var layer = LAYERS.get(uuid);
            if (layer == null) {
                return;
            }
            if (fadeTicks > 0 && layer.getAnimation() != null && layer.getAnimation().isActive()) {
                layer.replaceAnimationWithFade(
                        dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier
                                .standardFadeIn(fadeTicks, dev.kosmx.playerAnim.core.util.Ease.INOUTSINE),
                        null);
            } else {
                layer.setAnimation(null);
            }
        }

        private static void clearAll() {
            for (var layer : LAYERS.values()) {
                layer.setAnimation(null);
            }
            LAYERS.clear();
            BY_ENTITY.clear();
        }

        private Impl() {
        }
    }

    private AnimBridge() {
    }
}
