package cn.bfm.client.prone;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.MinecraftForge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional TaCZ probe used to keep gun animations from being overwritten by the
 * prone pose (which is what showed up as detached arms while shooting/reloading).
 */
public final class TaczProneCompat {
    private static final boolean AVAILABLE = ModList.get().isLoaded("tacz");
    private static final Map<UUID, Long> LAST_SHOT = new ConcurrentHashMap<>();

    static {
        if (AVAILABLE) {
            try {
                MinecraftForge.EVENT_BUS.register(new Impl());
            } catch (Throwable ignored) {
                // TaCZ absent or its event changed; reload detection still works.
            }
        }
    }

    public static boolean isGunAction(AbstractClientPlayer player) {
        if (player == null) {
            return false;
        }
        // Other gun mods (Superb Warfare etc.) animate the player's arms through
        // their own layers. Keep those arms instead of fighting them; the body,
        // head and legs still use the prone pose.
        if (isExternalGun(player)) {
            return true;
        }
        if (!AVAILABLE) {
            return false;
        }
        Long shot = LAST_SHOT.get(player.getUUID());
        if (shot != null && player.level().getGameTime() - shot < 8L) {
            return true;
        }
        try {
            return Impl.isReloading(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isFirearm(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (AVAILABLE && Impl.isGun(stack)) return true;
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.atsuishio.superbwarfare.item.gun.GunItem")) return true;
        }
        return false;
    }

    private static boolean isExternalGun(AbstractClientPlayer player) {
        String namespace = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).getNamespace();
        return namespace.equals("superbwarfare");
    }

    private static final class Impl {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public void onShoot(com.tacz.guns.api.event.common.GunShootEvent event) {
            if (event.getShooter() instanceof AbstractClientPlayer player) {
                LAST_SHOT.put(player.getUUID(), player.level().getGameTime());
            }
        }

        private static boolean isGun(net.minecraft.world.item.ItemStack stack) {
            return stack.getItem() instanceof com.tacz.guns.api.item.IGun;
        }

        private static boolean isReloading(AbstractClientPlayer player) {
            var operator = com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player);
            if (operator == null) {
                return false;
            }
            var state = operator.getSynReloadState();
            return state != null && state.getStateType() != null && state.getStateType().isReloading();
        }

        private Impl() {
        }
    }

    private TaczProneCompat() {
    }
}
