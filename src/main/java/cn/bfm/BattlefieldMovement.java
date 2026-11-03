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
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.bfm.client.ClientEvents.init();
            LOG.info("Battlefield Movement client ready: slide + prone share the stance key");
        }
    }
}
