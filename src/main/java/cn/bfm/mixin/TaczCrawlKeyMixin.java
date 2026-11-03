package cn.bfm.mixin;

import net.neoforged.neoforge.client.event.InputEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TaCZ's own crawl key is replaced by the stance key.  Without this, pressing Z
 * would toggle TaCZ crawl and this mod's prone at the same time.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.input.CrawlKey", remap = false)
public abstract class TaczCrawlKeyMixin {
    @Inject(method = "onCrawlPress", at = @At("HEAD"), cancellable = true, require = 0)
    private static void battlefieldMovement$ownStanceKey(InputEvent.Key event, CallbackInfo ci) {
        ci.cancel();
    }
}
