package cn.bfm;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * 战地机动 / Battlefield Movement.
 *
 * <p>One stance key (Z by default) owns both short slides and prone:
 * sprinting + Z slides, every other press toggles prone.  When BattlefieldOps
 * is installed its own Z handler yields to this mod (see the patched
 * {@code cn.bfops.crawl.mixin.client.TaczCrawlKeyMixin}); when it is absent this
 * mod still replaces TaCZ's native crawl key so one key cannot toggle twice.</p>
 */
@Mod(BattlefieldMovement.MODID)
public final class BattlefieldMovement {
    public static final String MODID = "battlefield_movement";
    public static final Logger LOG = LogUtils.getLogger();
    /** Runtime override flipped by the client key; the config remains the persisted default. */
    private static Boolean runtimeOverride;

    /** True when the mod should act. Guarded so a half-loaded config cannot throw during startup. */
    public static boolean enabled() {
        if (runtimeOverride != null) return runtimeOverride;
        try {
            return BfmConfig.MASTER_ENABLED == null || BfmConfig.MASTER_ENABLED.get();
        } catch (RuntimeException | LinkageError notLoaded) {
            return true;
        }
    }

    /** Personal toggle (client key): does not write the config file. */
    public static boolean toggleLocal() {
        runtimeOverride = !enabled();
        LOG.info("Battlefield Movement {}", runtimeOverride ? "enabled" : "disabled");
        return runtimeOverride;
    }

    /** World toggle (/bfm toggle): persists through the common config. */
    public static boolean toggleConfig() {
        boolean next = !enabled();
        runtimeOverride = null;
        BfmConfig.MASTER_ENABLED.set(next);
        BfmConfig.MASTER_ENABLED.save();
        LOG.info("Battlefield Movement {} (config)", next ? "enabled" : "disabled");
        return next;
    }

    public BattlefieldMovement(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BfmConfig.SPEC, "battlefield-movement-common.toml");
        modBus.addListener(BfmNetwork::register);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(BfmKeys::register);
            modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
                    event.enqueueWork(() -> {
                        cn.bfm.client.TaczAnimationCompat.warmUp();
                        cn.bfm.client.prone.PronePoseData.available();
                    }));
        }
        NeoForge.EVENT_BUS.register(new CommonEvents());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event) ->
                event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder
                        .<net.minecraft.commands.CommandSourceStack>literal("bfm")
                        .executes(ctx -> {
                            boolean on = enabled();
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    "Battlefield Movement: " + (on ? "on" : "off")), false);
                            return 1;
                        })
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder
                                .<net.minecraft.commands.CommandSourceStack>literal("toggle")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    boolean on = toggleConfig();
                                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                            "Battlefield Movement " + (on ? "enabled" : "disabled")), true);
                                    return 1;
                                }))));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.bfm.client.ClientEvents.init();
            LOG.info("Battlefield Movement client ready: slide + prone share the stance key");
        }
    }
}
