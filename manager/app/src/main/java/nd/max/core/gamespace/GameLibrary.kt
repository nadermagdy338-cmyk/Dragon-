/* Copyright (C) 2026 Nader Magdy. All rights reserved. */
package nd.max.core.gamespace

import nd.max.core.platform.ForegroundAppResolver

data class GameApp(val packageName: String, val label: String, val detectedGame: Boolean)

fun gameLibrary(apps: List<GameApp>, manual: Set<String>, excluded: Set<String> = emptySet()): List<GameApp> = apps
    .filter { ForegroundAppResolver.isPackageName(it.packageName) }
    .distinctBy { it.packageName }
    .filter { it.packageName !in excluded && (it.detectedGame || it.packageName in manual) }
    .sortedWith(compareBy<GameApp> { it.label.lowercase() }.thenBy { it.packageName })

/** Exclusion is explicit user intent and wins over automatic detection. No hardware profile is removed. */
data class GameLibraryMembership(val manual: Set<String>, val excluded: Set<String>) {
    fun change(pkg: String, include: Boolean): GameLibraryMembership {
        require(ForegroundAppResolver.isPackageName(pkg))
        return if (include) copy(manual = manual + pkg, excluded = excluded - pkg)
        else copy(manual = manual - pkg, excluded = excluded + pkg)
    }
}

/** Bound stored metadata without dropping valid but temporarily invisible/work-profile packages. */
fun validGamePackages(packages: Set<String>): Set<String> {
    require(packages.size <= GameProfileDocument.MAX_APPS)
    require(packages.all(ForegroundAppResolver::isPackageName))
    return packages.toSet()
}

/** Extension is a hint, never proof of the platform or of a launch contract. */
fun romSystemHint(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "nes" -> "NES"
    "sfc", "smc" -> "SNES"
    "gb", "gbc" -> "GB/GBC"
    "gba" -> "GBA"
    "nds" -> "NDS"
    "iso", "cso", "bin", "chd", "cue", "zip", "7z", "m3u" -> "AMBIGUOUS"
    "exe" -> "PC"
    else -> null
}

enum class VisibilityChange { NO_BASELINE, LOWER, SAME, HIGHER, INCOMPARABLE }
fun compareVisibility(baseline: Int?, current: Int?): VisibilityChange = when {
    current == null || (baseline != null && baseline < 0) || current < 0 -> VisibilityChange.INCOMPARABLE
    baseline == null -> VisibilityChange.NO_BASELINE
    current < baseline -> VisibilityChange.LOWER
    current > baseline -> VisibilityChange.HIGHER
    else -> VisibilityChange.SAME
}
