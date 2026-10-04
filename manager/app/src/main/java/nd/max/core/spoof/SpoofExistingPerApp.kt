/*
 * Copyright (C) 2026 Nader Magdy. All rights reserved.
 */
package nd.max.core.spoof

/**
 * A per-app key that already exists in this app. The spoof surface **reads** it and never writes it,
 * so one knob keeps exactly one writer.
 */
data class ExistingPerAppKnob(
    val key: String,
    val owner: String,
    /** False = nothing in `AppConfig` decodes this key, so a stored value there is dropped. */
    val consumedByConfig: Boolean,
)

/**
 * SP-09 — the measured per-app keys and their single owner.
 *
 * `resolution_target` is listed because the shipped `maxmanagerApplist.json` seed carries it, and
 * **not** because it works: `AppConfig` declares `resolution_downscale` instead and every reader
 * uses `ignoreUnknownKeys = true`, so a value stored under `resolution_target` is dropped. That is
 * reported here rather than fixed, since it is outside this plan's scope (ADR-18).
 */
object SpoofExistingPerApp {
    val DISPLAY_KEYS = listOf(
        ExistingPerAppKnob("refresh_rate", "ui/viewmodel/AppSettingsViewmodel.kt:201", true),
        ExistingPerAppKnob("renderer", "ui/viewmodel/AppSettingsViewmodel.kt:202", true),
        ExistingPerAppKnob("resolution_target", "maxmanagerApplist.json seed", false),
    )

    /**
     * Read-only field extraction from the app-list JSON text, mirroring the escaping rule the
     * background monitor already uses: the package name is escaped before it becomes a pattern.
     *
     * Returns `null` when the package block or the key is absent — an unreadable source is never
     * reported as an empty string, and a `default` value is returned as the literal it is.
     */
    fun value(json: String, packageName: String, key: String): String? {
        if (!SpoofWorkspace.validPackage(packageName)) return null
        val block = Regex("\"" + Regex.escape(packageName) + "\"\\s*:\\s*\\{([^{}]*)\\}")
            .find(json)?.groupValues?.get(1) ?: return null
        return Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"([^\"]*)\"").find(block)?.groupValues?.get(1)
    }
}
