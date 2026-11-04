package cn.bfm;

import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
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
    /** State the server pushed; null until the first message arrives. */
    private static volatile Boolean remoteEnabled;

    public static boolean enabled() {
        if (runtimeOverride != null) return runtimeOverride;
        if (remoteEnabled != null) return remoteEnabled;
        try {
            return BfmConfig.MASTER_ENABLED == null || BfmConfig.MASTER_ENABLED.get();
        } catch (RuntimeException | LinkageError notLoaded) {
            return true;
        }
    }

    /** Where the current answer comes from, for /bfm and the client feedback message. */
    public static String source() {
        if (runtimeOverride != null) return "local key";
        if (remoteEnabled != null) return "server";
        return "server config";
    }

    /** Applied on the client when the server pushes its state. A server "on" clears a stale local override. */
    public static void acceptRemote(boolean state) {
        remoteEnabled = state;
        if (state) runtimeOverride = null;
        LOG.info("Battlefield Movement {} by server", state ? "enabled" : "disabled");
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
        remoteEnabled = null;
        BfmConfig.MASTER_ENABLED.set(next);
        BfmConfig.MASTER_ENABLED.save();
        LOG.info("Battlefield Movement {} (config)", next ? "enabled" : "disabled");
        return next;
    }

    public BattlefieldMovement() {
        IEventBus modBus=net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        var container=net.minecraftforge.fml.ModLoadingContext.get();
        container.registerConfig(ModConfig.Type.COMMON, BfmConfig.SPEC, "battlefield-movement-common.toml");
        BfmNetwork.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(BfmKeys::register);
            modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                    event.enqueueWork(() -> {
                        cn.bfm.client.TaczAnimationCompat.warmUp();
                        cn.bfm.client.prone.PronePoseData.available();
                    }));
        }
        MinecraftForge.EVENT_BUS.register(new CommonEvents());
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.RegisterCommandsEvent event) ->
                event.getDispatcher().register(com.mojang.brigadier.builder.LiteralArgumentBuilder
                        .<net.minecraft.commands.CommandSourceStack>literal("bfm")
                        .executes(ctx -> {
                            boolean on = enabled();
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                    "Battlefield Movement: " + (on ? "on" : "off") + " (" + source() + ")"), false);
                            return 1;
                        })
                        .then(SlideSettingsCommand.build())
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder
                                .<net.minecraft.commands.CommandSourceStack>literal("toggle")
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> {
                                    boolean on = toggleConfig();
                                    MasterSwitch.broadcast();
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
