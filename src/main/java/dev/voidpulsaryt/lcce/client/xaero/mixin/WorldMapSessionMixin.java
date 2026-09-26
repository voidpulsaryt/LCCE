package dev.voidpulsaryt.lcce.client.xaero.mixin;

import dev.voidpulsaryt.lcce.client.xaero.LcceClaimHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xaero.map.highlight.HighlighterRegistry;

/**
 * {@code HighlighterRegistry} (see {@link LcceClaimHighlighter}) has no public registration event -
 * it's a short-lived object built fresh inside {@code WorldMapSession.init()} and sealed by its own
 * {@code end()} call before anything outside that method could reach it. The only way in, matching
 * the real {@code ftbxaerocompat} mod's own {@code WorldMapSessionMixin} exactly, is redirecting
 * that {@code end()} call to register first. {@code targets} (a string, not a {@code Class}
 * literal) so this mixin's own class can still be loaded - and simply not applied - on a client
 * that doesn't have Xaero's World Map installed at all, since the World Map is an optional
 * dependency for the rest of this mod.
 */
@Mixin(targets = "xaero.map.WorldMapSession", remap = false)
public class WorldMapSessionMixin {

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Lxaero/map/highlight/HighlighterRegistry;end()V"),
            remap = false
    )
    private void lcce$registerHighlighterBeforeEnd(HighlighterRegistry registry) {
        registry.register(new LcceClaimHighlighter());
        registry.end();
    }
}
