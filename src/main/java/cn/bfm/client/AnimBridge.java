package cn.bfm.client;

import cn.bfm.BattlefieldMovement;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optional PlayerAnimator bridge.
 *
 * <p>Every reference to PlayerAnimator lives in {@link Impl}; the outer class only
 * touches it when the mod is present, so a pack without PlayerAnimator still
 * loads this mod. All transitions use PlayerAnimator's fade modifier instead of
 * swapping clips abruptly.</p>
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
        private static final java.util.Set<String> MISSING = new java.util.HashSet<>();

        private static void play(AbstractClientPlayer player, String animation, int fadeTicks) {
            try {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BattlefieldMovement.MODID, animation);
                dev.kosmx.playerAnim.api.IPlayable playable =
                        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry.getAnimation(id);
                if (playable == null) {
                    if (MISSING.add(animation)) BattlefieldMovement.LOG.warn("Missing movement animation {}", id);
                    return;
                }
                dev.kosmx.playerAnim.api.layered.AnimationStack stack =
                        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess.getPlayerAnimLayer(player);
                var layer = LAYERS.get(player.getUUID());
                if (layer == null) {
                    layer = new dev.kosmx.playerAnim.api.layered.ModifierLayer<>();
                    stack.addAnimLayer(1000, layer);
                    LAYERS.put(player.getUUID(), layer);
                }
                BY_ENTITY.put(player.getId(), player.getUUID());
                var actual = playable.playAnimation();
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
