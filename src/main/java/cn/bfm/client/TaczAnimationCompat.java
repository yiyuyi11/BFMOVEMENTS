package cn.bfm.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Optional TaCZ PlayerAnimator control. TaCZ's lower-body layer keeps playing a
 * standing walk/hold animation while we pose the prone body, which shows up as
 * a second "stand up" motion. Stop its layers when our stance starts; the
 * lower-body update is also cancelled by TaczProneAnimatorMixin.
 */
public final class TaczAnimationCompat {
    private static final boolean AVAILABLE = ModList.get().isLoaded("tacz");
    private static boolean resolved;
    private static java.lang.reflect.Method stop;
    private static boolean immediate;

    public static void warmUp() {
        if (resolved || !AVAILABLE) return;
        resolved = true;
        try {
            Class<?> type = Class.forName("com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat");
            try {
                stop = type.getMethod("stopAllAnimation", net.minecraft.world.entity.LivingEntity.class, int.class);
                immediate = true;
            } catch (NoSuchMethodException missing) {
                stop = type.getMethod("stopAllAnimation", net.minecraft.world.entity.LivingEntity.class);
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            cn.bfm.BattlefieldMovement.LOG.warn("TaCZ animation stop bridge unavailable", ex);
        }
    }

    public static void stopAll(AbstractClientPlayer player) {
        if (!AVAILABLE || player == null) {
            return;
        }
        warmUp();
        if (stop == null) return;
        try {
            if (immediate) stop.invoke(null, player, 0);
            else stop.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError ex) {
            stop = null;
            cn.bfm.BattlefieldMovement.LOG.warn("TaCZ animation stop bridge disabled after failure", ex);
        }
    }

    private TaczAnimationCompat() {
    }
}
