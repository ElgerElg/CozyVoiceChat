package net.elgerelg.cozyvoicechat.api.addon;

/**
 * A registered addon, as listed by {@link net.elgerelg.cozyvoicechat.api.CozyVoiceApi#getAddons()}
 * and {@code /cozyvoice addons}.
 *
 * @param id         the id from {@link CozyVoicePlugin#getId()}
 * @param modId      id of the Forge mod whose jar contains the addon class
 * @param modVersion version of that mod, from its {@code mods.toml}
 * @param className  fully qualified name of the {@link CozyVoiceAddon} class
 * @since 1.1.0
 */
public record AddonInfo(String id, String modId, String modVersion, String className) {
}
