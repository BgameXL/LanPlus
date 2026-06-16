/**
 * Support layer: client-only code — UI, input, rendering, local state.
 *
 * Everything here must be guarded for the physical client (e.g.
 * {@code @Mod.EventBusSubscriber(value = Dist.CLIENT)}). Keep client-only imports out of common
 * code so the mod still loads on a dedicated server. Client impls of the domain interfaces (skin
 * texture binding, screens, keybinds) live here.
 */
package dev.bgame.lanplus.client;
